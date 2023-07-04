package me.anno.remsstudio.ui

import me.anno.config.DefaultConfig
import me.anno.language.translation.NameDesc
import me.anno.studio.StudioBase.Companion.addEvent
import me.anno.ui.Window
import me.anno.ui.base.buttons.TextButton
import me.anno.ui.base.groups.PanelListX
import me.anno.ui.base.menu.Menu
import me.anno.ui.input.FloatInput
import me.anno.ui.utils.WindowStack
import kotlin.concurrent.thread

object MenuUtils {

    fun askNumber(
        windowStack: WindowStack,
        title: NameDesc,
        value0: Double,
        actionName: NameDesc,
        callback: (Double) -> Unit
    ) {
        askNumber(
            windowStack,
            windowStack.mouseXi - Menu.paddingX,
            windowStack.mouseYi - Menu.paddingY,
            title, value0, actionName, callback
        )
    }

    fun askNumber(
        windowStack: WindowStack,
        x: Int, y: Int,
        title: NameDesc,
        value0: Double,
        actionName: NameDesc,
        callback: (Double) -> Unit
    ): Window {

        val style = DefaultConfig.style.getChild("menu")
        val panel = object : FloatInput(style) {
            // not fired...
            override fun onEnterKey(x: Float, y: Float) {
                callback(value)
                Menu.close(this)
            }
        }
        panel.setValue(value0, false)
        panel.inputPanel.placeholder = title.name
        panel.inputPanel.lineLimit = 1
        panel.inputPanel.setEnterListener {
            callback(panel.value)
            Menu.close(panel)
        }
        panel.setTooltip(title.desc)
        val submit = TextButton(actionName.name, false, style)
            .setTooltip(actionName.desc)
            .addLeftClickListener {
                callback(panel.value)
                Menu.close(panel)
            }

        val cancel = TextButton("Cancel", false, style)
            .addLeftClickListener { Menu.close(panel) }

        val buttons = PanelListX(style)
        buttons += cancel
        buttons += submit

        val window = Menu.openMenuByPanels(windowStack, x, y, title, listOf(panel, buttons))!!
        thread {
            // must be delayed, so the original press is not placed into it
            Thread.sleep(20)
            addEvent {
                panel.inputPanel.requestFocus()
            }
        }
        return window

    }


}