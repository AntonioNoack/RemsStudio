package me.anno.ecs.components.physics.constraints

import com.bulletphysics.dynamics.RigidBody
import com.bulletphysics.linearmath.Transform
import me.anno.ecs.Component
import me.anno.ecs.annotations.Type
import me.anno.ecs.components.physics.BulletPhysics.Companion.castB
import me.anno.ecs.components.physics.Rigidbody
import me.anno.ecs.prefab.PrefabSaveable
import me.anno.engine.gui.LineShapes
import me.anno.io.serialization.NotSerializedProperty
import org.joml.Quaterniond
import org.joml.Vector3d

// https://download.autodesk.com/global/docs/maya2014/en_us/index.html?url=files/GUID-CDB3638D-23AF-49EF-8EF6-53081EE4D39D.htm,topicNumber=d30e571077
abstract class Constraint<TypedConstraint : com.bulletphysics.dynamics.constraintsolver.TypedConstraint> : Component() {

    @Type("Rigidbody/PrefabSaveable")
    var other: Rigidbody? = null

    @NotSerializedProperty
    var bulletInstance: TypedConstraint? = null

    var disableCollisionsBetweenLinked = true

    var selfPosition = Vector3d()

    var selfRotation = Quaterniond()

    var otherPosition = Vector3d()

    var otherRotation = Quaterniond()

    fun getTA(): Transform {
        val t = Transform()
        t.origin.set(castB(selfPosition))
        t.basis.set(castB(selfRotation))
        return t
    }

    fun getTB(): Transform {
        val t = Transform()
        t.origin.set(castB(otherPosition))
        t.basis.set(castB(otherRotation))
        return t
    }

    abstract fun createConstraint(a: RigidBody, b: RigidBody, ta: Transform, tb: Transform): TypedConstraint

    override fun onDrawGUI() {
        LineShapes.drawPoint(entity, selfPosition, 1.0)
        LineShapes.drawPoint(other?.entity ?: entity, otherPosition, 1.0)
    }

    override fun copy(clone: PrefabSaveable) {
        super.copy(clone)
        clone as Constraint<*>
        clone.other = getInClone(other, clone)
        clone.selfPosition.set(selfPosition)
        clone.selfRotation.set(selfRotation)
        clone.otherPosition.set(otherPosition)
        clone.otherRotation.set(otherRotation)
        clone.disableCollisionsBetweenLinked = disableCollisionsBetweenLinked
    }

}