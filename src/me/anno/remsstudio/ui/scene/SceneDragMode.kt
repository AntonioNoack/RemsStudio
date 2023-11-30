package me.anno.remsstudio.ui.scene

enum class SceneDragMode(val displayName: String, val description: String) {
    MOVE("M", "Move (R-key)"),
    SCALE("S", "Scale (Y/Z-key)"),
    ROTATE("R", "Rotate (T-key)")
}