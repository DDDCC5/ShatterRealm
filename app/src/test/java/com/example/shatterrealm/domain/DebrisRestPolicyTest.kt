package com.example.shatterrealm.domain

import com.example.shatterrealm.domain.usecase.DebrisRestPolicy
import org.junit.Assert.*
import org.junit.Test

class DebrisRestPolicyTest {
    @Test fun freeFlightCannotSleepEvenAtZeroVelocity() {
        val p=DebrisRestPolicy()
        assertFalse(p.shouldSleep(0f,0f,0));assertFalse(p.shouldSleep(0f,0f,1000))
    }
    @Test fun contactMustRemainQuietForHalfSecond() {
        val p=DebrisRestPolicy();p.contact()
        assertFalse(p.shouldSleep(.001f,.1f,100));assertFalse(p.shouldSleep(0f,0f,599))
        assertTrue(p.shouldSleep(0f,0f,600))
    }
    @Test fun SlidingOrSpinningRestartsQuietWindow() {
        val p=DebrisRestPolicy();p.contact();p.shouldSleep(0f,0f,0)
        assertFalse(p.shouldSleep(.01f,0f,600));assertFalse(p.shouldSleep(0f,0f,700))
        assertFalse(p.shouldSleep(0f,1f,1300));assertFalse(p.shouldSleep(0f,0f,1400))
        assertTrue(p.shouldSleep(0f,0f,1900))
    }
    @Test fun WakeRequiresANewQuietWindow() {
        val p=DebrisRestPolicy();p.contact();p.shouldSleep(0f,0f,0)
        assertTrue(p.shouldSleep(0f,0f,500));p.wake()
        assertFalse(p.shouldSleep(0f,0f,1000));assertTrue(p.shouldSleep(0f,0f,1500))
    }
}
