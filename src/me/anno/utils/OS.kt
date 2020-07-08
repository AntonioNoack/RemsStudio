package me.anno.utils

object OS {// the os is important for some things, e.g. the allowed file names, and the home directory
    val data = System.getProperty("os.name")
    val isWindows = data.contains("windows", true)
}