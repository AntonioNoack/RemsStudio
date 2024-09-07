package me.anno.remsstudio

import me.anno.config.DefaultConfig
import me.anno.engine.RemsEngine.Companion.openConfigWindow
import me.anno.engine.RemsEngine.Companion.openStylingWindow
import me.anno.engine.projects.Projects.getRecentProjects
import me.anno.extensions.ExtensionLoader
import me.anno.gpu.GFX
import me.anno.input.ActionManager
import me.anno.input.Key
import me.anno.io.config.ConfigBasics
import me.anno.language.translation.Dict
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.RemsStudio.defaultWindowStack
import me.anno.remsstudio.RemsStudio.project
import me.anno.remsstudio.RemsStudio.root
import me.anno.remsstudio.RemsStudio.versionName
import me.anno.remsstudio.Rendering.overrideAudio
import me.anno.remsstudio.Rendering.renderAudio
import me.anno.remsstudio.Rendering.renderPart
import me.anno.remsstudio.Rendering.renderSetPercent
import me.anno.remsstudio.Selection.selectTransform
import me.anno.remsstudio.Selection.selectedTransforms
import me.anno.remsstudio.ui.*
import me.anno.remsstudio.ui.StudioTreeView.Companion.openAddMenu
import me.anno.remsstudio.ui.editor.cutting.LayerViewContainer
import me.anno.remsstudio.ui.graphs.GraphEditor
import me.anno.remsstudio.ui.scene.StudioSceneView
import me.anno.remsstudio.ui.sceneTabs.SceneTabs
import me.anno.ui.Panel
import me.anno.ui.Style
import me.anno.ui.base.SpacerPanel
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.menu.Menu.askName
import me.anno.ui.base.menu.Menu.msg
import me.anno.ui.base.menu.Menu.openMenu
import me.anno.ui.base.menu.Menu.openMenuByPanels
import me.anno.ui.base.menu.MenuOption
import me.anno.ui.custom.CustomContainer
import me.anno.ui.custom.CustomList
import me.anno.ui.debug.ConsoleOutputPanel.Companion.createConsoleWithStats
import me.anno.ui.editor.OptionBar
import me.anno.ui.editor.WelcomeUI
import me.anno.ui.editor.files.FileNames.toAllowedFilename
import me.anno.utils.files.OpenFileExternally.openInBrowser
import me.anno.utils.files.OpenFileExternally.openInExplorer
import org.apache.logging.log4j.LogManager

object RemsStudioUILayouts {

    private val windowStack get() = defaultWindowStack

    private val LOGGER = LogManager.getLogger(RemsStudioUILayouts::class)

    fun createEditorUI(welcomeUI: WelcomeUI, loadUI: Boolean = true) {

        val style = DefaultConfig.style
        val ui = PanelListY(style)
        val options = OptionBar(style)

        val configTitle = Dict["Config", "ui.top.config"]
        val projectTitle = Dict["Project", "ui.top.project"]
        val windowTitle = Dict["Window", "ui.top.window"]
        val debugTitle = Dict["Debug", "ui.top.debug"]
        val renderTitle = Dict["Render", "ui.top.render"]
        val toolsTitle = Dict["Tools", "ui.top.tools"]
        val helpTitle = Dict["Help", "ui.top.help"]

        options.addMajor(Dict["Add", "ui.top.add"]) {
            openAddMenu(selectedTransforms.firstOrNull() ?: root)
        }

        // todo complete translation
        options.addAction(configTitle, Dict["Settings", "ui.top.config.settings"]) {
            openConfigWindow(windowStack)
        }
        options.addAction(configTitle, Dict["Style", "ui.top.config.style"]) {
            openStylingWindow(windowStack)
        }
        options.addAction(configTitle, Dict["Keymap", "ui.top.config.keymap"]) {
            openConfigWindow(windowStack, ActionManager, false)
        }

        options.addAction(configTitle, Dict["BPM Snap Settings", "ui.top.config.bpmSnapping"]) {
            selectTransform(BPMSnapping)
        }

        options.addAction(configTitle, Dict["Language", "ui.top.config.language"]) {
            val tmp = Dict.selectLanguage(style) {
                val project = project
                if (project != null) {
                    RemsStudio.openProject(RemsStudio, project.name, project.file)
                }
            }
            tmp.window = windowStack.peek()
            tmp.onMouseClicked(windowStack.mouseX, windowStack.mouseY, Key.BUTTON_LEFT, false)
        }

        options.addAction(configTitle, Dict["Open Config Folder", "ui.top.config.openFolder"]) {
            openInExplorer(ConfigBasics.configFolder)
        }

        /**
         * Project options
         * */
        options.addAction(projectTitle, Dict["Change Language", ""]) {
            val panel = ProjectSettings.createSpellcheckingPanel(style)
            openMenuByPanels(windowStack, NameDesc("Change Project Language"), listOf(panel))
        }
        options.addAction(projectTitle, Dict["Save", "ui.top.project.save"]) {
            RemsStudio.save()
            LOGGER.info("Saved the project")
        }
        options.addAction(projectTitle, Dict["Load", "ui.top.project.load"]) {
            val name = NameDesc("Load Project", "", "ui.loadProject")
            val openRecentProject = welcomeUI.createRecentProjectsUI(RemsStudio, style, getRecentProjects())
            val createNewProject = welcomeUI.createNewProjectUI(RemsStudio, style)
            openMenuByPanels(windowStack, name, listOf(openRecentProject, createNewProject))
        }

        /**
         * Debugging
         * */
        options.addAction(debugTitle, "Reload Cache (Ctrl+F5)") { RemsStudio.clearAll() }
        options.addAction(debugTitle, "Clear Cache") { ConfigBasics.cacheFolder.delete() }
        options.addAction(debugTitle, "Reload Plugins") { ExtensionLoader.reloadPlugins() }

        /**
         * Rendering
         * */
        val callback: () -> Unit = { GFX.someWindow.requestAttentionMaybe() }
        options.addAction(renderTitle, Dict["Settings", "ui.top.render.settings"]) { selectTransform(RenderSettings) }
        options.addAction(renderTitle, Dict["Set%", "ui.top.render.topPercent"]) { renderSetPercent(true, callback) }
        options.addAction(renderTitle, Dict["Full", "ui.top.render.full"]) { renderPart(1, true, callback) }
        options.addAction(renderTitle, Dict["Half", "ui.top.render.half"]) { renderPart(2, true, callback) }
        options.addAction(renderTitle, Dict["Quarter", "ui.top.render.quarter"]) { renderPart(4, true, callback) }
        options.addAction(
            renderTitle,
            Dict["Override Audio", "ui.top.render.overrideAudio"]
        ) { overrideAudio(callback) }
        options.addAction(renderTitle, Dict["Audio Only", "ui.top.audioOnly"]) { renderAudio(true, callback) }

        /**
         * Window (Layout)
         * */
        // options to save/load/restore layout
        // todo option to delete layout?
        options.addAction(windowTitle, Dict["Reset Layout", "ui.top.resetUILayout"]) {
            val project = project
            if (project != null) {
                project.resetUIToDefault()
                createEditorUI(welcomeUI, false)
            }
        }
        options.addAction(windowTitle, Dict["Load Layout", "ui.top.loadUILayout"]) {
            val project = project
            if (project != null) {
                val list = Project.getUIFiles()
                if (list.isNotEmpty()) {
                    openMenu(windowStack, list.map { layoutFile ->
                        var name = layoutFile.name
                        if (name.endsWith(".layout.json"))
                            name = name.substring(0, name.length - ".layout.json".length)
                        MenuOption(NameDesc(name)) {
                            val loadedUI = project.loadUILayout(layoutFile)
                            if (loadedUI != null) {
                                project.mainUI = loadedUI
                                createEditorUI(welcomeUI, false)
                            } else msg(windowStack, NameDesc("File couldn't be loaded"))
                        }
                    })
                } else msg(windowStack, NameDesc("No saved layouts were found"))
            } else msg(windowStack, NameDesc("Project is null?"))
        }
        options.addAction(windowTitle, Dict["Save Layout", "ui.top.saveUILayout"]) {
            askName(windowStack, NameDesc("Layout Name"), "ui", NameDesc("Save"), {
                val trimmed = it.trim()
                if (trimmed.toAllowedFilename() == trimmed) {
                    if (Project.getUILayoutFile(trimmed).exists) {
                        0xffff00
                    } else 0x00ff00
                } else 0xff0000
            }, {
                val name = it.toAllowedFilename()
                if (name != null) {
                    val project = project
                    if (project != null) project.saveUILayout(name)
                    else msg(windowStack, NameDesc("Project is null?"))
                } else msg(windowStack, NameDesc("Filename is invalid"))
            })
        }

        /**
         * Tools
         * */
        options.addAction(toolsTitle, "Downloader") {
            DownloadUI.openUI(style, windowStack)
        }

        /**
         * Help
         * */
        options.addAction(helpTitle, "Tutorials") {
            openInBrowser("https://remsstudio.phychi.com/?s=learn")
        }
        options.addAction(helpTitle, "Version: $versionName") {}
        options.addAction(helpTitle, "About") {
            // to do more info
            msg(
                windowStack,
                NameDesc("Rem's Studio is being developed\nby Antonio Noack\nfrom Jena, Germany", "", "")
            )
            // e.g., the info, why I created it
            // that it is Open Source
        }

        ui += options
        ui += SceneTabs
        ui += SpacerPanel(0, 1, style)

        val project = project ?: throw IllegalStateException("Missing project")
        if (loadUI) project.loadInitialUI()

        ui += project.mainUI
        ui += SpacerPanel(0, 1, style)
        ui += createConsoleWithStats(true, style)

        windowStack.clear()
        windowStack.push(ui)

    }

    fun createDefaultMainUI(style: Style): Panel {

        val customUI = CustomList(true, style)
        customUI.weight = 10f

        val topHalf = CustomList(false, style)
        customUI.add(topHalf, 2f)

        val library = StudioUITypeLibrary()

        val topLeft = CustomList(true, style)
        topLeft.add(CustomContainer(StudioTreeView(style), library, style))
        topLeft.add(CustomContainer(StudioFileExplorer(project?.scenes, style), library, style))
        topHalf.add(CustomContainer(topLeft, library, style), 0.5f)

        topHalf.add(CustomContainer(StudioSceneView(style), library, style), 2f)

        val propertiesAndTime = CustomList(true, style)
        val properties = StudioPropertyInspector({ Selection.selectedInspectables }, style)
        propertiesAndTime.add(CustomContainer(properties, library, style), 0.95f)
        propertiesAndTime.add(CustomContainer(TimeControlsPanel(style), library, style), 0.05f)
        topHalf.add(propertiesAndTime, 0.5f)
        topHalf.weight = 1f

        customUI.add(CustomContainer(GraphEditor(style), library, style), 0.25f)
        customUI.add(CustomContainer(LayerViewContainer(style), library, style), 0.25f)

        return customUI

    }
}