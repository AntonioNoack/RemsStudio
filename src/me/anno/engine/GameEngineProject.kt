package me.anno.engine

import me.anno.ecs.prefab.PrefabInspector
import me.anno.io.NamedSaveable
import me.anno.io.base.BaseWriter
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.io.text.TextReader
import me.anno.studio.StudioBase
import me.anno.utils.files.LocalFile.toGlobalFile

class GameEngineProject() : NamedSaveable() {

    companion object {
        fun readOrCreate(location: FileReference?): GameEngineProject? {
            location ?: return null
            if (location == InvalidRef) return null
            return if (location.exists) {
                if (location.isDirectory) {
                    val configFile = location.getChild("config.json")
                    if (configFile.exists) {
                        TextReader.read(configFile).filterIsInstance<GameEngineProject>().firstOrNull()
                    } else GameEngineProject(location)
                } else {
                    // probably the config file
                    readOrCreate(location.getParent())
                }
            } else GameEngineProject(location)
        }
    }

    constructor(location: FileReference) : this() {
        this.location = location
        location.mkdirs()
    }

    var location: FileReference = InvalidRef // a folder
    var lastScene: FileReference = InvalidRef

    // todo save the config, if something changes

    fun init() {
        StudioBase.workspace = location
        // if last scene is invalid, create a valid scene
        if (lastScene == InvalidRef) {
            lastScene = location.getChild("Scene.json")
        }
        // create inspector
        PrefabInspector.currentInspector = PrefabInspector(lastScene)
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeFile("lastScene", lastScene)
        // location doesn't really need to be saved
    }

    override fun readString(name: String, value: String) {
        when (name) {
            "lastScene" -> lastScene = value.toGlobalFile()
            else -> super.readString(name, value)
        }
    }

    override val className: String = "GameEngineProject"
    override val approxSize: Int = 1_000_000

}