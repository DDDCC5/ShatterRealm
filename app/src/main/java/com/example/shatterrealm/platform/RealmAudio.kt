package com.example.shatterrealm.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import kotlin.random.Random

/** System media volume is the only volume control; approved 48 kHz material recordings. */
internal class RealmAudio(private val context:Context): AutoCloseable {
    private val pool=SoundPool.Builder().setMaxStreams(32).setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
    private val handler=Handler(Looper.getMainLooper())
    private val materialStreams=mutableSetOf<Int>()
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
    fun material(material:String,kind:String,energy:Float=1f) {
        val cap=when(kind) { "shatter" -> 1000L; "fragment-land" -> 280L; else -> null }
        play("$material/$kind-${Random.nextInt(1,4)}",energy.coerceIn(.08f,1f),cap)
    }
    fun stopMaterialSounds() { materialStreams.forEach { pool.stop(it) };materialStreams.clear();handler.removeCallbacksAndMessages(null) }
    fun ui(name:String) {
        val resolved=when(name) { "select" -> "pick"; "reset" -> "reset-complete"; "refresh" -> "refresh-complete"; else -> name }
        val key="ui/$resolved"
        play(key,.6f)
    }
    private fun play(key:String,gain:Float,capMillis:Long?=null) {
        val id=ids[key] ?: return
        if(synchronized(loaded) { id in loaded }) {
            val stream=pool.play(id,gain,gain,1,0,1f)
            if(stream!=0 && capMillis!=null) {
                materialStreams+=stream
                for(step in 1..4) handler.postDelayed({
                    if(stream in materialStreams) pool.setVolume(stream,gain*(1f-step/4f),gain*(1f-step/4f))
                },capMillis-100+step*20)
                handler.postDelayed({ pool.stop(stream);materialStreams-=stream },capMillis)
            }
        }
    }
    override fun close() { stopMaterialSounds();pool.release();ids.clear();synchronized(loaded) { loaded.clear() } }
}
