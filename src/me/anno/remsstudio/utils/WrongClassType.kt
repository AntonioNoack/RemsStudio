package me.anno.remsstudio.utils

import me.anno.io.Saveable
import org.apache.logging.log4j.LogManager

object WrongClassType {
    private val LOGGER = LogManager.getLogger(WrongClassType::class)
    fun warn(type: String, value: Saveable?){
        if(value != null) LOGGER.warn("Got $type, that isn't one: ${value.className}")
    }
}