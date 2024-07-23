package me.anno.remsstudio

import me.anno.Build
import me.anno.Time
import me.anno.engine.EngineActions
import me.anno.engine.EngineBase
import me.anno.engine.EngineBase.Companion.dragged
import me.anno.engine.Events.addEvent
import me.anno.gpu.GFX
import me.anno.gpu.GFXState
import me.anno.gpu.debug.DebugGPUStorage
import me.anno.input.ActionManager
import me.anno.input.Input
import me.anno.io.files.Reference.getReference
import me.anno.io.utils.StringMap
import me.anno.language.translation.Dict
import me.anno.remsstudio.RemsStudio.hoveredPanel
import me.anno.remsstudio.objects.modes.TransformVisibility
import me.anno.remsstudio.ui.editor.TimelinePanel
import me.anno.ui.Panel
import me.anno.ui.WindowStack.Companion.printLayout
import me.anno.ui.input.components.TitlePanel
import me.anno.utils.structures.lists.Lists.any2
import org.apache.logging.log4j.LogManager
import kotlin.math.round

@Suppress("MemberVisibilityCanBePrivate")
object StudioActions {

    private val LOGGER = LogManager.getLogger(StudioActions::class)

    fun nextFrame() {
        RemsStudio.editorTime = (round(RemsStudio.editorTime * RemsStudio.targetFPS) + 1) / RemsStudio.targetFPS
        RemsStudio.updateAudio()
    }

    fun previousFrame() {
        RemsStudio.editorTime = (round(RemsStudio.editorTime * RemsStudio.targetFPS) - 1) / RemsStudio.targetFPS
        RemsStudio.updateAudio()
    }

    private fun isInputInFocus(): Boolean {
        return GFX.windows.any2 { w ->
            val inFocus = w.windowStack.inFocus0
            inFocus != null &&
                    inFocus !is TitlePanel &&
                    inFocus.anyInHierarchy { pi -> pi is Panel && pi.isKeyInput() }
        }
    }

    private var lastTimeDilationChange = 0L
    fun setEditorTimeDilation(dilation: Double, allowKeys: Boolean = false): Boolean {
        val currentTime = Time.frameTimeNanos
        if (currentTime == lastTimeDilationChange || (!allowKeys && isInputInFocus())) {
            return false
        }
        return if (dilation == RemsStudio.editorTimeDilation) false
        else {
            LOGGER.info("Set dilation to $dilation")
            lastTimeDilationChange = currentTime
            RemsStudio.editorTimeDilation = dilation
            RemsStudio.updateAudio()
            true
        }
    }

    fun jumpToStart() {
        RemsStudio.editorTime = 0.0
        RemsStudio.updateAudio()
    }

    fun jumpToEnd() {
        RemsStudio.editorTime = RemsStudio.project?.targetDuration ?: 10.0
        RemsStudio.updateAudio()
    }

    fun register() {

        val actions = listOf(
            "Play" to { setEditorTimeDilation(1.0) },
            "Pause" to { setEditorTimeDilation(0.0) },
            "PlaySlow" to { setEditorTimeDilation(0.2) },
            "PlayReversed" to { setEditorTimeDilation(-1.0) },
            "PlayReversedSlow" to { setEditorTimeDilation(-0.2) },
            "ToggleFullscreen" to { GFX.someWindow.toggleFullscreen(); true },
            "PrintLayout" to { printLayout();true },
            "PrintDictDefaults" to { Dict.printDefaults();true },
            "NextFrame" to {
                nextFrame()
                true
            },
            "PreviousFrame" to {
                previousFrame()
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
                jumpToStart()
                true
            },
            "Jump2End" to {
                jumpToEnd()
                true
            },
            "DragEnd" to {
                val dragged = dragged
                if (dragged != null) {

                    val type = dragged.getContentType()
                    val data = dragged.getContent()
                    val window = GFX.someWindow

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

                    EngineBase.dragged = null

                    true
                } else false
            },
            "ClearCache" to {
                RemsStudio.clearAll()
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
                        for (panel in RemsStudio.root.listOfAll) {
                            if (panel.visibility == TransformVisibility.VIDEO_ONLY) {
                                panel.visibility = TransformVisibility.VISIBLE
                            }
                        }
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
                RemsStudio.save()
                true
            },
            "Paste" to {
                Input.paste(GFX.someWindow)
                true
            },
            "Copy" to {
                Input.copy(GFX.someWindow)
                true
            },
            "Duplicate" to {
                val window = GFX.someWindow
                Input.copy(window)
                Input.paste(window)
                true
            },
            "Cut" to {
                val window = GFX.someWindow
                Input.copy(window)
                Input.empty(window)
                true
            },
            "Import" to {
                Input.import()
                true
            },
            "OpenHistory" to {
                RemsStudio.openHistory()
                true
            },
            "SelectAll" to {
                val ws = GFX.someWindow.windowStack
                val inFocus0 = ws.inFocus0
                inFocus0?.onSelectAll(ws.mouseX, ws.mouseY)
                true
            },
            "DebugGPUStorage" to {
                DebugGPUStorage.openMenu()
                true
            },
            "ResetOpenGLSession" to {
                addEvent { GFXState.newSession() }
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

        register["global.space.down", "Play|Pause"]
        register["global.space.down.s", "PlaySlow|Pause"]
        register["global.space.down.c", "PlayReversed|Pause"]
        register["global.space.down.cs", "PlayReversedSlow|Pause"]
        register["global.f11.down", "ToggleFullscreen"]
        register["global.print.down", "PrintLayout"]
        register["global.left.up", "DragEnd"]
        register["global.f5.down.c", "ClearCache"]
        register["global.arrowLeft.t", "PreviousStep"]
        register["global.arrowRight.t", "NextStep"]
        register["global.arrowLeft.down.c", "Jump2Start"]
        register["global.arrowRight.down.c", "Jump2End"]
        register["global.comma.t", "PreviousFrame"]
        register["global.dot.t", "NextFrame"]
        register["global.z.t.c", "Undo"]
        register["global.z.t.cs", "Redo"]
        register["global.y.t.c", "Undo"]
        register["global.y.t.cs", "Redo"]
        register["global.h.t.a", "ShowAllObjects"]
        register["global.h.t", "ToggleHideObject"]

        // press instead of down for the delay
        register["ColorPaletteEntry.left.drag", "DragStart"]
        register["SceneTab.left.drag", "DragStart"]
        register["FileEntry.left.drag", "DragStart"]
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
        register["TreeViewPanel.left.drag", "DragStart"]
        register["TreeViewPanel.f2.down", "Rename"]
        register["StackPanel.left.drag", "DragStart"]

        register["HSVBox.left.down", "SelectColor"]
        register["HSVBox.left.press", "SelectColor"]
        register["AlphaBar.left.down", "SelectColor"]
        register["AlphaBar.left.press", "SelectColor"]
        register["HueBar.left.down", "SelectColor"]
        register["HueBar.left.press", "SelectColor"]
        register["HSVBoxMain.left.down", "SelectColor"]
        register["HSVBoxMain.left.press", "SelectColor"]

        register["StudioSceneView.right.p", "Turn"]
        register["StudioSceneView.left.p", "MoveObject"]
        register["StudioSceneView.left.p.s", "MoveObjectAlternate"]

        for (i in 0 until 10) {
            fun registerForClass(clazz: String) {
                // not everyone has a numpad -> support normal number keys, too
                val action = "Cam$i"
                register["$clazz.$i.down", action]
                register["$clazz.$i.down.c", action]
                register["$clazz.numpad$i.down", action]
                register["$clazz.numpad$i.down.c", action]
            }
            registerForClass("StudioSceneView")
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