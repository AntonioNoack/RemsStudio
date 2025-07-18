package me.anno.remsstudio.objects.particles

import me.anno.Time
import me.anno.config.DefaultConfig
import me.anno.engine.inspector.Inspectable
import me.anno.gpu.FinalRendering.isFinalRendering
import me.anno.gpu.FinalRendering.onMissingResource
import me.anno.io.base.BaseWriter
import me.anno.io.files.InvalidRef
import me.anno.io.json.saveable.JsonStringWriter
import me.anno.language.translation.Dict
import me.anno.language.translation.NameDesc
import me.anno.remsstudio.RemsStudio
import me.anno.remsstudio.animation.AnimatedProperty
import me.anno.remsstudio.animation.AnimationIntegral.findIntegralX
import me.anno.remsstudio.animation.AnimationIntegral.getIntegral
import me.anno.remsstudio.objects.Transform
import me.anno.remsstudio.objects.particles.distributions.*
import me.anno.remsstudio.objects.particles.forces.ForceField
import me.anno.remsstudio.objects.particles.forces.impl.BetweenParticleGravity
import me.anno.ui.Style
import me.anno.ui.base.SpyPanel
import me.anno.ui.base.buttons.TextButton
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.menu.Menu.openMenu
import me.anno.ui.base.menu.MenuOption
import me.anno.ui.editor.PropertyInspector.Companion.invalidateUI
import me.anno.ui.editor.SettingCategory
import me.anno.ui.editor.files.FileExplorerEntry.Companion.drawLoadingCircle
import me.anno.ui.editor.stacked.Option
import me.anno.ui.input.BooleanInput
import me.anno.ui.input.NumberType
import me.anno.utils.hpc.HeavyProcessing.processBalanced
import me.anno.utils.structures.Collections.filterIsInstance2
import me.anno.utils.structures.ValueWithDefault
import me.anno.utils.structures.ValueWithDefault.Companion.writeMaybe
import org.joml.Matrix4fArrayList
import org.joml.Vector3f
import org.joml.Vector4f
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Suppress("MemberVisibilityCanBePrivate")
open class ParticleSystem(parent: Transform? = null) : Transform(parent) {

    override fun getDocumentationURL() = "https://remsstudio.phychi.com/?s=learn/particle-systems"

    val spawnColor = AnimatedDistribution(NumberType.COLOR3, listOf(Vector3f(1f), Vector3f(0f), Vector3f(0f)))
    val spawnPosition = AnimatedDistribution(NumberType.POSITION, listOf(Vector3f(0f), Vector3f(1f), Vector3f(0f)))
    val spawnVelocity =
        AnimatedDistribution(GaussianDistribution(), NumberType.POSITION, listOf(Vector3f(), Vector3f(1f), Vector3f()))
    val spawnSize = AnimatedDistribution(NumberType.SCALE, listOf(Vector3f(1f), Vector3f(0f), Vector3f(0f)))
    var spawnSize1D = true

    val spawnOpacity = AnimatedDistribution(NumberType.FLOAT, listOf(1f, 0f))

    val spawnMass = AnimatedDistribution(NumberType.FLOAT, listOf(1f, 0f))

    val spawnRotation = AnimatedDistribution(NumberType.ROT_YXZ, Vector3f())
    val spawnRotationVelocity = AnimatedDistribution(NumberType.ROT_YXZ, Vector3f())

    val spawnRate = AnimatedProperty.floatPlus(10f)
    val lifeTime = AnimatedDistribution(NumberType.FLOAT, 10f)

    var showChildren = false
    var simulationStepI = ValueWithDefault(0.5)
    var simulationStep: Double
        get() = simulationStepI.value
        set(value) {
            simulationStepI.value = value
        }

    val aliveParticles = ArrayList<Particle>(1024)
    val particles = ArrayList<Particle>(1024)

    var seed = 0L
    var random = Random(seed)

    var sumWeight = 0f

    override fun usesFadingDifferently(): Boolean = true
    override fun getStartTime(): Double = Double.NEGATIVE_INFINITY
    override fun getEndTime(): Double = Double.POSITIVE_INFINITY

    val forces get() = children.filterIsInstance2(ForceField::class)

    private fun spawnIfRequired(time: Double, onlyFirst: Boolean) {

        val lastTime = particles.lastOrNull()?.birthTime ?: 0.0 // findReasonableLastTime(time, forces, lifeTime)
        val spawnRate = spawnRate
        val sinceThenIntegral = spawnRate.getIntegral(lastTime, time, false)

        var missingChildren = sinceThenIntegral.toInt()
        if (missingChildren > 0) {
            if (onlyFirst) missingChildren = 1

            val ps = particles.size

            // generate new particles
            val newParticles = ArrayList<Particle>()
            var timeI = lastTime
            for (i in 0 until missingChildren) {
                // more accurate calculation for changing spawn rates
                // - calculate, when the integral since lastTime surpassed 1.0 until we have reached time
                val nextTime = spawnRate.findIntegralX(timeI, time, 1.0, 1e-9)
                val newParticle = createParticle(ps + i, nextTime)
                timeI = nextTime
                newParticles += newParticle ?: continue
            }

            particles += newParticles
            aliveParticles += newParticles

        }

    }

    /**
     * returns whether everything was calculated
     * */
    fun step(time: Double, timeLimit: Double = 1.0 / 120.0): Boolean {
        val startTime = System.nanoTime()

        if (aliveParticles.isNotEmpty()) {
            val simulationStep = simulationStep
            val forces = forces
            val hasHeavyComputeForce = forces.any { it is BetweenParticleGravity }
            var currentTime = aliveParticles.minOfOrNull { it.lastTime(simulationStep) } ?: return true
            while (currentTime < time) {

                // 10 ms timeout
                val deltaTime = abs(System.nanoTime() - startTime)
                if (deltaTime / 1e9 > timeLimit) return false

                currentTime = min(time, currentTime + simulationStep)

                spawnIfRequired(currentTime, false)

                val needsUpdate = aliveParticles.filter { it.lastTime(simulationStep) < currentTime }

                // update all particles, which need an update
                if (hasHeavyComputeForce && needsUpdate.isNotEmpty()) {
                    // just process the first entries...
                    val limit = max(65536 / needsUpdate.size, 10)
                    if (needsUpdate.size > limit) {
                        processBalanced(0, limit, 16) { i0, i1 ->
                            for (i in i0 until i1) needsUpdate[i].step(simulationStep, forces, aliveParticles)
                        }
                        currentTime -= simulationStep // undo the advancing step...
                    } else {
                        processBalanced(0, needsUpdate.size, 16) { i0, i1 ->
                            for (i in i0 until i1) needsUpdate[i].step(simulationStep, forces, aliveParticles)
                        }
                        aliveParticles.removeIf { it.hasDied }
                    }
                } else {
                    // process all
                    processBalanced(0, needsUpdate.size, 256) { i0, i1 ->
                        for (i in i0 until i1) needsUpdate[i].step(simulationStep, forces, aliveParticles)
                    }
                    aliveParticles.removeIf { it.hasDied }
                }
            }

        } else {
            spawnIfRequired(time, true)
            return aliveParticles.isEmpty() || step(time)
        }

        return true
    }

    override fun drawChildrenAutomatically() = !isFinalRendering && showChildren

    open fun createParticle(index: Int, time: Double): Particle? {

        val random = random

        // find the particle type
        var randomIndex = random.nextFloat() * sumWeight
        var type = children.firstOrNull() ?: Transform()
        for (child in children.filterNot { it is ForceField }) {
            val cWeight = child.weight
            randomIndex -= cWeight
            if (randomIndex <= 0f) {
                type = child
                break
            }
        }

        val lifeTime = lifeTime.nextV1(time, random).toDouble()

        // create the particle
        val color3 = spawnColor.nextV3(time, random)
        val opacity = spawnOpacity.nextV1(time, random)
        val color4 = Vector4f(color3, opacity)
        val scale = if (spawnSize1D) Vector3f(spawnSize.nextV1(time, random)) else spawnSize.nextV3(time, random)
        val particle = Particle(type, time, lifeTime, spawnMass.nextV1(time, random), color4, scale, simulationStep)

        // create the initial state
        val state = ParticleState(
            spawnPosition.nextV3(time, random),
            spawnVelocity.nextV3(time, random),
            spawnRotation.nextV3(time, random),
            spawnRotationVelocity.nextV3(time, random)
        )

        // apply the state
        particle.states.add(state)

        return particle

    }

    fun clearCache(state: Any? = getSystemState()) {
        lastState = state
        lastCheckup = Time.nanoTime
        particles.clear()
        aliveParticles.clear()
        random = Random(seed)
        invalidateUI(false)
    }

    var lastState: Any? = null
    var lastCheckup = 0L
    var timeoutMultiplier = 1

    private fun checkNeedsUpdate() {
        val time = Time.nanoTime
        if (abs(time - lastCheckup) > 33_000_000 * timeoutMultiplier) {// 30 fps
            // how fast is this method?
            // would be binary writing and reading faster?
            val state = getSystemState()
            lastCheckup = time
            if (lastState != state) {
                timeoutMultiplier = 1
                lastState = state
                clearCache(state)
            } else {// once every 5s, if not changed
                timeoutMultiplier = min(
                    timeoutMultiplier + 1,
                    30 * 5
                )
            }
        }
    }

    open fun needsChildren() = true

    override fun onDraw(stack: Matrix4fArrayList, time: Double, color: Vector4f) {

        super.onDraw(stack, time, color)

        checkNeedsUpdate()

        // draw all forces
        if (!isFinalRendering) {
            for (force in children) {
                if (force is ForceField) {
                    stack.next {
                        force.draw(stack, time, color)
                    }
                }
            }
            val dist = selectedDistribution
            dist.update(time, Random())
            dist.distribution.onDraw(stack, color)
        }

        sumWeight = children
            .filterNot { it is ForceField }
            .sumOf { it.weight.toDouble() }.toFloat()
        if (needsChildren() && (time < 0f || children.isEmpty() || sumWeight <= 0.0)) return

        if (step(time)) {
            drawParticles(stack, time, color)
        } else {
            if (isFinalRendering) {
                onMissingResource("Computing Particles", null)
                return
            }
            drawLoadingCircle(stack, (Time.nanoTime * 1e-9f) % 1f)
            drawParticles(stack, time, color)
        }

    }

    private fun drawParticles(stack: Matrix4fArrayList, time: Double, color: Vector4f) {
        val fadeIn = fadeIn.value.toDouble()
        val fadeOut = fadeOut.value.toDouble()
        val simulationStep = simulationStep
        for (p in particles) {
            p.draw(stack, time, color, simulationStep, fadeIn, fadeOut)
        }
    }

    var selectedDistribution: AnimatedDistribution = spawnPosition

    override fun createInspector(
        inspected: List<Inspectable>, list: PanelListY, style: Style,
        getGroup: (NameDesc) -> SettingCategory
    ) {

        super.createInspector(inspected, list, style, getGroup)
        val c = inspected.filterIsInstance2(ParticleSystem::class)

        var viCtr = 0
        fun vi(c: List<ParticleSystem>, name: String, description: String, properties: List<AnimatedDistribution>) {
            val property = properties.first()
            fun getName() = "$name: ${property.distribution.className.split("Distribution").first()}"
            val group = getGroup(NameDesc(getName(), description, "$viCtr"))
            group.addChild(SpyPanel(style) {
                if (group.isAnyChildInFocus) {
                    var needsUpdate = false
                    for (i in c.indices) {
                        if (c[i].selectedDistribution !== properties[i]) {
                            c[i].selectedDistribution = properties[i]
                            needsUpdate = true
                        }
                    }
                    if (needsUpdate) invalidateUI(true)
                }
            })
            group.addRightClickListener {
                // show all options for different distributions
                openMenu(
                    list.windowStack,
                    NameDesc("Change Distribution", "", "obj.particles.changeDistribution"),
                    listDistributions().map { generator ->
                        val sample = generator()
                        MenuOption(NameDesc(sample.nameDesc.name, sample.nameDesc.desc, "")) {
                            RemsStudio.largeChange("Change $name Distribution") {
                                for (p in properties) p.distribution = generator()
                            }
                            clearCache()
                            group.content.clear()
                            group.titlePanel.text = getName()
                            property.createInspector(c, properties, group.content, style)
                        }
                    }
                )
            }
            property.createInspector(c, properties, group.content, style)
            viCtr++
        }

        fun vt(name: String, title: String, description: String, obj: List<AnimatedDistribution>) {
            vi(c, Dict[title, "obj.particles.$name"], Dict[description, "obj.particles.$name.desc"], obj)
        }

        vt("lifeTime", "Life Time", "How many seconds a particle is visible", c.map { it.lifeTime })
        vt("initPosition", "Initial Position", "Where the particles spawn", c.map { it.spawnPosition })
        vt("initVelocity", "Initial Velocity", "How fast the particles are, when they are spawned",
            c.map { it.spawnVelocity })
        vt("initRotation", "Initial Rotation", "How the particles are rotated initially", c.map { it.spawnRotation })
        vt("angularVel", "Rotation Velocity", "How fast the particles are rotating", c.map { it.spawnRotationVelocity })

        vt("color", "Color", "Initial particle color", c.map { it.spawnColor })
        vt("opacity", "Opacity", "Initial particle opacity (1-transparency)", c.map { it.spawnOpacity })
        vt("size", "Size", "Initial particle size", c.map { it.spawnSize })

        val general = getGroup(NameDesc("Particle System", "", "obj.particles"))

        general += vis(
            c, "Spawn Rate",
            "How many particles are spawned per second",
            "particles.spawnRate",
            c.map { it.spawnRate },
            style
        )
        general += vi(
            inspected, "Simulation Step",
            "Larger values are faster, while smaller values are more accurate for forces",
            "particles.simulationStep",
            NumberType.DOUBLE, simulationStep, style
        ) { it, _ ->
            if (it > 1e-9) for (x in c) x.simulationStep = it
            clearCache()
        }

        // general += vi("Fade In", "Time from spawning to the max. opacity", fadeIn, style)
        // general += vi("Fade Out", "Time before death, from which is starts to fade away", fadeOut, style)

        general += BooleanInput(NameDesc("Show Children"), showChildren, false, style)
            .setChangeListener { for (x in c) x.showChildren = it }
            .setIsSelectedListener { show(inspected.filterIsInstance2(Transform::class), null) }

        general += vi(
            inspected, "Seed", "The seed for all randomness", "particles.seed",
            null, seed, style
        ) { it, _ ->
            for (x in c) x.seed = it
            clearCache()
        }

        general += TextButton(NameDesc("Reset Cache"), false, style)
            .addLeftClickListener { for (x in c) x.clearCache() }

    }

    override fun getAdditionalChildrenOptions(): List<Option<Transform>> {
        return ForceField.getForceFields()
    }

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeMaybe(this, "simulationStep", simulationStepI)
        // writer.writeMaybe(this, "fadeIn", fadeInI)
        // writer.writeMaybe(this, "fadeOut", fadeOutI)
        writer.writeObject(this, "spawnPosition", spawnPosition)
        writer.writeObject(this, "spawnVelocity", spawnVelocity)
        writer.writeObject(this, "spawnRotation", spawnRotation)
        writer.writeObject(this, "spawnRotationVelocity", spawnRotationVelocity)
        writer.writeObject(this, "spawnRate", spawnRate)
        writer.writeObject(this, "lifeTime", lifeTime)
        writer.writeObject(this, "spawnColor", spawnColor)
        writer.writeObject(this, "spawnOpacity", spawnOpacity)
        writer.writeObject(this, "spawnSize", spawnSize)
    }

    override fun setProperty(name: String, value: Any?) {
        when (name) {
            "spawnPosition" -> spawnPosition.copyFrom(value)
            "spawnVelocity" -> spawnVelocity.copyFrom(value)
            "spawnRotation" -> spawnRotation.copyFrom(value)
            "spawnRotationVelocity" -> spawnRotationVelocity.copyFrom(value)
            "spawnRate" -> spawnRate.copyFrom(value)
            "lifeTime" -> lifeTime.copyFrom(value)
            "spawnColor" -> spawnColor.copyFrom(value)
            "spawnOpacity" -> spawnOpacity.copyFrom(value)
            "spawnSize" -> spawnSize.copyFrom(value)
            "simulationStep" -> simulationStep = max(1e-9, value as? Double ?: return)
            else -> super.setProperty(name, value)
        }
        clearCache()
    }

    open fun getSystemState(): Any? {
        // val t0 = System.nanoTime()
        val writer = JsonStringWriter(InvalidRef)
        var lastChanged = listOf(
            spawnPosition,
            spawnVelocity,
            spawnRotation,
            spawnRotationVelocity,
            lifeTime,
            spawnColor,
            spawnOpacity,
            spawnSize
        ).maxOfOrNull { it.lastChanged }!!
        lastChanged = max(lastChanged, spawnRate.lastChanged)
        val builder = writer.getFoolishWriteAccess()
        builder.append(simulationStepI.value)
        builder.append(lastChanged)
        for (it in children) {
            if (it is ForceField) {
                it.parent = null
                // todo instead of the force field, can we add all its class plus its properties.lastChanged?
                writer.add(it)
            } else {
                builder.append(it.weight)
                builder.append(it.clickId)
            }
        }
        writer.writeAllInList()
        for (it in children) {
            if (it is ForceField) {
                it.parent = this
            }
        }
        /* val t1 = System.nanoTime()
        timeSum += (t1-t0)*1e-6f
        timeCtr++
        LOGGER.info("${timeSum/timeCtr}ms")*/
        // 0.3-0.5 ms -> could be improved
        // -> improved it to ~ 0.056 ms by avoiding a full copy
        // could be improved to 0.045 ms (~20%) by using a binary writer,
        // but it's less well readable -> use the more expensive version;
        // the gain is just too small for the costs
        return builder.toString()
    }

    override fun acceptsWeight() = true
    override val className get() = "ParticleSystem"
    override val defaultDisplayName: String get() = Dict["Particle System", "obj.particles"]
    override val symbol get() = DefaultConfig["ui.symbol.particleSystem", "❄"]

    companion object {
        fun listDistributions(): List<() -> Distribution> {
            return listOf(
                { ConstantDistribution() },
                { GaussianDistribution() },
                { CuboidDistribution() },
                { CuboidHullDistribution() },
                { SphereVolumeDistribution() },
                { SphereHullDistribution() },
            )
        }
    }

}