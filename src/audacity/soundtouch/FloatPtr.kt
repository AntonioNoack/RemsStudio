package audacity.soundtouch

import me.anno.utils.structures.arrays.FloatArrayList

class FloatPtr(val array: FloatArrayList, val offset: Int) {
    operator fun plus(deltaOffset: Int) = FloatPtr(array, offset + deltaOffset)
    operator fun get(index: Int) = array[index + offset]
    operator fun set(index: Int, value: Float) {
        array[index + offset] = value
    }
}