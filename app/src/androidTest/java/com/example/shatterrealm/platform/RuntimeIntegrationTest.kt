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
        val floorAnchor=floor()!!
        val candidates=mutableListOf<Vector3>()
        floorAnchor.indices.chunked(3).forEach { tri ->
            val a=floorAnchor.vertices[tri[0]];val b=floorAnchor.vertices[tri[1]];val c=floorAnchor.vertices[tri[2]]
            for(i in 1..6) for(j in 1..6) if(i+j<8) {
                val local=a*(i/8f)+b*(j/8f)+c*(1f-(i+j)/8f)
                candidates+=floorAnchor.transform.position+floorAnchor.transform.quaternion.rotateVector(local)
            }
        }
        val destinations=mutableListOf<Vector3>()
        repeat(15) {
            val chosen=candidates.maxBy { candidate ->
                if(destinations.isEmpty()) 0f else destinations.minOf { old -> val d=candidate-old;d.x*d.x+d.y*d.y+d.z*d.z }
            }
            destinations+=chosen;candidates.remove(chosen)
        }
        val point=destinations.first()+Vector3(0f,.7f,0f)
        val listenerField=RealmRuntime::class.java.getDeclaredField("handListener").apply { isAccessible=true }
        @Suppress("UNCHECKED_CAST")
        val listener=listenerField.get(runtime) as com.pico.spatial.tracking.DataProvider.DataListener<HandTrackingData>
        val pinch=java.util.concurrent.atomic.AtomicReference(0f)
        val speed=java.util.concurrent.atomic.AtomicReference(0f)
        val movementStart=java.util.concurrent.atomic.AtomicLong(0L)
        val position=java.util.concurrent.atomic.AtomicReference(point)
        val running=java.util.concurrent.atomic.AtomicBoolean(true)
        val stream=Thread {
            while(running.get()) {
                val elapsed=(SystemClock.elapsedRealtimeNanos()-movementStart.get())/1e9f
                val p=position.get()+Vector3(0f,-elapsed*speed.get(),0f);val joints=HandJoint.Index.values().map { HandJoint(p,Quat(),it) }
                listener.onProvideData(HandTrackingData(HandPose(joints),null,SystemClock.elapsedRealtimeNanos(),HandPinch(PinchPose(p,Quat()),pinch.get(),true)))
                Thread.sleep(11)
            }
        }.apply { isDaemon=true;start() }
        try {
            waitFor("Synthetic tracked stream did not arrive") { vm.state.value.handsTracked }
            main { vm.controller(false);vm.event(ExperienceEvent.Agree) }
            waitFor("Catalog startup did not settle") { !vm.state.value.busy }
            val handsField=RealmRuntime::class.java.getDeclaredField("hands").apply { isAccessible=true }
            fun held()=main {
                val hand=(handsField.get(runtime) as Array<*>)[0]!!
                hand.javaClass.getDeclaredField("held").apply { isAccessible=true }.get(hand)!=null
            }
            val allItems=vm.state.value.items.filter { !it.custom }
            assertEquals(15,allItems.size)
            val only=InstrumentationRegistry.getArguments().getString("only_item")
            val items=allItems.filter { only==null || it.id==only }
            val expectedTotal=items.size
            val verifyRestore=InstrumentationRegistry.getArguments().getString("validate_restore")=="true"
            var previewPoint:Vector3?=null
            if(only!=null) {
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
            items.forEachIndexed { index,item ->
                val target=previewPoint ?: (destinations[index]+Vector3(0f,.7f,0f))
                pinch.set(0f);position.set(target)
                waitFor("Hand pose did not update") { main {
                    val hand=(handsField.get(runtime) as Array<*>)[0]!!
                    hand.javaClass.getDeclaredField("position").apply { isAccessible=true }.get(hand)==target
                } }
                main { vm.controller(false);vm.event(ExperienceEvent.Select(item.id)) }
                waitFor("Asset load timed out for ${item.id}",120) { !vm.state.value.busy }
                assertFalse("Asset load failed for ${item.id}",vm.state.value.loadError)
                pinch.set(1f)
                waitFor("Could not grab ${item.id}",15) { held() }
                if(only!=null) {
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
                } else Thread.sleep(60)
                pinch.set(0f)
                waitFor("Could not release ${item.id}",15) { !held() }
                waitFor("No plane fracture for ${item.id}",60) { vm.state.value.counts.total==index+1 }
                speed.set(0f)
                assertEquals(1,vm.state.value.counts.byItem[item.id])
                if(verifyRestore && index==0) {
                    val save=RealmRuntime::class.java.getDeclaredMethod("saveScene").apply { isAccessible=true }
                    val saved=main { save.invoke(runtime);vm.savedScene().toList() }
                    assertTrue(saved.any { it.fragmentIndex>=0 })
                    main { runtime.reset();vm.saveScene(saved) }
                    val restore=RealmRuntime::class.java.getDeclaredMethod("restoreScene",kotlin.coroutines.Continuation::class.java).apply { isAccessible=true }
                    runBlocking { withContext(Dispatchers.Main) {
                        kotlin.coroutines.suspendCoroutine<Unit> { continuation ->
                            val result=restore.invoke(runtime,continuation)
                            if(result!==kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED) continuation.resumeWith(Result.success(Unit))
                        }
                    } }
                    assertEquals(index+1,vm.state.value.counts.total)
                    val fragments=RealmRuntime::class.java.getDeclaredField("fragments").apply { isAccessible=true }
                    assertEquals(saved.count { it.fragmentIndex>=0 },main { (fragments.get(runtime) as Map<*,*>).size })
                    android.util.Log.i("ShatterRealm","INTEGRATION_RESTORE_VERIFIED: ${saved.size} bodies, unchanged counts")
                }
            }
            main { Unit }
            android.util.Log.i("ShatterRealm","INTEGRATION_SCENE_READY: ${expectedTotal} system plane fractures, persistent fragments")
            Thread.sleep(8000)
            main { vm.controller(false);vm.event(ExperienceEvent.Reset) }
            assertTrue(vm.state.value.resetConfirmation)
            assertEquals(expectedTotal,vm.state.value.counts.total)
            main { vm.event(ExperienceEvent.CancelReset) }
            assertFalse(vm.state.value.resetConfirmation)
            assertEquals(expectedTotal,vm.state.value.counts.total)
            main { vm.event(ExperienceEvent.Reset);vm.event(ExperienceEvent.ConfirmReset) }
            assertEquals(0,vm.state.value.counts.total)
            assertTrue(vm.state.value.counts.byItem.isEmpty())
            assertTrue(main { runtime.world.getChildren().all { it.getName().startsWith("Plane-") || (it.getName()=="ThrowableAndDebrisRoot" && it.getChildren().isEmpty()) } })
        } finally { running.set(false);stream.join(1000) }
    }
}
