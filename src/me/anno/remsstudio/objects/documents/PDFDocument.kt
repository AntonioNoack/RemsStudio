package me.anno.remsstudio.objects.documents

import me.anno.ui.input.NumberType
import me.anno.cache.instances.PDFCache
import me.anno.cache.instances.PDFCache.getTexture
import me.anno.config.DefaultConfig
import me.anno.gpu.GFX.isFinalRendering
import me.anno.gpu.GFX.viewportHeight
import me.anno.gpu.GFX.viewportWidth
import me.anno.gpu.drawing.UVProjection
import me.anno.gpu.texture.Clamping
import me.anno.gpu.texture.TextureLib.colorShowTexture
import me.anno.io.Saveable
import me.anno.io.base.BaseWriter
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.gpu.GFXx3Dv2
import me.anno.remsstudio.gpu.TexFiltering
import me.anno.remsstudio.gpu.TexFiltering.Companion.getFiltering
import me.anno.remsstudio.objects.GFXTransform
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.documents.SiteSelection.parseSites
import me.anno.remsstudio.objects.lists.Element
import me.anno.remsstudio.objects.lists.SplittableElement
import me.anno.engine.inspector.Inspectable
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.utils.Clipping
import me.anno.utils.files.LocalFile.toGlobalFile
import me.anno.utils.structures.ValueWithDefault.Companion.writeMaybe
import me.anno.utils.structures.ValueWithDefaultFunc
import me.anno.utils.structures.lists.Lists.median
import me.anno.utils.types.Floats.toRadians
import me.anno.utils.types.Strings.isBlank2
import org.apache.logging.log4j.LogManager
import org.apache.pdfbox.pdmodel.PDDocument
import org.joml.Matrix4f
import org.joml.Matrix4fArrayList
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.*

// todo different types of lists (x list, y list, grid, linear particle system, random particle system, ...)
// todo different types of iterators (pdf pages, parts of images, )
// todo re-project UV textures onto stuff to animate an image exploding (gets UVs from first frame, then just is a particle system or sth else)
// todo interpolation between lists and sets? could be interesting :)

open class PDFDocument(var file: FileReference, parent: Transform?) : GFXTransform(parent), SplittableElement {

    constructor() : this(InvalidRef, null)

    var selectedSites = ""

    var padding = AnimatedProperty.float(0f)

    var direction = AnimatedProperty.rotY()

    var editorQuality = 3f
    var renderQuality = 3f

    override val defaultDisplayName: String
        get() {
            // file can be null
            return if (file == InvalidRef || file.name.isBlank2()) "PDF"
            else file.name
        }

    override val className get() = "PDFDocument"
    override val symbol get() = "\uD83D\uDDCE"

    fun getSelectedSitesList() = parseSites(selectedSites)

    val meta get() = getMeta(file, true)
    val forcedMeta get() = getMeta(file, false)!!

    fun getMeta(src: FileReference, async: Boolean): PDFCache.AtomicCountedDocument? {
        if (!src.exists) return null
        return PDFCache.getDocumentRef(src, src.inputStreamSync(), true, async)
    }

    // rather heavy...
    override fun getRelativeSize(): Vector3f {
        val ref = forcedMeta
        val doc = ref.doc
        val pageCount = doc.numberOfPages
        val referenceScale = (0 until min(10, pageCount)).map {
            doc.getPage(it).mediaBox.run {
                if (viewportWidth > viewportHeight) height else width
            }
        }.median(0f)
        if (pageCount < 1) {
            ref.returnInstance()
            return Vector3f(1f)
        }
        val firstPage = getSelectedSitesList().firstOrNull()?.first ?: return Vector3f(1f)
        val size = doc.getPage(firstPage).mediaBox.run { Vector3f(width / referenceScale, height / referenceScale, 1f) }
        ref.returnInstance()
        return size
    }

    fun getQuality() = if (isFinalRendering) renderQuality else editorQuality
    val filtering = ValueWithDefaultFunc { DefaultConfig.getFiltering("default.video.filtering", TexFiltering.CUBIC) }

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {

        val file = file
        val ref = meta
        if (ref == null) {
            super.onDraw(stack, time, color)
            return checkFinalRendering()
        }

        val doc = ref.doc
        val quality = getQuality()
        val numberOfPages = doc.numberOfPages
        val pageRanges = getSelectedSitesList()
        val direction = -direction[time].toRadians()
        // find reference scale: median height (or width, or height if height > width?)
        val referenceScale = getReferenceScale(doc, numberOfPages)
        var wasDrawn = false
        val padding = padding[time]
        val cos = cos(direction)
        val sin = sin(direction)
        val normalizer = 1f / max(abs(cos), abs(sin))
        val scale = (1f + padding) * normalizer / referenceScale
        stack.next {
            for (pageRange in pageRanges) {
                for (pageNumber in max(pageRange.first, 0)..min(pageRange.last, numberOfPages - 1)) {
                    val mediaBox = doc.getPage(pageNumber).mediaBox
                    val w = mediaBox.width * scale
                    val h = mediaBox.height * scale
                    if (wasDrawn) {
                        stack.translate(cos * w, sin * h, 0f)
                    }
                    val x = w / h
                    // only query page, if it's visible
                    if (isVisible(stack, x)) {
                        var texture = getTexture(file, doc, quality, pageNumber)
                        if (texture == null) {
                            checkFinalRendering()
                            texture = colorShowTexture
                        }
                        // find out the correct size for the image
                        // find also the correct y offset...
                        if (texture === colorShowTexture) {
                            stack.next {
                                stack.scale(x, 1f, 1f)
                                GFXx3Dv2.draw3DVideo(
                                    this, time, stack, texture, color,
                                    TexFiltering.NEAREST, Clamping.CLAMP, null, UVProjection.Planar
                                )
                            }
                        } else {
                            GFXx3Dv2.draw3DVideo(
                                this, time, stack, texture, color,
                                filtering.value, Clamping.CLAMP, null, UVProjection.Planar
                            )
                        }
                    }
                    wasDrawn = true
                    stack.translate(cos * w, sin * h, 0f)
                }
            }
        }

        ref.returnInstance()

        if (!wasDrawn) {
            super.onDraw(stack, time, color)
        }

    }

    private fun isVisible(matrix: Matrix4f, x: Float): Boolean {
        return Clipping.isPlaneVisible(matrix, x, 1f)
    }

    override fun createInspector(
        inspected: List<Inspectable>,
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        super.createInspector(inspected, list, style, getGroup)
        val c = inspected.filterIsInstance<PDFDocument>()
        val doc = getGroup("Document", "", "docs")
        doc += vi(
            inspected, "Path", "", null, file, style
        ) { it, _ -> for (x in c) x.file = it }
        doc += vi(
            inspected, "Pages", "Comma separated list of page numbers. Ranges like 1-9 are fine, too.",
            null, selectedSites, style
        ) { it, _ -> for (x in c) x.selectedSites = it }
        doc += vis(c, "Padding", "", c.map { it.padding }, style)
        doc += vis(c, "Direction", "Top-Bottom/Left-Right in Degrees", c.map { it.direction }, style)
        doc += vi(
            inspected, "Editor Quality", "Factor for resolution; applied in editor",
            NumberType.FLOAT_PLUS, editorQuality, style
        ) { it, _ -> for (x in c) x.editorQuality = it }
        doc += vi(
            inspected, "Render Quality", "Factor for resolution; applied when rendering",
            NumberType.FLOAT_PLUS, renderQuality, style
        ) { it, _ -> for (x in c) x.renderQuality = it }
        doc += vi(
            inspected, "Filtering", "Pixelated look?", "texture.filtering",
            null, filtering.value, style
        ) { it, _ -> for (x in c) x.filtering.value = it }
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeFile("file", file)
        writer.writeObject(this, "padding", padding)
        writer.writeString("selectedSites", selectedSites)
        writer.writeObject(this, "direction", direction)
        writer.writeFloat("editorQuality", editorQuality)
        writer.writeFloat("renderQuality", renderQuality)
        writer.writeMaybe(this, "filtering", filtering)
    }

    override fun setProperty(name: String, value: Any?) {
        when(name){
            "editorQuality" -> editorQuality = value as? Float ?: return
            "renderQuality" -> renderQuality = value as? Float ?: return
            "filtering" -> filtering.value = filtering.value.find(value as? Int ?: return)
            "file" -> file = (value as? String)?.toGlobalFile() ?: (value as? FileReference) ?: InvalidRef
            "selectedSites" -> selectedSites = value as? String ?: return
            "padding" -> padding.copyFrom(value)
            "direction" -> direction.copyFrom(value)
            else -> super.setProperty(name, value)
        }
    }

    fun getReferenceScale(doc: PDDocument, numberOfPages: Int): Float {
        return (0 until min(10, numberOfPages)).map {
            doc.getPage(it).mediaBox.run {
                height//if (windowWidth > windowHeight) height else width
            }
        }.median(0f)
    }

    override fun getSplitElement(mode: String, index: Int): Element {
        val ref = forcedMeta
        val doc = ref.doc
        val numberOfPages = doc.numberOfPages
        val pages = getSelectedSitesList()
        var remainingIndex = index
        var pageNumber = 0
        for (it in pages) {
            val start = max(it.first, 0)
            val end = min(it.last + 1, numberOfPages)
            val length = max(0, end - start)
            if (remainingIndex < length) {
                pageNumber = start + remainingIndex
                break
            }
            remainingIndex -= length
        }
        val child = clone() as PDFDocument
        child.selectedSites = "$pageNumber"
        val mediaBox = doc.getPage(pageNumber).mediaBox
        val scale = 1f / getReferenceScale(doc, numberOfPages)
        ref.returnInstance()
        val w = mediaBox.width * scale
        val h = mediaBox.height * scale
        return Element(w, h, 0f, child)
    }

    override fun getSplitLength(mode: String): Int {
        val ref = meta!!
        val doc = ref.doc
        val numberOfPages = doc.numberOfPages
        val pages = getSelectedSitesList()
        var sum = 0
        for (it in pages) {
            val start = max(it.first, 0)
            val end = min(it.last + 1, numberOfPages)
            val length = max(0, end - start)
            sum += length
        }
        ref.returnInstance()
        return sum
    }

    companion object {
        init {
            // spams the output with it's drawing calls; using the error stream...
            LogManager.disableLogger("GlyphRenderer")
        }
    }
}