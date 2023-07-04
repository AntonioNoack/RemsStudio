package me.anno.remsstudio

import me.anno.Build
import me.anno.engine.EngineActions
import me.anno.gpu.GFX
import me.anno.gpu.GFXState
import me.anno.gpu.debug.DebugGPUStorage
import me.anno.input.ActionManager
import me.anno.input.Input
import me.anno.input.Modifiers
import me.anno.io.files.FileReference.Companion.getReference
import me.anno.io.utils.StringMap
import me.anno.remsstudio.RemsStudio.hoveredPanel
import me.anno.remsstudio.objects.modes.TransformVisibility
import me.anno.remsstudio.ui.editor.TimelinePanel
import me.anno.studio.StudioBase
import me.anno.ui.utils.WindowStack.Companion.printLayout
import kotlin.math.round

object StudioActions {

    fun register() {

        fun setEditorTimeDilation(dilation: Double): Boolean {
            return if (dilation == RemsStudio.editorTimeDilation ||
                GFX.someWindow?.windowStack?.inFocus0?.isKeyInput() == true) false
            else {
                RemsStudio.editorTimeDilation = dilation
                true
            }
        }

        val actions = listOf(
            "Play" to { setEditorTimeDilation(1.0) },
            "Pause" to { setEditorTimeDilation(0.0) },
            "PlaySlow" to { setEditorTimeDilation(0.2) },
            "PlayReversed" to { setEditorTimeDilation(-1.0) },
            "PlayReversedSlow" to { setEditorTimeDilation(-0.2) },
            "ToggleFullscreen" to { GFX.someWindow?.toggleFullscreen(); true },
            "PrintLayout" to { printLayout();true },
            "NextFrame" to {
                RemsStudio.editorTime = (round(RemsStudio.editorTime * RemsStudio.targetFPS) + 1) / RemsStudio.targetFPS
                RemsStudio.updateAudio()
                true
            },
            "PreviousFrame" to {
                RemsStudio.editorTime = (round(RemsStudio.editorTime * RemsStudio.targetFPS) - 1) / RemsStudio.targetFPS
                RemsStudio.updateAudio()
                true
            },
            "NextStep" to {
                TimelinePanel.moveRight(1f)
                true
            },
            "PreviousStep" to {
                TimelinePanel.moveRight(-1f)
                true
            },
            "Jump2Start" to {
                RemsStudio.editorTime = 0.0
                RemsStudio.updateAudio()
                true
            },
            "Jump2End" to {
                RemsStudio.editorTime = RemsStudio.project?.targetDuration ?: 10.0
                RemsStudio.updateAudio()
                true
            },
            "DragEnd" to {
                val dragged = StudioBase.dragged
                if (dragged != null) {

                    val type = dragged.getContentType()
                    val data = dragged.getContent()
                    val window = GFX.someWindow!!

                    when (type) {
                        "File" -> {
                            hoveredPanel?.onPasteFiles(
                                window.mouseX, window.mouseY,
                                data.split("\n").map { getReference(it) }
                            )
                        }
                        else -> {
                            hoveredPanel?.onPaste(window.mouseX, window.mouseY, data, type)
                        }
                    }

                    StudioBase.dragged = null

                    true
                } else false
            },
            "ClearCache" to {
                StudioBase.instance?.clearAll()
                true
            },
            "Redo" to {
                RemsStudio.history?.redo()
                true
            },
            "Undo" to {
                RemsStudio.history?.undo()
                true
            },
            "ShowAllObjects" to {
                if (RemsStudio.root.listOfAll.any { it.visibility == TransformVisibility.VIDEO_ONLY }) {
                    RemsStudio.largeChange("Show all objects") {
                        RemsStudio.root.listOfAll.filter { it.visibility == TransformVisibility.VIDEO_ONLY }
                            .forEach { it.visibility = TransformVisibility.VISIBLE }
                    }
                    true
                } else false
            },
            "ToggleHideObject" to {
                val transforms = Selection.selectedTransforms
                if (transforms.isNotEmpty()) {
                    RemsStudio.largeChange("Toggle Visibility") {
                        val newVis = when (transforms.firstOrNull()?.visibility) {
                            TransformVisibility.VISIBLE -> TransformVisibility.VIDEO_ONLY
                            else -> TransformVisibility.VISIBLE
                        }
                        for (s in transforms) {
                            s.visibility = newVis
                        }
                    }
                    true
                } else false
            },
            "Save" to {
                StudioBase.instance?.save()
                true
            },
            "Paste" to {
                Input.paste(GFX.someWindow!!)
                true
            },
            "Copy" to {
                Input.copy(GFX.someWindow!!)
                true
            },
            "Duplicate" to {
                val window = GFX.someWindow!!
                Input.copy(window)
                Input.paste(window)
                true
            },
            "Cut" to {
                val window = GFX.someWindow!!
                Input.copy(window)
                Input.empty(window)
                true
            },
            "Import" to {
                Input.import()
                true
            },
            "OpenHistory" to {
                StudioBase.instance?.openHistory()
                true
            },
            "SelectAll" to {
                val ws = GFX.someWindow!!.windowStack
                val inFocus0 = ws.inFocus0
                inFocus0?.onSelectAll(ws.mouseX, ws.mouseY)
                true
            },
            "DebugGPUStorage" to {
                DebugGPUStorage.openMenu()
                true
            },
            "ResetOpenGLSession" to {
                StudioBase.addEvent { GFXState.newSession() }
                true
            }
        )

        for ((name, action) in actions) {
            ActionManager.registerGlobalAction(name, action)
        }

        ActionManager.createDefaultKeymap = StudioActions::createKeymap

    }

    fun createKeymap(register: StringMap) {

        EngineActions.createKeymap(register)

        /**
         * types:
         * - typed -> typed
         * - down -> down
         * - while down -> press
         * - up -> up
         * */

        register["global.s.t.c", "Save"]
        register["global.c.t.c", "Copy"]
        register["global.v.t.c", "Paste"]
        register["global.x.t.c", "Cut"]
        register["global.d.t.c", "Duplicate"]
        register["global.i.t.c", "Import"]
        register["global.h.t.c", "OpenHistory"]
        register["global.a.t.c", "SelectAll"]

        if (Build.isDebug) {
            register["global.m.t.c", "DebugGPUStorage"]
            register["global.l.t.c", "ResetOpenGLSession"]
        }

        register["global.space.down.${Modifiers[false, false]}", "Play|Pause"]
        register["global.space.down.${Modifiers[false, true]}", "PlaySlow|Pause"]
        register["global.space.down.${Modifiers[true, false]}", "PlayReversed|Pause"]
        register["global.space.down.${Modifiers[true, true]}", "PlayReversedSlow|Pause"]
        register["global.f11.down", "ToggleFullscreen"]
        register["global.print.down", "PrintLayout"]
        register["global.left.up", "DragEnd"]
        register["global.f5.down.${Modifiers[true, false]}", "ClearCache"]
        register["global.arrowLeft.t", "PreviousStep"]
        register["global.arrowRight.t", "NextStep"]
        register["global.arrowLeft.down.c", "Jump2Start"]
        register["global.arrowRight.down.c", "Jump2End"]
        register["global.comma.t", "PreviousFrame"]
        register["global.dot.t", "NextFrame"]
        register["global.z.t.${Modifiers[true, false]}", "Undo"]
        register["global.z.t.${Modifiers[true, true]}", "Redo"]
        register["global.y.t.${Modifiers[true, false]}", "Undo"]
        register["global.y.t.${Modifiers[true, true]}", "Redo"]
        register["global.h.t.${Modifiers[false, false, true]}", "ShowAllObjects"]
        register["global.h.t", "ToggleHideObject"]

        // press instead of down for the delay
        register["ColorPaletteEntry.left.press", "DragStart"]
        register["SceneTab.left.press", "DragStart"]
        register["FileEntry.left.press", "DragStart"]
        register["FileEntry.left.double", "Enter|Open"]
        register["FileEntry.f2.down", "Rename"]
        register["FileEntry.right.down", "OpenOptions"]
        register["FileExplorerEntry.left.double", "Enter|Open"]
        register["FileExplorerEntry.f2.down", "Rename"]
        register["FileExplorerEntry.right.down", "OpenOptions"]
        register["FileExplorer.right.down", "OpenOptions"]
        register["FileExplorer.mouseBackward.down", "Back"]
        register["FileExplorer.mouseForward.down", "Forward"]
        register["StudioFileExplorer.right.down", "OpenOptions"]
        register["StudioFileExplorer.mouseBackward.down", "Back"]
        register["StudioFileExplorer.mouseForward.down", "Forward"]
        register["FileExplorerEntry.left.double", "Enter"]
        register["TreeViewPanel.left.press", "DragStart"]
        register["TreeViewPanel.f2.down", "Rename"]
        register["StackPanel.left.press", "DragStart"]

        register["HSVBox.left.down", "SelectColor"]
        register["HSVBox.left.press-unsafe", "SelectColor"]
        register["AlphaBar.left.down", "SelectColor"]
        register["AlphaBar.left.press-unsafe", "SelectColor"]
        register["HueBar.left.down", "SelectColor"]
        register["HueBar.left.press-unsafe", "SelectColor"]
        register["HSVBoxMain.left.down", "SelectColor"]
        register["HSVBoxMain.left.press-unsafe", "SelectColor"]

        register["StudioSceneView.right.p", "Turn"]
        register["StudioSceneView.left.p", "MoveObject"]
        register["StudioSceneView.left.p.${Modifiers[false, true]}", "MoveObjectAlternate"]

        for (i in 0 until 10) {
            // keyMap["SceneView.$i.down", "Cam$i"]
            register["StudioSceneView.numpad$i.down", "Cam$i"]
            // keyMap["SceneView.$i.down.${Modifiers[true, false]}", "Cam$i"]
            register["StudioSceneView.numpad$i.down.${Modifiers[true, false]}", "Cam$i"]
        }

        register["StudioSceneView.w.p", "MoveForward"]
        register["StudioSceneView.a.p", "MoveLeft"]
        register["StudioSceneView.s.p", "MoveBackward"]
        register["StudioSceneView.d.p", "MoveRight"]
        register["StudioSceneView.q.p", "MoveDown"]
        register["StudioSceneView.e.p", "MoveUp"]
        register["StudioSceneView.r.p", "SetMode(MOVE)"]
        register["StudioSceneView.t.p", "SetMode(ROTATE)"]
        register["StudioSceneView.z.p", "SetMode(SCALE)"]
        register["StudioSceneView.y.p", "SetMode(SCALE)"]

        register["CorrectingTextInput.backspace.typed", "DeleteBefore"]
        register["PureTextInputML.delete.typed", "DeleteAfter"]
        register["PureTextInputML.backspace.typed", "DeleteBefore"]
        register["PureTextInputML.leftArrow.typed", "MoveLeft"]
        register["PureTextInputML.rightArrow.typed", "MoveRight"]
        register["PureTextInputML.upArrow.typed", "MoveUp"]
        register["PureTextInputML.downArrow.typed", "MoveDown"]
        register["PureTextInput.leftArrow.typed", "MoveLeft"]
        register["PureTextInput.rightArrow.typed", "MoveRight"]
        register["ConsoleInput.upArrow.typed", "MoveUp"]
        register["ConsoleInput.downArrow.typed", "MoveDown"]

        register["PanelListX.leftArrow.typed", "Previous"]
        register["PanelListX.rightArrow.typed", "Next"]
        register["PanelListY.upArrow.typed", "Previous"]
        register["PanelListY.downArrow.typed", "Next"]

        register["FileExplorer.f5.typed", "Refresh"]
        register["StudioTreeView.delete.typed", "Delete"]
        register["GraphEditorBody.s.typed", "StartScale"]

    }

}