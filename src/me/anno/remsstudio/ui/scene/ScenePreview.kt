package me.anno.remsstudio.ui.scene

import me.anno.Time.deltaTime
import me.anno.gpu.drawing.DrawRectangles.drawRect
import me.anno.gpu.shader.renderer.Renderer
import me.anno.maths.Maths.clamp01
import me.anno.maths.Maths.length
import me.anno.maths.Maths.mixAngle
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.RemsStudio.editorTime
import me.anno.remsstudio.RemsStudio.nullCamera
import me.anno.remsstudio.Scene
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.ui.editor.ISceneView
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelList
import me.anno.utils.Color.black
import me.anno.utils.types.Floats.toDegrees
import org.joml.Vector3f
import java.util.*
import kotlin.math.atan2

@Suppress("MemberVisibilityCanBePrivate")
class ScenePreview(style: Style) : PanelList(style.getChild("sceneView")), ISceneView {

    init {
        weight = 1f
        background.color = 0
    }

    val camera = nullCamera ?: Camera()

    override val usesFPBuffers: Boolean get() = false
    override val isLocked2D get() = (System.currentTimeMillis() % 30000) > 25000

    val random = Random()

    var target = Vector3f()
    var pos = Vector3f()

    private val movementSpeed = if (isLocked2D) 1f else 0.1f
    private val rotationSpeed get() = if (isLocked2D) 1f else 0.1f

    fun updatePosition() {
        val radius = 1.5f
        var distance: Float
        while (true) {
            distance = target.distance(pos)
            if (distance > 0.3f) break
            // find a new target
            target.x = random.nextGaussian().toFloat() * radius
            target.y = random.nextGaussian().toFloat() * radius
            target.z = random.nextGaussian().toFloat() + 3f
        }
        // go towards that target
        val deltaTime = deltaTime.toFloat()
        val relativeMovement = clamp01(movementSpeed * deltaTime / (distance + 0.2f))
        val diff = pos - (target * 0.5f)
        val r0 = camera.rotationYXZ[0.0]
        val x0 = r0.x
        val x1 = if (isLocked2D) 0f else 0.3f * atan2(diff.y, length(diff.x, diff.z)).toDegrees()
        val y0 = r0.y
        val y1 = if (isLocked2D) 0f else atan2(diff.x, diff.z).toDegrees()
        val rs = clamp01(rotationSpeed * deltaTime)
        camera.rotationYXZ.set(Vector3f(mixAngle(x0, x1, rs), mixAngle(y0, y1, rs), 0f))
        pos.lerp(target, relativeMovement)
        camera.position.set(pos)
    }

    private val mutingColor = style.getColor("welcome.mutingColor", 0x55777777)
    override fun draw(x0: Int, y0: Int, x1: Int, y1: Int) {
        updatePosition()
        Scene.draw(
            camera, RemsStudio.root,
            0, 0, width, height,
            editorTime, false,
            Renderer.colorRenderer,
            this
        )
        drawRect(0, 0, width, height, mutingColor)
    }
}