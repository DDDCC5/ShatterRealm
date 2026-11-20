package com.example.shatterrealm.domain.usecase

import com.example.shatterrealm.domain.model.Q4
import com.example.shatterrealm.domain.model.V3
import kotlin.math.cos
import kotlin.math.sin

/** One-shot reachable placement relative to level head heading; independent of either hand. */
object FrontSpawnLayout {
    fun center(head:V3,rotation:Q4):V3 {
        val yaw=UprightHeading.yaw(rotation)
        return head+V3(-sin(yaw)*.55,-.28,-cos(yaw)*.55)
    }
    fun pivot(head:V3,rotation:Q4,localCenter:V3):V3 {
        val yaw=UprightHeading.yaw(rotation)
        val offset=V3(cos(yaw)*localCenter.x+sin(yaw)*localCenter.z,localCenter.y,
            -sin(yaw)*localCenter.x+cos(yaw)*localCenter.z)
        return center(head,rotation)-offset
    }
}
