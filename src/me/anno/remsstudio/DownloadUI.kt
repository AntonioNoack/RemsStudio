package me.anno.remsstudio

import me.anno.Engine
import me.anno.config.DefaultConfig
import me.anno.gpu.GFX
import me.anno.gpu.GFXBase
import me.anno.installer.Installer
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.language.translation.NameDesc
import me.anno.studio.StudioBase
import me.anno.ui.Panel
import me.anno.ui.base.buttons.TextButton
import me.anno.ui.base.constraints.AxisAlignment
import me.anno.ui.base.menu.Menu
import me.anno.ui.base.progress.ProgressBarPanel
import me.anno.ui.base.text.TextPanel
import me.anno.ui.debug.TestStudio
import me.anno.ui.input.URLInput
import me.anno.ui.style.Style
import me.anno.ui.utils.WindowStack
import me.anno.utils.OS
import me.anno.utils.files.Files.formatFileSize
import me.anno.utils.process.BetterProcessBuilder
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.logging.log4j.LogManager
import java.io.IOException
import kotlin.concurrent.thread

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

    fun createUI(style: Style): List<Panel> {

        // todo start and end time if possible
        // todo only video/audio if possible
        // todo link ffmpeg for the program, it needs it for some file types like streams

        val ui = ArrayList<Panel>()
        for (p in ensureInstall(style)) ui.add(p)

        val fi = URLInput(
            "Paste your link here", style, InvalidRef,
            emptyList(), false
        )

        fun loadMetadata(file: FileReference) {
            // todo for livestreams on YouTube use --live-from-start
            // todo show media information
            // -o dstFile
            // -j for metadata :3
            if (!executable.exists) return
            // soo much data 😳
            // todo is the data always in the same format, or does it depend on the domain?
            if (false) BetterProcessBuilder("python", 3, false)
                .add(executable.absolutePath)
                .add("-j")
                .add(file.absolutePath)
                .startAndPrint()
        }
        loadMetadata(fi.lastValue)
        fi.setChangeListener { loadMetadata(it) }
        ui.add(fi)
        val button = TextButton("Start Download", false, style)
        // todo why is this not working?
        button.alignmentX = AxisAlignment.FILL
        button.weight = 1f
        button.addLeftClickListener {
            // download
            var srcURL = fi.lastValue
            if (!srcURL.absolutePath.contains("://")) {
                LOGGER.warn("Adding https:// to $srcURL, because it seems to be missing!")
                srcURL = FileReference.getReference("https://${srcURL.absolutePath}")
            }
            // todo choose better file name
            // todo find file extension...? how?
            val dstFolder = RemsStudio.project?.scenes ?: StudioBase.workspace
            val dstFile = dstFolder.getChild("${srcURL.absolutePath.hashCode().toUInt()}.mp4")
            Menu.close(fi)
            LOGGER.info("Downloading $srcURL to $dstFile")
            val builder = BetterProcessBuilder("python", 3, false)
                .add(executable.absolutePath)
                .add("-o").add(dstFile.absolutePath)
                .add("--no-playlist")
                .add(srcURL.absolutePath)
            //     .startAndPrint()
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
        return ui
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