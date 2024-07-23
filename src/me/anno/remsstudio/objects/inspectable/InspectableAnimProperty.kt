package me.anno.remsstudio.objects.inspectable

import me.anno.language.translation.NameDesc
import me.anno.remsstudio.animation.AnimatedProperty

data class InspectableAnimProperty(val value: AnimatedProperty<*>, val nameDesc: NameDesc)