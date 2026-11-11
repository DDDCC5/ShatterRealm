package com.example.shatterrealm.domain

import com.example.shatterrealm.domain.model.Q4
import com.example.shatterrealm.domain.usecase.UprightHeading
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class UprightHeadingTest {
    private fun axis(x:Double,y:Double,z:Double,a:Double)=Q4(x*sin(a/2),y*sin(a/2),z*sin(a/2),cos(a/2))
    @Test fun tiltedLaunchKeepsHeadingButDropsPitchAndRoll() {
        for(yaw in listOf(-2.4,-.6,0.0,1.3,2.8)) for(pitch in listOf(-1.0,.0,.9)) for(roll in listOf(-.7,.0,.8)) {
            val q=axis(0.0,1.0,0.0,yaw)*axis(1.0,0.0,0.0,pitch)*axis(0.0,0.0,1.0,roll)
            assertEquals(yaw,UprightHeading.yaw(q),1e-9)
            assertEquals(yaw,UprightHeading.yaw(Q4(-q.x,-q.y,-q.z,-q.w)),1e-9)
        }
    }
    @Test fun verticalGazeUsesHorizontalHeadRight() {
        val q=axis(0.0,1.0,0.0,.8)*axis(1.0,0.0,0.0,PI/2)
        assertEquals(.8,UprightHeading.yaw(q),1e-9)
    }
}
