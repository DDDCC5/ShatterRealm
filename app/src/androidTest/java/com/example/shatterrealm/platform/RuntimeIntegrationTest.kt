package com.example.shatterrealm.platform

import android.content.Intent
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.shatterrealm.ui.experience.*
import com.pico.spatial.core.math.*
import com.pico.spatial.sense.plane.PlaneTrackingManager
import com.pico.spatial.tracking.hand.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/** Synthetic tracked frames test the real SDK render/collision path; this is not real-hand validation. */
@RunWith(AndroidJUnit4::class)
class RuntimeIntegrationTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private fun <T> main(block:()->T):T {
        val result=AtomicReference<T>();instrumentation.runOnMainSync { result.set(block()) };return result.get()
    }
    private fun waitFor(message:String,seconds:Int=30,condition:()->Boolean) {
        val until=SystemClock.elapsedRealtime()+seconds*1000
        while(SystemClock.elapsedRealtime()<until) { if(condition()) return;Thread.sleep(40) }
        fail(message)
    }
    @Test fun nativeImporterBuildsClosedFracturePackageOnArm64() {
        val context=instrumentation.targetContext
        val source=java.io.File(context.cacheDir,"integration-cup.glb")
        context.assets.open("models/glass-cup/intact.glb").use { input -> source.outputStream().use { input.copyTo(it) } }
        val item=CustomModelImporter.import(context,source)
        val directory=java.io.File(java.net.URI(item.baseUri))
        val metadata=org.json.JSONObject(java.io.File(directory,"metadata.json").readText())
        assertTrue(metadata.getJSONArray("fragmentRecords").length()>=20)
        assertTrue(java.io.File(directory,"colliders.json").isFile)
        assertTrue(java.io.File(directory,"intact.glb").length()>0)
        assertEquals("custom",item.material)
    }
    @Test fun approvedModelsFractureAgainstExistingSystemPlane() {
        val app=instrumentation.targetContext.applicationContext as SpatialApplication
        instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitFor("Spatial runtime was not attached") { main { app.activeRuntime!=null } }
        val runtime=main { app.activeRuntime!! }
        val field=RealmRuntime::class.java.getDeclaredField("vm").apply { isAccessible=true }
        val vm=main { field.get(runtime) as ExperienceViewModel }
        val planeField=RealmRuntime::class.java.getDeclaredField("planeData").apply { isAccessible=true }
        @Suppress("UNCHECKED_CAST")
        fun floor()=main { (planeField.get(runtime) as Map<java.util.UUID,com.pico.spatial.sense.plane.PlaneAnchor>).values.firstOrNull { it.semantics.toString().contains("floor",true) } }
        waitFor("No actual semantic system floor",60) { floor()!=null }
        // Initial Stage layout/recenter can settle after the first anchor callback.
        Thread.sleep(1800)
        val floorAnchor=floor()!!
        val candidates=mutableListOf<Vector3>()
        floorAnchor.indices.chunked(3).forEach { tri ->
            val a=floorAnchor.vertices[tri[0]];val b=floorAnchor.vertices[tri[1]];val c=floorAnchor.vertices[tri[2]]
            for(i in 1..6) for(j in 1..6) if(i+j<8) {
                val local=a*(i/8f)+b*(j/8f)+c*(1f-(i+j)/8f)
                candidates+=floorAnchor.transform.position+floorAnchor.transform.quaternion.rotateVector(local)
            }
        }
        // Validate sampled positions against the actual native floor collider, including boundary triangles.
        var reachable=emptyList<Vector3>()
        waitFor("System floor has no reachable collision surface",15) {
            reachable=main {
                val f=RealmRuntime::class.java.getDeclaredField("planes").apply { isAccessible=true }
                val floorEntity=(f.get(runtime) as Map<*,*>)[floorAnchor.anchorUUID] as? com.pico.spatial.core.ecs.Entity
                if(reachable.isEmpty() && floorEntity!=null) {
                    android.util.Log.i("ShatterRealm","FLOOR_FRAME expected=${floorAnchor.transform.position} actual=${floorEntity.convertPositionTo(Vector3.ZERO,null)} root=${runtime.world.convertPositionTo(Vector3.ZERO,null)}")
                }
                candidates.filter { point ->
                    runtime.world.scene!!.rayCast(runtime.world.convertPositionFrom(point+Vector3(0f,.5f,0f),null),
                        Vector3(0f,-1f,0f),1f,com.pico.spatial.core.ecs.simulation.CollisionCastHitMode.ALL,
                        com.pico.spatial.core.ecs.simulation.CollisionGroup(),runtime.world).results.any { it.entity.id==floorEntity?.id }
                }
            }
            reachable.isNotEmpty()
        }
        candidates.clear();candidates.addAll(reachable)
        val destinations=mutableListOf<Vector3>()
        repeat(15) {
            val chosen=candidates.maxBy { candidate ->
                if(destinations.isEmpty()) 0f else destinations.minOf { old -> val d=candidate-old;d.x*d.x+d.y*d.y+d.z*d.z }
            }
            destinations+=chosen;candidates.remove(chosen)
        }
        val retain=InstrumentationRegistry.getArguments().getString("validate_floor_retention")=="true"
        val isolate=InstrumentationRegistry.getArguments().getString("isolate_each_item")=="true"
        val dropSpeed=InstrumentationRegistry.getArguments().getString("retention_speed")?.toFloat() ?: 3f
        val spin=InstrumentationRegistry.getArguments().getString("retention_spin")?.toFloat() ?: 0f
        val handHeight=if(retain) (if(isolate) 1.6f else 1f)+dropSpeed*.1f else .7f
        if(retain) {
            val triangle=floorAnchor.indices.chunked(3).maxBy { t ->
                val a=floorAnchor.vertices[t[1]]-floorAnchor.vertices[t[0]];val b=floorAnchor.vertices[t[2]]-floorAnchor.vertices[t[0]]
                val x=a.y*b.z-a.z*b.y;val y=a.z*b.x-a.x*b.z;val z=a.x*b.y-a.y*b.x
                x*x+y*y+z*z
            }
            val center=triangle.map { floorAnchor.vertices[it] }.reduce { a,b -> a+b }*(1f/3f)
            destinations.indices.forEach { destinations[it]=floorAnchor.transform.position+floorAnchor.transform.quaternion.rotateVector(center) }
        }
        val point=destinations.first()+Vector3(0f,handHeight,0f)
        val listenerField=RealmRuntime::class.java.getDeclaredField("handListener").apply { isAccessible=true }
        @Suppress("UNCHECKED_CAST")
        val listener=listenerField.get(runtime) as com.pico.spatial.tracking.DataProvider.DataListener<HandTrackingData>
        // A connected headset also emits real frames; isolate the synthetic source in this test.
        val providerField=RealmRuntime::class.java.getDeclaredField("handProvider").apply { isAccessible=true }
        val provider=providerField.get(runtime) as HandTrackingProvider
        main { provider.removeListener(listener) }
        val grabHand=InstrumentationRegistry.getArguments().getString("grab_hand")?.toInt() ?: 0
        val dualHands=InstrumentationRegistry.getArguments().getString("dual_hands")=="true"
        val pinch=java.util.concurrent.atomic.AtomicReference(0f)
        val speed=java.util.concurrent.atomic.AtomicReference(0f)
        val movementStart=java.util.concurrent.atomic.AtomicLong(0L)
        val position=java.util.concurrent.atomic.AtomicReference(point)
        val running=java.util.concurrent.atomic.AtomicBoolean(true)
        val stream=Thread {
            while(running.get()) {
                val frame={
                    val elapsed=(SystemClock.elapsedRealtimeNanos()-movementStart.get())/1e9f
                    val motionTime=if(retain) elapsed.coerceAtMost(.1f) else elapsed
                    val p=position.get()+Vector3(0f,-motionTime*speed.get(),0f)
                    val q=if(retain) Quat(Vector3(0f,0f,1f),.65f+if(movementStart.get()==0L) 0f else spin*motionTime) else Quat()
                    val joints=HandJoint.Index.values().map { HandJoint(p,q,it) }
                    // End this synthetic gesture at its planned release, never drive a held hand through the floor
                    // while the render thread is busy. The released body then uses actual native physics.
                    val value=if(retain && speed.get()>0 && elapsed>=.1f) 0f else pinch.get()
                    val activePose=HandPose(joints)
                    val otherPoint=p+Vector3(.6f,0f,0f)
                    val otherPose=if(dualHands) HandPose(HandJoint.Index.values().map { HandJoint(otherPoint,Quat(),it) }) else null
                    val activePinch=HandPinch(PinchPose(p,Quat()),value,true)
                    val otherPinch=if(dualHands) HandPinch(PinchPose(otherPoint,Quat()),0f,true) else null
                    listener.onProvideData(if(grabHand==0)
                        HandTrackingData(activePose,otherPose,SystemClock.elapsedRealtimeNanos(),activePinch,otherPinch)
                        else HandTrackingData(otherPose,activePose,SystemClock.elapsedRealtimeNanos(),otherPinch,activePinch))
                }
                if(retain) main(frame) else frame()
                Thread.sleep(11)
            }
        }.apply { isDaemon=true;start() }
        try {
            waitFor("Synthetic tracked stream did not arrive") { vm.state.value.handsTracked }
            main { vm.controller(false);vm.event(ExperienceEvent.Agree) }
            waitFor("Catalog startup did not settle") { !vm.state.value.busy }
            val handsField=RealmRuntime::class.java.getDeclaredField("hands").apply { isAccessible=true }
            fun held()=main {
                val hand=(handsField.get(runtime) as Array<*>)[grabHand]!!
                hand.javaClass.getDeclaredField("held").apply { isAccessible=true }.get(hand)!=null
            }
            val allItems=vm.state.value.items.filter { !it.custom }
            assertEquals(15,allItems.size)
            val only=InstrumentationRegistry.getArguments().getString("only_item")
            val repeatCount=InstrumentationRegistry.getArguments().getString("repeat_item")?.toInt() ?: 1
            val items=if(only!=null && repeatCount>1) List(repeatCount) { allItems.first { it.id==only } }
                else allItems.filter { only==null || it.id==only }
            val expectedTotal=if(isolate) 1 else items.size
            val verifyRestore=InstrumentationRegistry.getArguments().getString("validate_restore")=="true"
            val fast=InstrumentationRegistry.getArguments().getString("fast_mode")=="true"
            var previewPoint:Vector3?=null
            if(only!=null && !fast) {
                val camera=main { com.pico.spatial.core.ecs.Entity().apply {
                    runtime.world.addChild(this)
                    components.set(com.pico.spatial.core.ecs.AnchorComponent(trackingMode=com.pico.spatial.core.ecs.AnchorComponent.TrackingMode.ONCE))
                } }
                Thread.sleep(1500)
                previewPoint=main {
                    val p=camera.convertPositionTo(Vector3.ZERO,null)
                    val q=camera.convertRotationTo(Quat(),null)
                    android.util.Log.i("ShatterRealm","PREVIEW_CAMERA_GLOBAL position=$p rotation=$q")
                    camera.destroy()
                    p+q.rotateVector(Vector3(.15f,-.15f,-.45f))
                }
            }
            var expectedShards=0
            items.forEachIndexed { index,item ->
                if(isolate && index>0) {
                    main { vm.hands(true);vm.controller(false);vm.event(ExperienceEvent.Reset);vm.event(ExperienceEvent.ConfirmReset) }
                    expectedShards=0
                }
                val expectedCount=if(isolate) 1 else index+1
                val target=previewPoint ?: (destinations[index]+Vector3(0f,handHeight,0f))
                pinch.set(0f);position.set(target)
                waitFor("Hand pose did not update") { main {
                    val hand=(handsField.get(runtime) as Array<*>)[grabHand]!!
                    hand.javaClass.getDeclaredField("position").apply { isAccessible=true }.get(hand)==target
                } }
                if(dualHands) { pinch.set(.5f);Thread.sleep(250) }
                main { vm.hands(true);vm.controller(false);vm.event(ExperienceEvent.Select(item.id)) }
                waitFor("Asset load timed out for ${item.id}",120) { !vm.state.value.busy }
                assertFalse("Asset load failed for ${item.id}",vm.state.value.loadError)
                if(InstrumentationRegistry.getArguments().getString("validate_front_spawn")=="true") {
                    val spawn=main {
                        val field=RealmRuntime::class.java.getDeclaredField("objects").apply { isAccessible=true }
                        val obj=(field.get(runtime) as Map<*,*>).values.first()!!
                        val entity=obj.javaClass.getDeclaredField("entity").apply { isAccessible=true }.get(obj) as com.pico.spatial.core.ecs.Entity
                        val prepared=obj.javaClass.getDeclaredField("prepared").apply { isAccessible=true }.get(obj) as PreparedItem
                        val hmd=RealmRuntime::class.java.getDeclaredField("hmdProvider").apply { isAccessible=true }.get(runtime) as com.pico.spatial.tracking.hmd.HMDTrackingProvider
                        val head=hmd.latestData.hmdPose
                        val actual=entity.convertPositionTo((prepared.pickupMin+prepared.pickupMax)*.5f,null)
                        val expected=com.example.shatterrealm.domain.usecase.FrontSpawnLayout.center(
                            com.example.shatterrealm.domain.model.V3(head.position.x.toDouble(),head.position.y.toDouble(),head.position.z.toDouble()),
                            com.example.shatterrealm.domain.model.Q4(head.rotation.x.toDouble(),head.rotation.y.toDouble(),head.rotation.z.toDouble(),head.rotation.w.toDouble()))
                        val delta=actual-Vector3(expected.x.toFloat(),expected.y.toFloat(),expected.z.toFloat())
                        // HMD may move slightly after appearance; stationary emulator is the strict placement check.
                        assertTrue("Item did not appear in front of HMD: actual=$actual expected=$expected",kotlin.math.sqrt(delta.x*delta.x+delta.y*delta.y+delta.z*delta.z)<.12f)
                        instrumentation.sendStatus(0,android.os.Bundle().apply { putString("stream","\nFRONT_SPAWN_VERIFIED item=${item.id} hand=$grabHand center=$actual head=${head.position}\n") })
                        entity.convertPositionTo(Vector3.ZERO,null)
                    }
                    position.set(spawn)
                    Thread.sleep(250)
                }
                // Offset along the item's local width, not the global X axis: the item is rotated
                // by the tracked palm, so a global offset can be below its thin underside.
                val grabOffset=InstrumentationRegistry.getArguments().getString("grab_offset")?.toFloat() ?: 0f
                val pickupRotation=if(InstrumentationRegistry.getArguments().getString("validate_front_spawn")=="true") main {
                    val f=RealmRuntime::class.java.getDeclaredField("objects").apply { isAccessible=true }
                    val obj=(f.get(runtime) as Map<*,*>).values.first()!!
                    val entity=obj.javaClass.getDeclaredField("entity").apply { isAccessible=true }.get(obj) as com.pico.spatial.core.ecs.Entity
                    entity.convertRotationTo(Quat(),null)
                } else if(retain) Quat(Vector3(0f,0f,1f),.65f) else Quat()
                position.set(position.get()+pickupRotation.rotateVector(Vector3(grabOffset,0f,0f)))
                pinch.set(InstrumentationRegistry.getArguments().getString("grab_strength")?.toFloat() ?: 1f)
                waitFor("Could not grab ${item.id}",15) { held() }
                instrumentation.sendStatus(0,android.os.Bundle().apply { putString("stream","\nGRAB_VERIFIED item=${item.id} hand=$grabHand dualHands=$dualHands strength=${pinch.get()} localOffsetM=$grabOffset\n") })
                val holdSeconds=InstrumentationRegistry.getArguments().getString("hold_motion_seconds")?.toInt() ?: 0
                if(holdSeconds>0) {
                    val holdStart=position.get()
                    val begin=SystemClock.elapsedRealtime()
                    val frameTimes=java.util.concurrent.CopyOnWriteArrayList<Long>()
                    val choreographer=main { android.view.Choreographer.getInstance() }
                    val callback=object:android.view.Choreographer.FrameCallback {
                        override fun doFrame(time:Long) {
                            frameTimes.add(time)
                            if(SystemClock.elapsedRealtime()-begin<holdSeconds*1000L) choreographer.postFrameCallback(this)
                        }
                    }
                    val physics=main {
                        val f=RealmRuntime::class.java.getDeclaredField("objects").apply { isAccessible=true }
                        val obj=(f.get(runtime) as Map<*,*>).values.first()!!
                        val entity=obj.javaClass.getDeclaredField("entity").apply { isAccessible=true }.get(obj) as com.pico.spatial.core.ecs.Entity
                        choreographer.postFrameCallback(callback)
                        (entity.components.get<com.pico.spatial.core.ecs.CollisionComponent>()!=null) to
                            (entity.components.get<com.pico.spatial.core.ecs.RigidBodyComponent>()!=null)
                    }
                    instrumentation.sendStatus(0,android.os.Bundle().apply { putString("stream","\nHELD_PHYSICS collision=${physics.first} rigidBody=${physics.second}\n") })
                    if(InstrumentationRegistry.getArguments().getString("expect_visual_hold")=="true") {
                        assertFalse("Held models must not move compound collision actors",physics.first)
                        assertFalse("Held models must not run rigid body simulation",physics.second)
                    }
                    var visibleMotion=false
                    var nextMotionCheck=begin+200
                    android.os.Trace.beginAsyncSection("ShatterRealm.HeldMove",1337)
                    while(SystemClock.elapsedRealtime()-begin<holdSeconds*1000L) {
                        val t=(SystemClock.elapsedRealtime()-begin)/1000f
                        position.set(holdStart+Vector3(.2f*kotlin.math.sin(t*3f),.08f*kotlin.math.sin(t*4f),0f))
                        assertTrue("Held object was dropped while moving",held())
                        if(SystemClock.elapsedRealtime()>=nextMotionCheck) {
                            nextMotionCheck+=200
                            visibleMotion=visibleMotion || main {
                                val f=RealmRuntime::class.java.getDeclaredField("objects").apply { isAccessible=true }
                                val obj=(f.get(runtime) as Map<*,*>).values.first()!!
                                val entity=obj.javaClass.getDeclaredField("entity").apply { isAccessible=true }.get(obj) as com.pico.spatial.core.ecs.Entity
                                val delta=entity.convertPositionTo(Vector3.ZERO,null)-holdStart
                                delta.x*delta.x+delta.y*delta.y+delta.z*delta.z>.0004f
                            }
                        }
                        Thread.sleep(16)
                    }
                    android.os.Trace.endAsyncSection("ShatterRealm.HeldMove",1337)
                    assertTrue("Held visual did not follow hand motion",visibleMotion)
                    main { choreographer.removeFrameCallback(callback) }
                    val gaps=frameTimes.zipWithNext { a,b -> (b-a)/1e6 }.sorted()
                    instrumentation.sendStatus(0,android.os.Bundle().apply { putString("stream","\nHELD_MOVE frames=${frameTimes.size} durationMs=${SystemClock.elapsedRealtime()-begin} p95FrameMs=${gaps.getOrNull((gaps.size*.95).toInt())} maxFrameMs=${gaps.lastOrNull()}\n") })
                    position.set(holdStart)
                    Thread.sleep(250)
                }
                main {
                    val f=RealmRuntime::class.java.getDeclaredField("objects").apply { isAccessible=true }
                    val obj=(f.get(runtime) as Map<*,*>).values.first()!!
                    val pieces=obj.javaClass.getDeclaredField("staged").apply { isAccessible=true }.get(obj) as List<*>
                    expectedShards+=pieces.size
                    assertTrue("Fragments must already be staged before throw",pieces.isNotEmpty())
                    pieces.forEach { piece ->
                        val entity=piece!!.javaClass.getDeclaredField("entity").apply { isAccessible=true }.get(piece) as com.pico.spatial.core.ecs.Entity
                        assertNull("Staged fragments must not enter physics/render scene",entity.scene)
                        assertFalse(entity.enabled)
                        assertNotNull(entity.components.get<com.pico.spatial.core.ecs.RigidBodyComponent>())
                        assertNotNull(entity.components.get<com.pico.spatial.core.ecs.CollisionComponent>())
                    }
                    android.util.Log.i("ShatterRealm","INTEGRATION_STAGING_VERIFIED: ${pieces.size} configured off-scene bodies")
                }
                if(InstrumentationRegistry.getArguments().getString("validate_pending_reset")=="true") {
                    main { vm.hands(true);vm.controller(false);vm.event(ExperienceEvent.Reset);vm.event(ExperienceEvent.ConfirmReset) }
                    val staging=RealmRuntime::class.java.getDeclaredField("stagingRoot").apply { isAccessible=true }
                    assertTrue(main { (staging.get(runtime) as com.pico.spatial.core.ecs.Entity).getChildren().isEmpty() })
                    assertEquals(0,vm.state.value.counts.total)
                    android.util.Log.i("ShatterRealm","INTEGRATION_PENDING_RESET_VERIFIED")
                    return
                }
                if(previewPoint!=null) {
                    main {
                        val objectsField=RealmRuntime::class.java.getDeclaredField("objects").apply { isAccessible=true }
                        val objects=objectsField.get(runtime) as Map<*,*>
                        objects.values.forEach { value ->
                            val entity=value!!.javaClass.getDeclaredField("entity").apply { isAccessible=true }.get(value) as com.pico.spatial.core.ecs.Entity
                            entity.components.set(com.pico.spatial.core.ecs.AnchorComponent().apply { positionOffset=Vector3(.15f,-.15f,-.45f) })
                            android.util.Log.i("ShatterRealm","VISUAL_BOUNDS ${entity.getVisualBounds(runtime.world)}")
                            fun inspect(node:com.pico.spatial.core.ecs.Entity) {
                                val model=node.components.get<com.pico.spatial.core.ecs.ModelComponent>()
                                android.util.Log.i("ShatterRealm","VISUAL_NODE ${node.getName()} enabled=${node.enabled} model=${model!=null} visible=${model?.isVisible} transform=${node.pose()}")
                                node.getChildren().forEach { inspect(it) }
                            }
                            inspect(entity)
                        }
                    }
                    android.util.Log.i("ShatterRealm","INTEGRATION_PREVIEW_READY: $only metric model at camera-relative tracked pose")
                    Thread.sleep(8000)
                    main {
                        val f=RealmRuntime::class.java.getDeclaredField("objects").apply { isAccessible=true }
                        (f.get(runtime) as Map<*,*>).values.forEach { value ->
                            val entity=value!!.javaClass.getDeclaredField("entity").apply { isAccessible=true }.get(value) as com.pico.spatial.core.ecs.Entity
                            entity.components.remove(com.pico.spatial.core.ecs.AnchorComponent::class.java)
                        }
                    }
                    // Preview anchoring is test-only; physical throws use the unchanged tracked world pose.
                    position.set(destinations[index]+Vector3(0f,.7f,0f))
                    Thread.sleep(400)
                    movementStart.set(SystemClock.elapsedRealtimeNanos());speed.set(.8f)
                    Thread.sleep(150)
                } else if(retain) {
                    movementStart.set(SystemClock.elapsedRealtimeNanos());speed.set(dropSpeed);Thread.sleep(100)
                } else Thread.sleep(60)
                val releaseAt=SystemClock.elapsedRealtime()
                pinch.set(0f)
                waitFor("Could not release ${item.id}",15) { !held() }
                waitFor("No plane fracture for ${item.id}",60) { vm.state.value.counts.total==expectedCount }
                val fractureMs=SystemClock.elapsedRealtime()-releaseAt
                instrumentation.sendStatus(0,android.os.Bundle().apply { putString("stream","\nTHROW_TIMING ${item.id} number=${index+1} releaseToFractureMs=$fractureMs\n") })
                InstrumentationRegistry.getArguments().getString("max_fracture_ms")?.toLong()?.let {
                    assertTrue("Fracture took ${fractureMs}ms",fractureMs<=it)
                }
                speed.set(0f)
                assertEquals(if(repeatCount>1) index+1 else 1,vm.state.value.counts.byItem[item.id])
                if(retain) {
                    Thread.sleep(5000)
                    val allHeights=mutableListOf<Float>()
                    fun sampleDepths():List<Float> {
                      allHeights.clear()
                      return main {
                        val f=RealmRuntime::class.java.getDeclaredField("fragments").apply { isAccessible=true }
                        (f.get(runtime) as Map<*,*>).values.mapNotNull { d ->
                            val entity=d!!.javaClass.getDeclaredField("entity").apply { isAccessible=true }.get(d) as com.pico.spatial.core.ecs.Entity
                            assertNotNull("Retained shard lost its collision",entity.components.get<com.pico.spatial.core.ecs.CollisionComponent>())
                            val point=floorAnchor.transform.quaternion.conjugate().rotateVector(entity.convertPositionTo(Vector3.ZERO,null)-floorAnchor.transform.position)
                            val prepared=d.javaClass.getDeclaredField("prepared").apply { isAccessible=true }.get(d) as PreparedItem
                            val fragmentIndex=d.javaClass.getDeclaredField("index").apply { isAccessible=true }.getInt(d)
                            val meshRotation=entity.convertRotationTo(Quat(),null)
                            if(InstrumentationRegistry.getArguments().getString("validate_front_spawn")=="true") {
                                // Front placement may be far from the test's original target triangle.
                                // Room relocalization can update that old snapshot while the item is held.
                                // Check each shard against its current measured support plane instead.
                                val gf=RealmRuntime::class.java.getDeclaredField("measuredGrounds").apply { isAccessible=true }
                                val gid=d.javaClass.getDeclaredField("groundId").apply { isAccessible=true }.get(d)
                                val ground=requireNotNull((gf.get(runtime) as Map<*,*>)[gid]) { "Shard has no current measured ground" }
                                val gp=ground.javaClass.getDeclaredField("point").apply { isAccessible=true }.get(ground) as Vector3
                                val gn=ground.javaClass.getDeclaredField("normal").apply { isAccessible=true }.get(ground) as Vector3
                                val delta=entity.convertPositionTo(Vector3.ZERO,runtime.world)-gp
                                val normal=entity.convertRotationTo(Quat(),runtime.world).conjugate().rotateVector(gn)
                                val support=prepared.fragments[fragmentIndex].supportPoints.minOf { it.x*normal.x+it.y*normal.y+it.z*normal.z }
                                val height=delta.x*gn.x+delta.y*gn.y+delta.z*gn.z+support
                                allHeights+=height
                                if(height<-.03f) android.util.Log.i("ShatterRealm","CURRENT_GROUND_DEPTH shard=$fragmentIndex height=$height ground=$gp normal=$gn")
                                return@mapNotNull height
                            }
                            fun supportHeight(normal:Vector3,pivotHeight:Float):Float {
                                val meshNormal=meshRotation.conjugate().rotateVector(floorAnchor.transform.quaternion.rotateVector(normal))
                                return pivotHeight+prepared.fragments[fragmentIndex].supportPoints.minOf { it.x*meshNormal.x+it.y*meshNormal.y+it.z*meshNormal.z }
                            }
                            floorAnchor.indices.chunked(3).firstNotNullOfOrNull { t ->
                                val a=floorAnchor.vertices[t[0]];val u=floorAnchor.vertices[t[1]]-a;val v=floorAnchor.vertices[t[2]]-a
                                var n=Vector3(u.y*v.z-u.z*v.y,u.z*v.x-u.x*v.z,u.x*v.y-u.y*v.x)
                                val length=kotlin.math.sqrt(n.x*n.x+n.y*n.y+n.z*n.z)
                                if(length<1e-7f) null else {
                                    n=n*(1f/length);if(floorAnchor.transform.quaternion.rotateVector(n).y<0f) n=n*(-1f)
                                    val p=point-a;val height=supportHeight(n,p.x*n.x+p.y*n.y+p.z*n.z)
                                    allHeights+=height
                                    if(height<-.03f) {
                                        val gf=RealmRuntime::class.java.getDeclaredField("measuredGrounds").apply { isAccessible=true }
                                        val gid=d.javaClass.getDeclaredField("groundId").apply { isAccessible=true }.get(d)
                                        val g=(gf.get(runtime) as Map<*,*>)[gid]
                                        val gp=g?.javaClass?.getDeclaredField("point")?.apply { isAccessible=true }?.get(g)
                                        val gn=g?.javaClass?.getDeclaredField("normal")?.apply { isAccessible=true }?.get(g)
                                        val sleeping=d.javaClass.getDeclaredField("sleeping").apply { isAccessible=true }.getBoolean(d)
                                        android.util.Log.i("ShatterRealm","RETENTION_REFERENCE item=${prepared.item.id} shard=$fragmentIndex supportHeight=$height sleeping=$sleeping velocity=${entity.components.get<com.pico.spatial.core.ecs.PhysicsVelocityComponent>()?.linearVelocity} root=${runtime.world.convertPositionTo(Vector3.ZERO,null)} anchor=${floorAnchor.transform.position} assignedGround=$gp normal=$gn")
                                    }
                                    true
                                }
                            }
                            floorAnchor.indices.chunked(3).firstNotNullOfOrNull { t ->
                                val a=floorAnchor.vertices[t[0]];val u=floorAnchor.vertices[t[1]]-a;val v=floorAnchor.vertices[t[2]]-a;val p=point-a
                                fun dot(a:Vector3,b:Vector3)=a.x*b.x+a.y*b.y+a.z*b.z
                                val uu=dot(u,u);val uv=dot(u,v);val vv=dot(v,v);val d=uu*vv-uv*uv
                                if(d<=1e-12f) null else {
                                    val bu=(vv*dot(u,p)-uv*dot(v,p))/d;val bv=(uu*dot(v,p)-uv*dot(u,p))/d
                                    if(bu<-.002f || bv<-.002f || bu+bv>1.002f) null else {
                                        var n=Vector3(u.y*v.z-u.z*v.y,u.z*v.x-u.x*v.z,u.x*v.y-u.y*v.x)
                                        n=n*(1f/kotlin.math.sqrt(dot(n,n)))
                                        if(floorAnchor.transform.quaternion.rotateVector(n).y<0f) n=n*(-1f)
                                        supportHeight(n,dot(p,n))
                                    }
                                }
                            }
                        }
                      }
                    }
                    var depths=sampleDepths()
                    if(depths.any { it<-.03f } || allHeights.any { it<-.03f }) {
                        // A snapshot can land between native contact and its recovery. Reject sustained
                        // penetration, allowing the next frames to complete collision resolution.
                        waitFor("Fragments did not recover from floor penetration",2) {
                            depths=sampleDepths()
                            depths.none { it<-.03f } && allHeights.none { it<-.03f }
                        }
                    }
                    instrumentation.sendStatus(0,android.os.Bundle().apply { putString("stream","\nFLOOR_RETENTION ${item.id} mappedShards=${depths.size} belowFloor=${depths.count { it<-.03f }} minimumHeightM=${depths.minOrNull()} totalShards=${allHeights.size} allBelowFloor=${allHeights.count { it<-.03f }} allMinimumHeightM=${allHeights.minOrNull()}\n") })
                    assertEquals("Recovery must preserve every shard",expectedShards,allHeights.size)
                    assertTrue("Fragments fell through mapped floor: $depths",depths.none { it<-.03f })
                    assertTrue("Fragments continued falling outside sparse floor mesh: $allHeights",allHeights.none { it<-.03f })
                }
                if(verifyRestore && index==0) {
                    val verifySleep=InstrumentationRegistry.getArguments().getString("validate_sleep")=="true"
                    val debrisField=RealmRuntime::class.java.getDeclaredField("fragments").apply { isAccessible=true }
                    fun sleepingCount()=main { (debrisField.get(runtime) as Map<*,*>).values.count { d ->
                        d!!.javaClass.getDeclaredField("sleeping").apply { isAccessible=true }.getBoolean(d)
                    } }
                    val save=RealmRuntime::class.java.getDeclaredMethod("saveScene").apply { isAccessible=true }
                    var saved=emptyList<com.example.shatterrealm.domain.model.BodySnapshot>()
                    if(verifySleep) waitFor("Contacted debris did not settle into sleep",20) {
                        // Observe and snapshot in one Main task; a genuine impact may wake a shard between tasks.
                        saved=main { save.invoke(runtime);vm.savedScene().toList() }
                        saved.any { it.sleeping }
                    } else saved=main { save.invoke(runtime);vm.savedScene().toList() }
                    assertTrue(saved.any { it.fragmentIndex>=0 })
                    if(verifySleep) assertTrue(saved.any { it.sleeping })
                    main { runtime.reset();vm.saveScene(saved) }
                    val restore=RealmRuntime::class.java.getDeclaredMethod("restoreScene",kotlin.coroutines.Continuation::class.java).apply { isAccessible=true }
                    runBlocking { withContext(Dispatchers.Main) {
                        kotlin.coroutines.suspendCoroutine<Unit> { continuation ->
                            val result=restore.invoke(runtime,continuation)
                            if(result!==kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) continuation.resumeWith(Result.success(Unit))
                        }
                    } }
                    assertEquals(expectedCount,vm.state.value.counts.total)
                    val fragments=RealmRuntime::class.java.getDeclaredField("fragments").apply { isAccessible=true }
                    assertEquals(saved.count { it.fragmentIndex>=0 },main { (fragments.get(runtime) as Map<*,*>).size })
                    if(verifySleep) {
                        assertTrue(sleepingCount()>0)
                        main { (fragments.get(runtime) as Map<*,*>).values.forEach { d ->
                            if(d!!.javaClass.getDeclaredField("sleeping").apply { isAccessible=true }.getBoolean(d)) {
                                val entity=d.javaClass.getDeclaredField("entity").apply { isAccessible=true }.get(d) as com.pico.spatial.core.ecs.Entity
                                assertNotNull(entity.components.get<com.pico.spatial.core.ecs.CollisionComponent>())
                                assertEquals(com.pico.spatial.core.ecs.simulation.RigidBodyMode.KINEMATIC,entity.components.get<com.pico.spatial.core.ecs.RigidBodyComponent>()!!.rigidBodyMode)
                                assertNull("Sleeping bodies must not retain a motion driver",entity.components.get<com.pico.spatial.core.ecs.PhysicsVelocityComponent>())
                            }
                        } }
                        instrumentation.sendStatus(0,android.os.Bundle().apply { putString("stream","\nDEBRIS_SLEEP_RESTORE_VERIFIED: sleeping bodies retained colliders\n") })
                    }
                    android.util.Log.i("ShatterRealm","INTEGRATION_RESTORE_VERIFIED: ${saved.size} bodies, unchanged counts")
                }
            }
            main { Unit }
            android.util.Log.i("ShatterRealm","INTEGRATION_SCENE_READY: ${items.size} item cases verified; active throws=${expectedTotal}; persistent fragments")
            Thread.sleep(if(fast) 500 else 8000)
            // Keep synthetic input valid within each UI transaction: a 1 Hz emulator can expire
            // the hand frame between the background stream and this Main task.
            main { vm.hands(true);vm.controller(false);vm.event(ExperienceEvent.Reset) }
            assertTrue(vm.state.value.resetConfirmation)
            assertEquals(expectedTotal,vm.state.value.counts.total)
            main { vm.hands(true);vm.controller(false);vm.event(ExperienceEvent.CancelReset) }
            assertFalse(vm.state.value.resetConfirmation)
            assertEquals(expectedTotal,vm.state.value.counts.total)
            main { vm.hands(true);vm.controller(false);vm.event(ExperienceEvent.Reset);vm.event(ExperienceEvent.ConfirmReset) }
            assertEquals(0,vm.state.value.counts.total)
            assertTrue(vm.state.value.counts.byItem.isEmpty())
            assertTrue(main { runtime.world.getChildren().all { it.getName().startsWith("Plane-") || (it.getName()=="ThrowableAndDebrisRoot" && it.getChildren().isEmpty()) } })
        } finally { running.set(false);stream.join(1000);main { provider.addListener(listener) } }
    }
}
