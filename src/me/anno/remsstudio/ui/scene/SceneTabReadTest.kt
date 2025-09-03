package me.anno.remsstudio.ui.scene

import me.anno.engine.EngineBase.Companion.workspace
import me.anno.remsstudio.Project
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.Rendering
import me.anno.remsstudio.ui.sceneTabs.SceneTabs
import me.anno.utils.OS.documents
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun main() {
    RemsStudio.run(false)

    workspace = documents.getChild("RemsStudio")
    val folder = workspace.getChild("Rendering Stuck")

    RemsStudio.loadProject(folder.name, folder) { _, e ->
        e?.printStackTrace()

        val project = Project(folder.name, folder)
        project.loadInitialUI()

        val file = folder.getChild("Scenes/Root.json")
        assertTrue(file.exists)

        val tab = SceneTabs.currentTab!!
        assertEquals(tab.file, file)

        println("Starting Rendering")
        Rendering.renderPart(1, false) {
            println("Done Rendering")
        }
    }

}