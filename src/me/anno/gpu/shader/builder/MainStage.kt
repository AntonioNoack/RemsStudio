package me.anno.gpu.shader.builder

import me.anno.gpu.deferred.DeferredSettingsV2

class MainStage {

    // defined via in vec<s> name
    val attributes = ArrayList<Variable>()

    // color & position computations
    val stages = ArrayList<ShaderStage>()

    // define existing constants
    val defined = HashSet<Variable>()

    fun add(stage: ShaderStage) {
        stages.add(stage)
        for (function in stage.functions) {
            defineFunction(function)
        }
    }

    val imported = HashSet<Variable>()
    val exported = HashSet<Variable>()

    val uniforms = HashSet<Variable>()

    fun define(variable: Variable) {
        defined.add(variable)
    }

    fun findImportsAndDefineValues(
        previous: MainStage?, previousDefined: Set<Variable>?
    ): Set<Variable> {
        val defined = HashSet(defined)
        defined += attributes
        val definedByPrevious = HashSet<Variable>()
        if (previousDefined != null) {
            for (value in previousDefined) {
                if (value !in defined) {
                    definedByPrevious += value
                }
            }
        }
        uniforms.clear()
        for (stage in stages) {
            for (variable in stage.parameters) {
                if (variable.isInput) {
                    if (variable !in defined) {
                        if (variable in definedByPrevious) {
                            // we need this variable
                            imported.add(variable)
                            previous?.exported?.add(variable)
                        } else {
                            uniforms.add(variable)
                            defined += variable
                        }
                    }
                }
            }
            for (variable in stage.parameters) {
                if (variable.isOutput) {
                    defined += variable
                    // now it's locally overridden
                    definedByPrevious.remove(variable)
                }
            }
        }
        return defined
    }

    fun createCode(isFragmentStage: Boolean, deferredSettingsV2: DeferredSettingsV2?): Pair<String, List<Variable>> {

        // set what is all defined
        defined += imported

        val code = StringBuilder()

        for (stage in stages) {
            for (define in stage.defines) {
                code.append("#define ")
                code.append(define)
                code.append('\n')
            }
        }

        if (isFragmentStage) {
            // fragment shader
            if (deferredSettingsV2 == null) {
                code.append("out vec4 glFragColor;\n")
            } else {
                // register all of the layers
                deferredSettingsV2.appendLayerDeclarators(code)
            }
            code.append('\n')
        }

        for (variable in attributes.sortedBy { it.size }) {
            variable.appendGlsl(code, "attribute ")
        }
        if (attributes.isNotEmpty()) code.append('\n')

        val varying = (imported + exported).toList()
        /*for (variable in imported.sortedBy { it.size }) {
            variable.appendGlsl(code, if (variable.isFlat) "flat varying " else "varying ")
        }
        if (imported.isNotEmpty()) code.append('\n')

        for (variable in exported.sortedBy { it.size }) {
            variable.appendGlsl(code, "varying ")
        }
        if (exported.isNotEmpty()) code.append('\n')*/

        // define the missing variables
        // sorted by size, so small uniforms get a small location,
        // which in return allows them to be cached
        for (variable in uniforms.sortedBy { it.size }) {
            variable.appendGlsl(code, "uniform ")
        }
        if (uniforms.isNotEmpty()) code.append('\n')

        // define all required functions
        for ((_, func) in functions) {
            code.append(func)
        }
        if (functions.isNotEmpty()) code.append('\n')

        code.append("void main(){\n")

        val defined = HashSet(defined)
        defined += uniforms
        defined += imported
        defined += exported

        for (i in stages.indices) {
            val stage = stages[i]
            code.append("// start of stage ${stage.callName}\n")
            val params = stage.parameters
            // if this function defines a variable, which has been undefined before, define it
            for (param in params) {
                if (param.isOutput && param !in defined) {
                    param.appendGlsl(code, "")
                    defined += param
                }
            }
            code.append("// stage\n")
            code.append(stage.body)
            if (!code.endsWith('\n')) code.append('\n')
            code.append("// end of stage\n")
            // expensive
            // inlining is better
            // maybe only because we had this large matrix as a param, but still, it was massively slower (30fps -> 7fps)
            /*code.append("  ")
            code.append(stage.callName)
            code.append("(")
            for (j in params.indices) {
                if (j > 0) code.append(',')
                code.append(params[j].name)
            }
            code.append(");\n")*/
        }

        if (isFragmentStage) {
            // fragment shader
            if (deferredSettingsV2 == null) {
                // use last layer, if defined
                val lastLayer = stages.last()
                val lastOutputs = lastLayer.parameters.filter { it.isOutput }
                val outputSum = lastOutputs.sumOf {
                    when (it.type) {
                        "float" -> 1
                        "vec2" -> 2
                        "vec3" -> 3
                        "vec4" -> 4
                        else -> Int.MAX_VALUE
                    }
                }
                if (outputSum in 1..4) {
                    if (outputSum == 4 && lastOutputs.size == 1) {
                        code.append("glFragColor = ${lastOutputs[0].name};\n")
                    } else {
                        code.append("glFragColor = vec4(")
                        for (i in lastOutputs.indices) {
                            if (i > 0) code.append(',')
                            code.append(lastOutputs[i].name)
                        }
                        for (i in outputSum until 4) {
                            code.append(",1")
                        }
                        code.append(");\n")
                    }
                } else {
                    code.append("glFragColor = vec4(finalColor, finalAlpha);\n")
                }
            } else {

                val layerTypes = deferredSettingsV2.layerTypes
                for (type in layerTypes) {
                    // write the default values, if not already defined
                    if (type.glslName !in defined.map { it.name }) {
                        type.appendDefinition(code)
                        code.append(" = ")
                        type.appendDefaultValue(code)
                        code.append(";\n")
                    }
                }

                deferredSettingsV2.appendLayerWriters(code)

            }
        }
        code.append("}\n")
        return code.toString() to varying
    }

    val functions = HashMap<String, String>()

    private fun defineFunction(function: Function) {
        functions[function.header] = function.body
    }

}