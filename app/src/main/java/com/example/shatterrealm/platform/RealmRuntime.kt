package com.example.shatterrealm.platform

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.shatterrealm.domain.model.*
import com.example.shatterrealm.domain.usecase.FrontSpawnLayout
import com.example.shatterrealm.domain.usecase.UprightHeading
import com.example.shatterrealm.domain.usecase.NearGrabPolicy
import com.example.shatterrealm.domain.usecase.MotionTracker
import com.example.shatterrealm.domain.usecase.FractureSoundGate
import com.example.shatterrealm.domain.usecase.DebrisRestPolicy
import com.example.shatterrealm.domain.usecase.MappedFloorPrisms
import com.example.shatterrealm.domain.usecase.RealmCollisionLayers
import com.example.shatterrealm.domain.usecase.FloorPenetrationRecovery
import com.example.shatterrealm.ui.experience.*
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.event.CollisionEvents
import com.pico.spatial.core.ecs.event.SceneEvents
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
private fun Vector3.dot(b:Vector3) = x*b.x+y*b.y+z*b.z

/** Owns one physical world. Interface anchors are outside this world. All ECS mutations on Main. */
class RealmRuntime(context:Context,private val vm:ExperienceViewModel): ExperienceRuntime,AutoCloseable {
    private val application=context.applicationContext as SpatialApplication
    val world=Entity().apply { setName("RealmPhysicsWorld");components.set(PhysicsWorldComponent(Vector3(0f,-9.8f,0f))) }
    private var stagingRoot=Entity().apply { setName("OffSceneFragmentStaging");enabled=false }
    private var bodyRoot=Entity().apply { setName("ThrowableAndDebrisRoot");world.addChild(this) }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private val assets=PhysicsAssetStore(context)
    private val audio=RealmAudio(context)
    private val fractureSounds=FractureSoundGate()
    private val handProvider=HandTrackingProvider()
    private val controllers=ControllerTrackingProvider()
    private val subscriptions=mutableListOf<Cancellable>()
    private val planes=mutableMapOf<UUID,Entity>()
    private val planeIds=mutableSetOf<Long>()
    private val planeData=mutableMapOf<UUID,PlaneAnchor>()
    private val planeResources=mutableMapOf<UUID,List<Resource>>()
    private val debrisGroundSupports=mutableMapOf<UUID,Entity>()
    private data class GroundSupport(val id:UUID,val point:Vector3,val normal:Vector3)
    private val measuredGrounds=mutableMapOf<UUID,GroundSupport>()
    private val debrisFilter=CollisionFilter(CollisionGroup(RealmCollisionLayers.DEBRIS),CollisionGroup(CollisionGroup.COLLISION_GROUP_ALL))
    private val debrisGroundFilter=CollisionFilter(CollisionGroup(RealmCollisionLayers.DEBRIS_GROUND),CollisionGroup(RealmCollisionLayers.DEBRIS))
    private val objects=mutableMapOf<Long,ThrowableObject>()
    private val fragments=mutableMapOf<Long,Debris>()
    private var generation=0L
    private val pendingRestore=linkedMapOf<Pair<Long,Int>,BodySnapshot>()
    private var lastHandNanos=0L
    private var lastControllerUse=Long.MIN_VALUE/2
    private var closed=false
    private val hands=arrayOf(HandState(),HandState())
    private val hmdProvider=com.pico.spatial.tracking.hmd.HMDTrackingProvider()
    private var heldPosePending=false
    private fun applyHeldPose(obj:ThrowableObject,hand:HandState) {
        val position=hand.position ?: return
        val transform=obj.entity.pose()
        transform.position=bodyRoot.convertPositionFrom(position,null)
        transform.quaternion=bodyRoot.convertRotationFrom(hand.rotation,null)
    }
    private fun scheduleHeldPose() { heldPosePending=true }
    private data class StagedFragment(val template:FragmentTemplate,val entity:Entity,val velocity:PhysicsVelocityComponent)
    private data class ThrowableObject(val instance:Long,val prepared:PreparedItem,val entity:Entity,
        var released:Boolean=false,var heldBy:Int?=null,val staging:Entity=Entity().apply { enabled=false },
        val staged:MutableList<StagedFragment> = mutableListOf(),var ready:Boolean=false)
    private data class Debris(var entity:Entity,val prepared:PreparedItem,val index:Int,val instance:Long,
        var sleeping:Boolean=false,val rest:DebrisRestPolicy=DebrisRestPolicy(),var groundId:UUID?=null,var verifyUntil:Long=0L)
    private class HandState {
        var position:Vector3?=null;var rotation=Quat()
        var motion:Motion?=null;var pinched=false;var pinchStrength=0f;var held:Long?=null
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
        subscriptions+=content.subscribe(SceneEvents.Update::class.java) {
            // Submit transforms inside the SDK scene frame, not a separate Android callback.
            if(!closed && heldPosePending) {
                heldPosePending=false
                hands.forEach { hand -> hand.held?.let { objects[it] }?.let { applyHeldPose(it,hand) } }
            }
        }
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
        scope.launch { monitorRestingDebris() }
        scope.launch { for(frame in trackedFrames) if(!closed) onHands(frame.data,frame.nanos,frame.motions) }
        handProvider.addListener(handListener);handProvider.start()
        hmdProvider.start()
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
                vm.hands(false);hands.forEach { it.motion=null;it.position=null;it.pinchStrength=0f;it.pinched=false }
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
            var plane=planes[anchor.anchorUUID]
            if(geometryChanged || plane==null) {
                // Configure while detached. Adding CollisionComponent after scene attachment can
                // register a static actor outside this PhysicsWorld on device: rays hit, bodies pass.
                val collision=preparePlaneCollision(anchor)
                val replacement=Entity().apply {
                    setName("Plane-${anchor.anchorUUID}")
                    pose().position=world.convertPositionFrom(anchor.transform.position,null)
                    pose().quaternion=world.convertRotationFrom(anchor.transform.quaternion,null)
                    components.set(CollisionComponent(collision.shapes,collision.material))
                }
                world.addChild(replacement)
                val support=collision.supportShape?.let { shape -> Entity().apply {
                    setName("Plane-DebrisSupport-${anchor.anchorUUID}")
                    pose().position=replacement.pose().position;pose().quaternion=replacement.pose().quaternion
                    components.set(CollisionComponent(listOf(shape),collision.material,collisionFilter=debrisGroundFilter))
                    world.addChild(this)
                } }
                plane?.let { planeIds-=it.id;it.destroy() }
                debrisGroundSupports.remove(anchor.anchorUUID)?.destroy()
                if(support!=null) debrisGroundSupports[anchor.anchorUUID]=support
                planeResources.put(anchor.anchorUUID,collision.resources)?.asReversed()?.forEach { it.close() }
                planes[anchor.anchorUUID]=replacement;planeIds+=replacement.id;plane=replacement
            } else if(old==null || old.transform.position!=anchor.transform.position || old.transform.quaternion!=anchor.transform.quaternion) {
                plane.pose().position=world.convertPositionFrom(anchor.transform.position,null)
                plane.pose().quaternion=world.convertRotationFrom(anchor.transform.quaternion,null)
                debrisGroundSupports[anchor.anchorUUID]?.let { support ->
                    support.pose().position=plane.pose().position;support.pose().quaternion=plane.pose().quaternion
                }
            }
            // No rigid body means a static collider. Duplicate anchor notifications need no recooking.
            planeData[anchor.anchorUUID]=anchor
            measuredGround(anchor)?.let { ground ->
                val previous=measuredGrounds.put(anchor.anchorUUID,ground)
                if(previous!=null && ((previous.point-ground.point).length()>.01f || (previous.normal-ground.normal).length()>.01f)) {
                    val until=SystemClock.elapsedRealtime()+1000
                    fragments.values.filter { it.groundId==anchor.anchorUUID }.forEach { it.verifyUntil=until }
                }
            }
            vm.planes(planes.size)
        }.onFailure { Log.e("ShatterRealm","Cannot update real plane ${anchor.anchorUUID}",it) }
    }
    private fun measuredGround(anchor:PlaneAnchor):GroundSupport? {
        if(!anchor.semantics.toString().contains("floor",true)) return null
        return anchor.indices.chunked(3).firstNotNullOfOrNull { t ->
            val a=anchor.vertices[t[0]];val n=(anchor.vertices[t[1]]-a).cross(anchor.vertices[t[2]]-a);val length=n.length()
            if(length<1e-7f) null else {
                var normal=world.convertRotationFrom(anchor.transform.quaternion,null).rotateVector(n*(1f/length))
                if(normal.y<0) normal=normal*(-1f)
                GroundSupport(anchor.anchorUUID,world.convertPositionFrom(anchor.transform.position+anchor.transform.quaternion.rotateVector(a),null),normal)
            }
        }
    }
    private fun groundBelow(position:Vector3):GroundSupport? {
        val grounds=measuredGrounds.values
        return grounds.filter { (position-it.point).dot(it.normal)>=-.1f }
            .minByOrNull { (position-it.point).dot(it.normal).coerceAtLeast(0f) }
            ?: grounds.minByOrNull { kotlin.math.abs((position-it.point).dot(it.normal)) }
    }
    private data class PlaneCollision(val shapes:List<ShapeResource>,val material:PhysicsMaterialResource,val resources:List<Resource>,val supportShape:ShapeResource?)
    private fun preparePlaneCollision(anchor:PlaneAnchor):PlaneCollision {
        val resources=mutableListOf<Resource>()
        fun <T:Resource> own(resource:T):T { resource.toGlobal();resources+=resource;return resource }
        try {
            var supportShape:ShapeResource?=null
            val shapes=if(anchor.semantics.toString().contains("floor",true)) {
                val up=anchor.transform.quaternion.conjugate().rotateVector(Vector3(0f,1f,0f))
                val vertices=anchor.vertices.map { it.domain() }
                val support=MappedFloorPrisms.debrisSupport(vertices,anchor.indices,up.domain())
                val supportMesh=own(MeshResource.createWithMeshModel(MeshModel(support.vertices.map { it.sdk() },support.indices),name="DebrisFloor-${anchor.anchorUUID}"))
                supportShape=own(ShapeResource.createConvexMesh(supportMesh))
                val prisms=MappedFloorPrisms.build(vertices,anchor.indices,up.domain())
                require(prisms.isNotEmpty()) { "Empty mapped floor collision geometry" }
                prisms.mapIndexed { index,prism ->
                    val mesh=own(MeshResource.createWithMeshModel(MeshModel(prism.vertices.map { it.sdk() },prism.indices),name="Floor-${anchor.anchorUUID}-$index"))
                    own(ShapeResource.createConvexMesh(mesh))
                }
            } else {
                val indices=anchor.indices
                val triangles=indices+indices.chunked(3).flatMap { listOf(it[0],it[2],it[1]) }
                val mesh=own(MeshResource.createWithMeshModel(MeshModel(anchor.vertices,triangles),name="Plane-${anchor.anchorUUID}"))
                listOf(own(ShapeResource.createStaticMesh(mesh)))
            }
            val material=own(PhysicsMaterialResource(.6f,.5f,.12f))
            return PlaneCollision(shapes,material,resources.toList(),supportShape)
        } catch(error:Throwable) { resources.asReversed().forEach { it.close() };throw error }
    }
    private fun removePlane(id:UUID) {
        planeData.remove(id);planes.remove(id)?.let { planeIds-=it.id;it.destroy() }
        debrisGroundSupports.remove(id)?.destroy()
        measuredGrounds.remove(id)
        planeResources.remove(id)?.asReversed()?.forEach { it.close() };vm.planes(planes.size)
    }
    private fun discard(obj:ThrowableObject) {
        objects.remove(obj.entity.id)
        obj.staging.destroy(recursively=true)
        obj.entity.destroy(recursively=true)
    }
    /** Detached from every Scene: these bodies cannot participate in physics before impact. */
    private suspend fun stageFragments(obj:ThrowableObject,token:Long):Boolean {
        val started=SystemClock.elapsedRealtime()
        stagingRoot.addChild(obj.staging)
        var batchStarted=SystemClock.elapsedRealtimeNanos()
        for((index,template) in obj.prepared.fragments.withIndex()) {
            if(token!=generation || closed) return false
            val shard=requireNotNull(template.visual.clone(Entity.CloneOptions(recursive=true)))
            obj.staging.addChild(shard);shard.enabled=true
            shard.components.set(CollisionComponent(template.shapes,obj.prepared.physicsMaterial,collisionFilter=debrisFilter))
            shard.components.set(RigidBodyComponent(template.mass,RigidBodyMode.DYNAMIC).apply {
                isAffectedByGravity=true;collisionDetectionMode=CollisionDetectionMode.CONTINUOUS_SPECULATIVE
                linearDamping=0f;angularDamping=.01f
            })
            val velocity=PhysicsVelocityComponent(Vector3.ZERO,Vector3.ZERO)
            shard.components.set(velocity)
            obj.staged+=StagedFragment(template,shard,velocity)
            // Bound each Main-thread staging batch; yield() alone can immediately resume
            // ahead of input/render callbacks, especially for many-piece electronic items.
            if(SystemClock.elapsedRealtimeNanos()-batchStarted>=2_000_000) {
                delay(1);batchStarted=SystemClock.elapsedRealtimeNanos()
            }
        }
        if(token!=generation || closed) return false
        obj.ready=true
        Log.i("ShatterRealm","Staged ${obj.prepared.item.id}: ${obj.staged.size} off-scene fragments in ${SystemClock.elapsedRealtime()-started}ms")
        return true
    }
    override fun select(id:String) {
        val item=vm.item(id) ?: return
        val token=++generation
        vm.loading(true)
        scope.launch {
            var createdId:Long?=null
            runCatching {
                val prepared=assets.get(item)
                if(token!=generation || closed) return@launch
                check(planes.isNotEmpty()) { "Real system plane colliders are required before spawning" }
                objects.values.filter { !it.released && it.heldBy==null }.toList().forEach { discard(it) }
                check(vm.state.value.accepted && !vm.state.value.controllerActive)
                val entity=requireNotNull(prepared.intact.clone(Entity.CloneOptions(recursive=true)))
                val obj=ThrowableObject(vm.nextInstance(),prepared,entity)
                createdId=entity.id;objects[entity.id]=obj
                entity.enabled=false;bodyRoot.addChild(entity)
                if(!stageFragments(obj,token)) return@launch
                check(vm.state.value.accepted && !vm.state.value.controllerActive)
                // Center the actual collider geometry, not its potentially offset model pivot.
                // This is a one-shot placement; subsequent head movement does not move the item.
                val head=hmdProvider.latestData.hmdPose
                val level=Quat(Vector3(0f,1f,0f),UprightHeading.yaw(head.rotation.domain()).toFloat())
                val center=(prepared.pickupMin+prepared.pickupMax)*.5f
                val pivot=FrontSpawnLayout.pivot(head.position.domain(),head.rotation.domain(),center.domain()).sdk()
                entity.pose().position=bodyRoot.convertPositionFrom(pivot,null)
                entity.pose().quaternion=bodyRoot.convertRotationFrom(level,null)
                // Pending and held items are visual-only. Moving a many-shape kinematic actor
                // needlessly synchronizes its colliders and contacts every tracked-hand frame.
                // The complete collision/rigid body is activated once, on release.
                entity.enabled=true
                vm.loading(false);audio.ui("select")
            }.onFailure { error ->
                createdId?.let { objects[it] }?.let { discard(it) }
                if(token==generation && !closed) { vm.error();Log.e("ShatterRealm","Prepare ${item.id}",error) }
            }
        }
    }
    private fun onHands(data:HandTrackingData,now:Long,motions:List<Motion?>) {
        if(SystemClock.elapsedRealtimeNanos()-now>100_000_000) return
        lastHandNanos=now
        vm.hands(listOfNotNull(data.left,data.right).any { it[HandJoint.Index.PALM].position!=Vector3.ZERO })
        listOf(data.left to data.leftPinch,data.right to data.rightPinch).forEachIndexed { index,(pose,pinch) ->
            val hand=hands[index]
            if(pose==null || pose[HandJoint.Index.PALM].position==Vector3.ZERO) { hand.position=null;hand.motion=null;hand.pinchStrength=0f;hand.pinched=false;return@forEachIndexed }
            val joint=pose[HandJoint.Index.PALM]
            hand.position=joint.position;hand.rotation=joint.rotation
            hand.motion=motions[index]
            hand.pinchStrength=if(pinch?.isReady==true) pinch.pinchValue else 0f
            if(!vm.state.value.accepted || vm.state.value.controllerActive || vm.state.value.resetConfirmation) { hand.motion=null;return@forEachIndexed }
            val ready=pinch?.isReady==true
            val pinched=NearGrabPolicy.pinched(ready,hand.pinchStrength,hand.pinched)
            if(pinched && hand.held==null) {
                val points=listOfNotNull(joint.position,pinch?.takeIf { it.isReady }?.pinchPose?.position)
                objects.values.asSequence().filter { it.ready && !it.released && it.heldBy==null }
                    .map { obj -> obj to points.minOf { point ->
                        val local=obj.entity.convertPositionFrom(point,null).domain()
                        NearGrabPolicy.distanceSquared(local,obj.prepared.pickupMin.domain(),obj.prepared.pickupMax.domain())
                    } }.filter { NearGrabPolicy.inReach(it.second) }.minByOrNull { it.second }?.first?.let {
                    hand.held=it.entity.id;it.heldBy=index
                }
            }
            val held=hand.held?.let { objects[it] }
            if(held!=null) {
                // Visual motion is coalesced to the newest pose once per display frame.
                // Release is immediate and uses the last raw tracked pose and motion sample.
                if(ready && !pinched && hand.motion!=null) {
                    applyHeldPose(held,hand);release(held,hand)
                } else scheduleHeldPose()
            }
            if(ready) hand.pinched=pinched
        }
    }
    private fun release(obj:ThrowableObject,hand:HandState) {
        val motion=hand.motion ?: return
        val rootLinear=world.convertRotationFrom(Quat(),null).rotateVector(motion.linear.sdk())
        val angular=world.convertRotationFrom(Quat(),null).rotateVector(motion.angular.sdk())
        val linear=rootLinear+angular.cross(obj.entity.pose().quaternion.rotateVector(obj.prepared.mass.centerOfMass))
        obj.entity.components.set(CollisionComponent(obj.prepared.shapes,obj.prepared.physicsMaterial))
        obj.entity.components.set(RigidBodyComponent(obj.prepared.mass,RigidBodyMode.DYNAMIC).apply {
            isAffectedByGravity=true;collisionDetectionMode=CollisionDetectionMode.CONTINUOUS_DYNAMIC
            linearDamping=0f;angularDamping=0f
        })
        obj.entity.components.set(PhysicsVelocityComponent(linear,angular))
        obj.released=true;obj.heldBy=null;hand.held=null
        audio.material(obj.prepared.item.material,"throw")
        Log.i("ShatterRealm","Release ${obj.prepared.item.id} linear=$linear angular=$angular")
    }
    private fun collide(a:Long,b:Long,impulse:Float) {
        contactDebris(a,b,impulse);contactDebris(b,a,impulse)
        if(a in planeIds) fracture(b,a) else if(b in planeIds) fracture(a,b)
        if(a in fragments) debrisSound(a,impulse)
        if(b in fragments) debrisSound(b,impulse)
    }
    private fun contactDebris(id:Long,other:Long,impulse:Float) {
        val debris=fragments[id] ?: return
        debris.rest.contact()
        val movingOther=objects[other]?.released==true || fragments[other]?.sleeping==false
        if(debris.sleeping && movingOther && impulse>.0001f) {
            debris.entity.components.get<RigidBodyComponent>()?.apply {
                rigidBodyMode=RigidBodyMode.DYNAMIC;isAffectedByGravity=true
            }
            debris.entity.components.set(PhysicsVelocityComponent(Vector3.ZERO,Vector3.ZERO))
            debris.sleeping=false;debris.rest.wake()
        }
    }
    private suspend fun monitorRestingDebris() {
        var cursor=0
        while(!closed) {
            delay(50)
            val now=SystemClock.elapsedRealtime()
            val active=fragments.values.filter { !it.sleeping || now<it.verifyUntil }
            if(active.isEmpty()) continue
            repeat(minOf(48,active.size)) {
                val debris=active[cursor%active.size];cursor=(cursor+1)%active.size
                if(fragments[debris.entity.id]!==debris) return@repeat
                recoverGroundPenetration(debris)
                if(closed || fragments[debris.entity.id]!==debris || debris.sleeping) return@repeat
                val currentVelocity=debris.entity.components.get<PhysicsVelocityComponent>() ?: return@repeat
                val v=currentVelocity.linearVelocity;val a=currentVelocity.angularVelocity
                if(debris.rest.shouldSleep(v.x*v.x+v.y*v.y+v.z*v.z,a.x*a.x+a.y*a.y+a.z*a.z,now)) {
                    debris.entity.components.get<RigidBodyComponent>()?.apply {
                        rigidBodyMode=RigidBodyMode.KINEMATIC;isAffectedByGravity=false
                    }
                    // Kinematic bodies are still driven by PhysicsVelocityComponent. Remove the
                    // motion driver instead of leaving a stale low velocity that keeps drifting.
                    debris.entity.components.remove(PhysicsVelocityComponent::class.java)
                    debris.sleeping=true
                    // Native final pose synchronization can arrive after the sleep component update.
                    // Recheck briefly, then leave stable debris out of the hot loop.
                    debris.verifyUntil=now+1000
                }
            }
        }
    }
    private fun recoverGroundPenetration(debris:Debris) {
        val position=debris.entity.convertPositionTo(Vector3.ZERO,world)
        val ground=debris.groundId?.let { measuredGrounds[it] }
            ?: groundBelow(position)?.also { debris.groundId=it.id } ?: return
        val template=debris.prepared.fragments[debris.index]
        if((position-ground.point).dot(ground.normal)>template.supportRadius) return
        val rotation=debris.entity.convertRotationTo(Quat(),world)
        val localNormal=rotation.conjugate().rotateVector(ground.normal)
        val support=template.supportPoints.minOf { it.dot(localNormal) }
        val contact=FloorPenetrationRecovery.contactPosition(position.domain(),ground.point.domain(),ground.normal.domain(),support.toDouble()) ?: return
        // Replace the invalid native actor, not just its render transform. The logical shard remains
        // the same instance/index and retains the same appearance, mass and collision shape.
        val replacement=requireNotNull(template.visual.clone(Entity.CloneOptions(recursive=true))).apply {
            enabled=false
            pose().position=bodyRoot.convertPositionFrom(contact.sdk(),world)
            pose().quaternion=bodyRoot.convertRotationFrom(rotation,world)
            components.set(CollisionComponent(template.shapes,debris.prepared.physicsMaterial,collisionFilter=debrisFilter))
            components.set(RigidBodyComponent(template.mass,RigidBodyMode.KINEMATIC).apply {
                isAffectedByGravity=false;collisionDetectionMode=CollisionDetectionMode.CONTINUOUS_SPECULATIVE
                linearDamping=0f;angularDamping=.01f
            })
        }
        val previous=debris.entity
        fragments.remove(previous.id)
        debris.entity=replacement;debris.sleeping=true;debris.verifyUntil=SystemClock.elapsedRealtime()+1000
        fragments[replacement.id]=debris
        bodyRoot.addChild(replacement);replacement.enabled=true
        previous.destroy(recursively=true)
        debris.rest.contact();debris.rest.wake()
        Log.i("ShatterRealm","Grounded recovered ${debris.prepared.item.id} shard=${debris.index} depth=${(position-ground.point).dot(ground.normal)}m; collider retained")
    }
    private fun fracture(id:Long,planeId:Long) {
        val obj=objects[id] ?: return
        if(!obj.released || !obj.ready) return
        val started=SystemClock.elapsedRealtimeNanos()
        val pose=obj.entity.pose();val position=pose.position;val rotation=pose.quaternion
        val velocity=obj.entity.components.get<PhysicsVelocityComponent>() ?: return
        val linear=velocity.linearVelocity;val angular=velocity.angularVelocity
        val floor=planes.entries.firstOrNull { it.value.id==planeId }?.key?.let { planeData[it] }
            ?.takeIf { it.semantics.toString().contains("floor",true) }
        val floorContact=floor?.let { anchor ->
            anchor.indices.chunked(3).firstNotNullOfOrNull { t ->
                val a=anchor.vertices[t[0]];val n=(anchor.vertices[t[1]]-a).cross(anchor.vertices[t[2]]-a)
                val length=n.length()
                if(length<1e-7f) null else {
                    var normal=world.convertRotationFrom(anchor.transform.quaternion,null).rotateVector(n*(1f/length))
                    if(normal.y<0f) normal=normal*(-1f)
                    val point=world.convertPositionFrom(anchor.transform.position+anchor.transform.quaternion.rotateVector(a),null)
                    point to normal
                }
            }
        }
        var separation=0f
        if(!vm.fractured(obj.instance,obj.prepared.item.id,true,true)) return
        objects.remove(id)
        // Remove the intact collider before any fragment can enter physics, not just its rendering.
        obj.entity.destroy()
        fractureSounds.begin(obj.instance,SystemClock.elapsedRealtime())
        audio.material(obj.prepared.item.material,"shatter")
        obj.staged.forEachIndexed { index,piece ->
            val template=piece.template;val shard=piece.entity
            var shardPosition=position+rotation.rotateVector(template.offset)
            floorContact?.let { (point,normal) ->
                val localNormal=rotation.conjugate().rotateVector(normal)
                val support=template.supportPoints.minOf { it.dot(localNormal) }
                val correction=(.001f-((shardPosition-point).dot(normal)+support)).coerceAtLeast(0f)
                shardPosition+=normal*correction
                separation=maxOf(separation,correction)
            }
            shard.pose().position=shardPosition
            shard.pose().quaternion=rotation
            val comOffset=rotation.rotateVector(template.offset-obj.prepared.mass.centerOfMass)
            piece.velocity.linearVelocity=linear+angular.cross(comOffset)
            piece.velocity.angularVelocity=angular
            fragments[shard.id]=Debris(shard,obj.prepared,index,obj.instance,groundId=groundBelow(shardPosition)?.id)
        }
        obj.staging.enabled=true
        check(obj.staging.setParent(bodyRoot)) { "Cannot activate prepared fracture group" }
        val elapsed=(SystemClock.elapsedRealtimeNanos()-started)/1_000_000.0
        Log.i("ShatterRealm","Fractured ${obj.prepared.item.id}: ${obj.staged.size} persistent collidable fragments; total=${vm.state.value.counts.total}; prepared activation=${elapsed}ms; initial floor separation=${separation}m")
    }
    private fun debrisSound(id:Long,impulse:Float) {
        val debris=fragments[id] ?: return
        if(impulse>.0008f && fractureSounds.allowLanding(debris.instance,SystemClock.elapsedRealtime()))
            audio.material(debris.prepared.item.material,"fragment-land",sqrt(impulse*8).coerceAtMost(1f))
    }
    override fun reset() {
        val resetStart=SystemClock.elapsedRealtime()
        generation++
        vm.saveScene(emptyList());pendingRestore.clear()
        hands.forEach { it.held=null;it.motion=null }
        // One recursive native destruction instead of one round-trip per body.
        bodyRoot.destroy(recursively=true)
        stagingRoot.destroy(recursively=true)
        stagingRoot=Entity().apply { setName("OffSceneFragmentStaging");enabled=false }
        bodyRoot=Entity().apply { setName("ThrowableAndDebrisRoot");world.addChild(this) }
        objects.clear();fragments.clear();fractureSounds.clear();audio.stopMaterialSounds();vm.loading(false)
        Log.i("ShatterRealm","Explicit reset: intact objects and fragments cleared in ${SystemClock.elapsedRealtime()-resetStart}ms")
    }
    override fun uiSound(name:String) { audio.ui(name) }
    private fun snapshot(entity:Entity,item:Item,instance:Long,index:Int,released:Boolean):BodySnapshot {
        val velocity=entity.components.get<PhysicsVelocityComponent>()
        val globalRotation=world.convertRotationTo(Quat(),null)
        return BodySnapshot(item,instance,index,entity.convertPositionTo(Vector3.ZERO,null).domain(),
            entity.convertRotationTo(Quat(),null).domain(),
            globalRotation.rotateVector(velocity?.linearVelocity ?: Vector3(0f)).domain(),
            globalRotation.rotateVector(velocity?.angularVelocity ?: Vector3(0f)).domain(),released,fragments[entity.id]?.sleeping==true)
    }
    private fun saveScene() {
        val bodies=objects.values.map { snapshot(it.entity,it.prepared.item,it.instance,-1,it.released) }+
            fragments.values.map { snapshot(it.entity,it.prepared.item,it.instance,it.index,true) }
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
            entity.enabled=false;bodyRoot.addChild(entity)
            entity.pose().position=world.convertPositionFrom(body.position.sdk(),null)
            entity.pose().quaternion=world.convertRotationFrom(body.rotation.sdk(),null)
            if(template==null) {
                val obj=ThrowableObject(body.instance,prepared,entity,body.released)
                objects[entity.id]=obj
                if(!stageFragments(obj,token)) return
            }
            if(body.released || template!=null) {
            entity.components.set(CollisionComponent(template?.shapes ?: prepared.shapes,prepared.physicsMaterial,
                collisionFilter=if(template==null) CollisionFilter.COLLISION_FILTER_DEFAULT else debrisFilter))
            entity.components.set(RigidBodyComponent(template?.mass ?: prepared.mass,if(body.released && !body.sleeping) RigidBodyMode.DYNAMIC else RigidBodyMode.KINEMATIC).apply {
                isAffectedByGravity=body.released && !body.sleeping
                collisionDetectionMode=if(template==null) CollisionDetectionMode.CONTINUOUS_DYNAMIC else CollisionDetectionMode.CONTINUOUS_SPECULATIVE
                linearDamping=0f;angularDamping=.01f
            })
            val inverseWorld=world.convertRotationFrom(Quat(),null)
            if(!body.sleeping) entity.components.set(PhysicsVelocityComponent(inverseWorld.rotateVector(body.linear.sdk()),inverseWorld.rotateVector(body.angular.sdk())))
            }
            if(template!=null) fragments[entity.id]=Debris(entity,prepared,body.fragmentIndex,body.instance,body.sleeping)
            entity.enabled=true
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
        handProvider.removeListener(handListener);handProvider.stop();hmdProvider.stop()
        controllers.removeControllerActionListener(controllerListener);controllers.stop()
        subscriptions.forEach { it.cancel() };subscriptions.clear();PlaneTrackingManager.stop()
        trackedFrames.close();scope.cancel();world.destroy();stagingRoot.destroy(recursively=true);objects.clear();fragments.clear();assets.close();audio.close()
        planeResources.values.forEach { resources -> resources.asReversed().forEach { it.close() } };planeResources.clear()
        debrisGroundSupports.clear()
        measuredGrounds.clear()
    }
}
