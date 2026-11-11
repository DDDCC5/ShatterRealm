package com.example.shatterrealm.domain.usecase

import com.example.shatterrealm.domain.model.*
import kotlin.math.*

/** Process-owned, deliberately not persisted to disk or saved instance state. */
class ExperienceSession {
    var accepted = false; private set
    var counts = Counts(); private set
    private val fractured = mutableSetOf<Long>()
    var bodies: List<BodySnapshot> = emptyList()
    private var sequence = 0L
    fun nextInstance(): Long = ++sequence
    fun accept() { accepted = true }
    fun fracture(instance: Long, item: String, released: Boolean, realPlane: Boolean): Boolean {
        if (!accepted || !released || !realPlane || !fractured.add(instance)) return false
        val updated = LinkedHashMap(counts.byItem)
        updated[item] = (updated[item] ?: 0) + 1
        counts = Counts(updated)
        return true
    }
    fun reset() { fractured.clear(); counts = Counts(); bodies=emptyList() }
}

/** Two consecutive tracked poses; no velocity scaling, smoothing, aim assist, or clamping. */
class MotionTracker {
    private var previous: PoseSample? = null
    fun clear() { previous = null }
    fun sample(current: PoseSample): Motion? {
        val before = previous; previous = current
        if (before == null) return null
        val elapsed = current.nanos - before.nanos
        if (elapsed <= 0 || elapsed > 100_000_000) return null
        val dt = elapsed / 1_000_000_000.0
        var delta = current.rotation * before.rotation.inverse()
        if (delta.w < 0) delta = Q4(-delta.x,-delta.y,-delta.z,-delta.w)
        val n = sqrt(delta.x*delta.x+delta.y*delta.y+delta.z*delta.z)
        val angular = if (n < 1e-10) V3(0.0,0.0,0.0) else {
            val angle = 2*atan2(n,delta.w)
            V3(delta.x,delta.y,delta.z)*(angle/(n*dt))
        }
        return Motion((current.position-before.position)*(1/dt),angular)
    }
}
