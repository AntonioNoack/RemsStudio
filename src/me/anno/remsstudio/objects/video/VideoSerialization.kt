package me.anno.remsstudio.objects.video

import me.anno.animation.LoopingState
import me.anno.gpu.texture.Clamping
import me.anno.io.base.BaseWriter
import me.anno.io.files.FileReference
import me.anno.io.files.InvalidRef
import me.anno.maths.Maths.clamp
import me.anno.remsstudio.audio.effects.SoundPipeline
import me.anno.remsstudio.video.UVProjection
import me.anno.utils.files.LocalFile.toGlobalFile
import me.anno.utils.structures.ValueWithDefault.Companion.writeMaybe
import me.anno.utils.types.AnyToBool
import me.anno.utils.types.AnyToInt

object VideoSerialization {
    fun Video.copyUnserializableProperties(clone: Video): Video {
        // copy non-serialized properties
        clone.lastFrame = lastFrame
        clone.lastW = lastW
        clone.lastH = lastH
        clone.lastDuration = lastDuration
        clone.lastFile = lastFile
        clone.lastAddedEndKeyframesFile = lastAddedEndKeyframesFile
        clone.type = type
        clone.needsImageUpdate = needsImageUpdate
        return clone
    }

    fun Video.needSuperSetProperty(name: String, value: Any?): Boolean {
        when (name) {
            "stayVisibleAtEnd" -> stayVisibleAtEnd = AnyToBool.anyToBool(value)
            "tiling" -> tiling.copyFrom(value)
            "cgSaturation" -> cgSaturation.copyFrom(value)
            "cgOffset", "cgOffsetAdd" -> cgOffsetAdd.copyFrom(value)
            "cgOffsetSub" -> cgOffsetSub.copyFrom(value)
            "cgSlope" -> cgSlope.copyFrom(value)
            "cgPower" -> cgPower.copyFrom(value)
            "videoScale" -> videoScale.value = AnyToInt.getInt(value, 1)
            "filtering" -> filtering.value = filtering.value.find(AnyToInt.getInt(value, -1))
            "clamping" -> clampMode.value = Clamping.entries.firstOrNull { it.id == value } ?: return false
            "uvProjection" -> uvProjection.value = UVProjection.entries.firstOrNull { it.id == value } ?: return false
            "editorVideoFPS" -> editorVideoFPS.value = clamp(AnyToInt.getInt(value), 1, 1000)
            "cornerRadius" -> cornerRadius.copyFrom(value)
            "isLooping" -> isLooping.value = LoopingState.getState(AnyToInt.getInt(value))
            "amplitude" -> amplitude.copyFrom(value)
            "effects" -> pipeline = value as? SoundPipeline ?: return false
            "src", "file", "path" -> file =
                (value as? String)?.toGlobalFile() ?: (value as? FileReference) ?: InvalidRef
            else -> return true
        }
        return false
    }

    fun Video.save1(writer: BaseWriter) {
        writer.writeFile("file", file)
        writer.writeObject(this, "amplitude", amplitude)
        writer.writeObject(this, "effects", pipeline)
        writer.writeMaybe(this, "isLooping", isLooping)
        writer.writeObject(this, "tiling", tiling)
        writer.writeMaybe(this, "filtering", filtering)
        writer.writeMaybe(this, "clamping", clampMode)
        writer.writeMaybe(this, "uvProjection", uvProjection)
        writer.writeMaybe(this, "videoScale", videoScale)
        writer.writeObject(this, "cgSaturation", cgSaturation)
        writer.writeObject(this, "cgOffsetAdd", cgOffsetAdd)
        writer.writeObject(this, "cgOffsetSub", cgOffsetSub)
        writer.writeObject(this, "cgSlope", cgSlope)
        writer.writeObject(this, "cgPower", cgPower)
        writer.writeMaybe(this, "editorVideoFPS", editorVideoFPS)
        writer.writeBoolean("stayVisibleAtEnd", stayVisibleAtEnd)
        writer.writeObject(this, "cornerRadius", cornerRadius)
    }
}