package com.example.shatterrealm.platform

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Bounded public-resource factory threading probe; no Entity/Component mutation off Main. */
@RunWith(AndroidJUnit4::class)
class ResourceFactoryThreadTest {
    @Test fun convexFactoriesOnWorker() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        context.startActivity(Intent(context,LaunchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val app=context.applicationContext as SpatialApplication
        val end=System.currentTimeMillis()+30000
        while(app.activeRuntime==null && System.currentTimeMillis()<end) Thread.sleep(50)
        assertNotNull(app.activeRuntime)
        runBlocking {
            withContext(Dispatchers.Default) {
                val vertices=listOf(Vector3(0f,0f,0f),Vector3(.1f,0f,0f),Vector3(0f,.1f,0f),Vector3(0f,0f,.1f))
                val mesh=MeshResource.createWithMeshModel(MeshModel(vertices,listOf(0,2,1,0,1,3,0,3,2,1,2,3)),name="worker-thread-probe")
                mesh.toGlobal()
                val shape=ShapeResource.createConvexMesh(mesh);shape.toGlobal()
                assertTrue(mesh.valid);assertTrue(shape.valid)
                shape.close();mesh.close()
            }
        }
    }
}
