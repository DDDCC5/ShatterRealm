package com.example.shatterrealm.domain.usecase

/** A whole-object sound burst, never a separate repeated sound budget for every shard. */
class FractureSoundGate {
    private val landingUntil=mutableMapOf<Long,Long>()
    fun begin(instance:Long,nowMillis:Long) {
        landingUntil.entries.removeAll { it.value<nowMillis }
        landingUntil[instance]=nowMillis+500
    }
    fun allowLanding(instance:Long,nowMillis:Long):Boolean {
        val until=landingUntil.remove(instance) ?: return false
        return nowMillis<=until
    }
    fun clear()=landingUntil.clear()
}
