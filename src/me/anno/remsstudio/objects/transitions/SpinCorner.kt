package me.anno.remsstudio.objects.transitions

import me.anno.language.translation.NameDesc
import org.joml.Vector2f

enum class SpinCorner(val id: Int, val value: Vector2f, val nameDesc: NameDesc) {
    TOP_LEFT(0, Vector2f(0f, 1f), NameDesc("Top-Left")),
    TOP_RIGHT(1, Vector2f(1f, 1f), NameDesc("Top-Right")),
    BOTTOM_LEFT(2, Vector2f(0f, 0f), NameDesc("Bottom-Left")),
    BOTTOM_RIGHT(3, Vector2f(1f, 0f), NameDesc("Bottom-Right")),
}