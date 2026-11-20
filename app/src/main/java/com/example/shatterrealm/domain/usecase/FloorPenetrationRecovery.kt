package com.example.shatterrealm.domain.usecase

import com.example.shatterrealm.domain.model.V3

/** A contact projection for native collision misses, using the measured floor and convex support. */
object FloorPenetrationRecovery {
    private fun V3.dot(b:V3)=x*b.x+y*b.y+z*b.z
    fun contactPosition(position:V3,floorPoint:V3,normal:V3,supportOffset:Double):V3? {
        val height=(position-floorPoint).dot(normal)
        if(height+supportOffset>=-.02) return null
        return position+normal*(.001-height-supportOffset)
    }
}
