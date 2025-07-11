package me.anno.remsstudio

import me.anno.engine.ECSRegistry
import me.anno.io.saveable.Saveable.Companion.registerCustomClass
import me.anno.io.saveable.SaveableArray
import me.anno.io.utils.StringMap
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.animation.Keyframe
import me.anno.remsstudio.animation.drivers.FunctionDriver
import me.anno.remsstudio.animation.drivers.HarmonicDriver
import me.anno.remsstudio.animation.drivers.PerlinNoiseDriver
import me.anno.remsstudio.animation.drivers.RhythmDriver
import me.anno.remsstudio.audio.effects.SoundPipeline
import me.anno.remsstudio.audio.effects.falloff.ExponentialFalloff
import me.anno.remsstudio.audio.effects.falloff.LinearFalloff
import me.anno.remsstudio.audio.effects.falloff.SquareFalloff
import me.anno.remsstudio.audio.effects.impl.*
import me.anno.remsstudio.history.History
import me.anno.remsstudio.history.HistoryState
import me.anno.remsstudio.objects.*
import me.anno.remsstudio.objects.attractors.EffectColoring
import me.anno.remsstudio.objects.attractors.EffectMorphing
import me.anno.remsstudio.objects.documents.PDFDocument
import me.anno.remsstudio.objects.effects.MaskLayer
import me.anno.remsstudio.objects.particles.forces.impl.*
import me.anno.remsstudio.objects.geometric.Circle
import me.anno.remsstudio.objects.geometric.LinePolygon
import me.anno.remsstudio.objects.geometric.Polygon
import me.anno.remsstudio.objects.particles.ParticleSystem
import me.anno.remsstudio.objects.particles.TextParticles
import me.anno.remsstudio.objects.particles.distributions.*
import me.anno.remsstudio.objects.text.Chapter
import me.anno.remsstudio.objects.text.Text
import me.anno.remsstudio.objects.text.Timer
import me.anno.remsstudio.objects.transitions.Transition
import me.anno.remsstudio.objects.video.Video
import me.anno.remsstudio.ui.scene.SceneTabData

object RemsRegistry {

    fun init() {

        registerCustomClass(StringMap())

        ECSRegistry.init()

        registerCustomClass(SaveableArray())
        registerCustomClass(Transform())
        registerCustomClass(Text())
        registerCustomClass(Circle())
        registerCustomClass(Polygon())
        registerCustomClass(Video())
        registerCustomClass(GFXArray())
        registerCustomClass(MaskLayer())
        registerCustomClass(ParticleSystem())
        registerCustomClass(Camera())
        registerCustomClass(MeshTransform())
        registerCustomClass(Timer())
        registerCustomClass(AnimatedProperty<Any>())
        registerCustomClass(Keyframe<Any>())
        registerCustomClass(HarmonicDriver())
        registerCustomClass(PerlinNoiseDriver())
        registerCustomClass(FunctionDriver())
        registerCustomClass(RhythmDriver())
        registerCustomClass(SceneTabData())
        registerCustomClass(EffectColoring())
        registerCustomClass(EffectMorphing())
        registerCustomClass(SoundPipeline())
        registerCustomClass(EchoEffect())
        registerCustomClass(AmplitudeEffect())
        registerCustomClass(EqualizerEffect())
        registerCustomClass(PitchEffect())
        registerCustomClass(SquareFalloff())
        registerCustomClass(LinearFalloff())
        registerCustomClass(ExponentialFalloff())
        registerCustomClass(NoiseSuppressionEffect())
        registerCustomClass(AnimatedDistribution())
        registerCustomClass(GaussianDistribution())
        registerCustomClass(ConstantDistribution())
        registerCustomClass(CuboidDistribution())
        registerCustomClass(CuboidHullDistribution())
        registerCustomClass(SphereHullDistribution())
        registerCustomClass(SphereVolumeDistribution())
        registerCustomClass(GlobalForce())
        registerCustomClass(GravityField())
        registerCustomClass(LorentzForce())
        registerCustomClass(NoisyLorentzForce())
        registerCustomClass(BetweenParticleGravity())
        registerCustomClass(TornadoField())
        registerCustomClass(VelocityFrictionForce())
        registerCustomClass(History())
        registerCustomClass(HistoryState())
        registerCustomClass(TextParticles())
        registerCustomClass(SoftLink())
        registerCustomClass(PDFDocument())
        registerCustomClass(LinePolygon())
        registerCustomClass(FourierTransform())
        registerCustomClass(Chapter())
        registerCustomClass(Transition())
    }

}