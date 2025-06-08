package me.anno.remsstudio.objects.modes

import me.anno.language.translation.NameDesc

enum class TextRenderMode(val id: Int, val nameDesc: NameDesc) {
    MESH(0, NameDesc("Mesh")),
    SDF(1, NameDesc("Signed Distance Field")),
    SDF_JOINED(2, NameDesc("Merged Signed Distance Field"))
}