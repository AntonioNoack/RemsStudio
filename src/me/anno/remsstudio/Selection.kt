package me.anno.remsstudio

import me.anno.engine.inspector.Inspectable
import me.anno.io.Saveable
import me.anno.io.find.PropertyFinder
import me.anno.remsstudio.RemsStudio.root
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.objects.Transform
import me.anno.ui.editor.PropertyInspector.Companion.invalidateUI
import me.anno.utils.structures.maps.BiMap
import org.apache.logging.log4j.LogManager

object Selection {

    // todo make it possible to select multiple stuff
    //  to edit common properties of all selected members <3 :D

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

    fun selectProperty(property: List<Saveable?>) {
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

    fun select(transform: Transform, property: Saveable?) {
        select(listOf(transform), if (property != null) listOf(property) else null)
    }

    fun select(transforms0: List<Transform>, properties0: List<Saveable?>?) {

        if (same(transforms0, selectedTransforms) && same(properties0, selectedProperties)) return
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

        if (same(transforms, selectedTransforms) && same(properties0, selectedProperties)) return
        val newName = if (properties0.isNullOrEmpty() || properties0[0] == null) null
        else PropertyFinder.getName(transforms[0], properties0[0]!!)
        val propName = newName ?: selectedPropName

        val uuids = transforms.map { getIdFromTransform(it) }
        val foundProperties = transforms.map { PropertyFinder.getValue(it, selectedPropName ?: "") }
        val inspectables = foundProperties.withIndex().map { (i, it) -> it as? Inspectable ?: transforms[i] }
        val properties = foundProperties.map { it as? AnimatedProperty<*> }

        if (
            uuids != selectedUUIDs ||
            propName != selectedPropName ||
            inspectables != selectedInspectables ||
            properties != selectedProperties ||
            transforms != selectedTransforms
        ) {
            // println("Selecting $uuids/$propName/${inspectables.map { it.javaClass.simpleName }}/${transforms.map { getIdFromTransform(it) }}")
            RemsStudio.largeChange("Select ${transforms.firstOrNull()?.name ?: "Nothing"}:$propName") {
                selectedUUIDs = uuids
                selectedPropName = propName
                selectedInspectables = inspectables
                selectedProperties = properties
                selectedTransforms = transforms
            }
            invalidateUI(true)
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
        if (needsUpdate) {
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
        val givenId = specialIds[t]
        if (givenId != null) return givenId
        val newId = specialIds.size + specialIdOffset
        specialIds[t] = newId
        return newId
    }

}