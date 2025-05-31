package me.anno.remsstudio.objects.effects

import me.anno.ecs.annotations.ExtendableEnum
import me.anno.language.translation.NameDesc
import me.anno.utils.OS.res

class MaskType(
    override val id: Int,
    override val nameDesc: NameDesc,
    val shaderString: String
) : ExtendableEnum {

    override val values: List<ExtendableEnum> get() = entries

    init {
        entries.add(this)
    }

    companion object {

        val entries = ArrayList<MaskType>()

        private fun load(path: String): String {
            val text = res.getChild("shader/mask-effects/$path.glsl").readTextSync()
                .trim().replace("\r", "")
            return text.substring(text.indexOf('\n') + 1, text.lastIndexOf('\n'))
        }

        private val blurCode = "" +
                "effect = mix(mask.a, dot(vec3(0.3), mask.rgb), useMaskColor);\n" +
                "if(invertMask1) effect = 1.0 - effect;\n" +
                "color = mix(texture(tex, uv2), texture(tex2, uv2), effect);\n"

        val MASKING = MaskType(
            0, NameDesc("Masking", "", "obj.maskType.masking"),
            load("Masking")
        )
        val TRANSITION = MaskType(
            9, NameDesc("Transition", "Use a grayscale image as a transition pattern", "obj.maskType.transition"),
            load("Transition")
        )
        val QUAD_PIXELATION = MaskType(
            1, NameDesc("Quad Pixelation", "", "obj.maskType.pixelating"),
            load("QuadPixelating")
        )
        val TRI_PIXELATION = MaskType(
            10, NameDesc("Triangle Pixelation", "", "obj.maskType.triPixelating"),
            load("TriPixelating")
        )
        val HEX_PIXELATION = MaskType(
            11, NameDesc("Hexagon Pixelation", "", "obj.maskType.hexPixelating"),
            load("HexPixelating")
        )
        val VORONOI_PIXELATION = MaskType(
            12, NameDesc("Voronoi Pixelation", "", "obj.maskType.voronoiPixelating"),
            load("VoronoiPixelating")
        )
        val GAUSSIAN_BLUR = MaskType(
            2, NameDesc("Gaussian Blur", "", "obj.maskType.gaussianBlur"),
            blurCode
        )
        val RADIAL_BLUR_1 = MaskType(
            7, NameDesc("Radial Blur (1)", "", "obj.maskType.radialBlur1"),
            load("RadialBlur1")
        )
        val RADIAL_BLUR_2 = MaskType(
            8, NameDesc("Radial Blur (2)", "", "obj.maskType.radialBlur2"),
            load("RadialBlur2")
        )
        val BOKEH_BLUR = MaskType(
            3, NameDesc("Bokeh Blur", "", "obj.maskType.bokehBlur"),
            blurCode
        )
        val BLOOM = MaskType(
            5, NameDesc("Bloom", "", "obj.maskType.bloom"),
            blurCode
        )
        val UV_OFFSET = MaskType(
            4, NameDesc("Per-Pixel Offset", "", "obj.maskType.pixelOffset"),
            "" +
                    "vec2 offset = (mask.rg-mask.gb) * pixelating;\n" +
                    "color = texture(tex, uv2 + offset);\n"
        )
        val GREEN_SCREEN = MaskType(
            6, NameDesc("Green-Screen", "", "obj.maskType.greenScreen"),
            load("GreenScreen")
        )
    }

}