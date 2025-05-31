package me.anno.remsstudio

import me.anno.config.VersionFeatures
import me.anno.io.utils.StringMap
import me.anno.remsstudio.objects.FourierTransform
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.documents.PDFDocument
import me.anno.remsstudio.objects.geometric.LinePolygon
import me.anno.remsstudio.objects.text.Chapter
import me.anno.remsstudio.objects.transitions.Transition

class RemsVersionFeatures(oldVersion: Int) : VersionFeatures(oldVersion) {

    constructor(config: StringMap) : this(config["version", -1])

    private fun addInstance(config: StringMap, name: String, value: Transform) {
        val list = config["createNewInstancesList"] as? StringMap ?: return
        list[name, value]
    }

    /**
     * When new stuff is added, it can be forced upon the user
     * */
    override fun addNewPackages(config: StringMap) {

        addVersion(10002) {
            addInstance(config, "PDF Document", PDFDocument())
        }

        addVersion(10102) {
            addInstance(config, "Line", LinePolygon())
        }

        addVersion(10104) {
            addInstance(config, "Fourier Transform", FourierTransform())
        }

        addVersion(10106) {
            addInstance(config, "Chapter", Chapter())
        }

        addVersion(10400) {
            addInstance(config, "Transition", Transition())
        }

        config["version"] = RemsStudio.versionNumber

    }

}