package me.anno.remsstudio.audio.effects.impl

import me.anno.engine.inspector.Inspectable
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.audio.effects.Domain
import me.anno.remsstudio.audio.effects.SoundEffect
import me.anno.remsstudio.audio.effects.Time
import me.anno.remsstudio.objects.video.Video
import me.anno.remsstudio.objects.Camera
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory

// different distance based effects <3 :D
//  -> falloff
// todo velocity-based effects
// todo normalize amplitude effect
// todo limit amplitude effect (straight cut-off; smooth-cut-off)

class AmplitudeEffect : SoundEffect(Domain.TIME_DOMAIN, Domain.TIME_DOMAIN) {

    override fun getStateAsImmutableKey(source: Video, destination: Camera, time0: Time, time1: Time): Any {
        return source.amplitude.toString()
    }

    override fun apply(
        getDataSrc: (Int) -> FloatArray,
        dataDst: FloatArray,
        source: Video,
        destination: Camera,
        time0: Time,
        time1: Time
    ) {
        val src = getDataSrc(0)
        src.copyInto(dataDst)
    }

    override fun createInspector(
        inspected: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) {
    }

    override val displayName get() = "Nothing (Deprecated)"
    override val description get() = "Nothing"
    override val className get() = "AmplitudeEffect"

}