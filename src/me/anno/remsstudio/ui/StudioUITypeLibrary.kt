package me.anno.remsstudio.ui

import me.anno.config.DefaultConfig
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.Selection
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.ui.editor.TimelinePanel
import me.anno.remsstudio.ui.editor.cutting.LayerViewContainer
import me.anno.remsstudio.ui.graphs.GraphEditor
import me.anno.remsstudio.ui.scene.StudioSceneView
import me.anno.ui.Panel
import me.anno.ui.base.menu.Menu.askName
import me.anno.ui.custom.CustomPanelType
import me.anno.ui.custom.UITypeLibrary
import me.anno.ui.editor.files.FileExplorer.Companion.invalidateFileExplorers
import me.anno.ui.editor.files.FileExplorerOption
import me.anno.ui.editor.files.FileNames.toAllowedFilename

class StudioUITypeLibrary : UITypeLibrary(typeList) {
    companion object {
        val createTransform = FileExplorerOption(
            NameDesc("Create Component", "Create a new folder component", "ui.newComponent")
        ) { p, folders ->
            val folder = folders.firstOrNull()
            if (folder != null) askName(
                p.windowStack,
                NameDesc("Name", "", "ui.newComponent.askName"),
                "",
                NameDesc("Create"),
                { -1 }) {
                val validName = it.toAllowedFilename()
                if (validName != null) {
                    folder.getChild("${validName}.json").writeText(
                        Transform()
                            .apply { name = it }
                            .toString())
                    invalidateFileExplorers(p)
                }
            }
        }

        val typeList = listOf<Pair<NameDesc, () -> Panel>>(
            NameDesc("Scene", "", "ui.customize.sceneView") to
                    { StudioSceneView(DefaultConfig.style) },
            NameDesc("Tree", "", "ui.customize.treeView") to
                    { StudioTreeView(DefaultConfig.style) },
            NameDesc("Properties", "", "ui.customize.inspector") to
                    { StudioPropertyInspector({ Selection.selectedInspectables }, DefaultConfig.style) },
            NameDesc("Cutting", "", "ui.customize.cuttingPanel") to
                    { LayerViewContainer(DefaultConfig.style) },
            NameDesc("Timeline", "", "ui.customize.timeline") to
                    { TimelinePanel(DefaultConfig.style) },
            NameDesc("Keyframe Editor", "", "ui.customize.graphEditor") to
                    { GraphEditor(DefaultConfig.style) },
            NameDesc("Files", "", "ui.customize.fileExplorer") to
                    { StudioFileExplorer(RemsStudio.project?.scenes, DefaultConfig.style) },
            NameDesc("Time Control", "", "ui.customize.timeControl") to
                    { TimeControlsPanel(DefaultConfig.style) }
        ).map { CustomPanelType(it.first, it.second) }.toMutableList()
    }
}