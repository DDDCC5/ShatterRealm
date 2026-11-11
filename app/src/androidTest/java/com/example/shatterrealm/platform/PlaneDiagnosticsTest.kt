package com.example.shatterrealm.platform

import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pico.spatial.sense.plane.PlaneTrackingManager
import com.pico.spatial.sense.base.TrackingState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import org.junit.runner.RunWith

/** Read public provider state on hardware; does not fabricate or modify room data. */
@RunWith(AndroidJUnit4::class)
class PlaneDiagnosticsTest {
    @Test fun reportSystemPlanes() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        context.startActivity(Intent(context,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        repeat(12) { index ->
            Thread.sleep(1000)
            runBlocking {
                val state=PlaneTrackingManager.state
                val anchors=if(state==TrackingState.RUNNING) withTimeoutOrNull(1500) { PlaneTrackingManager.loadAllAnchors() } else null
                Log.i("RealmPlaneProbe","sample=$index state=$state anchors=${anchors?.size} semantics=${anchors?.joinToString { it.semantics.toString() }}")
            }
        }
    }
}
