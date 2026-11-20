package com.example.shatterrealm.domain.usecase

/** Sleep only after real contact and sustained low motion, never at a free-flight apex. */
class DebrisRestPolicy {
    private var contacted=false
    private var quietSince:Long?=null
    fun contact() { contacted=true }
    fun wake() { quietSince=null }
    fun shouldSleep(linearSquared:Float,angularSquared:Float,now:Long):Boolean {
        if(!contacted || linearSquared>.0016f || angularSquared>.25f) { quietSince=null;return false }
        val since=quietSince ?: now.also { quietSince=it }
        return now-since>=500
    }
}
