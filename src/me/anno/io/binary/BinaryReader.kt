package me.anno.io.binary

import me.anno.io.ISaveable
import me.anno.io.base.BaseReader
import me.anno.io.binary.BinaryTypes.*
import me.anno.utils.input.readNBytes2
import org.joml.*
import java.io.DataInputStream

/**
 * writing as text is:
 * - easier debuggable
 * - similar speed
 * - similar length when compressed
 * */
class BinaryReader(val input: DataInputStream) : BaseReader() {

    private val knownNames = ArrayList<String>()

    private val knownNameTypes = HashMap<String, ArrayList<NameType>>()

    private var currentClass = ""
    private var currentNameTypes = knownNameTypes.getOrPut(currentClass) { ArrayList() }

    private fun usingType(type: String, run: () -> Unit) {
        val old1 = currentClass
        val old2 = currentNameTypes
        currentClass = type
        currentNameTypes = knownNameTypes.getOrPut(type) { ArrayList() }
        run()
        currentClass = old1
        currentNameTypes = old2
    }

    private fun readEfficientString(): String? {
        val id = input.readInt()
        return when {
            id == -1 -> null
            id >= +0 -> knownNames[id]
            else -> {
                val length = -id - 2
                val bytes = input.readNBytes2(length, true)
                val value = String(bytes)
                knownNames += value
                value
            }
        }
    }

    private fun readTypeString(): String {
        return readEfficientString()!!
    }

    private fun readTypeName(id: Int): NameType {
        return if (id >= 0) {
            currentNameTypes[id]
        } else {
            val name = readTypeString()
            val type = input.read().toChar()
            val value = NameType(name, type)
            currentNameTypes.add(value)
            value
        }
    }

    private fun readTypeName(): NameType {
        return readTypeName(input.readInt())
    }

    override fun readObject(): ISaveable {
        val clazz = readTypeString()
        return readObject(clazz)
    }

    private fun readBooleanArray() = BooleanArray(input.readInt()) { input.readBoolean() }
    private fun readByteArray() = ByteArray(input.readInt()) { input.readByte() }
    private fun readShortArray() = ShortArray(input.readInt()) { input.readShort() }
    private fun readIntArray() = IntArray(input.readInt()) { input.readInt() }
    private fun readLongArray() = LongArray(input.readInt()) { input.readLong() }
    private fun readFloatArray() = FloatArray(input.readInt()) { input.readFloat() }
    private fun readDoubleArray() = DoubleArray(input.readInt()) { input.readDouble() }
    private fun readStringArray() = Array(input.readInt()) { readEfficientString()!! }
    private fun readObjectOrNull(): ISaveable? {
        return when (val subType = input.read().toChar()) {
            OBJECT_IMPL -> readObject()
            OBJECT_PTR -> content[input.readInt()]!!
            OBJECT_NULL -> null
            else -> throw RuntimeException("Unknown sub-type $subType")
        }
    }

    private fun readHeterogeneousObjectArray() = Array(input.readInt()) { readObjectOrNull() }
    private fun readHomogeneousObjectArray(type: String): Array<ISaveable?> {
        return Array(input.readInt()) {
            when (val subType = input.read().toChar()) {
                OBJECT_IMPL -> readObject(type)
                OBJECT_PTR -> content[input.readInt()]!!
                OBJECT_NULL -> null
                else -> throw RuntimeException("Unknown sub-type $subType")
            }
        }
    }

    fun readObject(clazz: String): ISaveable {
        val obj = getNewClassInstance(clazz)
        usingType(clazz) {
            val ptr = input.readInt()
            // real all properties
            while (true) {
                val typeId = input.readInt()
                if (typeId < -1) break
                val typeName = readTypeName(typeId)
                val name = typeName.name
                when (typeName.type) {

                    BOOL -> obj.readBoolean(name, input.readBoolean())
                    BOOL_ARRAY -> obj.readBooleanArray(name, readBooleanArray())
                    BOOL_ARRAY_2D -> obj.readBooleanArray2D(name, Array(input.readInt()) { readBooleanArray() })

                    BYTE -> obj.readByte(name, input.readByte())
                    BYTE_ARRAY -> obj.readByteArray(name, readByteArray())
                    BYTE_ARRAY_2D -> obj.readByteArray2D(name, Array(input.readInt()) { readByteArray() })

                    SHORT -> obj.readShort(name, input.readShort())
                    SHORT_ARRAY -> obj.readShortArray(name, readShortArray())
                    SHORT_ARRAY_2D -> obj.readShortArray2D(name, Array(input.readInt()) { readShortArray() })

                    INT -> obj.readInt(name, input.readInt())
                    INT_ARRAY -> obj.readIntArray(name, readIntArray())
                    INT_ARRAY_2D -> obj.readIntArray2D(name, Array(input.readInt()) { readIntArray() })

                    LONG -> obj.readLong(name, input.readLong())
                    LONG_ARRAY -> obj.readLongArray(name, readLongArray())
                    LONG_ARRAY_2D -> obj.readLongArray2D(name, Array(input.readInt()) { readLongArray() })

                    FLOAT -> obj.readFloat(name, input.readFloat())
                    FLOAT_ARRAY -> obj.readFloatArray(name, readFloatArray())
                    FLOAT_ARRAY_2D -> obj.readFloatArray2D(name, Array(input.readInt()) { readFloatArray() })

                    DOUBLE -> obj.readDouble(name, input.readDouble())
                    DOUBLE_ARRAY -> obj.readDoubleArray(name, readDoubleArray())
                    DOUBLE_ARRAY_2D -> obj.readDoubleArray2D(name, Array(input.readInt()) { readDoubleArray() })

                    STRING -> obj.readString(name, readEfficientString()!!)
                    STRING_ARRAY -> obj.readStringArray(name, readStringArray())
                    STRING_ARRAY_2D -> obj.readStringArray2D(name, Array(input.readInt()) { readStringArray() })

                    OBJECT_IMPL -> obj.readObject(name, readObject())
                    OBJECT_PTR -> {
                        val ptr2 = input.readInt()
                        val child = content[ptr2]
                        if (child == null) {
                            addMissingReference(obj, name, ptr2)
                        } else {
                            obj.readObject(name, child)
                        }
                    }

                    OBJECT_NULL -> obj.readObject(name, null)
                    OBJECT_ARRAY -> obj.readObjectArray(name, readHeterogeneousObjectArray())
                    OBJECT_ARRAY_2D -> obj.readObjectArray2D(name, Array(input.readInt()){ readHeterogeneousObjectArray() })

                    OBJECTS_HOMOGENOUS_ARRAY -> {
                        val type = readTypeString()
                        obj.readObjectArray(name, readHomogeneousObjectArray(type))
                    }

                    VECTOR2F -> obj.readVector2f(name, readVector2f())
                    VECTOR3F -> obj.readVector3f(name, readVector3f())
                    VECTOR4F -> obj.readVector4f(name, readVector4f())

                    VECTOR2F_ARRAY -> obj.readVector2fArray(name, Array(input.readInt()) { readVector2f() })
                    VECTOR3F_ARRAY -> obj.readVector3fArray(name, Array(input.readInt()) { readVector3f() })
                    VECTOR4F_ARRAY -> obj.readVector4fArray(name, Array(input.readInt()) { readVector4f() })

                    VECTOR2D -> obj.readVector2d(name, readVector2d())
                    VECTOR3D -> obj.readVector3d(name, readVector3d())
                    VECTOR4D -> obj.readVector4d(name, readVector4d())

                    VECTOR2D_ARRAY -> obj.readVector2dArray(name, Array(input.readInt()) { readVector2d() })
                    VECTOR3D_ARRAY -> obj.readVector3dArray(name, Array(input.readInt()) { readVector3d() })
                    VECTOR4D_ARRAY -> obj.readVector4dArray(name, Array(input.readInt()) { readVector4d() })

                    else -> throw RuntimeException("Unknown type ${typeName.type}")
                }
            }
            register(obj, ptr)
        }
        return obj
    }

    private fun readVector2f() = Vector2f(input.readFloat(), input.readFloat())
    private fun readVector3f() = Vector3f(input.readFloat(), input.readFloat(), input.readFloat())
    private fun readVector4f() = Vector4f(input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat())

    private fun readVector2d() = Vector2d(input.readDouble(), input.readDouble())
    private fun readVector3d() = Vector3d(input.readDouble(), input.readDouble(), input.readDouble())
    private fun readVector4d() =
        Vector4d(input.readDouble(), input.readDouble(), input.readDouble(), input.readDouble())

    override fun readAllInList() {
        val nameType = readTypeName()
        assert(nameType.name == "", "Expected object without a name")
        assert(nameType.type == OBJECT_LIST_UNKNOWN_LENGTH, "Expected list of unknown length")
        loop@ while (true) {
            val type = input.read().toChar()
            if (type != OBJECT_IMPL) throw RuntimeException("Type must be OBJECT_IMPL, but got $type != $OBJECT_IMPL")
            readObject()
            when (val code = input.read()) {
                17 -> Unit
                37 -> break@loop
                else -> {
                    throw RuntimeException("Invalid Code $code")
                }
            }
        }
    }

}