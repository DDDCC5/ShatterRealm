package com.example.shatterrealm.platform

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.shatterrealm.domain.model.*
import com.example.shatterrealm.domain.usecase.MotionTracker
import com.example.shatterrealm.ui.experience.*
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.event.CollisionEvents
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.ecs.simulation.*
import com.pico.spatial.core.lifecycle.Cancellable
import com.pico.spatial.core.math.*
import com.pico.spatial.sense.base.AnchorUpdate
import com.pico.spatial.sense.base.TrackingState
import com.pico.spatial.sense.plane.*
import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.hand.*
import com.pico.spatial.tracking.controller.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.UUID
import kotlin.math.sqrt

private fun Vector3.domain() = V3(x.toDouble(),y.toDouble(),z.toDouble())
private fun Quat.domain() = Q4(x.toDouble(),y.toDouble(),z.toDouble(),w.toDouble())
private fun Q4.sdk() = Quat(x.toFloat(),y.toFloat(),z.toFloat(),w.toFloat())
private fun V3.sdk() = Vector3(x.toFloat(),y.toFloat(),z.toFloat())
private fun Vector3.length() = sqrt(x*x+y*y+z*z)
private fun Vector3.cross(b:Vector3) = Vector3(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x)

/** Owns one physical world. Interface anchors are outside this world. All ECS mutations on Main. */
class RealmRuntime(context:Context,private val vm:ExperienceViewModel): ExperienceRuntime,AutoCloseable {
    private val application=context.applicationContext as SpatialApplication
    val world=Entity().apply { setName("RealmPhysicsWorld");components.set(PhysicsWorldComponent(Vector3(0f,-9.8f,0f))) }
    private var bodyRoot=Entity().apply { setName("ThrowableAndDebrisRoot");world.addChild(this) }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private val assets=PhysicsAssetStore(context)
    private val audio=RealmAudio(context)
    private val handProvider=HandTrackingProvider()
    private val controllers=ControllerTrackingProvider()
    private val subscriptions=mutableListOf<Cancellable>()
    private val planes=mutableMapOf<UUID,Entity>()
    private val planeIds=mutableSetOf<Long>()
    private val planeData=mutableMapOf<UUID,PlaneAnchor>()
    private val objects=mutableMapOf<Long,ThrowableObject>()
    private val fragments=mutableMapOf<Long,Debris>()
    private var generation=0L
    private val pendingRestore=linkedMapOf<Pair<Long,Int>,BodySnapshot>()
    private var lastHandNanos=0L
    private var lastControllerUse=Long.MIN_VALUE/2
    private var closed=false
    private val hands=arrayOf(HandState(),HandState())
    private data class ThrowableObject(val instance:Long,val prepared:PreparedItem,val entity:Entity,var released:Boolean=false,var heldBy:Int?=null)
    private data class Debris(val prepared:PreparedItem,val index:Int,val instance:Long,var lastSound:Long=0)
    private class HandState {
        var position:Vector3?=null;var rotation=Quat()
        var motion:Motion?=null;var pinched=false;var held:Long?=null
    }
    private data class TrackedFrame(val data:HandTrackingData,val nanos:Long,val motions:List<Motion?>)
    private val rawTrackers=arrayOf(MotionTracker(),MotionTracker())
    private val frameLock=Any()
    private val trackedFrames=Channel<TrackedFrame>(Channel.CONFLATED)
    private val handListener=DataProvider.DataListener<HandTrackingData> { data ->
        val timestamp=SystemClock.elapsedRealtimeNanos()
        val motions=synchronized(frameLock) {
            listOf(data.left,data.right).mapIndexed { index,pose ->
                val joint=pose?.get(HandJoint.Index.PALM)
                if(joint==null || joint.position==Vector3.ZERO) { rawTrackers[index].clear();null }
                else rawTrackers[index].sample(PoseSample(timestamp,joint.position.domain(),joint.rotation.domain()))
            }
        }
        trackedFrames.trySend(TrackedFrame(data,timestamp,motions))
    }
    private val controllerListener=ControllerTrackingProvider.ControllerActionListener { data ->
        if(active(data.left)||active(data.right)) {
            lastControllerUse=SystemClock.elapsedRealtimeNanos()
            scope.launch { vm.controller(true) }
        }
    }
    private fun active(a:ControllerAction) = a.aButtonPressed||a.bButtonPressed||a.xButtonPressed||a.yButtonPressed||
        a.aButtonTouched||a.bButtonTouched||a.xButtonTouched||a.yButtonTouched||a.triggerPressed||a.triggerTouched||
        a.gripPressed||a.thumbstickPressed||a.thumbstickTouched||a.triggerValue>.04f||a.gripValue>.04f
    fun attach(content:SpatialViewContent) {
        // Stop the previous owner before starting shared tracking managers for this scene.
        application.activeRuntime?.takeIf { it!==this }?.close()
        content.addEntity(world)
        subscriptions+=content.subscribe(CollisionEvents.Enter::class.java) { event ->
            val a=event.entityA?.id;val b=event.entityB?.id
            val impulse=event.impulse.length()
            if(a!=null && b!=null) scope.launch { if(!closed) collide(a,b,impulse) }
        }
        subscriptions+=PlaneTrackingManager.subscribeAnchorUpdate { update -> scope.launch {
            if(!closed) {
                if(update.event==AnchorUpdate.Event.REMOVED) removePlane(update.anchor.anchorUUID)
                else updatePlane(update.anchor)
            }
        } }
        PlaneTrackingManager.start()
        scope.launch { monitorRoomPlanes() }
        scope.launch { for(frame in trackedFrames) if(!closed) onHands(frame.data,frame.nanos,frame.motions) }
        handProvider.addListener(handListener);handProvider.start()
        controllers.addControllerActionListener(controllerListener);controllers.start()
        scope.launch {
            // Pose motion is activity; a connected, stationary controller alone never opens the notice.
            val previous=arrayOfNulls<ControllerPose>(2)
            var before=0L
            controllers.dataFlow.collect { data ->
                val now=SystemClock.elapsedRealtimeNanos(); val dt=(now-before)/1e9;before=now
                listOf(data.left,data.right).forEachIndexed { index,p ->
                    val old=previous[index];previous[index]=p
                    if(p!=null && old!=null && dt>0 && dt<.1 && (p.position-old.position).length()/dt>.18) {
                        lastControllerUse=now;vm.controller(true)
                    }
                }
            }
        }
        scope.launch { while(isActive) {
            delay(100)
            val now=SystemClock.elapsedRealtimeNanos()
            if(now-lastHandNanos>150_000_000) {
                vm.hands(false);hands.forEach { it.motion=null;it.position=null }
            }
            if(now-lastControllerUse>800_000_000 && vm.state.value.handsTracked) vm.controller(false)
        } }
        application.activeRuntime=this
        vm.runtime=this
        scope.launch { runCatching { restoreScene() }.onFailure { vm.error();Log.e("ShatterRealm","Session restore",it) } }
        Log.i("ShatterRealm","Runtime attached: SDK 6.1.9, Mixed Stage, gravity=9.8, real planes only")
    }
    /** start() is asynchronous; loading before RUNNING silently returns an empty array.
     * Reload on every resume and while empty; anchor events remain the normal update path. */
    private suspend fun monitorRoomPlanes() {
        var previous:TrackingState?=null
        var lastLoad=0L
        while(!closed) {
            val state=PlaneTrackingManager.state
            val changed=state!=previous
            if(changed) Log.i("ShatterRealm","Plane provider state=$state colliders=${planes.size}")
            if(state==TrackingState.RUNNING) {
                val now=SystemClock.elapsedRealtime()
                if(changed || (planes.isEmpty() && now-lastLoad>=2000)) {
                    lastLoad=now
                    try {
                        val anchors=withTimeout(4000) { PlaneTrackingManager.loadAllAnchors() }
                        if(closed) return
                        anchors.forEach { updatePlane(it) }
                        vm.roomStatus(if(planes.isEmpty()) RoomPlaneStatus.EMPTY else RoomPlaneStatus.READY)
                        Log.i("ShatterRealm","Loaded system planes=${anchors.size} colliders=${planes.size} semantics=${anchors.map { it.semantics }.distinct()}")
                    } catch(e:TimeoutCancellationException) {
                        vm.roomStatus(RoomPlaneStatus.ERROR);Log.w("ShatterRealm","Plane load timed out; retry when running")
                    } catch(e:CancellationException) { throw e }
                    catch(e:Exception) { vm.roomStatus(RoomPlaneStatus.ERROR);Log.e("ShatterRealm","Plane load",e) }
                }
            } else vm.roomStatus(when(state) {
                TrackingState.PAUSED -> RoomPlaneStatus.PAUSED
                TrackingState.INVALID,TrackingState.UNKNOWN -> RoomPlaneStatus.UNAVAILABLE
                else -> RoomPlaneStatus.STARTING
            })
            previous=state
            delay(500)
        }
    }
    private fun updatePlane(anchor:PlaneAnchor) {
        runCatching {
            val old=planeData[anchor.anchorUUID]
            val geometryChanged=old==null || old.vertices!=anchor.vertices || old.indices!=anchor.indices
            val plane=planes.getOrPut(anchor.anchorUUID) {
                Entity().apply { setName("Plane-${anchor.anchorUUID}");world.addChild(this);planeIds+=id }
            }
            if(old==null || old.transform.position!=anchor.transform.position || old.transform.quaternion!=anchor.transform.quaternion) {
                plane.pose().position=world.convertPositionFrom(anchor.transform.position,null)
                plane.pose().quaternion=world.convertRotationFrom(anchor.transform.quaternion,null)
            }
            if(geometryChanged) {
                val mesh=MeshResource.loadFromPlaneAnchor(anchor.anchorUUID)
                val shape=ShapeResource.createStaticMesh(mesh)
                val material=plane.components.get<CollisionComponent>()?.physicsMaterial ?: PhysicsMaterialResource(.6f,.5f,.12f)
                plane.components.set(CollisionComponent(listOf(shape),material))
            }
            // No rigid body means a static collider. Duplicate anchor notifications need no recooking.
            planeData[anchor.anchorUUID]=anchor
            vm.planes(planes.size)
        }.onFailure { Log.e("ShatterRealm","Cannot update real plane ${anchor.anchorUUID}",it) }
    }
    private fun removePlane(id:UUID) { planeData.remove(id);planes.remove(id)?.let { planeIds-=it.id;it.destroy() };vm.planes(planes.size) }
    override fun select(id:String) {
        val item=vm.item(id) ?: return
        val token=++generation
        vm.loading(true)
        scope.launch {
            var created:Entity?=null
            runCatching {
                val prepared=assets.get(item)
                if(token!=generation || closed) return@launch
                check(planes.isNotEmpty()) { "Real system plane colliders are required before spawning" }
                // Replace only an unthrown preview; previous throws and all debris persist.
                objects.values.filter { !it.released && it.heldBy==null }.toList().forEach { objects.remove(it.entity.id);it.entity.destroy() }
                val hand=hands.firstOrNull { it.position!=null } ?: error("Hand tracking is required")
                check(vm.state.value.accepted && !vm.state.value.controllerActive)
                val position=requireNotNull(hand.position)
                val entity=requireNotNull(prepared.intact.clone(Entity.CloneOptions(recursive=true))).also { created=it }
                // Register before attachment so Reset/teardown can always recover a partial setup.
                objects[entity.id]=ThrowableObject(vm.nextInstance(),prepared,entity)
                entity.enabled=true;bodyRoot.addChild(entity)
                entity.pose().position=world.convertPositionFrom(position,null)
                entity.pose().quaternion=world.convertRotationFrom(hand.rotation,null)
                entity.components.set(CollisionComponent(prepared.shapes,prepared.physicsMaterial))
                entity.components.set(RigidBodyComponent(prepared.mass,RigidBodyMode.KINEMATIC).apply {
                    isAffectedByGravity=false;collisionDetectionMode=CollisionDetectionMode.CONTINUOUS_DYNAMIC
                    linearDamping=0f;angularDamping=0f
                })
                vm.loading(false);audio.ui("select")
            }.onFailure { error -> created?.let { objects.remove(it.id);it.destroy() };if(token==generation) { vm.error();Log.e("ShatterRealm","Prepare ${item.id}",error) } }
        }
    }
    private fun onHands(data:HandTrackingData,now:Long,motions:List<Motion?>) {
        if(SystemClock.elapsedRealtimeNanos()-now>100_000_000) return
        lastHandNanos=now
        vm.hands(listOfNotNull(data.left,data.right).any { it[HandJoint.Index.PALM].position!=Vector3.ZERO })
        listOf(data.left to data.leftPinch,data.right to data.rightPinch).forEachIndexed { index,(pose,pinch) ->
            val hand=hands[index]
            if(pose==null || pose[HandJoint.Index.PALM].position==Vector3.ZERO) { hand.position=null;hand.motion=null;return@forEachIndexed }
            val joint=pose[HandJoint.Index.PALM]
            hand.position=joint.position;hand.rotation=joint.rotation
            hand.motion=motions[index]
            if(!vm.state.value.accepted || vm.state.value.controllerActive || vm.state.value.resetConfirmation) { hand.motion=null;return@forEachIndexed }
            val ready=pinch?.isReady==true
            val pinched=ready && if(hand.pinched) pinch!!.pinchValue>.25f else pinch!!.pinchValue>.9f
            if(pinched && hand.held==null) {
                objects.values.firstOrNull { !it.released && it.heldBy==null &&
                    (world.convertPositionTo(it.entity.pose().position,null)-joint.position).length()<.18f }?.let {
                    hand.held=it.entity.id;it.heldBy=index
                }
            }
            val held=hand.held?.let { objects[it] }
            if(held!=null) {
                held.entity.pose().position=world.convertPositionFrom(joint.position,null)
                held.entity.pose().quaternion=world.convertRotationFrom(joint.rotation,null)
                // A lost/not-ready pinch does not manufacture a release velocity.
                if(ready && !pinched && hand.motion!=null) release(held,hand)
            }
            if(ready) hand.pinched=pinched
        }
    }
    private fun release(obj:ThrowableObject,hand:HandState) {
        val motion=hand.motion ?: return
        val rootLinear=world.convertRotationFrom(Quat(),null).rotateVector(motion.linear.sdk())
        val angular=world.convertRotationFrom(Quat(),null).rotateVector(motion.angular.sdk())
        val linear=rootLinear+angular.cross(obj.entity.pose().quaternion.rotateVector(obj.prepared.mass.centerOfMass))
        obj.entity.components.get<RigidBodyComponent>()!!.apply { rigidBodyMode=RigidBodyMode.DYNAMIC;isAffectedByGravity=true }
        obj.entity.components.set(PhysicsVelocityComponent(linear,angular))
        obj.released=true;obj.heldBy=null;hand.held=null
        audio.material(obj.prepared.item.material,"throw")
        Log.i("ShatterRealm","Release ${obj.prepared.item.id} linear=$linear angular=$angular")
    }
    private fun collide(a:Long,b:Long,impulse:Float) {
        if(a in planeIds) fracture(b) else if(b in planeIds) fracture(a)
        if(a in fragments) debrisSound(a,impulse)
        if(b in fragments) debrisSound(b,impulse)
    }
    private fun fracture(id:Long) {
        val obj=objects[id] ?: return
        if(!obj.released) return
        val pose=obj.entity.pose();val position=pose.position;val rotation=pose.quaternion
        val velocity=obj.entity.components.get<PhysicsVelocityComponent>() ?: return
        val linear=velocity.linearVelocity;val angular=velocity.angularVelocity
        if(!vm.fractured(obj.instance,obj.prepared.item.id,true,true)) return
        objects.remove(id)
        obj.entity.enabled=false
        obj.prepared.fragments.forEachIndexed { index,template ->
            val shard=requireNotNull(template.visual.clone(Entity.CloneOptions(recursive=true)))
            shard.enabled=true;bodyRoot.addChild(shard)
            val offset=rotation.rotateVector(template.offset)
            shard.pose().position=position+offset;shard.pose().quaternion=rotation
            shard.components.set(CollisionComponent(template.shapes,obj.prepared.physicsMaterial))
            shard.components.set(RigidBodyComponent(template.mass,RigidBodyMode.DYNAMIC).apply {
                isAffectedByGravity=true;collisionDetectionMode=CollisionDetectionMode.CONTINUOUS_DYNAMIC;linearDamping=0f;angularDamping=.01f
            })
            val comOffset=rotation.rotateVector(template.offset-obj.prepared.mass.centerOfMass)
            shard.components.set(PhysicsVelocityComponent(linear+angular.cross(comOffset),angular))
            fragments[shard.id]=Debris(obj.prepared,index,obj.instance,SystemClock.elapsedRealtimeNanos())
        }
        obj.entity.destroy();audio.material(obj.prepared.item.material,"shatter")
        Log.i("ShatterRealm","Fractured ${obj.prepared.item.id}: ${obj.prepared.fragments.size} persistent collidable fragments; total=${vm.state.value.counts.total}")
    }
    private fun debrisSound(id:Long,impulse:Float) {
        val debris=fragments[id] ?: return;val now=SystemClock.elapsedRealtimeNanos()
        if(impulse>.0008f && now-debris.lastSound>90_000_000) { debris.lastSound=now;audio.material(debris.prepared.item.material,"fragment-land",sqrt(impulse*8).coerceAtMost(1f)) }
    }
    override fun reset() {
        val resetStart=SystemClock.elapsedRealtime()
        generation++
        vm.saveScene(emptyList());pendingRestore.clear()
        hands.forEach { it.held=null;it.motion=null }
        // One recursive native destruction instead of one round-trip per body.
        bodyRoot.destroy(recursively=true)
        bodyRoot=Entity().apply { setName("ThrowableAndDebrisRoot");world.addChild(this) }
        objects.clear();fragments.clear();vm.loading(false)
        Log.i("ShatterRealm","Explicit reset: intact objects and fragments cleared in ${SystemClock.elapsedRealtime()-resetStart}ms")
    }
    override fun uiSound(name:String) { audio.ui(name) }
    private fun snapshot(entity:Entity,item:Item,instance:Long,index:Int,released:Boolean):BodySnapshot {
        val pose=entity.pose();val velocity=entity.components.get<PhysicsVelocityComponent>()
        val globalRotation=world.convertRotationTo(Quat(),null)
        return BodySnapshot(item,instance,index,world.convertPositionTo(pose.position,null).domain(),
            world.convertRotationTo(pose.quaternion,null).domain(),
            globalRotation.rotateVector(velocity?.linearVelocity ?: Vector3(0f)).domain(),
            globalRotation.rotateVector(velocity?.angularVelocity ?: Vector3(0f)).domain(),released)
    }
    private fun saveScene() {
        val bodies=objects.values.map { snapshot(it.entity,it.prepared.item,it.instance,-1,it.released) }+
            bodyRoot.getChildren().mapNotNull { entity -> fragments[entity.id]?.let { snapshot(entity,it.prepared.item,it.instance,it.index,true) } }
        // If disposal interrupts async restore, preserve yet-unrestored bodies too.
        vm.saveScene((pendingRestore.values+bodies).associateBy { it.instance to it.fragmentIndex }.values.toList())
    }
    private suspend fun restoreScene() {
        val saved=vm.savedScene()
        val token=generation
        saved.forEach { pendingRestore[it.instance to it.fragmentIndex]=it }
        for(body in saved) {
            vm.rememberItem(body.item)
            val prepared=assets.get(body.item)
            if(token!=generation || closed) return
            val template=body.fragmentIndex.takeIf { it>=0 }?.let { prepared.fragments[it] }
            val entity=requireNotNull((template?.visual ?: prepared.intact).clone(Entity.CloneOptions(recursive=true)))
            entity.enabled=true;bodyRoot.addChild(entity)
            entity.pose().position=world.convertPositionFrom(body.position.sdk(),null)
            entity.pose().quaternion=world.convertRotationFrom(body.rotation.sdk(),null)
            entity.components.set(CollisionComponent(template?.shapes ?: prepared.shapes,prepared.physicsMaterial))
            entity.components.set(RigidBodyComponent(template?.mass ?: prepared.mass,if(body.released) RigidBodyMode.DYNAMIC else RigidBodyMode.KINEMATIC).apply {
                isAffectedByGravity=body.released;collisionDetectionMode=CollisionDetectionMode.CONTINUOUS_DYNAMIC;linearDamping=0f;angularDamping=.01f
            })
            val inverseWorld=world.convertRotationFrom(Quat(),null)
            entity.components.set(PhysicsVelocityComponent(inverseWorld.rotateVector(body.linear.sdk()),inverseWorld.rotateVector(body.angular.sdk())))
            if(template==null) objects[entity.id]=ThrowableObject(body.instance,prepared,entity,body.released)
            else fragments[entity.id]=Debris(prepared,body.fragmentIndex,body.instance,SystemClock.elapsedRealtimeNanos())
            pendingRestore.remove(body.instance to body.fragmentIndex)
        }
        vm.saveScene(emptyList())
        if(saved.isNotEmpty()) Log.i("ShatterRealm","Restored ${saved.size} process-session bodies")
    }
    override fun close() {
        if(closed) return;closed=true;generation++
        saveScene()
        if(application.activeRuntime===this) application.activeRuntime=null
        if(vm.runtime===this) vm.runtime=null
        handProvider.removeListener(handListener);handProvider.stop()
        controllers.removeControllerActionListener(controllerListener);controllers.stop()
        subscriptions.forEach { it.cancel() };subscriptions.clear();PlaneTrackingManager.stop()
        trackedFrames.close();scope.cancel();world.destroy();assets.close();audio.close()
    }
}
