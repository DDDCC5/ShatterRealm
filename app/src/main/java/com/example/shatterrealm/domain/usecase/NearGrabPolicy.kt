package com.example.shatterrealm.domain.usecase

import com.example.shatterrealm.domain.model.V3

/** Near pickup tolerance only. Does not modify motion samples or released body velocity. */
object NearGrabPolicy {
    fun pinched(ready:Boolean,strength:Float,wasPinched:Boolean):Boolean =
        ready && strength.isFinite() && strength > if(wasPinched) .25f else .65f

    fun distanceSquared(point:V3,min:V3,max:V3):Double {
        fun gap(x:Double,lo:Double,hi:Double)=maxOf(lo-x,0.0,x-hi)
        val x=gap(point.x,min.x,max.x);val y=gap(point.y,min.y,max.y);val z=gap(point.z,min.z,max.z)
        return x*x+y*y+z*z
    }
    fun inReach(distanceSquared:Double)=distanceSquared.isFinite() && distanceSquared<=.12*.12
}
