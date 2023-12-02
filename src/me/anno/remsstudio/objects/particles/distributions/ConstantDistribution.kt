package me.anno.remsstudio.objects.particles.distributions

import me.anno.language.translation.NameDesc
import me.anno.ui.editor.sceneView.Grid
import org.joml.Matrix4fArrayList
import org.joml.Vector3f
import org.joml.Vector4f

class ConstantDistribution(center: Vector4f) :
    CenterDistribution(NameDesc("Constant", "Always the same value", "obj.dist.constant"), center) {

    constructor() : this(0f)
    constructor(center: Float) : this(Vector4f(center))

    override val className get() = "ConstantDistribution"

}