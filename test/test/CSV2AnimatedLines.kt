package test

import me.anno.animation.Interpolation
import me.anno.io.csv.CSVReader
import me.anno.io.files.InvalidRef
import me.anno.io.json.saveable.JsonStringWriter
import me.anno.remsstudio.RemsRegistry
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.geometric.Circle
import me.anno.remsstudio.objects.geometric.LinePolygon
import me.anno.utils.OS
import org.joml.Vector3f

fun main() {

    // todo the lines are awkward...
    // todo why is it so laggy, when moving something?

    val folder = OS.home.getChild( "OneDrive\\Dokumente\\tsunami_lab")
    // solution_<timeStep>.csv
    val filePrefix = "solution_"
    val fileExtension = "csv"
    val timeScale = 10.0 // = 10 timeSteps per second
    val files = folder.listChildren()
        .filter { it.name.startsWith(filePrefix) && it.extension == fileExtension }
    val data = files.associate {
        val timeStep = it.nameWithoutExtension.substring(filePrefix.length).toDouble() / timeScale
        val values = CSVReader.readNumerical(it.readTextSync(), ',', '\n', 0.0)
        timeStep to values
    }.toSortedMap() // sort by timeStep

    // register all types for writing
    RemsRegistry.init()

    val root = Transform()

    // data: x, y, and then the lines
    val data0 = data[data.firstKey()]!!
    val x0 = data0["x"]!! // must exist
    val y0 = data0["y"]

    val lineStrength = Vector3f(0.05f)

    for (valueKey in data0.keys) {
        if (valueKey != "x" && valueKey != "y") {
            val line = LinePolygon(root)
            line.name = valueKey
            // add all points
            Array(x0.size) { index ->
                val circle = Circle(line)
                val p = circle.position
                val scale = circle.scale
                scale.set(lineStrength)
                p.isAnimated = true
                val x = x0[index].toFloat()
                val z = y0?.getOrNull(index)?.toFloat() ?: 0f
                // animate their position, y = up
                for ((time, values) in data) {
                    val y = values[valueKey]!![index].toFloat()
                    p.addKeyframe(time.toDouble(), Vector3f(x, y, z))
                }
                for (kf in p.keyframes) {
                    kf.interpolation = Interpolation.STEP
                }
                circle
            }
        }
    }

    // export this as a Rem's Studio project file, where we have multiple animated lines
    folder.getChild("lines.json").writeText(JsonStringWriter.toText(root, InvalidRef))

}