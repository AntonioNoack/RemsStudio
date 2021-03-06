package me.anno.ecs.components.mesh

import me.anno.gpu.blending.PipelineStage
import me.anno.gpu.shader.BaseShader
import me.anno.gpu.shader.Shader
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import org.joml.*
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL21

class Material {

    val shaderOverrides = HashMap<String, TypeValue>()
    var pipelineStage: PipelineStage? = null
    var shader: BaseShader? = null

    var diffuseBase = Vector4f(1f)
    var diffuseMap: FileReference = InvalidRef

    var normalTex: FileReference = InvalidRef

    var emissiveBase = Vector4f(1f)
    var emissiveMap: FileReference = InvalidRef

    var displacementMap: FileReference = InvalidRef

    var occlusionMap: FileReference = InvalidRef



    operator fun set(name: String, type: GLSLType, value: Any) {
        shaderOverrides[name] = TypeValue(type, value)
    }

    operator fun set(name: String, value: TypeValue) {
        shaderOverrides[name] = value
    }

    enum class GLSLType {
        V1I, V2I, V3I, V4I,
        V1F, V2F, V3F, V4F,
        M3x3, M4x3, M4x4
    }

    class TypeValue(val type: GLSLType, val value: Any) {

        fun bind(shader: Shader, uniformName: String) {
            val location = shader[uniformName]
            if (location >= 0) bind(shader, uniformName)
        }

        fun bind(shader: Shader, location: Int) {
            when (type) {
                GLSLType.V1I -> shader.v1(location, value as Int)
                GLSLType.V2I -> {
                    value as Vector2ic
                    GL21.glUniform2i(location, value.x(), value.y())
                }
                GLSLType.V3I -> {
                    value as Vector3ic
                    GL20.glUniform3i(location, value.x(), value.y(), value.z())
                }
                GLSLType.V4I -> {
                    value as Vector4ic
                    GL20.glUniform4i(location, value.x(), value.y(), value.z(), value.w())
                }
                GLSLType.V1F -> shader.v1(location, value as Float)
                GLSLType.V2F -> shader.v2(location, value as Vector2fc)
                GLSLType.V3F -> shader.v3(location, value as Vector3fc)
                GLSLType.V4F -> shader.v4(location, value as Vector4fc)
                GLSLType.M3x3 -> shader.m3x3(location, value as Matrix3fc)
                GLSLType.M4x3 -> shader.m4x3(location, value as Matrix4x3fc)
                GLSLType.M4x4 -> shader.m4x4(location, value as Matrix4fc)
                // else -> LOGGER.warn("Type ${valueType.type} is not yet supported")
            }
        }

    }

}