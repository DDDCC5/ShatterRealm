package com.example.shatterrealm.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlin.random.Random

/** System media volume is the only volume control; approved 48 kHz material recordings. */
internal class RealmAudio(private val context:Context): AutoCloseable {
    private val pool=SoundPool.Builder().setMaxStreams(32).setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val ids=mutableMapOf<String,Int>()
    private val loaded=mutableSetOf<Int>()
    init {
        pool.setOnLoadCompleteListener { _,id,status -> if(status==0) synchronized(loaded) { loaded+=id } }
        for(material in listOf("glass","ceramic","plastic","custom")) for(kind in listOf("throw","shatter","fragment-land")) for(n in 1..3)
            load("$material/$kind-$n","audio/materials/$material/$kind-0$n.wav")
        context.assets.list("audio/ui")?.filter { it.endsWith(".wav") }?.forEach { load("ui/"+it.removeSuffix(".wav"),"audio/ui/$it") }
    }
    private fun load(key:String,path:String) {
        context.assets.openFd(path).use { ids[key]=pool.load(it,1) }
    }
    fun material(material:String,kind:String,energy:Float=1f) { play("$material/$kind-${Random.nextInt(1,4)}",energy.coerceIn(.08f,1f)) }
    fun ui(name:String) {
        val resolved=when(name) { "select" -> "pick"; "reset" -> "reset-complete"; "refresh" -> "refresh-complete"; else -> name }
        val key="ui/$resolved"
        play(key,.6f)
    }
    private fun play(key:String,gain:Float) {
        val id=ids[key] ?: return
        if(synchronized(loaded) { id in loaded }) pool.play(id,gain,gain,1,0,1f)
    }
    override fun close() { pool.release();ids.clear();synchronized(loaded) { loaded.clear() } }
}
