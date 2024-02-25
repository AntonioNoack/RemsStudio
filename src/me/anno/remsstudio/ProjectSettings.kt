package me.anno.remsstudio

import me.anno.language.Language
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.RemsStudio.project
import me.anno.remsstudio.objects.Transform
import me.anno.ui.Panel
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.text.TextPanel
import me.anno.ui.editor.SettingCategory
import me.anno.ui.input.EnumInput

object ProjectSettings : Transform() {

    override val defaultDisplayName get() = "Project Settings"

    override fun createInspector(
        list: PanelListY,
        style: Style,
        getGroup: (title: String, description: String, dictSubPath: String) -> SettingCategory
    ) {
        list.add(createSpellcheckingPanel(style))
    }

    fun createSpellcheckingPanel(style: Style): Panel {
        val project = project ?: return TextPanel("Project missing :/", style)
        val name = NameDesc("Language", "For Spellchecking", "")
        val values = Language.values()
        return EnumInput(name, project.language.naming, values.map { it.naming }, style)
            .setChangeListener { _, index, _ ->
                project.language = values[index]; save()
            }
    }

    fun save() = RenderSettings.save()

}