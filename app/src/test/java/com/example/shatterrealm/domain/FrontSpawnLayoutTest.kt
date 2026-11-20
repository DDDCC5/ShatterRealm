package com.example.shatterrealm.domain

import com.example.shatterrealm.domain.model.*
import com.example.shatterrealm.domain.usecase.FrontSpawnLayout as Front
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class FrontSpawnLayoutTest {
    @Test fun appearsAtReachableCenterInFrontOfHead() {
        val c=Front.center(V3(1.0,1.7,2.0),Q4.IDENTITY)
        assertEquals(1.0,c.x,1e-9);assertEquals(1.42,c.y,1e-9);assertEquals(1.45,c.z,1e-9)
    }
    @Test fun headYawRotatesPlacementRatherThanPickingASideOrHand() {
        val c=Front.center(V3(0.0,1.7,0.0),Q4(0.0,sin(PI/4),0.0,cos(PI/4)))
        assertEquals(-.55,c.x,1e-9);assertEquals(0.0,c.z,1e-9)
    }
    @Test fun headPitchDoesNotLiftItemOutOfReach() {
        val c=Front.center(V3(0.0,1.7,0.0),Q4(sin(.3),0.0,0.0,cos(.3)))
        assertEquals(1.42,c.y,1e-9);assertEquals(-.55,c.z,1e-9)
    }
    @Test fun offsetModelPivotStillPlacesItsGeometryCenterInFront() {
        val p=Front.pivot(V3(0.0,1.7,0.0),Q4.IDENTITY,V3(.2,.1,-.3))
        assertEquals(-.2,p.x,1e-9);assertEquals(1.32,p.y,1e-9);assertEquals(-.25,p.z,1e-9)
    }
}
