package me.anno.ecs.components.light

import me.anno.ecs.Component
import me.anno.ecs.components.mesh.Mesh
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.engine.pbr.DeferredRenderer
import me.anno.gpu.ShaderLib
import me.anno.gpu.deferred.DeferredLayerType
import me.anno.gpu.shader.BaseShader
import me.anno.gpu.shader.builder.Variable
import me.anno.io.serialization.NotSerializedProperty
import me.anno.io.serialization.SerializedProperty
import org.joml.Matrix4x3f
import org.joml.Vector3f

abstract class LightComponent : Component() {

    // todo instead of using the matrix directly, define a matrix
    // todo this matrix then can include the perspective transform if required

    // todo how do we get the normal then?
    // todo just divide by z?


    // todo for shadow mapping, we need this information...


    enum class ShadowMapType {
        CUBEMAP,
        PLANE
    }

    var shadowMapCascades = 0
    var shadowMapPower = 4f

    var isInstanced = false

    override fun copy(clone: PrefabSaveable) {
        super.copy(clone)
        clone as LightComponent
        clone.isInstanced = isInstanced
        clone.shadowMapCascades = shadowMapCascades
        clone.shadowMapPower = shadowMapPower
        clone.color = color
    }

    // black lamp light?
    @SerializedProperty
    var color: Vector3f = Vector3f(1f)

    abstract fun getLightPrimitive(): Mesh

    // todo glsl?
    // todo AES lights, and their textures?
    // todo shadow map type

    // todo update/calculate shadow maps

    // is set by the pipeline
    @NotSerializedProperty
    val invWorldMatrix = Matrix4x3f()

    companion object {

        // todo plane of light... how?
        // todo lines of light... how?

        val lightShaders = HashMap<String, BaseShader>()
        fun getShader(sample: LightComponent): BaseShader {
            return lightShaders.getOrPut(sample.className) {
                val deferred = DeferredRenderer.deferredSettings!!
                ShaderLib.createShaderPlus(
                    "PointLight",
                    ShaderLib.v3DBase +
                            "a3 coords;\n" +
                            "uniform mat4x3 localTransform;\n" +
                            "void main(){\n" +
                            "   finalPosition = coords;\n" +
                            "   finalPosition = localTransform * vec4(finalPosition, 1.0);\n" +
                            "   gl_Position = transform * vec4(finalPosition, 1.0);\n" +
                            "   center = localTransform * vec4(0,0,0,1);\n" +
                            "   uvw = gl_Position.xyw;\n" +
                            ShaderLib.positionPostProcessing +
                            "}", ShaderLib.y3D + Variable("vec3","center"), "" +
                            "float getIntensity(vec3 dir){\n" +
                            "   return 1.0;\n" +
                            // todo fix
                            //"" + sample.getFalloffFunction() +
                            "}\n" +
                            "uniform vec3 uColor;\n" +
                            "uniform mat3 invLocalTransform;\n" +
                            "uniform sampler2D ${deferred.settingsV1.layerNames.joinToString()};\n" +
                            // roughness / metallic + albedo defines reflected light points -> needed as input as well
                            // todo roughness / metallic + albedo defines reflected light points -> compute them
                            // todo environment map is required for brilliant results as well
                            "void main(){\n" +
                            "   vec2 uv = uvw.xy/uvw.z*.5+.5;\n" +
                            "   vec3 globalDelta = ${DeferredLayerType.POSITION.getValue(deferred)} - center;\n" +
                            "   vec3 localDelta = invLocalTransform * globalDelta;\n" + // transform into local coordinates
                            "   vec3 nor = ${DeferredLayerType.NORMAL.getValue(deferred)};\n" +
                            "   float intensity = getIntensity(localDelta * 5.0) * max(0.0, -dot(globalDelta, nor));\n" +
                            "   vec3 finalColor = uColor * intensity;\n" +
                            "   float finalAlpha = 0.125;\n" +
                            "}", deferred.settingsV1.layerNames
                )
            }
        }

    }

}