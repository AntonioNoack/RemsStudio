package me.anno.engine.ui

import me.anno.ecs.Entity
import me.anno.engine.ECSWorld
import me.anno.ui.editor.treeView.AbstractTreeView
import me.anno.ui.style.Style

// todo runtime and pre-runtime view
// todo unity oriented
// todo easily add stuff
// todo add prefabs
// todo generally: prefabs

// todo easy scripting
// todo support many languages at runtime via scripting
// todo compile all scripting languages for export? <3

// todo add / remove components
// todo reorder them by dragging
// todo just generalize the TreeViewPanel? sounds like a good idea :)


// todo switch between programming language styles easily, throughout the code?... idk whether that's possible...
// maybe on a per-function-basis

class ECSTreeView(val library: ECSTypeLibrary, isGaming: Boolean, style: Style) :
    AbstractTreeView<Entity>(
        listOfVisible(library.world, isGaming),
        {
            // todo open add menu for often created entities: camera, light, nodes, ...
            // we could use which prefabs were most often created :)
            // temporary solution:
            it.add(Entity())
        },
        ECSFileImporter,
        false,
        style
    ) {

    override val selectedElement: Entity? = library.selection as? Entity

    override fun selectElement(element: Entity?) {
        library.selection = element
    }

    override fun focusOnElement(element: Entity) {
        selectElement(element)
        // todo focus on the element by inverting the camera transform and such...
    }

    override val className get() = "ECSTreeView"

    companion object {
        fun listOfVisible(root: ECSWorld, isGaming: Boolean): List<Entity> {
            return if (isGaming) listOf(
                root.globallyShared,
                root.playerPrefab,
                root.locallyShared,
                root.localPlayers,
                root.remotePlayers
            )
            else listOf(root.globallyShared, root.playerPrefab, root.locallyShared)
        }
    }

}