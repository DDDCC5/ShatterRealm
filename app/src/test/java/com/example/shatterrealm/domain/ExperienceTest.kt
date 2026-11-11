package com.example.shatterrealm.domain

import com.example.shatterrealm.domain.model.*
import com.example.shatterrealm.domain.usecase.*
import org.junit.Assert.*
import org.junit.Test

class ExperienceTest {
    @Test fun countsOnlyFirstRealPlaneFractureAndResetsDetails() {
        val session = ExperienceSession()
        assertFalse(session.accepted)
        session.accept()
        assertEquals(0, session.counts.total)
        assertTrue(session.counts.byItem.isEmpty())
        assertFalse(session.fracture(1, "cup", released = false, realPlane = true))
        assertFalse(session.fracture(1, "cup", released = true, realPlane = false))
        assertTrue(session.fracture(1, "cup", released = true, realPlane = true))
        assertFalse(session.fracture(1, "cup", released = true, realPlane = true))
        assertTrue(session.fracture(2, "plate", released = true, realPlane = true))
        assertEquals(listOf("cup", "plate"), session.counts.byItem.keys.toList())
        session.reset()
        assertEquals(0, session.counts.total)
        assertTrue(session.counts.byItem.isEmpty())
        assertTrue(session.accepted)
        assertFalse(ExperienceSession().accepted)
    }
    @Test fun throwPreservesMeasuredVelocityAndIgnoresMissingOrStaleSamples() {
        val tracker = MotionTracker()
        assertNull(tracker.sample(PoseSample(1_000_000_000L, V3(0.0,0.0,0.0), Q4.IDENTITY)))
        val measured = tracker.sample(PoseSample(1_010_000_000L, V3(.03,-.01,.02), Q4.IDENTITY))!!
        assertEquals(3.0, measured.linear.x, 1e-9)
        assertEquals(-1.0, measured.linear.y, 1e-9)
        assertEquals(2.0, measured.linear.z, 1e-9)
        assertNull(tracker.sample(PoseSample(2_000_000_000L,V3(1.0,0.0,0.0),Q4.IDENTITY)))
        tracker.clear()
        assertNull(tracker.sample(PoseSample(2_010_000_000L,V3(2.0,0.0,0.0),Q4.IDENTITY)))
    }
    @Test fun angularVelocityUsesMeasuredRotationAndQuaternionSignContinuity() {
        val tracker=MotionTracker()
        tracker.sample(PoseSample(1_000_000_000L,V3(0.0,0.0,0.0),Q4.IDENTITY))
        val angle=.1
        val rotation=Q4(0.0,0.0,-kotlin.math.sin(angle/2),-kotlin.math.cos(angle/2))
        val motion=tracker.sample(PoseSample(1_020_000_000L,V3(0.0,0.0,0.0),rotation))!!
        assertEquals(5.0,motion.angular.z,1e-8)
        assertEquals(0.0,motion.angular.x,1e-8)
    }
    @Test fun fractureVelocityConservesRigidBodyMotionWithoutExplosionImpulse() {
        val velocity = Motion(V3(2.0,0.0,0.0),V3(0.0,0.0,3.0))
        assertEquals(V3(2.0,3.0,0.0), velocity.atOffset(V3(1.0,0.0,0.0)))
    }
}
