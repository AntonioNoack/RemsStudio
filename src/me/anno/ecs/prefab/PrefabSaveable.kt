package me.anno.ecs.prefab

import me.anno.io.NamedSaveable
import me.anno.io.serialization.NotSerializedProperty
import me.anno.io.serialization.SerializedProperty
import me.anno.objects.inspectable.Inspectable
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.editor.stacked.Option
import me.anno.ui.style.Style
import me.anno.utils.LOGGER
import me.anno.utils.structures.Hierarchical

abstract class PrefabSaveable : NamedSaveable(), Hierarchical<PrefabSaveable>, Inspectable, Cloneable {

    @SerializedProperty
    override var isEnabled = true

    @NotSerializedProperty
    override var isCollapsed = false

    @NotSerializedProperty
    var prefab: PrefabSaveable? = null

    @NotSerializedProperty
    var prefab2: Prefab? = null

    @NotSerializedProperty
    override var parent: PrefabSaveable? = null

    private fun getSuperParent(): PrefabSaveable {
        return prefab ?: this::class.java.getConstructor().newInstance()
    }

    fun getDefaultValue(name: String): Any? {
        return getSuperParent()[name]
    }

    fun resetProperty(name: String): Any? {
        // how do we find the default value, if the root is null? -> create an empty copy
        val parent = getSuperParent()
        val reflections = getReflections()
        val defaultValue = reflections[parent, name]
        reflections[this, name] = defaultValue
        LOGGER.info("Reset $className/$name to $defaultValue")
        return defaultValue
    }

    fun pathInRoot(root: PrefabSaveable? = null): ArrayList<Any> {
        if (this == root) return ArrayList()
        val parent = parent
        return if (parent != null) {
            val index = parent.getIndexOf(this)
            val list = parent.pathInRoot()
            list.add(name)
            list.add(index)
            list.add(parent.getTypeOf(this))
            return list
        } else ArrayList()
    }

    fun pathInRoot2(root: PrefabSaveable? = null, withExtra: Boolean): Path {
        val path = pathInRoot(root)
        if (withExtra) {
            path.add("")
            path.add(-1)
            path.add(' ')
        }
        val size = path.size / 3
        val names = Array(size) { path[it * 3] as String }
        val ids = IntArray(size) { path[it * 3 + 1] as Int }
        val types = CharArray(size) { path[it * 3 + 2] as Char }
        return Path(names, ids, types)
    }

    // e.g. "ec" for child entities + child components
    open fun listChildTypes(): String = ""
    open fun getChildListByType(type: Char): List<PrefabSaveable> = emptyList()
    open fun getChildListNiceName(type: Char): String = ""
    open fun addChildByType(index: Int, type: Char, instance: PrefabSaveable) {}
    open fun getOptionsByType(type: Char): List<Option>? = null

    override fun add(child: PrefabSaveable) {
        val type = getTypeOf(child)
        val length = getChildListByType(type).size
        addChildByType(length, type, child)
    }

    override fun add(index: Int, child: PrefabSaveable) = addChildByType(index, getTypeOf(child), child)
    override fun remove(child: PrefabSaveable) {
        val list = getChildListByType(getTypeOf(child))
        val index = list.indexOf(child)
        if (index < 0) return
        list as MutableList<*>
        list.remove(child)
    }

    open fun getIndexOf(child: PrefabSaveable): Int = getChildListByType(getTypeOf(child)).indexOf(child)
    open fun getTypeOf(child: PrefabSaveable): Char = ' '

    public abstract override fun clone(): PrefabSaveable
    open fun copy(clone: PrefabSaveable) {
        // todo when these are in the tree being cloned, we need the clones here
        clone.name = name
        clone.description = description
        clone.isEnabled = isEnabled
        clone.isCollapsed = isCollapsed
        clone.prefab = prefab
        clone.prefab2 = prefab2
    }

    override fun onDestroy() {}

    @NotSerializedProperty
    override val symbol: String = ""

    @NotSerializedProperty
    override val defaultDisplayName: String
        get() = name

    @NotSerializedProperty
    override val children: List<PrefabSaveable>
        get() = getChildListByType(listChildTypes()[0])

    override fun createInspector(
        list: PanelListY, style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        PrefabInspector.currentInspector?.inspect(this, list, style) ?: LOGGER.warn("Missing inspector!")
    }

}