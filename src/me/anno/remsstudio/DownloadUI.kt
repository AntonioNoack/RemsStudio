package me.anno.remsstudio

import me.anno.Engine
import me.anno.config.DefaultConfig
import me.anno.gpu.GFX
import me.anno.gpu.GFXBase
import me.anno.installer.Installer
import me.anno.io.Streams.readText
import me.anno.io.files.FileReference
import me.anno.io.files.FileReference.Companion.getReference
import me.anno.io.files.InvalidRef
import me.anno.io.json.JsonReader
import me.anno.language.translation.NameDesc
import me.anno.studio.StudioBase
import me.anno.ui.Panel
import me.anno.ui.base.buttons.TextButton
import me.anno.ui.base.constraints.AxisAlignment
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.menu.Menu
import me.anno.ui.base.progress.ProgressBarPanel
import me.anno.ui.base.text.TextPanel
import me.anno.ui.debug.TestStudio
import me.anno.ui.editor.files.toAllowedFilename
import me.anno.ui.input.EnumInput
import me.anno.ui.input.FileInput
import me.anno.ui.input.URLInput
import me.anno.ui.style.Style
import me.anno.ui.utils.WindowStack
import me.anno.utils.Color.black
import me.anno.utils.Color.withAlpha
import me.anno.utils.OS
import me.anno.utils.files.Files.formatFileSize
import me.anno.utils.process.BetterProcessBuilder
import me.anno.utils.types.Floats.f2
import me.anno.utils.types.Strings.formatTime
import me.anno.video.ffmpeg.FFMPEG.ffmpegPath
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.logging.log4j.LogManager
import java.io.IOException
import kotlin.concurrent.thread
import kotlin.math.round

/**
 * Will be a GUI for yt-dlp, because using music and other videos from YouTube and such can be very helpful.
 * */
object DownloadUI {

    private val LOGGER = LogManager.getLogger(DownloadUI::class)

    private val dstFile = OS.downloads.getChild("lib/yt-dlp")
    private val tmpZip = dstFile.getChild("tmp.zip")
    private var executable = dstFile.getChild("yt_dlp/__main__.py")

    private fun ensureInstall(style: Style): List<Panel> {
        // add install check
        val checkedFile = dstFile.getChild("yt_dlp/__main__.py")
        return if (!checkedFile.exists) {
            val height = TextPanel(style).textSize.toInt() + 8
            val progress = ProgressBarPanel("Installing", "Steps", 2.0, height, style)

            dstFile.tryMkdirs()
            // download zip
            Installer.download("yt-dlp.zip", tmpZip) {
                progress.progress = 1.0
                progress.progressBar.name = "Unpacking"

                // unpack zip
                tmpZip.inputStream { it, exc ->
                    if (it != null) {

                        val zip = ZipArchiveInputStream(it)
                        while (true) {

                            val entry = zip.nextZipEntry ?: break
                            val name = entry.name

                            // check name for being malicious
                            if (".." in name || name.trim() != name ||
                                name.any {
                                    it !in 'A'..'Z' && it !in 'a'..'z' &&
                                            it !in '0'..'9' && it !in "._-/"
                                }
                            ) {
                                zip.close()
                                throw IOException("Illegal file name: $name")
                            }

                            if (entry.size > 1e6) {
                                throw IOException("File suspiciously large: $name, ${entry.size.formatFileSize()}")
                            }

                            val dstChild = dstFile.getChild(name)
                            if (entry.isDirectory) {
                                dstChild.tryMkdirs()
                            } else {
                                dstChild.writeBytes(zip.readBytes())
                            }
                        }
                        zip.close()

                        // delete zip
                        tmpZip.delete()

                        progress.progressBar.finish(true)
                        progress.progressBar.name = "Finished"
                        thread {
                            Thread.sleep(1000)
                            progress.isVisible = false
                        }
                    } else exc?.printStackTrace()
                }
            }
            listOf(progress)
        } else emptyList() // done
    }

    private fun createUI(style: Style): List<Panel> {

        // start and end time if possible -> doesn't seem possible :/
        // done quality settings if possible

        val ui = PanelListY(style)
        for (p in ensureInstall(style)) ui.add(p)

        val srcInput = URLInput(
            "Paste your link here", style, InvalidRef,
            emptyList(), false
        )

        val dstFolder = RemsStudio.project?.scenes ?: StudioBase.workspace
        val dstPanel = FileInput(
            "Destination File", style,
            dstFolder.getChild(System.currentTimeMillis().toString(16)),
            emptyList(), false
        )

        val infoPanel = TextPanel("", style)
        infoPanel.isVisible = false

        /*var thumbnailSource: FileReference = InvalidRef
        val thumbnailPanel = object : ImagePanel(style) {
            override fun getTexture() = ImageGPUCache[thumbnailSource, false]
            override fun calculateSize(w: Int, h: Int) {
                val texture = getTexture()
                if (texture != null) {
                    val size = min(min(w, h), 700) / 3
                    val maxSize = max(texture.w, texture.h)
                    minW = size * texture.w / maxSize
                    minH = size * texture.h / maxSize
                } else {
                    minW = 1
                    minH = 1
                }
                this.w = minW
                this.h = minH
            }
        }
        thumbnailPanel.flipY = true
        thumbnailPanel.stretchMode = ImagePanel.StretchModes.PADDING*/

        val bestFormat = NameDesc("Best", "best", "")
        val discardFormat = NameDesc("Discard", "", "")
        val defaultFormats = listOf(bestFormat, discardFormat)

        val outputFormats = HashMap<String, String?>()

        val audioFormatList = ArrayList<NameDesc>()
        val videoFormatList = ArrayList<NameDesc>()
        videoFormatList.addAll(defaultFormats)
        audioFormatList.addAll(defaultFormats)

        val videoFormatUI = EnumInput(NameDesc("Video Format"), bestFormat, videoFormatList, style)
        val audioFormatUI = EnumInput(NameDesc("Audio Format"), bestFormat, audioFormatList, style)
        val stateUI = TextPanel("Invalid URL", style)
        val tc = stateUI.textColor

        var iter = 0
        fun loadMetadata(file: FileReference) {

            val iteration = ++iter
            infoPanel.isVisible = false

            // todo for livestreams on YouTube use --live-from-start
            // show media information
            // -o dstFile
            // -j for metadata :3

            if (!executable.exists) {
                // todo when download of library is finished, update this
                stateUI.text = "Executable Missing/Downloading"
                stateUI.textColor = 0xffff00 or black
                return
            }

            val path = file.absolutePath
            if (path.isBlank()) {
                stateUI.text = "Invalid URL"
                stateUI.textColor = 0xff0000 or black
                return
            }

            stateUI.text = "Requesting Metadata"
            stateUI.textColor = tc.withAlpha(0.5f)

            val builder = BetterProcessBuilder("python", 3, false)
                .add(executable.absolutePath)
                .add("-j")
                .add(path)

            val process = builder.start()
            thread(name = "cmd(${builder.args}):error") {
                process.errorStream.use {
                    val reader = it.bufferedReader()
                    while (!Engine.shutdown) {
                        val line = reader.readLine() ?: break
                        if (line.isNotEmpty()) {
                            stateUI.text = "Error :/"
                            LOGGER.warn(line)
                        }
                    }
                }
            }

            thread(name = "cmd(${builder.args}):input") {
                process.inputStream.use { input ->
                    val txt = input.readText()
                    if (iter != iteration) return@use


                    if (txt.isEmpty()) {
                        stateUI.text = "Error :/"
                        stateUI.textColor = 0xff0000 or black
                        infoPanel.isVisible = false
                        return@use
                    } else {
                        stateUI.text = "Ready for Download"
                        stateUI.textColor = 0x00ff00 or black
                    }

                    val data = JsonReader(txt).readObject()
                    /*val thumbnail = data["thumbnail"]?.toString()
                    if (thumbnail != null &&
                        (thumbnail.startsWith("https://") || thumbnail.startsWith("http://"))
                    ) thumbnailSource = getReference(thumbnail)*/
                    val dstName = (data["title"] ?: data["id"] ?: data["filesize"])?.toString()?.toAllowedFilename()
                    if (dstName != null) {
                        dstPanel.setValue((dstPanel.value.getParent() ?: dstFolder).getChild(dstName), true)
                    }

                    val formats = data["formats"] as? List<Any?>
                    if (formats != null) {

                        videoFormatList.clear()
                        audioFormatList.clear()
                        videoFormatList.addAll(defaultFormats)
                        audioFormatList.addAll(defaultFormats)

                        if (videoFormatUI.value !in defaultFormats) videoFormatUI.setValue(bestFormat, 0, true)
                        if (audioFormatUI.value !in defaultFormats) audioFormatUI.setValue(bestFormat, 0, true)

                        for (format in formats) {
                            format as? HashMap<*, *> ?: continue
                            val id = format["format_id"]?.toString() ?: continue
                            val w = format["width"]
                            val videoFormat = (format["ext"] ?: format["video_ext"] ?: format["vcodec"]) as? String
                            val audioFormat = (format["audio_ext"] ?: format["acodec"]) as? String
                            if (videoFormat == "mhtml") {
                                LOGGER.info("Ignored mhtml-source, because we can't read it: $format")
                                continue
                            } // we can't read that yet
                            if (w != null) {
                                val fps = format["fps"] as? Double
                                val quality = format["quality"]?.toString()?.toDoubleOrNull()?.toInt()
                                val dynamicRange = format["dynamic_range"]
                                val title = "${format["format"]}, $w x ${format["height"]} " +
                                        (if (fps != null) "at ${if (fps < 10.0) fps.f2() else round(fps).toInt()} Hz, " else "") +
                                        (if (dynamicRange != null) "$dynamicRange, " else "") +
                                        (if (quality != null) "Quality $quality" else "")
                                val desc = NameDesc(title, id, "")
                                videoFormatList.add(desc)
                                if (audioFormat != null && audioFormat != "none") {
                                    audioFormatList.add(desc)
                                }
                                if (videoFormat != null && videoFormat != "none")
                                    outputFormats[id] = videoFormat
                            } else {
                                val title = format["format"]?.toString() ?: "Audio"
                                audioFormatList.add(NameDesc(title, id, ""))
                            }
                        }
                    }

                    val unknown = "?"
                    infoPanel.isVisible = true
                    infoPanel.text = "" +
                            "Title: ${data["title"] ?: unknown}\n" +
                            "ID: ${data["id"] ?: unknown}\n" +
                            "Channel: ${data["channel"] ?: unknown}\n" +
                            "File Size: ${(data["filesize"] as? Double)?.toLong()?.formatFileSize() ?: unknown}\n" +
                            "Resolution: ${data["width"] ?: unknown} x ${data["height"] ?: unknown}\n" +
                            "Frame Rate: ${data["fps"] ?: unknown} Hz\n" +
                            "Duration: ${
                                data["duration"]?.toString()?.toDoubleOrNull()?.formatTime(0) ?: unknown
                            }\n" +
                            "Sample Rate: ${data["asr"] ?: unknown} Hz\n" +
                            "Audio Channels: ${data["audio_channels"] ?: unknown}"
                }
            }
        }
        loadMetadata(srcInput.value)
        srcInput.setChangeListener { loadMetadata(it) }

        ui.add(srcInput)
        ui.add(stateUI)
        ui.add(infoPanel)
        // ui.add(thumbnailPanel)
        ui.add(videoFormatUI)
        ui.add(audioFormatUI)
        ui.add(dstPanel)
        val button = TextButton("Start Download", false, style)
        // todo why is this not working?
        button.alignmentX = AxisAlignment.FILL
        button.weight = 1f
        button.addLeftClickListener {

            if (videoFormatUI.value == discardFormat && audioFormatUI.value == discardFormat) {
                Menu.msg(GFX.someWindow!!.windowStack, NameDesc("Discard everything?"))
                return@addLeftClickListener
            }

            // download
            var srcURL = srcInput.value
            if (!srcURL.absolutePath.contains("://")) {
                LOGGER.warn("Adding https:// to $srcURL, because it seems to be missing!")
                srcURL = getReference("https://${srcURL.absolutePath}")
            }

            var dstFile = dstPanel.value
            if (dstFile.lcExtension.isEmpty()) {
                val ext = if (videoFormatUI.value == discardFormat) "mp3"
                else outputFormats[videoFormatUI.value.desc] ?: "mp4"
                dstFile = dstFile.getSibling("${dstFile.name}.$ext")
            }

            // extension will get added by tool automatically
            Menu.close(srcInput)
            LOGGER.info("Downloading $srcURL to $dstFile")
            val builder = BetterProcessBuilder("python", 5, false)
                .add(executable.absolutePath)
                // define output path
                .add("-o").add(dstFile.absolutePath)
                // don't download whole playlists
                .add("--no-playlist")
                .addIf("--extract-audio", videoFormatUI.value == discardFormat)
                .addAllIf(
                    listOf(
                        "-f", if (videoFormatUI.value == audioFormatUI.value ||
                            audioFormatUI.value == discardFormat
                        ) {
                            videoFormatUI.value.desc
                        } else if (videoFormatUI.value == discardFormat) {
                            audioFormatUI.value.desc
                        } else if (audioFormatUI.value == bestFormat) {
                            "${videoFormatUI.value.desc}+bestaudio"
                        } else if (videoFormatUI.value == bestFormat) {
                            "bestvideo+${audioFormatUI.value.desc}"
                        } else {
                            "${videoFormatUI.value.desc}+${audioFormatUI.value.desc}"
                        },
                        // choose output format by chosen video format
                        "--merge-output-format", outputFormats[videoFormatUI.value.desc] ?: "mp4"
                    ), videoFormatUI.value !in defaultFormats || audioFormatUI.value !in defaultFormats
                )
                // link ffmpeg for the program, it needs it for some file types like streams
                .add("--ffmpeg-location").add(ffmpegPath.absolutePath)
                // define input url
                .add(srcURL.absolutePath)
            val process = builder.start()
            val progress = GFX.someWindow!!.addProgressBar("Download", "%", 100.0)
            thread(name = "cmd(${builder.args}):error") {
                val reader = process.errorStream.bufferedReader()
                while (!Engine.shutdown) {
                    val line = reader.readLine() ?: break
                    if (line.isNotEmpty()) {
                        if ("ERROR" in line) progress.cancel(true)
                        LOGGER.warn(line)
                    }
                }
                reader.close()
            }
            thread(name = "cmd(${builder.args}):input") {
                // while downloading library, show progress bar
                // [download]  87.1% of  228.51MiB at    5.75MiB/s ETA 00:05
                // todo it would be nice if we could show the actual data size
                val reader = process.inputStream.bufferedReader()
                while (!Engine.shutdown) {
                    val line = reader.readLine() ?: break
                    if (line.startsWith("[download]")) {
                        val i1 = line.indexOf('%')
                        if (i1 > 0) {
                            val i0 = line.lastIndexOf(' ', i1 - 1)
                            val percentage = line.substring(i0 + 1, i1).toDoubleOrNull()
                            if (percentage != null) progress.progress = percentage
                        }
                    }
                    if (line.isNotEmpty()) {
                        LOGGER.info(line)
                    }
                }
                progress.finish(true)
                reader.close()
            }
        }
        ui.add(button)
        return listOf(ui)
    }

    fun openUI(style: Style, windowStack: WindowStack) {
        Menu.openMenuByPanels(windowStack, NameDesc("Download Media (yt-dlp)"), createUI(style))
    }

    /**
     * Test for this UI
     * */
    @JvmStatic
    fun main(args: Array<String>) {
        GFXBase.disableRenderDoc()
        TestStudio.testUI2 { createUI(DefaultConfig.style) }
    }

}