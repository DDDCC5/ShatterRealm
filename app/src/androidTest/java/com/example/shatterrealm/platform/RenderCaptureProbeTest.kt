package com.example.shatterrealm.platform

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Opaque SDK primitive separates capture-layer limitations from GLB material behavior. */
@RunWith(AndroidJUnit4::class)
class RenderCaptureProbeTest {
    @Test fun opaqueCameraMarker() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val app=context.applicationContext as SpatialApplication
        context.startActivity(Intent(context,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val end=java.lang.System.currentTimeMillis()+30000
        while(app.activeRuntime==null && java.lang.System.currentTimeMillis()<end) Thread.sleep(50)
        assertNotNull(app.activeRuntime)
        Thread.sleep(6000)
        var probe:Entity?=null
        instrumentation.runOnMainSync {
            probe=ModelEntity(MeshResource.createSphere(.05f),UnlitMaterial.create().apply { setBaseColor(Color4(1f,0f,0f,1f)) }).apply {
                setName("OpaqueCameraCaptureProbe")
                app.activeRuntime!!.world.addChild(this)
                components.set(AnchorComponent().apply { positionOffset=Vector3(-.3f,.2f,-.65f) })
            }
            android.util.Log.i("ShatterRealm","INTEGRATION_PREVIEW_READY: opaque red native sphere capture probe")
        }
        Thread.sleep(12000)
        instrumentation.runOnMainSync { probe!!.destroy() }
    }
}
