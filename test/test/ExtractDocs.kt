package test

import me.anno.remsstudio.objects.transitions.TransitionType

fun main() {
    val values = TransitionType.entries
    println(values.joinToString("\n") {
        "<p><b>${it.nameDesc.name}</b>: ${it.nameDesc.desc}</p>"
    })
}