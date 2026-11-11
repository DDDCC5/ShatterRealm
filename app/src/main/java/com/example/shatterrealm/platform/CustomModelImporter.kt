package com.example.shatterrealm.platform

import android.content.Context
import com.example.shatterrealm.domain.model.Item
import java.io.File
import java.security.MessageDigest

/** Real Assimp GLB/FBX/OBJ decoding plus watertight Manifold plane partitions, cached by content. */
object CustomModelImporter {
    init { System.loadLibrary("shatter_import") }
    private external fun convertNative(source:String,directory:String)
    fun import(context:Context,source:File): Item {
        require(source.extension.lowercase() in setOf("glb","fbx","obj"))
        require(source.length()<=64L*1024*1024)
        val digest=MessageDigest.getInstance("SHA-256")
        source.inputStream().use { input -> val bytes=ByteArray(65536);while(true) { val n=input.read(bytes);if(n<0) break;digest.update(bytes,0,n) } }
        val hash=digest.digest().joinToString("") { "%02x".format(it) }
        val cache=File(context.filesDir,"imported/v2-$hash")
        if(!File(cache,"metadata.json").isFile) {
            val temporary=File(context.filesDir,"imported/.working-$hash");temporary.deleteRecursively();temporary.mkdirs()
            try { convertNative(source.absolutePath,temporary.absolutePath);check(temporary.renameTo(cache)) }
            catch(error:Throwable) { temporary.deleteRecursively();throw error }
        }
        return Item("custom-$hash",source.nameWithoutExtension,source.nameWithoutExtension,"custom",cache.toURI().toString().removeSuffix("/"),true)
    }
}
