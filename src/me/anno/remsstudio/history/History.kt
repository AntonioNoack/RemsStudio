package me.anno.remsstudio.history

import me.anno.io.Saveable
import me.anno.io.base.BaseWriter
import me.anno.language.translation.Dict
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.RemsStudio.defaultWindowStack
import me.anno.remsstudio.history.HistoryState.Companion.capture
import me.anno.ui.base.menu.Menu.openMenu
import me.anno.ui.base.menu.MenuOption
import org.apache.logging.log4j.LogManager
import kotlin.math.max

class History : Saveable() {

    var currentState: HistoryState? = null
    var nextInsertIndex = 0
        set(value) {
            field = max(value, 0)
        }

    private val states = ArrayList<HistoryState>()

    fun isEmpty() = states.isEmpty()

    fun clearToSize() {
        assert(maxChanged > 0)
        synchronized(states) {
            while (states.size > maxChanged) {
                states.removeAt(0)
            }
        }
    }

    fun update(title: String, code: Any) {
        val last = states.lastOrNull()
        if (last?.code == code) {
            last.capture(last)
            last.title = title
        } else {
            put(title, code)
        }
    }

    fun put(change: HistoryState): Int {
        synchronized(states) {
            // remove states at the top of the stack...
            // while (states.size > nextInsertIndex) states.removeAt(states.lastIndex)
            states += change
            clearToSize()
            nextInsertIndex = states.size
            return nextInsertIndex
        }
    }

    fun put(title: String, code: Any) {
        val nextState = capture(title, code, currentState)
        if (nextState != currentState) {
            put(nextState)
            currentState = nextState
        }
    }

    fun redo() {
        synchronized(states) {
            if (nextInsertIndex < states.size) {
                states[nextInsertIndex].apply()
                nextInsertIndex++
            } else LOGGER.info("Nothing left to redo!")
        }
    }

    fun undo() {
        synchronized(states) {
            if (nextInsertIndex > 1) {
                nextInsertIndex--
                states[nextInsertIndex - 1].apply()
            } else LOGGER.info("Nothing left to undo!")
        }
    }

    private fun redo(index: Int) {
        states.getOrNull(index)?.apply {
            // put(this)
            apply()
        }
    }

    fun display() {
        openMenu(
            defaultWindowStack,
            NameDesc("Inspect History", "", "ui.inspectHistory"),
            states.mapIndexed { index, change ->
                val title = if (index == nextInsertIndex - 1) "* ${change.title}" else change.title
                MenuOption(NameDesc(title, Dict["Click to redo", "ui.history.clickToUndo"], "")) {
                    redo(index)
                }
            }.reversed()
        )
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "nextInsertIndex" -> nextInsertIndex = value as? Int ?: return
            "state" -> states += value as? HistoryState ?: return
            "states" -> {
                val values = value as? Array<*> ?: return
                synchronized(states) {
                    states += values.filterIsInstance<HistoryState>()
                }
            }
            else -> super.setProperty(name, value)
        }
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeInt("nextInsertIndex", nextInsertIndex)
        synchronized(states) {
            writer.writeObjectList(this, "states", states)
        }
    }

    override val approxSize get() = 1_500_000_000
    override fun isDefaultValue(): Boolean = false
    override val className get() = "History"

    companion object {
        private val LOGGER = LogManager.getLogger(History::class)
        val maxChanged = 512
    }

}