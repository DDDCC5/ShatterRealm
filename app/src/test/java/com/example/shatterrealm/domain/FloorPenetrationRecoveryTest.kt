package com.example.shatterrealm.domain

import com.example.shatterrealm.domain.model.V3
import com.example.shatterrealm.domain.usecase.FloorPenetrationRecovery
import org.junit.Assert.*
import org.junit.Test

class FloorPenetrationRecoveryTest {
    private val up=V3(0.0,1.0,0.0)
    @Test fun inAirAndRestingContactsAreUntouched() {
        for(y in listOf(2.0,.1,.0,-.009)) assertNull(FloorPenetrationRecovery.contactPosition(V3(0.0,y,0.0),V3(0.0,0.0,0.0),up,-.01))
    }
    @Test fun belowFloorPivotWithSupportedGeometryIsNotAPenetration() {
        assertNull(FloorPenetrationRecovery.contactPosition(V3(0.0,-.1,0.0),V3(0.0,0.0,0.0),up,.11))
        assertNotNull(FloorPenetrationRecovery.contactPosition(V3(0.0,.03,0.0),V3(0.0,0.0,0.0),up,-.08))
    }
    @Test fun contactUsesMeasuredElevationAndShardSupport() {
        val c=FloorPenetrationRecovery.contactPosition(V3(2.0,-3.0,4.0),V3(0.0,1.3,0.0),up,-.01)!!
        assertEquals(1.311,c.y,1e-9)
        assertEquals(2.0,c.x,1e-9);assertEquals(4.0,c.z,1e-9)
    }
    @Test fun slopedGroundProjectsAlongItsNormal() {
        val n=V3(.6,.8,0.0)
        val c=FloorPenetrationRecovery.contactPosition(V3(-1.0,-1.0,2.0),V3(0.0,0.0,0.0),n,-.02)!!
        assertEquals(.021,c.x*n.x+c.y*n.y,1e-9)
        assertEquals(2.0,c.z,1e-9)
    }
    @Test fun negativeMeasuredElevationsDoNotBecomeAnArbitraryZeroFloor() {
        assertEquals(-1.189,FloorPenetrationRecovery.contactPosition(V3(0.0,-2.0,0.0),V3(0.0,-1.2,0.0),up,-.01)!!.y,1e-9)
    }
}
