package me.anno.remsstudio.objects.transitions

import me.anno.ecs.annotations.ExtendableEnum
import me.anno.language.translation.NameDesc
import me.anno.utils.OS.res

class TransitionType(
    override val id: Int,
    override val nameDesc: NameDesc,
    val shaderString: Pair<String, String>,
) : ExtendableEnum {

    override val values: List<ExtendableEnum>
        get() = entries

    init {
        entries.add(this)
    }

    @Suppress("unused")
    companion object {

        val entries = ArrayList<TransitionType>()

        private fun load(path: String): Pair<String, String> {
            val text = res.getChild("shader/transitions/$path.glsl").readTextSync()
                .trim().replace("\r", "")
            val start = text.indexOf("void main(")
            val libraries = text.substring(0, start)
            val main = text.substring(text.indexOf('\n', start) + 1, text.lastIndexOf('\n'))
            return libraries to main
        }

        val CROSS_FADE = TransitionType(
            0, NameDesc("Cross Fade", "Just linear blending", ""),
            load("CrossFade")
        )
        val CUT = TransitionType(
            1, NameDesc("Cut", "Instantly cuts from one video to the other", ""),
            load("Cut")
        )
        val FADE_TO_COLOR = TransitionType(
            2, NameDesc("Fade to Color", "Going to a color, then to the second video", ""),
            load("FadeToColor")
        )
        val WIPE = TransitionType(
            3, NameDesc("Wipe", "Edge that moves across screen", ""),
            load("Wipe")
        )
        val SPLIT_SCREEN_SLIDE = TransitionType(
            4, NameDesc("Split Screen Slide", "Two opposing edges.", ""),
            load("SplitScreen")
        )
        val CIRCLE_GROWING = TransitionType(
            5, NameDesc("Circle Grow", "Circle that becomes bigger", ""),
            load("CircleGrow")
        )
        val CIRCLE_SHRINKING = TransitionType(
            6, NameDesc("Circle Shrink", "Circle that becomes smaller", ""),
            load("CircleShrink")
        )
        val ZOOM_IN = TransitionType(
            7, NameDesc("Zoom In", "Zooms into the video quickly, then out of the center of the second", ""),
            load("ZoomIn")
        )
        val ZOOM_OUT = TransitionType(
            8, NameDesc("Zoom Out", "Zooms out of the video, then back in", ""),
            load("ZoomOut")
        )
        val SLIDE = TransitionType(
            9, NameDesc("Slide", "First video moves out, second moves in", ""),
            load("Slide")
        )
        val SPIN = TransitionType(
            10, NameDesc("Spin", "Rotates first image out, second image in. Center should be any of the corners.", ""),
            load("Spin")
        )
        val ROTATE3D = TransitionType(
            21, NameDesc("Rotate 3D", "Rotates in 3D. Image switches when it's rotated exactly 90°.", ""),
            load("Rotate3D")
        )
        val LUMA_FADE = TransitionType(
            11, NameDesc("Luma Fade", "Fades based on brightness of first or second texture", ""),
            load("LumaFade")
        )
        val FLASH_BANG = TransitionType(
            12, NameDesc("Flash Bang", "Makes the image incredibly bright, then dims to the second", ""),
            load("FlashBang")
        )
        val DIRECTIONAL_BLUR = TransitionType(
            22, NameDesc("Directional Blur", "Blurs video along a direction", ""),
            load("DirectionalBlur")
        )

        // todo do we want to support all pixelation types, again? quad/tri/hex/voronoi
        val PIXELATION = TransitionType(
            20, NameDesc("Pixelation", "Pixelates the image, transitions, then unpixelates", ""),
            load("Pixelation")
        )

        val GLITCH = 0 // todo this is probably a lot to do...

        // LIGHT_LEAKS is just additional light on top... not really a transition effect

        val SWIPE = 0
        val OBJECT_WIPE = 0 // todo moving object transitions to the next scene??? is that a third texture???

        val WHIP_PAN =
            0 // "Fast camera-like movement between clips, often blurred." todo can we even implement that or is that just the camera...???
        val SHAKE = 0 // todo add artificial shake, and then transition?

        val INK_BLEED = 0 // todo how can we implement that???
        val SHAPE_REVEAL = 0 // todo circles/triangles/custom, what does this do?

        val TIME_WARP = 0 // todo "trails motion between clips", what does this do?
        // match action cut - action in one scene matches the next = we cannot do that one

    }

}