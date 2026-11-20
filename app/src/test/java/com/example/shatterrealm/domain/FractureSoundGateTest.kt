package com.example.shatterrealm.domain

import com.example.shatterrealm.domain.usecase.FractureSoundGate
import org.junit.Assert.*
import org.junit.Test

class FractureSoundGateTest {
    @Test fun manyShardsShareOnlyOneLanding() {
        val gate=FractureSoundGate();gate.begin(1,1000)
        assertEquals(1,(1..431).count { gate.allowLanding(1,1100) })
    }
    @Test fun oldOrRestoredShardsStaySilent() {
        val gate=FractureSoundGate();gate.begin(1,1000)
        assertFalse(gate.allowLanding(1,1501))
        assertFalse(gate.allowLanding(99,1200))
    }
    @Test fun differentThrowsAndResetHaveSeparateBudgets() {
        val gate=FractureSoundGate();gate.begin(1,100);gate.begin(2,150)
        assertTrue(gate.allowLanding(1,200));assertTrue(gate.allowLanding(2,210))
        gate.begin(3,300);gate.clear();assertFalse(gate.allowLanding(3,350))
    }
}
