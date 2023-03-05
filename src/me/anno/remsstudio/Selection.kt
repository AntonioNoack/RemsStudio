package me.anno.remsstudio

import me.anno.io.ISaveable
import me.anno.io.find.PropertyFinder
import me.anno.remsstudio.RemsStudio.root
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.objects.Transform
import me.anno.studio.Inspectable
import me.anno.ui.editor.PropertyInspector.Companion.invalidateUI
import me.anno.utils.structures.maps.BiMap
import org.apache.logging.log4j.LogManager

object Selection {

    // todo make it possible to select multiple stuff
    // todo to edit common properties of all selected members <3 :D

    private val LOGGER = LogManager.getLogger(Selection::class)

    var selectedProperties: List<AnimatedProperty<*>?>? = null
        private set

    var selectedTransforms: List<Transform> = emptyList()
        private set

    var selectedInspectables: List<Inspectable> = emptyList()
        private set

    var selectedUUIDs = emptyList<Int>()
    var selectedPropName: String? = null
    var needsUpdate = true

    fun clear() {
        selectedUUIDs = emptyList()
        selectedPropName = null
        needsUpdate = true
    }

    fun select(uuids: List<Int>, name: String?) {
        selectedUUIDs = uuids
        selectedPropName = name
        needsUpdate = true
    }

    fun selectProperty(property: List<ISaveable?>) {
        if (selectedProperties == property) {
            select(selectedTransforms, null)
        } else select(selectedTransforms, property)
    }

    fun selectTransform(transform: List<Transform>) {
        select(transform, null)
    }

    fun selectTransform(transform: Transform?) {
        select(if (transform != null) listOf(transform) else emptyList(), null)
    }

    fun select(transform: Transform, property: ISaveable?) {
        select(listOf(transform), if (property != null) listOf(property) else null)
    }

    fun select(transforms0: List<Transform>, properties: List<ISaveable?>?) {
        if (same(transforms0, selectedTransforms) && same(properties, selectedProperties)) return
        val transforms = transforms0.map { transform ->
            val loi = transform.listOfInheritance.toList()
            var replacement: Transform? = null
            for (i in loi.lastIndex downTo 1) {
                if (loi[i].areChildrenImmutable) {
                    replacement = loi[i]
                    LOGGER.info("Selected immutable element ${transform.name}, selecting the parent ${replacement.name}")
                    break
                }
            }
            replacement ?: transform
        }

        if (selectedTransforms == transforms && selectedProperties == properties) return
        val newName = if (properties == null || properties.isEmpty() || properties[0] == null) null
        else PropertyFinder.getName(transforms[0], properties[0]!!)
        val propName = newName ?: selectedPropName
        // LOGGER.info("$newName:$propName from ${transform?.className}:${property?.className}")
        RemsStudio.largeChange("Select ${transforms.firstOrNull()?.name ?: "Nothing"}:$propName") {
            selectedUUIDs = transforms.map { getIdFromTransform(it) }
            selectedPropName = propName
            selectedTransforms = transforms
            val property2 = transforms.map { PropertyFinder.getValue(it, selectedPropName ?: "") }
            selectedInspectables = property2.withIndex().map { (i, it) -> it as? Inspectable ?: transforms[i] }
            selectedProperties = property2.map { it as? AnimatedProperty<*> }
        }
    }

    fun <V> same(l0: List<V>?, l1: List<V>?): Boolean {
        if (l0 === l1) return true
        if (l0 == null || l1 == null) return false
        if (l0.size != l1.size) return false
        for (i in l0.indices) {
            if (l0[i] !== l1[i])
                return false
        }
        return true
    }

    fun update() {
        if (!needsUpdate) {

            // nothing to do

        } else {

            // re-find the selected transform and property...
            selectedTransforms = getTransformsFromId()
            val selectedTransforms = selectedTransforms
            val selectedPropName = selectedPropName
            if (selectedTransforms.isNotEmpty() && selectedPropName != null) {
                val values = selectedTransforms.map {
                    PropertyFinder.getValue(it, selectedPropName)
                }
                selectedProperties = values.map { it as? AnimatedProperty<*> }
                selectedInspectables = values.mapNotNull { it as? Inspectable }
            } else {
                selectedProperties = null
                selectedInspectables = selectedTransforms
            }

            invalidateUI(true)

            needsUpdate = false

        }
    }

    private fun getIdFromTransform(transform: Transform?): Int {
        var id = if (transform == null) -1 else root.listOfAll.indexOf(transform)
        // a special value
        if (transform != null && id == -1) {
            id = getSpecialUUID(transform)
        }
        return id
    }

    private fun getTransformsFromId(): List<Transform> {
        return selectedUUIDs.mapNotNull { selectedUUID ->
            when {
                selectedUUID < 0 -> null
                selectedUUID < specialIdOffset -> root.listOfAll.getOrNull(selectedUUID)
                else -> specialIds.reverse.getOrDefault(selectedUUID, null)
            }
        }
    }

    private fun <V> Sequence<V>.getOrNull(index: Int): V? {
        if (index < 0) return null
        val iterator = iterator()
        for (i in 0 until index) {
            if (!iterator.hasNext()) return null
            iterator.next()
        }
        if (!iterator.hasNext()) return null
        return iterator.next()
    }

    private const val specialIdOffset = 1_000_000_000
    private val specialIds = BiMap<Transform, Int>(32)
    private fun getSpecialUUID(t: Transform): Int {
        if (t in specialIds) return specialIds[t]!!
        val id = specialIds.size + specialIdOffset
        specialIds[t] = id
        return id
    }

}