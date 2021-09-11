package me.anno.ecs.prefab.change

import me.anno.ecs.prefab.PrefabSaveable
import me.anno.io.base.BaseWriter

class CSet() : Change(5) {

    constructor(path: Path, name: String?, value: Any?) : this() {
        this.path = path
        this.name = name
        this.value = value
    }

    override fun withPath(path: Path): Change {
        return CSet(path, name, value)
    }

    var name: String? = null
    var value: Any? = null

    /**
     * shallow copy
     * */
    override fun clone(): Change {
        val clone = CSet()
        clone.path = path
        clone.name = name
        clone.value = value
        return clone
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeString("name", name)
        writer.writeSomething(null, "value", value, true)
    }

    override fun readString(name: String, value: String?) {
        when (name) {
            "name" -> this.name = value
            else -> super.readString(name, value)
        }
    }

    override fun readSomething(name: String, value: Any?) {
        when (name) {
            "value" -> this.value = value
            else -> super.readSomething(name, value)
        }
    }

    override fun applyChange(instance: PrefabSaveable) {
        // LOGGER.info("set $name = $value for ${instance::class}")
        instance[name ?: return] = value
    }

    override val approxSize: Int = 10
    override fun isDefaultValue(): Boolean = false

    override val className: String = "CSet"

    override fun toString(): String {
        var str = value.toString()
        val maxLength = 100
        if (str.length > maxLength) {
            str = str.substring(0, maxLength * 7 / 10 - 3) +
                    "..." +
                    str.substring(str.length - maxLength * 3 / 10)
        }
        return "CSet($path, $name, $str)"
    }

}