package me.anno.remsstudio.ui.scene

import me.anno.Time.deltaTime
import me.anno.config.DefaultConfig
import me.anno.engine.EngineBase.Companion.dragged
import me.anno.engine.EngineBase.Companion.shiftSlowdown
import me.anno.gpu.Clipping
import me.anno.gpu.GFX
import me.anno.gpu.GFXState.renderDefault
import me.anno.gpu.GPUTasks.addGPUTask
import me.anno.gpu.drawing.DrawRectangles
import me.anno.gpu.drawing.DrawRectangles.drawBorder
import me.anno.gpu.framebuffer.DepthBufferType
import me.anno.gpu.framebuffer.FBStack
import me.anno.gpu.framebuffer.Screenshots
import me.anno.gpu.framebuffer.StableWindowSize
import me.anno.gpu.shader.renderer.Renderer
import me.anno.gpu.shader.renderer.Renderer.Companion.colorRenderer
import me.anno.gpu.shader.renderer.Renderer.Companion.colorSqRenderer
import me.anno.input.Input
import me.anno.input.Input.isControlDown
import me.anno.input.Input.isShiftDown
import me.anno.input.Key
import me.anno.input.Touch.Companion.touches
import me.anno.io.files.FileReference
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.length
import me.anno.maths.Maths.pow
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.RemsStudio.editorTime
import me.anno.remsstudio.RemsStudio.editorTimeDilation
import me.anno.remsstudio.RemsStudio.isPaused
import me.anno.remsstudio.RemsStudio.lastTouchedCamera
import me.anno.remsstudio.RemsStudio.nullCamera
import me.anno.remsstudio.RemsStudio.project
import me.anno.remsstudio.Scene
import me.anno.remsstudio.Selection.select
import me.anno.remsstudio.Selection.selectTransform
import me.anno.remsstudio.Selection.selectedTransforms
import me.anno.remsstudio.objects.Camera
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.effects.ToneMappers
import me.anno.remsstudio.objects.particles.ParticleSystem
import me.anno.remsstudio.objects.particles.distributions.CenterDistribution
import me.anno.remsstudio.objects.particles.distributions.CenterSizeDistribution
import me.anno.remsstudio.ui.StudioFileImporter.addChildFromFile
import me.anno.remsstudio.ui.StudioTreeView.Companion.zoomToObject
import me.anno.remsstudio.ui.editor.ISceneView
import me.anno.remsstudio.ui.editor.SimplePanel
import me.anno.ui.Panel
import me.anno.ui.Style
import me.anno.ui.base.buttons.TextButton
import me.anno.ui.base.groups.PanelList
import me.anno.ui.custom.CustomContainer
import me.anno.ui.editor.PropertyInspector.Companion.invalidateUI
import me.anno.ui.editor.files.FileContentImporter
import me.anno.utils.Color.black
import me.anno.utils.Color.convertARGB2ABGR
import me.anno.utils.structures.lists.Lists.firstInstanceOrNull2
import me.anno.utils.types.Booleans.toInt
import me.anno.utils.types.Floats.toRadians
import org.apache.logging.log4j.LogManager
import org.joml.*
import java.lang.Math.toDegrees
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

// todo disable ui circles via some check-button at the top bar

// todo key controls like in Blender:
//  start command with s/g/...
//  then specify axis if needed
//  then say the number + change axis
//  then press enter to apply the change

@Suppress("MemberVisibilityCanBePrivate")
open class StudioSceneView(style: Style) : PanelList(null, style.getChild("sceneView")), ISceneView {

    companion object {
        private val LOGGER = LogManager.getLogger(StudioSceneView::class)
    }

    init {
        weight = 1f
        backgroundColor = 0
    }

    var camera = nullCamera ?: Camera()

    override fun isOpaqueAt(x: Int, y: Int): Boolean = true

    override val usesFPBuffers get() = camera.toneMapping != ToneMappers.RAW8
    final override var isLocked2D = camera.rotationYXZ.isDefaultValue()

    val controls = ArrayList<SimplePanel>()

    val iconSize = style.getSize("fontSize", 12) * 2
    val pad = (iconSize + 4) / 8

    val borderThickness = style.getSize("blackWhiteBorderThickness", 2)

    override fun onPropertiesChanged() {
        invalidateDrawing()
    }

    // we need the depth for post-processing effects like dof

    final override val children: ArrayList<Panel> get() = super.children

    init {
        val is2DPanel = TextButton(
            NameDesc(
                if (isLocked2D) "2D" else "3D", "Lock the camera; use control to keep the angle",
                "ui.sceneView.3dSwitch"
            ), true, style
        )
        is2DPanel.instantTextLoading = true
        controls += SimplePanel(
            is2DPanel,
            true, true,
            pad, pad,
            iconSize
        ).setOnClickListener {
            isLocked2D = !isLocked2D
            // control can be used to avoid rotating the camera
            if (isLocked2D && !isControlDown) {
                val rot = camera.rotationYXZ
                val rot0z = rot[camera.lastLocalTime].z
                camera.putValue(rot, Vector3f(0f, 0f, rot0z), true)
            }
            is2DPanel.text = if (isLocked2D) "2D" else "3D"
            invalidateDrawing()
        }
        fun add(i: Int, mode: SceneDragMode) {
            controls += SimplePanel(
                object : TextButton(NameDesc(mode.displayName, mode.description, ""), true, style) {
                    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {
                        drawBackground(x0, y0, x1, y1)
                        drawButtonText()
                        drawButtonBorder(
                            leftColor, topColor, rightColor, bottomColor,
                            isInputAllowed, borderSize, isPressed || mode == this@StudioSceneView.mode
                        )
                    }
                }, true, true,
                pad * 2 + iconSize * (i + 1), pad,
                iconSize
            ).setOnClickListener {
                this.mode = mode
                invalidateDrawing()
            }
        }
        add(0, SceneDragMode.MOVE)
        add(1, SceneDragMode.ROTATE)
        add(2, SceneDragMode.SCALE)
        controls += SimplePanel(
            TextButton(
                NameDesc("\uD83D\uDCF7", "Take a screenshot", "ui.sceneView.takeScreenshot"),
                true, style
            ),
            true, true,
            pad * 3 + iconSize * (3 + 1), pad,
            iconSize
        ).setOnClickListener {
            val renderer = colorRenderer
            val w = stableSize.stableWidth
            val h = stableSize.stableHeight
            Screenshots.takeScreenshot(w, h, renderer) {
                Scene.draw(camera, RemsStudio.root, 0, 0, w, h, editorTime, true, renderer, this)
            }
        }
    }

    init {
        for (it in controls) {
            children += it.drawable
        }
    }

    open fun onInteraction() {
        lastTouchedCamera = camera
    }

    override fun getVisualState(): Any =
        Pair(
            editorTime,
            (stableSize.stableWidth.shl(16) or stableSize.stableHeight).shl(2) +
                    Input.isKeyDown('l').toInt(1) + Input.isKeyDown('n').toInt(2)
        )

    override fun onUpdate() {
        super.onUpdate()
        parseKeyInput()
        parseTouchInput()
        claimResources()
        updateStableSize()
    }

    fun updateStableSize() {
        stableSize.updateSize(
            width - 2 * borderThickness, height - 2 * borderThickness,
            if (camera.onlyShowTarget) RemsStudio.targetWidth else -1, RemsStudio.targetHeight
        )
    }

    var mode = SceneDragMode.MOVE
        set(value) {
            field = value
            val selectedTransforms = selectedTransforms
            select(
                selectedTransforms,
                when (value) {
                    SceneDragMode.MOVE -> selectedTransforms.map { it.position }
                    SceneDragMode.SCALE -> selectedTransforms.map { it.scale }
                    SceneDragMode.ROTATE -> selectedTransforms.map { it.rotationYXZ }
                }
            )
        }

    var velocity = Vector3f()

    var inputDx = 0f
    var inputDy = 0f
    var inputDz = 0f

    // switch between manual control and autopilot for time :)
    // -> do this by disabling controls when playing, excepts when it's the inspector camera (?)
    val mayControlCamera
        get() = if (DefaultConfig["ui.editor.lockCameraWhenPlaying", false]) camera === nullCamera || isPaused
        else true

    fun claimResources() {
        // this is expensive, so do it only when the time changed
        val edt = editorTimeDilation
        val et = editorTime
        val loadedTimeSeconds = 3.0
        // load the next 3 seconds of data
        RemsStudio.root.claimResources(et, et + loadedTimeSeconds * if (edt == 0.0) 1.0 else edt, 1f, 1f)
    }

    override val canDrawOverBorders: Boolean
        get() = true

    private val stableSize = StableWindowSize()
    override fun onDraw(x0: Int, y0: Int, x1: Int, y1: Int) {

        val mode = if (camera.toneMapping == ToneMappers.RAW8) colorRenderer else colorSqRenderer

        GFX.check()

        val bt = borderThickness
        val bth = bt / 2

        updateStableSize()

        val dx = stableSize.dx + bt
        val dy = stableSize.dy + bt

        val white = -1
        val b = DrawRectangles.startBatch()
        drawBorder(x, y, width, height, white, bth)
        drawBorder(x + bth, y + bth, width - bt, height - bt, black, bth)
        // filled with scene background color anyway
        DrawRectangles.finishBatch(b)

        val x00 = x + dx
        val y00 = y + dy
        val window = window ?: GFX.someWindow.windowStack.first()
        val wx = min(stableSize.stableWidth, window.width - x00)
        val wy = min(stableSize.stableHeight, window.height - y00)
        val rw = min(wx, width - 2 * bt)
        val rh = min(wy, height - 2 * bt)
        if (rw > 0 && rh > 0) {
            Scene.draw(
                camera, RemsStudio.root,
                x00, y00, wx, wy,
                editorTime, false,
                mode, this
            )
        }

        Clipping.clip2(x0, y0, x1, y1) {
            renderDefault {
                for (control in controls) {
                    control.draw(x, y, width, height, x0, y0, x1, y1)
                }
            }
            drawChildren(x0, y0, x1, y1)
        }

        // todo this text is always black... why???
        // drawTypeInCorner("Scene", white)

    }

    fun resolveClick(clickX: Float, clickY: Float, width: Int, height: Int, callback: (Transform?) -> Unit) {

        val camera = camera
        GFX.check()

        val buffer = FBStack["resolveClick", width, height, 4, true, 1, DepthBufferType.INTERNAL]

        val diameter = 5

        val bt = borderThickness
        val dx0 = stableSize.dx + bt
        val dy0 = stableSize.dy + bt
        val cXInt = clickX.toInt() - (this.x + dx0)
        val cYInt = clickY.toInt() - (this.y + dy0)

        val root = RemsStudio.root

        val dx = 0
        val dy = 0

        val idBuffer = Screenshots.getU8RGBAPixels(diameter, cXInt, cYInt, buffer, Renderer.idRenderer) {
            Scene.draw(camera, root, dx, dy, width, height, editorTime, false, Renderer.idRenderer, this)
        }

        val depthBuffer = Screenshots.getFP32RPixels(diameter, cXInt, cYInt, buffer, Renderer.depthRenderer) {
            Scene.draw(camera, root, dx, dy, width, height, editorTime, false, Renderer.depthRenderer, this)
        }

        convertARGB2ABGR(idBuffer)

        LOGGER.debug(
            "ResolveClick: " +
                    "[${idBuffer.joinToString { it.toUInt().toString(16) }}], " +
                    "[${depthBuffer.joinToString()}]"
        )
        LOGGER.debug("Available IDs: ${
            root.listOfAll.toList()
                .joinToString { it.clickId.toUInt().toString(16) }
        }")

        val bestResult = Screenshots.getClosestId(diameter, idBuffer, depthBuffer)
        // find the transform with the id to select it
        if (bestResult > 0) {

            var transform = root.listOfAll.firstOrNull { it.clickId == bestResult }
            if (transform == null) {// transformed, so it works without project as well
                val nullCamera = project?.nullCamera
                if (nullCamera != null && nullCamera.clickId == bestResult) {
                    transform = nullCamera
                }
            }
            callback(transform)

        } else callback(null)

        GFX.check()

    }

    // todo camera movement in orthographic view is a bit broken

    fun parseKeyInput() {
        if (!mayControlCamera) return
        moveCamera(clamp(deltaTime.toFloat(), 0f, 0.1f))
    }

    fun moveCamera(dx: Float, dy: Float, dz: Float) {
        val defaultFPS = 60f
        val dt = 0.2f
        val scale = defaultFPS * dt
        this.inputDx += dx * scale
        this.inputDy += dy * scale
        this.inputDz += dz * scale
    }

    fun moveCamera(dt: Float) {

        val camera = camera
        if (!camera.lockTransform) {
            val (cameraTransform, cameraTime) = camera.getGlobalTransformTime(editorTime)

            val radius = camera.orbitRadius[cameraTime]
            val speed = if (radius == 0f) 1f else 0.1f + 0.9f * radius
            val acceleration = Vector3f(inputDx, inputDy, inputDz).mul(speed)

            velocity.mul(1f - dt)
            velocity.mulAdd(dt, acceleration)

            if (velocity.lengthSquared() > 0f) {
                val oldPosition = camera.position[cameraTime]
                val step = velocity * dt
                val step2 = cameraTransform.transformDirection(step)
                val newPosition = oldPosition + step2
                if (camera == nullCamera) {
                    camera.position.addKeyframe(cameraTime, newPosition, 0.01)
                } else {
                    RemsStudio.incrementalChange("Move Camera") {
                        camera.position.addKeyframe(cameraTime, newPosition, 0.01)
                    }
                }
                invalidateDrawing()
            }
        }

        // todo if camera.isOrthographic, then change fov instead of moving forward/backward?

        inputDx = 0f
        inputDy = 0f
        inputDz = 0f
    }

    fun parseTouchInput() {

        if (!mayControlCamera) return

        // todo rotate/move our camera or the selected object?
        val size = -20f * shiftSlowdown / window!!.height
        val touches = touches.values.toList()
        when (touches.size) {
            2 -> {
                val first = touches[0]
                if (contains(first.x, first.y)) {
                    // this gesture started on this view -> this is our gesture
                    // rotating is the hardest on a touchpad, because we need to click right
                    // -> rotation
                    // axes: angle, zoom,
                    val dx = touches.sumOf { (it.x - it.lastX).toDouble() }.toFloat() * size * 0.5f
                    val dy = touches.sumOf { (it.y - it.lastY).toDouble() }.toFloat() * size * 0.5f

                    val t0 = touches[0]
                    val t1 = touches[1]

                    val d1 = length(t1.x - t0.x, t1.y - t0.y)
                    val d0 = length(t1.lastX - t0.lastX, t1.lastY - t0.lastY)

                    val minDistance = 10
                    if (d1 > minDistance && d0 > minDistance) {
                        val time = cameraTime
                        val oldCamZoom = camera.orbitRadius[time]
                        if (oldCamZoom == 0f) {
                            // todo delta zoom for cameras without orbit
                        } else {
                            val newZoom = oldCamZoom * d0 / d1
                            camera.putValue(camera.orbitRadius, newZoom, false)
                        }
                    }

                    val (_, time) = camera.getGlobalTransformTime(editorTime)
                    val old = camera.rotationYXZ[time]
                    val rotationSpeed = -10f
                    if (!isLocked2D) {
                        // todo test this
                        val newRotation = Vector3f(dy * rotationSpeed, dx * rotationSpeed, 0f).add(old)
                        newRotation.x = clamp(newRotation.x, -90f, +90f)
                        camera.rotationYXZ.addKeyframe(time, newRotation)
                    } else {
                        // move camera? completely ignore, what is selected
                    }
                    touches.forEach { it.update() }
                    invalidateDrawing()
                }
            }

            3 -> {
                // very slow..., but we can move around with a single finger, so it shouldn't matter...
                // move the camera around
                val first = touches.first()
                val speed = 10f / 3f
                if (contains(first.x, first.y)) {
                    val dx = speed * touches.sumOf { (it.x - it.lastX).toDouble() }.toFloat() * size
                    val dy = speed * touches.sumOf { (it.y - it.lastY).toDouble() }.toFloat() * size
                    moveSelected(camera, dx, dy)
                    for (touch in touches) {
                        touch.update()
                    }
                    invalidateDrawing()
                }
            }
        }
    }

    val global2normUI = Matrix4fArrayList()

    private val global2target = Matrix4f()
    private val camera2target = Matrix4f()
    private val target2camera = Matrix4f()

    fun moveSelected(selected: List<Transform>, dx0: Float, dy0: Float) {
        for (s in selected) {
            moveSelected(s, dx0, dy0)
        }
    }

    fun moveSelected(selected: Transform, dx0: Float, dy0: Float) {

        if (!mayControlCamera || selected.lockTransform) return
        if (dx0 == 0f && dy0 == 0f) return

        val (target2global, localTime) = (selected.parent ?: selected).getGlobalTransformTime(editorTime)

        val camera = camera
        val (camera2global, cameraTime) = camera.getGlobalTransformTime(editorTime)

        global2normUI.clear()
        camera.applyTransform(cameraTime, camera2global, global2normUI)

        val global2target = target2global.invert(global2target)

        // transforms: global to local
        // ->
        // camera local to global, then global to local
        //      obj   cam
        // v' = G2L * L2G * v
        val camera2target = camera2global.mul(global2target, camera2target)
        val target2camera = camera2target.invert(target2camera)

        // where the object is on screen
        val targetZOnUI = target2camera.getTranslation(Vector3f())
        val targetZ = -targetZOnUI.z
        val shiftSlowdown = shiftSlowdown
        val speed = shiftSlowdown * 2 * targetZ / height * pow(0.02f, camera.orthographicness[cameraTime])
        val dx = dx0 * speed
        val dy = dy0 * speed

        val delta0 = dx0 - dy0
        val delta = dx - dy

        val selectedDist = (selected as? ParticleSystem)?.selectedDistribution
        val selectedPosDist = selectedDist?.distribution as? CenterDistribution
        val selectedRotDist = selectedPosDist as? CenterSizeDistribution

        when (mode) {
            SceneDragMode.MOVE -> {

                // todo find the (truly) correct speed...
                // depends on FOV, camera and object transform

                val oldPosition = selected.position[localTime]
                val localDelta = if (isControlDown) Vector3f(0f, 0f, -delta * targetZ / 6f)
                else Vector3f(dx, -dy, 0f)

                camera2target.transformDirection(localDelta)

                invalidateDrawing()
                RemsStudio.incrementalChange("Move Object") {
                    if (selectedPosDist != null) {
                        // add keyframe to selectedPosDist
                        val ch = selectedDist.channels[CenterSizeDistribution.POSITION_INDEX]
                        when (val prev = ch[localTime]) {
                            is Float -> ch.addKeyframe(localTime, prev + localDelta.x)
                            is Vector2f -> ch.addKeyframe(localTime, Vector2f(prev).add(localDelta.x, localDelta.y))
                            is Vector3f -> ch.addKeyframe(localTime, Vector3f(prev).add(localDelta))
                            is Vector4f -> {
                                val localDelta1 = Vector4f(localDelta.x, localDelta.y, localDelta.z, 0f)
                                ch.addKeyframe(localTime, Vector4f(prev).add(localDelta1))
                            }
                        }
                    } else {
                        selected.position.addKeyframe(localTime, Vector3f(oldPosition).add(localDelta))
                    }
                    invalidateUI(false)
                }
            }
            SceneDragMode.SCALE -> {
                val speed2 = 1f / height
                val oldScale = selected.scale[localTime]
                val localDelta = target2camera.transformDirection(
                    if (isControlDown) Vector3f(dx0, dy0, 0f)
                    else Vector3f(delta0)
                )
                localDelta.mul(speed2)
                val base = 2f
                invalidateDrawing()
                RemsStudio.incrementalChange("Scale Object") {
                    if (selectedRotDist != null) {
                        // add keyframe to selectedRotDist
                        val ch = selectedDist.channels[CenterSizeDistribution.SCALE_INDEX]
                        when (val prev = ch[localTime]) {
                            is Float -> ch.addKeyframe(localTime, prev * pow(base, delta0))
                            is Vector2f -> ch.addKeyframe(
                                localTime, Vector2f(prev).mul(
                                    pow(base, localDelta.x),
                                    pow(base, localDelta.y),
                                )
                            )
                            is Vector3f -> ch.addKeyframe(
                                localTime, Vector3f(prev).mul(
                                    pow(base, localDelta.x),
                                    pow(base, localDelta.y),
                                    pow(base, localDelta.z),
                                )
                            )
                            is Vector4f -> ch.addKeyframe(
                                localTime, Vector4f(prev).mul(
                                    pow(base, localDelta.x),
                                    pow(base, localDelta.y),
                                    pow(base, localDelta.z),
                                    1f,
                                )
                            )
                        }
                    } else {
                        selected.scale.addKeyframe(
                            localTime, Vector3f(oldScale).mul(
                                pow(base, localDelta.x),
                                pow(base, localDelta.y),
                                pow(base, localDelta.z)
                            )
                        )
                    }
                    invalidateUI(false)
                }
            }
            SceneDragMode.ROTATE -> {
                // todo transform rotation??? quaternions...
                val centerX = x + width / 2
                val centerY = y + height / 2
                val window = window!!
                val mdx = (window.mouseX - centerX).toDouble()
                val mdy = (window.mouseY - centerY).toDouble()
                val oldDegree = toDegrees(atan2(mdy - dy0, mdx - dx0)).toFloat()
                val newDegree = toDegrees(atan2(mdy, mdx)).toFloat()
                val deltaDegree = newDegree - oldDegree
                val speed2 = 20f / height
                val oldRotation = selected.rotationYXZ[localTime]
                val localDelta =
                    if (isControlDown) Vector3f(dx0 * speed2, -dy0 * speed2, 0f)
                    else Vector3f(0f, 0f, -deltaDegree)
                invalidateDrawing()
                RemsStudio.incrementalChange("Rotate Object") {
                    if (selectedRotDist != null) {
                        // add keyframe to selectedRotDist
                        val ch = selectedDist.channels[CenterSizeDistribution.ROTATION_INDEX]
                        when (val prev = ch[localTime]) {
                            is Float -> ch.addKeyframe(localTime, prev - deltaDegree)
                            is Vector2f -> ch.addKeyframe(localTime, Vector2f(prev).add(localDelta.x, localDelta.y))
                            is Vector3f -> ch.addKeyframe(localTime, Vector3f(prev).add(localDelta))
                            is Vector4f -> {
                                val localDelta1 = Vector4f(localDelta.x, localDelta.y, localDelta.z, 0f)
                                ch.addKeyframe(localTime, Vector4f(prev).add(localDelta1))
                            }
                        }
                    } else {
                        selected.rotationYXZ.addKeyframe(localTime, Vector3f(oldRotation).add(localDelta))
                    }
                    invalidateUI(false)
                }
            }
        }

    }

    override fun onMouseMoved(x: Float, y: Float, dx: Float, dy: Float) {
        // fov is relative to height -> modified to depend on height
        val size = 20f * shiftSlowdown / window!!.height
        val dx0 = dx * size
        val dy0 = dy * size
        // move stuff, if mouse is down and no touch is down
        if (Input.isLeftDown && touches.size < 2) {
            // move the object
            val selected = selectedTransforms
            if (selected.isNotEmpty() && camera !in selected) {
                moveSelected(selected, dx, dy)
            } else {
                moveCamera(-dx0, +dy0, 0f)
            }
        }
    }

    fun turn(dx: Float, dy: Float) {
        if (!mayControlCamera) {
            if (length(dx, dy) > 0f) LOGGER.warn("Cannot control camera")
            return
        }
        if (isLocked2D) {
            if (length(dx, dy) > 0f) LOGGER.warn("Rotation is locked")
            return
        }
        // move the camera
        val window = window ?: return
        val turnSpeed = DefaultConfig["ui.editor.turnSpeed", -400f] * shiftSlowdown / max(window.width, window.height)
        val dx0 = dx * turnSpeed
        val dy0 = dy * turnSpeed
        val camera = camera
        val cameraTime = cameraTime
        val oldRotation = camera.rotationYXZ[cameraTime]
        // if(camera.orthographicness[cameraTime] > 0.5f) scaleFactor = -scaleFactor
        invalidateDrawing()
        RemsStudio.incrementalChange("Turn Camera") {
            val newRotation = Vector3f(oldRotation).add(dy0, dx0, 0f)
            newRotation.x = clamp(newRotation.x, -90f, 90f)
            camera.putValue(camera.rotationYXZ, newRotation, false)
            invalidateUI(false)
        }
    }

    val cameraTime get() = camera.getGlobalTransformTime(editorTime).second
    val firstCamera get() = root.listOfAll.firstInstanceOrNull2(Camera::class)

    fun rotateCameraTo(rotation: Vector3f) {
        camera.putValue(camera.rotationYXZ, rotation, true)
    }

    fun rotateCamera(delta: Vector3f) {
        val oldRot = camera.rotationYXZ[cameraTime]
        camera.putValue(
            camera.rotationYXZ,
            oldRot + delta,
            true
        )
    }

    override fun onGotAction(x: Float, y: Float, dx: Float, dy: Float, action: String, isContinuous: Boolean): Boolean {
        when (action) {
            "SetMode(MOVE)" -> {
                mode = SceneDragMode.MOVE
                invalidateDrawing()
            }
            "SetMode(SCALE)" -> {
                mode = SceneDragMode.SCALE
                invalidateDrawing()
            }
            "SetMode(ROTATE)" -> {
                mode = SceneDragMode.ROTATE
                invalidateDrawing()
            }
            "Cam0", "ResetCamera" -> {
                val firstCamera = firstCamera
                if (firstCamera == null) {
                    camera.resetTransform(true)
                } else {
                    // copy the transform
                    val firstCameraTime = firstCamera.getGlobalTransformTime(editorTime).second
                    RemsStudio.largeChange("Reset Camera") {
                        camera.cloneTransform(firstCamera, firstCameraTime)
                    }
                }
                invalidateDrawing()
            }

            "Cam5" -> {// switch between orthographic and perspective
                camera.putValue(camera.orthographicness, 1f - camera.orthographicness[cameraTime], true)
            }
            // todo control + numpad does not work
            "Cam1" -> rotateCameraTo(Vector3f(0f, if (isControlDown) 180f else 0f, 0f))// default view
            "Cam3" -> rotateCameraTo(Vector3f(0f, if (isControlDown) -90f else +90f, 0f))// rotate to side view
            "Cam7" -> rotateCameraTo(Vector3f(if (isControlDown) +90f else -90f, 0f, 0f)) // look from above
            "Cam4" -> rotateCamera(
                if (isShiftDown) {// left
                    Vector3f(0f, 0f, -15f)
                } else {
                    Vector3f(0f, -15f, 0f)
                }
            )

            "Cam6" -> rotateCamera(
                if (isShiftDown) {// right
                    Vector3f(0f, 0f, +15f)
                } else {
                    Vector3f(0f, +15f, 0f)
                }
            )

            "Cam8" -> rotateCamera(Vector3f(-15f, 0f, 0f)) // up
            "Cam2" -> rotateCamera(Vector3f(+15f, 0f, 0f)) // down
            "Cam9" -> rotateCamera(Vector3f(0f, 180f, 0f)) // look at back; rotate by 90 degrees on y-axis
            "MoveLeft" -> this.inputDx--
            "MoveRight" -> this.inputDx++
            "MoveUp" -> this.inputDy++
            "MoveDown" -> this.inputDy--
            "MoveForward" -> this.inputDz--
            "MoveBackward", "MoveBack" -> this.inputDz++
            "Turn" -> turn(dx, dy)
            "TurnLeft" -> turn(-1f, 0f)
            "TurnRight" -> turn(1f, 0f)
            "TurnUp" -> turn(0f, -1f)
            "TurnDown" -> turn(0f, 1f)
            else -> return super.onGotAction(x, y, dx, dy, action, isContinuous)
        }
        return true
    }

    fun resolveClick(x: Float, y: Float, onClick: (Transform?) -> Unit) {
        val w = stableSize.stableWidth
        val h = stableSize.stableHeight
        addGPUTask("click", w, h) {
            try {
                resolveClick(x, y, w, h, onClick)
            } catch (e: Exception) {
                LOGGER.warn("could not execute click")
                e.printStackTrace()
            }
        }
    }

    override fun onDoubleClick(x: Float, y: Float, button: Key) {
        if (button == Key.BUTTON_LEFT && mayControlCamera) {
            onInteraction()
            invalidateDrawing()
            val xi = x.toInt()
            val yi = y.toInt()
            for (it in controls) {
                if (it.contains(xi, yi)) {
                    it.drawable.onMouseClicked(x, y, button, false)
                    return
                }
            }
            // goFullscreen()
            // zoom on that object instead
            resolveClick(x, y) {
                if (it != null) {
                    zoomToObject(it)
                }
            }
        } else super.onDoubleClick(x, y, button)
    }

    override fun onMouseClicked(x: Float, y: Float, button: Key, long: Boolean) {
        onInteraction()
        invalidateDrawing()
        if (button == Key.BUTTON_LEFT && (parent as? CustomContainer)?.clicked(x, y) != true) {

            var isProcessed = false
            val xi = x.toInt()
            val yi = y.toInt()
            for (it in controls) {
                if (it.contains(xi, yi)) {
                    it.drawable.onMouseClicked(x, y, button, long)
                    isProcessed = true
                }
            }

            if (!isProcessed && wasInFocus) {
                resolveClick(x, y) { tr ->
                    if (isShiftDown || isControlDown) {
                        // todo if(isShiftDown) select all in-between...
                        if (tr != null) {
                            val newList = if (tr in selectedTransforms) {
                                selectedTransforms.filter { it != tr }
                            } else {
                                selectedTransforms + tr
                            }
                            select(newList, emptyList())
                        }
                    } else selectTransform(tr)
                    invalidateDrawing()
                }
            }
        } else super.onMouseClicked(x, y, button, long)
    }

    override fun onPaste(x: Float, y: Float, data: String, type: String) {
        when (type) {
            "Transform" -> {
                val original = dragged?.getOriginal() ?: return
                if (original is Camera) {
                    RemsStudio.largeChange("Changed Scene-View Camera to ${original.name}") {
                        camera = original
                    }
                }// else focus?
                invalidateDrawing()
            }
            // file -> paste object from file?
            // paste that object one meter in front of the camera?
            else -> super.onPaste(x, y, data, type)
        }
    }

    override fun onEmpty(x: Float, y: Float) {
        onInteraction()
        deleteSelectedTransform()
    }

    override fun onDeleteKey(x: Float, y: Float) {
        onInteraction()
        deleteSelectedTransform()
    }

    private fun deleteSelectedTransform() {
        val selectedTransforms = selectedTransforms
        if (selectedTransforms.isEmpty()) return
        invalidateDrawing()
        RemsStudio.largeChange("Deleted Component") {
            for (s in selectedTransforms) s.destroy()
            select(emptyList(), emptyList())
        }
    }

    override fun onPasteFiles(x: Float, y: Float, files: List<FileReference>) {
        val mode = FileContentImporter.SoftLinkMode.ASK
        for (file in files) {
            addChildFromFile(RemsStudio.root, file, mode, true) { }
        }
        invalidateDrawing()
    }

    override fun onMouseWheel(x: Float, y: Float, dx: Float, dy: Float, byMouse: Boolean) {
        if (mayControlCamera && !camera.lockTransform) {
            onInteraction()
            invalidateDrawing()
            RemsStudio.incrementalChange("Zoom In / Out") {
                val camera = camera
                val radius = camera.orbitRadius[cameraTime]
                if (radius == 0f) {
                    // no orbiting
                    moveCamera(0f, 0f, -0.5f * dy)
                } else {
                    val delta = -dy * shiftSlowdown
                    val factor = pow(1.02f, delta)
                    val newOrbitDistance = radius * factor
                    if (isLocked2D) {
                        // zoom in on point in 2D using mouse position
                        val fov =
                            (factor - 1f) * radius * tan(camera.fovYDegrees[cameraTime].toRadians() * 0.5f) * 2f / height
                        val dx2 = +(this.x - x + this.width * 0.5f) * fov
                        val dy2 = -(this.y - y + this.height * 0.5f) * fov
                        val oldPos = camera.position[cameraTime]
                        oldPos.add(dx2, dy2, 0f)
                        camera.putValue(camera.position, oldPos, false)
                    }
                    camera.putValue(camera.orbitRadius, newOrbitDistance, false)
                    if (camera == nullCamera) {
                        camera.putValue(camera.farZ, camera.farZ[cameraTime] * factor, false)
                        camera.putValue(camera.nearZ, camera.nearZ[cameraTime] * factor, false)
                    }
                }
            }
        } else super.onMouseWheel(x, y, dx, dy, byMouse)
    }

    override val className get() = "StudioSceneView"

}