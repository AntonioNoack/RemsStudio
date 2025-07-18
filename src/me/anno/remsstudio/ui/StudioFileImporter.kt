package me.anno.remsstudio.ui

import me.anno.cache.ThreadPool
import me.anno.config.DefaultConfig
import me.anno.engine.Events.addEvent
import me.anno.io.files.FileReference
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.RemsStudio.defaultWindowStack
import me.anno.remsstudio.Selection.selectTransform
import me.anno.remsstudio.objects.MeshTransform
import me.anno.remsstudio.objects.SoftLink
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.Transform.Companion.toTransform
import me.anno.remsstudio.objects.documents.PDFDocument
import me.anno.remsstudio.objects.text.Text
import me.anno.remsstudio.objects.video.Video
import me.anno.remsstudio.video.UVProjection
import me.anno.ui.base.menu.Menu.ask
import me.anno.ui.base.menu.Menu.openMenu
import me.anno.ui.base.menu.MenuOption
import me.anno.ui.editor.files.FileContentImporter
import me.anno.utils.types.Strings.getImportTypeByExtension
import org.apache.logging.log4j.LogManager
import org.joml.Vector3f
import kotlin.concurrent.thread

@Suppress("MemberVisibilityCanBePrivate")
object StudioFileImporter : FileContentImporter<Transform>() {

    override fun setName(element: Transform, name: String) {
        element.nameI.value = name
    }

    override fun createNode(parent: Transform?): Transform {
        return Transform(parent)
    }

    override fun import(
        parent: Transform?,
        file: FileReference,
        useSoftLink: SoftLinkMode,
        doSelect: Boolean,
        depth: Int,
        callback: (Transform) -> Unit
    ) {
        val name = file.name
        when (getImportTypeByExtension(file.lcExtension)) {
            "Transform" -> when (useSoftLink) {
                SoftLinkMode.ASK -> openMenu(
                    defaultWindowStack, listOf(
                        MenuOption(NameDesc("Link")) {
                            addChildFromFile(parent, file, SoftLinkMode.CREATE_LINK, doSelect, depth, callback)
                        },
                        MenuOption(NameDesc("Copy")) {
                            addChildFromFile(parent, file, SoftLinkMode.COPY_CONTENT, doSelect, depth, callback)
                        }
                    ))
                SoftLinkMode.CREATE_LINK -> {
                    val transform = SoftLink(file)
                    RemsStudio.largeChange("Added ${transform.name} to ${file.name}") {
                        var name2 = "${file.getParent().getParent().name}/${file.getParent().name}/${file.name}"
                        name2 = name2.replace("/Scenes/Root", "/")
                        name2 = name2.replace("/Scenes/", "/")
                        if (name2.endsWith(".json")) name2 = name2.substring(0, name2.length - 5)
                        if (name2.endsWith("/")) name2 = name2.substring(0, name2.lastIndex)
                        transform.name = name2
                        parent?.addChild(transform)
                        if (doSelect) selectTransform(transform)
                        callback(transform)
                    }
                }
                else -> {
                    ThreadPool.start("ImportFromFile") {
                        val text = file.readTextSync()
                        try {
                            val transform = text.toTransform()
                            if (transform == null) {
                                LOGGER.warn("JSON didn't contain Transform!")
                                addText(name, parent, text, doSelect, callback)
                            } else {
                                addEvent {
                                    RemsStudio.largeChange("Added ${transform.name}") {
                                        parent?.addChild(transform)
                                        if (doSelect) selectTransform(transform)
                                        callback(transform)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            LOGGER.warn("Didn't understand JSON! ${e.message}")
                            addText(name, parent, text, doSelect, callback)
                        }
                    }
                }
            }
            "Cubemap-Equ" -> {
                RemsStudio.largeChange("Added Cubemap") {
                    val cube = Video(file, parent)
                    cube.scale.set(Vector3f(1000f, 1000f, 1000f))
                    cube.uvProjection.value = UVProjection.Equirectangular
                    cube.name = name
                    if (doSelect) selectTransform(cube)
                    callback(cube)
                }
            }
            "Cubemap-Tiles" -> {
                RemsStudio.largeChange("Added Cubemap") {
                    val cube = Video(file, parent)
                    cube.scale.set(Vector3f(1000f, 1000f, 1000f))
                    cube.uvProjection.value = UVProjection.TiledCubemap
                    cube.name = name
                    if (doSelect) selectTransform(cube)
                    callback(cube)
                }
            }
            "Video", "Image", "Audio" -> {// the same, really ;)
                // rather use a list of keywords?
                RemsStudio.largeChange("Added Video") {
                    val video = Video(file, parent)
                    val fName = file.name
                    video.name = fName
                    if (DefaultConfig["import.decideCubemap", true]) {
                        if (fName.contains("360", true)) {
                            video.scale.set(Vector3f(1000f, 1000f, 1000f))
                            video.uvProjection.value = UVProjection.Equirectangular
                        } else if (fName.contains("cubemap", true)) {
                            video.scale.set(Vector3f(1000f, 1000f, 1000f))
                            video.uvProjection.value = UVProjection.TiledCubemap
                        }
                    }
                    if (doSelect) selectTransform(video)
                    callback(video)
                }
            }
            "Text" -> {
                try {
                    addText(name, parent, file.readTextSync(), doSelect, callback)
                } catch (e: Exception) {
                    e.printStackTrace()
                    return
                }
            }
            "Mesh" -> {
                RemsStudio.largeChange("Added Mesh") {
                    val mesh = MeshTransform(file, parent)
                    mesh.name = name
                    if (doSelect) selectTransform(mesh)
                    callback(mesh)
                }
            }
            "PDF" -> {
                RemsStudio.largeChange("Added PDF") {
                    val doc = PDFDocument(file, parent)
                    if (doSelect) selectTransform(doc)
                    callback(doc)
                }
            }
            "HTML" -> {
                // parse HTML? maybe, but HTML and CSS are complicated
                // rather use screenshots or SVG...
                // integrated browser?
                LOGGER.warn("todo html")
            }
            "Markdeep", "Markdown" -> {
                // execute markdeep script or interpret markdown to convert it to HTML? no
                // I see few use-cases
                LOGGER.warn("todo markdeep")
            }
            else -> {
                LOGGER.warn("Unknown file type: ${file.extension}")
                try {
                    addText(name, parent, file.readTextSync(), doSelect, callback)
                } catch (e: Exception) {
                    e.printStackTrace()
                    return
                }
            }
        }
    }

    fun addText(name: String, parent: Transform?, text: String, doSelect: Boolean, callback: (Transform) -> Unit) {
        // important ;)
        // should maybe be done sometimes in object as well ;)
        if (text.length > 500) {
            addEvent {
                ask(
                    defaultWindowStack,
                    NameDesc("Text has %1 characters, import?", "", "obj.text.askLargeImport")
                        .with("%1", text.codePoints().count().toString())
                ) {
                    RemsStudio.largeChange("Imported Text") {
                        val textNode = Text(text, parent)
                        textNode.name = name
                        if (doSelect) selectTransform(textNode)
                        callback(textNode)
                    }
                }
            }
        } else {
            addEvent {
                RemsStudio.largeChange("Imported Text") {
                    val textNode = Text(text, parent)
                    textNode.name = name
                    if (doSelect) selectTransform(textNode)
                    callback(textNode)
                }
            }
        }
    }

    private val LOGGER = LogManager.getLogger(StudioFileImporter::class)
}