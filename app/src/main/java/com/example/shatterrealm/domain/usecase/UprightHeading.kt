package com.example.shatterrealm.domain.usecase

import com.example.shatterrealm.domain.model.Q4
import kotlin.math.*

/** Heading of the projected viewing direction. Head pitch/roll never tilt interface panels. */
object UprightHeading {
    fun yaw(rotation:Q4):Double {
        val norm=rotation.x*rotation.x+rotation.y*rotation.y+rotation.z*rotation.z+rotation.w*rotation.w
        require(norm.isFinite() && norm>1e-12)
        val x=rotation.x/sqrt(norm);val y=rotation.y/sqrt(norm)
        val z=rotation.z/sqrt(norm);val w=rotation.w/sqrt(norm)
        val backX=2*(x*z+w*y);val backZ=1-2*(x*x+y*y)
        // Looking exactly up/down has no projected viewing direction; preserve heading from head-right.
        return if(backX*backX+backZ*backZ>1e-8) atan2(backX,backZ)
        else atan2(-2*(x*z-w*y),1-2*(y*y+z*z))
    }
}
