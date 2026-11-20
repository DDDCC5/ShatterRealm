package com.example.shatterrealm.domain

import com.example.shatterrealm.domain.model.V3
import com.example.shatterrealm.domain.usecase.NearGrabPolicy as Grab
import org.junit.Assert.*
import org.junit.Test

class NearGrabPolicyTest {
    @Test fun moderatePinchAcquiresAndSmallTrackingNoiseDoesNotDrop() {
        assertTrue(Grab.pinched(true,.75f,false))
        assertTrue(Grab.pinched(true,.4f,true))
        assertFalse(Grab.pinched(true,.4f,false))
        assertFalse(Grab.pinched(true,.2f,true))
    }
    @Test fun invalidPinchCannotAcquire() {
        assertFalse(Grab.pinched(false,1f,false))
        assertFalse(Grab.pinched(true,Float.NaN,false))
    }
    @Test fun largeItemCanBePickedUpAtItsEdgeRatherThanOnlyItsOrigin() {
        val d=Grab.distanceSquared(V3(.29,0.0,0.0),V3(-.25,-.1,-.1),V3(.25,.1,.1))
        assertTrue(Grab.inReach(d))
        assertEquals(.04*.04,d,1e-9)
        assertFalse(Grab.inReach(Grab.distanceSquared(V3(.5,0.0,0.0),V3(-.25,-.1,-.1),V3(.25,.1,.1))))
    }
    @Test fun offsetPivotsAndDistantHandsDoNotExpandPickupToInfinity() {
        assertTrue(Grab.inReach(Grab.distanceSquared(V3(1.05,0.0,0.0),V3(.9,-.1,-.1),V3(1.0,.1,.1))))
        assertFalse(Grab.inReach(Grab.distanceSquared(V3(0.0,0.0,0.0),V3(.9,-.1,-.1),V3(1.0,.1,.1))))
        assertFalse(Grab.inReach(Double.NaN))
    }
}
