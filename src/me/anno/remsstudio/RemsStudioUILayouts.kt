package me.anno.remsstudio

import me.anno.config.DefaultConfig
import me.anno.extensions.ExtensionLoader
import me.anno.gpu.GFX
import me.anno.input.MouseButton
import me.anno.io.config.ConfigBasics
import me.anno.io.files.InvalidRef
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
import me.anno.remsstudio.ui.StudioFileExplorer
import me.anno.remsstudio.ui.StudioTreeView
import me.anno.remsstudio.ui.StudioTreeView.Companion.openAddMenu
import me.anno.remsstudio.ui.StudioUITypeLibrary
import me.anno.remsstudio.ui.editor.cutting.LayerViewContainer
import me.anno.remsstudio.ui.graphs.GraphEditor
import me.anno.remsstudio.ui.scene.StudioSceneView
import me.anno.remsstudio.ui.sceneTabs.SceneTabs
import me.anno.studio.Projects.getRecentProjects
import me.anno.studio.StudioBase.Companion.instance
import me.anno.ui.Panel
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
import me.anno.ui.editor.PropertyInspector
import me.anno.ui.editor.WelcomeUI
import me.anno.ui.editor.config.ConfigPanel
import me.anno.ui.editor.files.toAllowedFilename
import me.anno.ui.style.Style
import me.anno.ui.utils.WindowStack.Companion.createReloadWindow
import me.anno.utils.files.OpenInBrowser.openInBrowser
import org.apache.logging.log4j.LogManager
import java.net.URL

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
            val panel = ConfigPanel(DefaultConfig, false, style)
            val window = createReloadWindow(panel, transparent = false, fullscreen = true) { createEditorUI(welcomeUI) }
            panel.create()
            windowStack.push(window)
        }

        options.addAction(configTitle, Dict["Style", "ui.top.config.style"]) {
            val panel = ConfigPanel(DefaultConfig.style.values, true, style)
            val window = createReloadWindow(panel, transparent = false, fullscreen = true) { createEditorUI(welcomeUI) }
            panel.create()
            windowStack.push(window)
        }

        options.addAction(configTitle, Dict["Language", "ui.top.config.language"]) {
            Dict.selectLanguage(style).onMouseClicked(windowStack.mouseX, windowStack.mouseY, MouseButton.LEFT, false)
        }

        options.addAction(configTitle, Dict["Open Config Folder", "ui.top.config.openFolder"]) {
            ConfigBasics.configFolder.openInExplorer()
        }


        /**
         * Project options
         * */
        options.addAction(projectTitle, Dict["Change Language", ""]) {
            val panel = ProjectSettings.createSpellcheckingPanel(style)
            openMenuByPanels(windowStack, NameDesc("Change Project Language"), listOf(panel))
        }
        options.addAction(projectTitle, Dict["Save", "ui.top.project.save"]) {
            instance?.save()
            LOGGER.info("Saved the project")
        }
        options.addAction(projectTitle, Dict["Load", "ui.top.project.load"]) {
            val name = NameDesc("Load Project", "", "ui.loadProject")
            val menuStyle = style.getChild("menu")
            val openRecentProject = welcomeUI.createRecentProjectsUI(RemsStudio, menuStyle, getRecentProjects())
            val createNewProject = welcomeUI.createNewProjectUI(RemsStudio, menuStyle)
            openMenuByPanels(windowStack, name, listOf(openRecentProject, createNewProject))
        }

        /**
         * Debugging
         * */
        options.addAction(debugTitle, "Reload Cache (Ctrl+F5)") { RemsStudio.clearAll() }
        options.addAction(debugTitle, "Clear Cache") { ConfigBasics.cacheFolder.deleteRecursively() }
        options.addAction(debugTitle, "Reload Plugins") { ExtensionLoader.reloadPlugins() }
        // todo overview to show plugins & mods
        // todo marketplace for plugins & mods?
        // ...

        // todo shortcuts, which can be set for all actions??...

        /**
         * Rendering
         * */
        val callback: () -> Unit = { GFX.someWindow?.requestAttentionMaybe() }
        options.addAction(renderTitle, Dict["Settings", "ui.top.render.settings"]) { selectTransform(RenderSettings) }
        options.addAction(renderTitle, Dict["Set%", "ui.top.render.topPercent"]) { renderSetPercent(true, callback) }
        options.addAction(renderTitle, Dict["Full", "ui.top.render.full"]) { renderPart(1, true, callback) }
        options.addAction(renderTitle, Dict["Half", "ui.top.render.half"]) { renderPart(2, true, callback) }
        options.addAction(renderTitle, Dict["Quarter", "ui.top.render.quarter"]) { renderPart(4, true, callback) }
        options.addAction(
            renderTitle,
            Dict["Override Audio", "ui.top.render.overrideAudio"]
        ) { overrideAudio(InvalidRef, true, callback) }
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
                // todo ask-for-file-name-menu
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
            URL("https://remsstudio.phychi.com/?s=learn").openInBrowser()
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

        val project = project!!
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

        val animationWindow = CustomList(false, style)
        customUI.add(animationWindow, 2f)

        val library = StudioUITypeLibrary()

        val treeFiles = CustomList(true, style)
        treeFiles += CustomContainer(StudioTreeView(style), library, style)
        treeFiles += CustomContainer(StudioFileExplorer(project?.scenes, style), library, style)
        animationWindow.add(CustomContainer(treeFiles, library, style), 0.5f)
        animationWindow.add(CustomContainer(StudioSceneView(style), library, style), 2f)
        animationWindow.add(
            CustomContainer(
                PropertyInspector({ Selection.selectedInspectables }, style),
                library, style
            ), 0.5f
        )
        animationWindow.weight = 1f

        val timeline = GraphEditor(style)
        customUI.add(CustomContainer(timeline, library, style), 0.25f)

        val linear = LayerViewContainer(style)
        customUI.add(CustomContainer(linear, library, style), 0.25f)

        return customUI

    }

}