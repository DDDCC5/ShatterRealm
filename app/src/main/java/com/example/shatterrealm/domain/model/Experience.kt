package com.example.shatterrealm.domain.model

import kotlin.math.*

data class Item(val id: String, val zh: String, val en: String, val material: String,
                val baseUri: String, val custom: Boolean = false) {
    fun label(chinese: Boolean) = if (chinese) zh else en
}
data class Counts(val byItem: Map<String, Int> = emptyMap()) {
    val total get() = byItem.values.sum()
}
data class V3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(b: V3) = V3(x+b.x,y+b.y,z+b.z)
    operator fun minus(b: V3) = V3(x-b.x,y-b.y,z-b.z)
    operator fun times(k: Double) = V3(x*k,y*k,z*k)
    fun cross(b: V3) = V3(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x)
}
data class Q4(val x: Double, val y: Double, val z: Double, val w: Double) {
    operator fun times(b: Q4) = Q4(w*b.x+x*b.w+y*b.z-z*b.y,w*b.y-x*b.z+y*b.w+z*b.x,
        w*b.z+x*b.y-y*b.x+z*b.w,w*b.w-x*b.x-y*b.y-z*b.z)
    fun inverse() = Q4(-x,-y,-z,w)
    companion object { val IDENTITY = Q4(0.0,0.0,0.0,1.0) }
}
data class PoseSample(val nanos: Long, val position: V3, val rotation: Q4)
data class Motion(val linear: V3, val angular: V3) {
    fun atOffset(offset: V3) = linear + angular.cross(offset)
}

/** Process-only restore point. Fragment index -1 denotes an intact object. Global meter/radian coordinates. */
data class BodySnapshot(val item:Item,val instance:Long,val fragmentIndex:Int,val position:V3,
    val rotation:Q4,val linear:V3,val angular:V3,val released:Boolean)
