package me.anno.remsstudio.objects

import me.anno.animation.Type
import me.anno.config.DefaultConfig
import me.anno.gpu.GFX
import me.anno.gpu.drawing.Perspective.perspective2
import me.anno.gpu.pipeline.Sorting
import me.anno.io.ISaveable
import me.anno.io.base.BaseWriter
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.language.translation.Dict
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.pow
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.RemsStudio.currentlyDrawnCamera
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.objects.effects.ToneMappers
import me.anno.remsstudio.objects.models.CameraModel.drawCamera
import me.anno.studio.Inspectable
import me.anno.ui.base.buttons.TextButton
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.style.Style
import me.anno.utils.Color.white4
import me.anno.utils.files.LocalFile.toGlobalFile
import me.anno.utils.pooling.JomlPools
import me.anno.utils.types.Casting.castToFloat2
import me.anno.utils.types.Floats.toRadians
import org.joml.*

class Camera(parent: Transform? = null) : Transform(parent) {

    // kind of done allow cameras to be merged
    // kind of done allow cameras to film camera (post processing) -> create a stack of cameras/scenes?
    // by implementing SoftLink: scenes can be included in others

    // orthographic-ness by setting the camera back some amount, and narrowing the view

    // todo actually order the scene before drawing
    var order = Sorting.FRONT_TO_BACK

    var lut: FileReference = InvalidRef
    val nearZ = AnimatedProperty.floatPlus(0.001f)
    val farZ = AnimatedProperty.floatPlus(1000f)
    val fovYDegrees = AnimatedProperty(fovType, 90f)
    val chromaticAberration = AnimatedProperty.floatPlus()
    val chromaticOffset = AnimatedProperty.vec2()
    val chromaticAngle = AnimatedProperty.float()
    val distortion = AnimatedProperty.vec3()
    val distortionOffset = AnimatedProperty.vec2()
    val orthographicness = AnimatedProperty.float01()
    val vignetteStrength = AnimatedProperty.floatPlus()
    val vignetteColor = AnimatedProperty.color3(Vector3f(0f, 0f, 0f))

    val orbitRadius = AnimatedProperty.floatPlus(1f)

    val cgOffsetAdd = AnimatedProperty.color3(Vector3f())
    val cgOffsetSub = AnimatedProperty.color3(Vector3f())
    val cgSlope = AnimatedProperty.color(white4)
    val cgPower = AnimatedProperty.color(white4)
    val cgSaturation = AnimatedProperty.float(1f) // only allow +? only 01?

    val bloomSize = AnimatedProperty.floatPlus(0.05f)
    val bloomIntensity = AnimatedProperty.floatPlus(0f)
    val bloomThreshold = AnimatedProperty.floatPlus(0.8f)

    var toneMapping = ToneMappers.RAW8

    var onlyShowTarget = true
    var useDepth = true

    var backgroundColor = AnimatedProperty.color(Vector4f(0f, 0f, 0f, 1f))

    fun getEffectiveOffset(localTime: Double) = orthographicDistance(orthographicness[localTime])
    fun getEffectiveNear(localTime: Double, offset: Float = getEffectiveOffset(localTime)) = nearZ[localTime] + offset
    fun getEffectiveFar(localTime: Double, offset: Float = getEffectiveOffset(localTime)) = farZ[localTime] + offset
    fun getEffectiveFOV(localTime: Double, offset: Float = getEffectiveOffset(localTime)) =
        orthographicFOV(fovYDegrees[localTime], offset)

    fun orthographicDistance(orthographicness: Float) = pow(200f, orthographicness) - 1f
    fun orthographicFOV(fov: Float, offset: Float) = fov / (1f + offset)

    override fun createInspector(
        inspected: List<Inspectable>,
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {

        super.createInspector(inspected, list, style, getGroup)
        val c = inspected.filterIsInstance<Camera>()

        val transform = getGroup("Transform", "", "transform")
        transform += vis(
            inspected, c, "Orbit Radius", "Orbiting Distance", "camera.orbitDis",
            c.map { it.orbitRadius }, style
        )
        transform += vis(
            inspected, c, "Background Color", "Clearing color for the screen", "camera.backgroundColor",
            c.map { it.backgroundColor }, style
        )


        val cam = getGroup("Projection", "How rays of light are mapped to the screen", "projection")
        cam += vis(
            inspected, c, "FOV", "Field Of View, in degrees, vertical", "camera.fov",
            c.map { it.fovYDegrees }, style
        )
        cam += vis(
            inspected, c, "Perspective - Orthographic",
            "Sets back the camera", "camera.orthographicness",
            c.map { it.orthographicness }, style
        )

        val depth = getGroup("Depth", "Z-axis related settings; from camera perspective", "depth")
        depth += vis(
            inspected, c, "Near Z", "Closest Visible Distance", "camera.depth.near",
            c.map { it.nearZ }, style
        )
        depth += vis(
            inspected, c, "Far Z", "Farthest Visible Distance", "camera.depth.far",
            c.map { it.farZ }, style
        )
        depth += vi(
            inspected, "Use Depth",
            "Causes Z-Fighting, but allows 3D", "camera.depth.enabled",
            null, useDepth, style
        ) { for (x in c) x.useDepth = it }
        /*depth += vi(
            "Order By Z", "Transparent objects need to be sorted to appear correctly", null, order, style
        ) { order = it }*/


        val chroma = getGroup("Chromatic Aberration", "Effect occurring in cheap lenses", "chroma")
        chroma += vis(
            inspected, c, "Strength", "How large the effect is", "camera.chromaStrength",
            c.map { it.chromaticAberration }, style
        )
        chroma += vis(
            inspected, c, "Offset", "Offset", "camera.chromaOffset",
            c.map { it.chromaticOffset }, style
        )
        chroma += vis(
            inspected, c, "Rotation", "Rotation/angle in Degrees", "camera.chromaRotation",
            c.map { it.chromaticAngle }, style
        )


        val dist = getGroup("Distortion", "Transforms the image", "distortion")
        dist += vis(
            inspected, c, "Distortion", "Params: R², R⁴, Scale",
            c.map { it.distortion }, style
        )
        dist += vis(
            inspected, c, "Distortion Offset", "Moves the center of the distortion",
            c.map { it.distortionOffset }, style
        )


        val vignette = getGroup("Vignette", "Darkens/colors the border", "vignette")
        vignette += vis(
            inspected, c, "Vignette Color", "Color of border", "vignette.color",
            c.map { it.vignetteColor }, style
        )
        vignette += vis(
            inspected, c, "Vignette Strength", "Strength of colored border", "vignette.strength",
            c.map { it.vignetteStrength }, style
        )


        val bloom = getGroup("Bloom", "Adds a light halo around bright objects", "bloom")
        bloom += vis(
            inspected, c, "Intensity", "Brightness of effect, 0 = off", "bloom.intensity",
            c.map { it.bloomIntensity }, style
        )
        bloom += vis(
            inspected, c, "Effect Size", "How much it is blurred", "bloom.size",
            c.map { it.bloomSize }, style
        )
        bloom += vis(
            inspected, c, "Threshold", "Minimum brightness", "bloom.threshold",
            c.map { it.bloomThreshold }, style
        )


        val color = getGroup("Color", "Tint and Tonemapping", "color")
        color += vi(
            inspected, "Tone Mapping",
            "Maps large ranges of brightnesses (e.g. HDR) to monitor color space", "camera.toneMapping",
            null, toneMapping, style
        ) { for (x in c) x.toneMapping = it }
        color += vi(
            inspected, "Look Up Table",
            "LUT, Look Up Table for colors, formatted like in UE4", "camera.lut", null, lut, style
        ) { for (x in c) x.lut = it }

        ColorGrading.createInspector(
            inspected, c, c.map { it.cgPower }, c.map { it.cgSaturation }, c.map { it.cgSlope },
            c.map { it.cgOffsetAdd }, c.map { it.cgOffsetSub }, { it }, getGroup, style
        )

        val editor = getGroup("Editor", "Settings, which only effect editing", "editor")
        editor += vi(
            inspected, "Only Show Target",
            "Forces the viewport to have the correct aspect ratio",
            null, onlyShowTarget, style
        ) { for (x in c) x.onlyShowTarget = it }

        val ops = getGroup("Operations", "Actions", "operations")
        ops += TextButton("Reset Transform", "If accidentally moved", "obj.camera.resetTransform", false, style)
            .addLeftClickListener {
                RemsStudio.largeChange("Reset Camera Transform") {
                    for (x in c) x.resetTransform(false)
                }
            }
    }

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {

        if (GFX.isFinalRendering) return
        if (this === currentlyDrawnCamera) return

        val offset = getEffectiveOffset(time)
        val fov = getEffectiveFOV(time, offset)
        val near = getEffectiveNear(time, offset)
        val far = getEffectiveFar(time, offset)

        super.onDraw(stack, time, color)

        stack.translate(0f, 0f, orbitRadius[time])

        drawCamera(stack, offset, color, fov, near, far)

    }

    fun resetTransform(updateHistory: Boolean) {
        if (updateHistory) {
            RemsStudio.largeChange("Reset Camera Transform") {
                resetTransform(false)
            }
        } else {
            putValue(position, Vector3f(), false)
            putValue(scale, Vector3f(1f, 1f, 1f), false)
            putValue(skew, Vector2f(0f, 0f), false)
            putValue(rotationYXZ, Vector3f(), false)
            putValue(orbitRadius, 1f, false)
            putValue(nearZ, 0.001f, false)
            putValue(farZ, 1000f, false)
        }
    }

    fun cloneTransform(src: Transform, srcTime: Double) {
        putValue(position, src.position[srcTime], false)
        putValue(rotationYXZ, src.rotationYXZ[srcTime], false)
        putValue(scale, src.scale[srcTime], false)
        putValue(skew, src.skew[srcTime], false)
        if (src is Camera) {
            putValue(fovYDegrees, src.fovYDegrees[srcTime], false)
            putValue(orbitRadius, src.orbitRadius[srcTime], false)
            putValue(nearZ, src.nearZ[srcTime], false)
            putValue(farZ, src.farZ[srcTime], false)
        }
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeObject(this, "orbitRadius", orbitRadius)
        writer.writeObject(this, "nearZ", nearZ)
        writer.writeObject(this, "farZ", farZ)
        writer.writeObject(this, "fovY", fovYDegrees)
        writer.writeObject(this, "chromaticAberration", chromaticAberration)
        writer.writeObject(this, "chromaticOffset", chromaticOffset)
        writer.writeObject(this, "distortion", distortion)
        writer.writeObject(this, "distortionOffset", distortionOffset)
        writer.writeObject(this, "orthographicness", orthographicness)
        writer.writeObject(this, "vignetteStrength", vignetteStrength)
        writer.writeObject(this, "vignetteColor", vignetteColor)
        writer.writeObject(this, "bloomIntensity", bloomIntensity)
        writer.writeObject(this, "bloomSize", bloomSize)
        writer.writeObject(this, "bloomThreshold", bloomThreshold)
        writer.writeInt("toneMapping", toneMapping.id, true)
        writer.writeBoolean("onlyShowTarget", onlyShowTarget)
        writer.writeBoolean("useDepth", useDepth)
        writer.writeFile("lut", lut)
        writer.writeObject(this, "cgSaturation", cgSaturation)
        writer.writeObject(this, "cgOffsetAdd", cgOffsetAdd)
        writer.writeObject(this, "cgOffsetSub", cgOffsetSub)
        writer.writeObject(this, "cgSlope", cgSlope)
        writer.writeObject(this, "cgPower", cgPower)
        writer.writeObject(this, "backgroundColor", backgroundColor)
    }

    override fun readBoolean(name: String, value: Boolean) {
        when (name) {
            "onlyShowTarget" -> onlyShowTarget = value
            "useDepth" -> useDepth = value
            else -> super.readBoolean(name, value)
        }
    }

    override fun readObject(name: String, value: ISaveable?) {
        when (name) {
            "orbitRadius" -> orbitRadius.copyFrom(value)
            "nearZ" -> nearZ.copyFrom(value)
            "farZ" -> farZ.copyFrom(value)
            "fovY" -> fovYDegrees.copyFrom(value)
            "chromaticAberration" -> chromaticAberration.copyFrom(value)
            "chromaticOffset" -> chromaticOffset.copyFrom(value)
            "distortion" -> distortion.copyFrom(value)
            "distortionOffset" -> distortionOffset.copyFrom(value)
            "orthographicness" -> orthographicness.copyFrom(value)
            "vignetteStrength" -> vignetteStrength.copyFrom(value)
            "vignetteColor" -> vignetteColor.copyFrom(value)
            "bloomIntensity" -> bloomIntensity.copyFrom(value)
            "bloomThreshold" -> bloomThreshold.copyFrom(value)
            "bloomSize" -> bloomSize.copyFrom(value)
            "cgSaturation" -> cgSaturation.copyFrom(value)
            "cgOffset", "cgOffsetAdd" -> cgOffsetAdd.copyFrom(value)
            "cgOffsetSub" -> cgOffsetSub.copyFrom(value)
            "cgSlope" -> cgSlope.copyFrom(value)
            "cgPower" -> cgPower.copyFrom(value)
            "backgroundColor" -> backgroundColor.copyFrom(value)
            else -> super.readObject(name, value)
        }
    }

    override fun readString(name: String, value: String?) {
        when (name) {
            "lut" -> lut = value?.toGlobalFile() ?: InvalidRef
            else -> super.readString(name, value)
        }
    }

    override fun readFile(name: String, value: FileReference) {
        when (name) {
            "lut" -> lut = value
            else -> super.readFile(name, value)
        }
    }

    override fun readInt(name: String, value: Int) {
        when (name) {
            "toneMapping" -> toneMapping = ToneMappers.values().firstOrNull { it.id == value } ?: toneMapping
            else -> super.readInt(name, value)
        }
    }

    fun applyTransform(time: Double, cameraTransform: Matrix4f, stack: Matrix4fArrayList) {
        val offset = getEffectiveOffset(time)
        val orbitRadius = orbitRadius[time]
        val tmpMatrix0 = JomlPools.mat4f.create()
        cameraTransform.translate(0f, 0f, orbitRadius)
        val cameraTransform2 = if (offset != 0f) {
            tmpMatrix0.set(cameraTransform).translate(0f, 0f, offset)
        } else cameraTransform
        val fov = getEffectiveFOV(time, offset)
        val near = getEffectiveNear(time, offset)
        val far = getEffectiveFar(time, offset)
        val tmp0 = JomlPools.vec3f.create()
        val tmp1 = JomlPools.vec3f.create()
        val tmp2 = JomlPools.vec3f.create()
        val position = cameraTransform2.transformProject(tmp0.set(0f, 0f, 0f))
        val up = cameraTransform2.transformProject(tmp1.set(0f, 1f, 0f))
            .sub(position).normalize()
        val lookAt = cameraTransform2.transformProject(tmp2.set(0f, 0f, -1f))
        val aspectRatio = GFX.viewportWidth.toFloat() / GFX.viewportHeight
        stack.perspective2(
            fov.toRadians(), aspectRatio, near, far, 0f, 0f
        ).lookAt(position, lookAt, up)
        val scale = pow(1f / orbitRadius, orthographicness[time])
        if (scale != 0f && scale.isFinite()) stack.scale(scale)
        JomlPools.vec3f.sub(3)
        JomlPools.mat4f.sub(1)
    }

    override val className get() = "Camera"
    override val defaultDisplayName get() = Dict["Camera", "obj.camera"]
    override val symbol get() = DefaultConfig["ui.symbol.camera", "\uD83C\uDFA5"]

    companion object {

        // linear and exponential aren't really the correct types...
        // around 0f and 180f should have exponential speed decay
        val fovType = Type(90f, 1, 1f, true, true, { clamp(castToFloat2(it), 0.001f, 179.999f) }, { it })

        const val DEFAULT_VIGNETTE_STRENGTH = 5f

    }

}