package me.anno.remsstudio

import me.anno.engine.Events.addEvent
import me.anno.installer.Installer
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.RemsStudio.defaultWindowStack
import me.anno.ui.base.menu.Menu
import me.anno.ui.base.menu.MenuOption
import me.anno.utils.OS
import me.anno.utils.files.OpenFileExternally.openInBrowser
import me.anno.utils.files.OpenFileExternally.openInExplorer
import org.apache.logging.log4j.LogManager
import java.io.IOException
import java.net.URL
import kotlin.concurrent.thread

@Suppress("unused")
object CheckVersion {

    private fun formatVersion(version: Int): String {
        val mega = version / 10000
        val major = (version / 100) % 100
        val minor = version % 100
        return "$mega.$major.$minor"
    }

    private val url get() = "https://remsstudio.phychi.com/version.php?isWindows=${if (OS.isWindows) 1 else 0}"

    fun checkVersion() {
        val windowStack = defaultWindowStack
        thread(name = "CheckVersion") {
            val latestVersion = checkVersion(URL(url))
            if (latestVersion > -1) {
                if (latestVersion > RemsStudio.versionNumber) {
                    val name = "RemsStudio ${formatVersion(latestVersion)}.${if (OS.isWindows) "exe" else "jar"}"
                    val dst = OS.documents.getChild(name)
                    if (!dst.exists) {
                        LOGGER.info("Found newer version: $name")
                        // wait for everything to be loaded xD
                        addEvent {
                            Menu.openMenu(windowStack,
                                NameDesc("New Version Available!", "", "ui.newVersion"), listOf(
                                    MenuOption(NameDesc("See Download Options", "", "ui.newVersion.openLink")) {
                                        openInBrowser("https://remsstudio.phychi.com/s=download")
                                    },
                                    MenuOption(NameDesc("Download with Browser", "", "ui.newVersion.openLink")) {
                                        openInBrowser("https://remsstudio.phychi.com/download/$name")
                                    },
                                    MenuOption(NameDesc("Download to ~/Documents", "", "ui.newVersion.download")) {
                                        // download the file
                                        // RemsStudio_v1.00.00.jar ?
                                        Installer.download(name, dst) {
                                            Menu.openMenu(windowStack, listOf(
                                                MenuOption(
                                                    NameDesc("Downloaded file to %1", "", "")
                                                        .with("%1", dst.toString())
                                                ) {
                                                    openInExplorer(dst)
                                                }
                                            ))
                                        }
                                    }
                                )
                            )
                        }
                    } else {
                        LOGGER.warn("Newer version available, but not used! $dst")
                    }
                } else {
                    LOGGER.info(
                        "The newest version is in use: ${RemsStudio.versionName} (Server: ${
                            formatVersion(latestVersion)
                        })"
                    )
                }
            }
        }
    }


    fun checkVersion(url: URL): Int {
        try {
            val reader = url.openStream().bufferedReader()
            while (true) {
                val line = reader.readLine() ?: break
                val index = line.indexOf(':')
                if (index > 0) {
                    val key = line.substring(0, index).trim()
                    val value = line.substring(index + 1).trim()
                    if (key.equals("VersionId", true)) {
                        return value.toIntOrNull() ?: continue
                    }
                }
            }
        } catch (e: IOException) {
            if (url.protocol.equals("https", true)) {
                return checkVersion(URL(url.toString().replace("https://", "http://")))
            } else {
                LOGGER.warn("${e.javaClass.name}: ${e.message ?: ""}")
            }
        }
        return -1
    }

    private val LOGGER = LogManager.getLogger(CheckVersion::class)
}