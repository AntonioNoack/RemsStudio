package me.anno.remsstudio.objects

import me.anno.animation.LoopingState
import me.anno.engine.inspector.Inspectable
import me.anno.gpu.GFX.isFinalRendering
import me.anno.gpu.drawing.GFXx2D.getSize
import me.anno.gpu.drawing.GFXx2D.getSizeX
import me.anno.gpu.drawing.GFXx2D.getSizeY
import me.anno.io.MediaMetadata
import me.anno.io.base.BaseWriter
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.language.translation.NameDesc
import me.anno.maths.Maths
import me.anno.maths.Maths.clamp
import me.anno.maths.Maths.fract
import me.anno.maths.Maths.max
import me.anno.maths.Maths.mix
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.audio.AudioFXCache2
import me.anno.remsstudio.audio.AudioFXCache2.getIndex
import me.anno.remsstudio.audio.AudioFXCache2.getTime
import me.anno.remsstudio.audio.effects.Domain
import me.anno.remsstudio.audio.effects.Time
import me.anno.ui.Style
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.editor.SettingCategory
import me.anno.ui.input.NumberType
import me.anno.utils.files.LocalFile.toGlobalFile
import me.anno.utils.structures.Collections.filterIsInstance2
import me.anno.video.MissingFrameException
import org.joml.Matrix4f
import org.joml.Matrix4fArrayList
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.floor
import kotlin.math.sqrt

// todo option for gaussian smoothing
// option for sample rate
// todo option for logarithmic transform
// todo option for min and max index

// todo write a wiki entry about this

@Suppress("MemberVisibilityCanBePrivate")
class FourierTransform : Transform() {

    val meta get() = MediaMetadata.getMeta(file, true)
    val forcedMeta get() = MediaMetadata.getMeta(file, false)

    // effect properties; default is logarithmic x scale
    val rotLin = AnimatedProperty.rotYXZ()
    val rotLog = AnimatedProperty.rotYXZ()
    val scaOff = AnimatedProperty.scale(Vector3f(1f, 0f, 1f))
    val scaLin = AnimatedProperty.scale(Vector3f(0f, 0f, 0f))
    val scaLog = AnimatedProperty.scale(Vector3f(0f, 1f, 0f))
    val posLin = AnimatedProperty.pos()
    val posLog = AnimatedProperty.pos()

    // should be 60 fps, so sampleRate = 60 * bufferSize
    var sampleRate = 48000
    var bufferSize = 512

    var minBufferIndex = -1
    var maxBufferIndex = -1

    // todo implement using half buffers...
    var enableHalfBuffers = true

    // support audio effects stack? no, the user can go the extra step of rendering it,
    // if they need something that specific; if I get many requests for it, I can implement it later
    var file: FileReference = InvalidRef

    // todo we need references
    // todo for that we need to drag transforms into other fields: transformInput
    // they are problematic in regards to reloading the scene... the input will have a different value...
    var getIndexParent: Transform? = null

    fun getIndexAndSize(): Int {

        var parent = parent
        if (parent == null) {
            lastWarning = "Missing parent"
            return -1
        }

        val parent0 = parent
        val targetParent = getIndexParent
        if (targetParent != null && targetParent !== parent) {
            while (parent != null) {
                val nextParent = parent.parent
                if (nextParent == targetParent) {
                    // found it :)
                    return getIndexAndSize(parent, nextParent)
                }
                parent = nextParent
            }
            lastWarning = "Index parent must be in direct line of inheritance"
        }

        return getIndexAndSize(this, parent0)

    }

    fun getIndexAndSize(child: Transform, parent: Transform): Int {
        val index = child.indexInParent
        val size = parent.drawnChildCount
        return getSize(kotlin.math.max(index, 0), size)
    }

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {

        lastWarning = null

        stack.next {

            val indexSize = getIndexAndSize()
            val index = getSizeX(indexSize)
            val size = getSizeY(indexSize)

            if (index < size) {
                // get the two nearest fourier transforms, and interpolate them
                val meta = forcedMeta
                if (meta != null) {
                    val bufferIndex = getIndex(time, bufferSize, sampleRate)
                    val bufferIndex0 = floor(bufferIndex).toLong()
                    val buff0 = getBuffer(meta) { Time(getTime(bufferIndex0 + it - 1, bufferSize, sampleRate)) }
                    val buff1 = getBuffer(meta) { Time(getTime(bufferIndex0 + it, bufferSize, sampleRate)) }
                    if (buff0 != null && buff1 != null) {

                        val bufferSize = buff0.first.size / 4 // half is mirrored, half is real/imaginary

                        val minIndex = clamp(minBufferIndex, 0, bufferSize - 1)
                        val maxIndex0 = kotlin.math.max(minIndex, maxBufferIndex)
                        val maxIndex = if (maxBufferIndex < 0) bufferSize - 1 else maxIndex0
                        val deltaIndex = 1 + maxIndex - minIndex

                        val relativeIndex0 = index.toFloat() / size
                        val relativeIndex1 = (index + 1f) / size

                        var indexInBuffer0 = minIndex + (relativeIndex0 * deltaIndex).toInt()
                        var indexInBuffer1 = minIndex + (relativeIndex1 * deltaIndex).toInt()

                        if (indexInBuffer1 <= indexInBuffer0) indexInBuffer1 = indexInBuffer0 + 1
                        if (indexInBuffer0 >= bufferSize) indexInBuffer0 = bufferSize - 1
                        if (indexInBuffer1 > bufferSize) indexInBuffer1 = bufferSize

                        val fractIndex = fract(bufferIndex).toFloat()
                        val amplitude0 = getAmplitude(indexInBuffer0, indexInBuffer1, buff0)
                        val amplitude1 = getAmplitude(indexInBuffer0, indexInBuffer1, buff1)
                        val amplitude = mix(amplitude0, amplitude1, fractIndex)

                        applyTransform(stack, time, amplitude)

                    } else throw RuntimeException("null, why???")
                } else lastWarning = "Missing audio source"
            }

            super.onDraw(stack, time, color)

            drawChildren(stack, time, color)

            // todo change the transform based on the audio levels
            // todo multiplicative effect for children

            // todo choose left / right / average?
            // (for stereo music this may be really cool)

            // todo change rotation as well?
            // todo and translation :D

        }
    }

    fun applyTransform(stack: Matrix4f, time: Double, amplitude: Float) {

        // first position, then rotation, and then scale
        // skew??? nah... for consistency maybe, but really there shouldn't be a user using it...

        val value = amplitude / 32767f
        val logValue = clampedLogarithm(value, 100f)

        val translation = Vector3f(logValue).mul(posLog[time]).add(Vector3f(posLin[time]).mul(value))
        stack.translate(translation)

        val rotation = Vector3f(logValue).mul(rotLog[time]).add(Vector3f(rotLin[time]).mul(value))
        stack.rotateY(rotation.y)
        stack.rotateX(rotation.x)
        stack.rotateZ(rotation.z)

        val scale = Vector3f(logValue).mul(scaLog[time]).add(Vector3f(scaLin[time]).mul(value)).add(scaOff[time])
        stack.scale(scale)

    }

    // logarithm-like function for values from [0,1] to [0,1]
    fun clampedLogarithm(value: Float, sharpness: Float): Float {
        val vx = value * sharpness
        return Maths.log(vx + 1f) / Maths.log(sharpness + 1f)
    }

    fun getAmplitude(index0: Int, index1: Int, buffer: Pair<FloatArray, FloatArray>): Float {
        return (getAmplitude(index0, index1, buffer.first) + getAmplitude(index0, index1, buffer.second)) * 0.5f
    }

    // interpolate all matching positions
    // todo use a gaussian filter instead (optional)
    fun getAmplitude(index0: Int, index1: Int, buffer: FloatArray): Float {
        var sum = 0f
        if (index1 <= index0) throw IllegalArgumentException()
        for (index in index0 until index1) {
            val real = buffer[index * 2]
            val imag = buffer[index * 2 + 1]
            sum += real * real + imag * imag
        }
        return sqrt(sum / (index1 - index0))
    }

    var loopingState = LoopingState.PLAY_LOOP
    // todo option to change the domain? may be nice, but otherwise, it's really fast...

    fun getKey(sampleIndex0: Long, half: Boolean): AudioFXCache2.PipelineKey {
        val fraction = if (half) 0.5 else 0.0
        return getKey { Time(getTime(sampleIndex0 + it, fraction, bufferSize, sampleRate)) }
    }

    fun getKey(getTime: (Int) -> Time): AudioFXCache2.PipelineKey {
        return AudioFXCache2.PipelineKey(
            file, getTime(0), getTime(1), bufferSize, false, "",
            loopingState, null,
            getTime
        )
    }

    fun getBuffer(
        meta: MediaMetadata,
        getTime: (Int) -> Time
    ): Pair<FloatArray, FloatArray>? {
        val data = AudioFXCache2.getBuffer0(meta, getKey(getTime), false)
        if (isFinalRendering && data == null) throw MissingFrameException(file.absolutePath)
        if (data == null) return null
        return data.getBuffersOfDomain(Domain.FREQUENCY_DOMAIN)
    }

    override fun createInspector(
        inspected: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) {
        super.createInspector(inspected, list, style, getGroup)
        val c = inspected.filterIsInstance2(FourierTransform::class)
        val fourier = getGroup(NameDesc("Fourier Transform", "", "obj.fourier"))
        list.addChild(vi(
            inspected, "Audio File", "Source file, which gets used as an input into the FFT", "fourier.sourceFile",
            null, file, style
        ) { it, _ -> for (x in c) x.file = it })
        fourier.addChild(
            vi(
                inspected, "Sample Rate", "What the highest frequency should be", "fourier.sampleRate",
                // higher frequencies are eliminated, because we interpolate samples (I think...)
                sampleRateType, sampleRate, style
            ) { it, _ -> for (x in c) x.sampleRate = max(64, it) })
        fourier.addChild(
            vi(
                inspected, "Buffer Size",
                "Should be at least twice the buffer size, 'Resolution' of the fourier transform, and length of samples per batch",
                "fourier.bufferSize",
                bufferSizeType, bufferSize, style
            ) { it, _ -> for (x in c) x.bufferSize = max(64, it) })
        fourier.addChild(
            vi(
                inspected, "Buffer Min", "Use only a part of the fourier transform; -1 = disabled",
                "fourier.bufferMin",
                null, minBufferIndex, style
            ) { it, _ -> for (x in c) x.minBufferIndex = it })
        fourier.addChild(
            vi(
                inspected, "Buffer Max", "Use only a part of the fourier transform; -1 = disabled",
                "fourier.bufferMax",
                null, maxBufferIndex, style
            ) { it, _ -> for (x in c) x.maxBufferIndex = it })
        val amplitude = getGroup(NameDesc("Amplitude", "", "obj.amplitude"))
        amplitude.addChild(
            vis(
                c, "Position, Linear", "", "fourier.position.linear",
                c.map { it.posLin }, style
            )
        )
        amplitude.addChild(
            vis(
                c, "Position, Logarithmic", "", "fourier.position.logarithmic",
                c.map { it.posLog }, style
            )
        )
        amplitude.addChild(
            vis(
                c, "Rotation, Linear", "", "fourier.rotation.linear",
                c.map { it.rotLin }, style
            )
        )
        amplitude.addChild(
            vis(
                c, "Rotation, Logarithmic", "", "fourier.rotation.logarithmic",
                c.map { it.rotLog }, style
            )
        )
        amplitude.addChild(
            vis(
                c, "Scale, Offset", "", "fourier.scale.offset",
                c.map { it.scaOff }, style
            )
        )
        amplitude.addChild(
            vis(
                c, "Scale, Linear", "", "fourier.scale.linear",
                c.map { it.scaLin }, style
            )
        )
        amplitude.addChild(
            vis(
                c, "Scale, Logarithmic", "", "fourier.scale.logarithmic",
                c.map { it.scaLog }, style
            )
        )
    }

    override fun drawChildrenAutomatically(): Boolean = false

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeFile("file", file)
        writer.writeInt("sampleRate", sampleRate)
        writer.writeInt("bufferSize", bufferSize)
        writer.writeInt("minBufferIndex", minBufferIndex)
        writer.writeInt("maxBufferIndex", maxBufferIndex)
        writer.writeObject(this, "posLin", posLin)
        writer.writeObject(this, "posLog", posLog)
        writer.writeObject(this, "rotLin", rotLin)
        writer.writeObject(this, "rotLog", rotLog)
        writer.writeObject(this, "scaOff", scaOff)
        writer.writeObject(this, "scaLin", scaLin)
        writer.writeObject(this, "scaLog", scaLog)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "posLin" -> posLin.copyFrom(value)
            "posLog" -> posLog.copyFrom(value)
            "rotLin" -> rotLin.copyFrom(value)
            "rotLog" -> rotLog.copyFrom(value)
            "scaOff" -> scaOff.copyFrom(value)
            "scaLin" -> scaLin.copyFrom(value)
            "scaLog" -> scaLog.copyFrom(value)
            "file" -> file = (value as? String)?.toGlobalFile() ?: (value as? FileReference) ?: InvalidRef
            "sampleRate" -> sampleRate = max(64, value as? Int ?: return)
            "bufferSize" -> bufferSize = max(64, value as? Int ?: return)
            "minBufferIndex" -> minBufferIndex = value as? Int ?: return
            "maxBufferIndex" -> maxBufferIndex = value as? Int ?: return
            else -> super.setProperty(name, value)
        }
    }

    override val className get() = "FourierTransform"
    override val defaultDisplayName get() = "Fourier Transform"

    companion object {
        val sampleRateType =
            NumberType(24000, 1, 1024f, false, true, { clamp(it.toString().toDouble().toInt(), 64, 96000) }, { it })
        val bufferSizeType =
            NumberType(512, 1, 512f, false, true, { clamp(it.toString().toDouble().toInt(), 64, 65536) }, { it })
    }
}