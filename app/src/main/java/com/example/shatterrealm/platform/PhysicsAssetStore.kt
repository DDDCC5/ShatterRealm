package com.example.shatterrealm.platform

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.shatterrealm.domain.model.Item
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.ecs.simulation.*
import com.pico.spatial.core.math.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI

internal fun JSONArray.vector() = Vector3(getDouble(0).toFloat(),getDouble(1).toFloat(),getDouble(2).toFloat())
internal fun JSONArray.quaternion() = Quat(getDouble(0).toFloat(),getDouble(1).toFloat(),getDouble(2).toFloat(),getDouble(3).toFloat())
internal fun Entity.pose() = requireNotNull(components.get<TransformComponent>())
internal data class FragmentTemplate(val visual: Entity,val offset: Vector3,val mass: MassProperties,val shapes: List<ShapeResource>,val supportPoints:List<Vector3>) {
    val supportRadius=kotlin.math.sqrt(supportPoints.maxOf { it.x*it.x+it.y*it.y+it.z*it.z })
}
internal data class PreparedItem(val item: Item,val intact: Entity,val fragmentsRoot: Entity,
    val fragments: List<FragmentTemplate>,val shapes: List<ShapeResource>,val mass: MassProperties,val physicsMaterial: PhysicsMaterialResource) {
    private val pickupPoints=fragments.flatMap { piece -> piece.supportPoints.map { it+piece.offset } }
    val pickupMin=Vector3(pickupPoints.minOf { it.x },pickupPoints.minOf { it.y },pickupPoints.minOf { it.z })
    val pickupMax=Vector3(pickupPoints.maxOf { it.x },pickupPoints.maxOf { it.y },pickupPoints.maxOf { it.z })
}

internal class PhysicsAssetStore(private val context:Context) {
    private val cache=mutableMapOf<String,PreparedItem>()
    private val resources=mutableSetOf<Resource>()
    private val loadLock=Mutex()
    private data class CookedPiece(val id:String,val offset:Vector3,val mass:MassProperties,val shapes:List<ShapeResource>,val supportPoints:List<Vector3>)
    private data class Cooked(val pieces:List<CookedPiece>,val compound:List<ShapeResource>,val mass:MassProperties,
        val material:PhysicsMaterialResource,val resources:List<Resource>)
    suspend fun get(item: Item): PreparedItem = loadLock.withLock { load(item) }
    private fun read(uri:String):String = if(uri.startsWith("asset://"))
        context.assets.open(uri.removePrefix("asset://")).bufferedReader().use { it.readText() }
        else File(URI(uri)).readText()
    private suspend fun load(item: Item): PreparedItem {
        cache[item.baseUri]?.let { return it }
        val metadata=withContext(Dispatchers.IO) { JSONObject(read(item.baseUri+"/metadata.json")) }
        val collisionData=withContext(Dispatchers.IO) { JSONArray(read(item.baseUri+"/colliders.json")) }
        val begin=SystemClock.elapsedRealtime()
        Log.i("ShatterRealm","Loading ${item.id} visuals and cooking exact collision geometry")
        val loaded=mutableListOf<Entity>()
        val cooked=java.util.concurrent.atomic.AtomicReference<Cooked?>()
        try {
            // Entity loading and every Entity/Component access stay on Main. Only public resource
            // factories run on the worker (verified by ResourceFactoryThreadTest on ARM64).
            val result=coroutineScope {
                val physical=async(Dispatchers.Default) { cook(item,metadata,collisionData).also { cooked.set(it) } }
                val visuals=listOf("intact.glb","fragments.glb").map { filename -> async {
                    Entity.loadSuspend(item.baseUri+"/"+filename).also {
                        it.enabled=false;loaded+=it
                        Log.i("ShatterRealm","Loaded ${item.id}/$filename after ${SystemClock.elapsedRealtime()-begin}ms")
                    }
                } }.awaitAll()
                physical.await()
                visuals
            }
            val (intact,fragments)=result
            val geometry=requireNotNull(cooked.get())
            val pieces=geometry.pieces.map { record ->
                FragmentTemplate(requireNotNull(fragments.findEntity(record.id)) { "Missing ${record.id}" },record.offset,record.mass,record.shapes,record.supportPoints)
            }
            val prepared=PreparedItem(item,intact,fragments,pieces,geometry.compound,geometry.mass,geometry.material)
            resources.addAll(geometry.resources)
            cache[item.baseUri]=prepared
            Log.i("ShatterRealm","Prepared ${item.id}: ${pieces.size} fragments, ${geometry.compound.size} hulls in ${SystemClock.elapsedRealtime()-begin}ms")
            return prepared
        } catch(error:Throwable) {
            loaded.forEach { it.destroy() };cooked.get()?.resources?.forEach { it.close() };throw error
        }
    }
    private suspend fun cook(item:Item,metadata:JSONObject,data:JSONArray):Cooked {
        val owned=mutableListOf<Resource>()
        fun <T:Resource> own(resource:T):T { resource.toGlobal();owned+=resource;return resource }
        try {
            val hullData=(0 until data.length()).map { data.getJSONObject(it) }
            val material=own(PhysicsMaterialResource(.55f,.4f,if(item.material=="plastic") .24f else .16f))
            val records=metadata.getJSONArray("fragmentRecords")
            val pieces=(0 until records.length()).map { i ->
                currentCoroutineContext().ensureActive()
                val record=records.getJSONObject(i);val id=record.getString("id")
                val hulls=hullData.filter { it.getString("name").startsWith("$id-hull-") }
                val points=mutableListOf<Vector3>();val triangles=mutableListOf<Int>()
                hulls.forEach { hull ->
                    currentCoroutineContext().ensureActive()
                    val p=hull.getJSONArray("positions");val t=hull.getJSONArray("indices");val offset=points.size
                    repeat(p.length()) { points+=p.getJSONArray(it).vector() }
                    repeat(t.length()) { triangles+=offset+t.getInt(it) }
                }
                require(points.isNotEmpty()) { "Missing hulls $id" }
                // One convex envelope per shard instead of a compound of many overlapping hulls.
                // The approved render mesh and measured mass/inertia remain unchanged.
                val mesh=own(MeshResource.createWithMeshModel(MeshModel(points,triangles),name="${item.id}-$id-collision"))
                val shapes=listOf(own(ShapeResource.createConvexMesh(mesh)))
                val principal=record.optJSONArray("principalInertia")
                val mass=if(principal!=null) MassProperties(record.getDouble("massKg").toFloat(),principal.vector(),Vector3(0f),record.getJSONArray("principalRotation").quaternion())
                    else MassProperties.generateByShapesAndMass(shapes,record.getDouble("massKg").toFloat())
                CookedPiece(id,record.getJSONArray("positionM").vector(),mass,shapes,points.distinct())
            }
            val compound=pieces.flatMap { piece -> piece.shapes.map { own(it.offsetByTranslation(piece.offset)) } }
            val mass=MassProperties.generateByShapesAndMass(compound,metadata.getDouble("massKg").toFloat())
            currentCoroutineContext().ensureActive()
            return Cooked(pieces,compound,mass,material,owned)
        } catch(error:Throwable) { owned.forEach { it.close() };throw error }
    }
    fun close() {
        cache.values.forEach { it.intact.destroy();it.fragmentsRoot.destroy() };cache.clear()
        val release=resources.toList();resources.clear()
        // Resource factories/close are worker-safe; never block Stage disposal on thousands of releases.
        CoroutineScope(SupervisorJob()+Dispatchers.Default).launch {
            release.forEach { resource -> runCatching { resource.close() }.onFailure { Log.w("ShatterRealm","Resource cleanup",it) } }
        }
    }
}
