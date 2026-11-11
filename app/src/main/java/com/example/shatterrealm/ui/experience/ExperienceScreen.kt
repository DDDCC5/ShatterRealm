package com.example.shatterrealm.ui.experience

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.shatterrealm.platform.RealmRuntime
import com.example.shatterrealm.platform.pose
import com.example.shatterrealm.ui.experience.components.*
import com.pico.spatial.ui.platform.containers.closeStage
import com.pico.spatial.ui.platform.LocalSpatialContainerStateManager
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.core.math.Quat
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.event.SceneEvents
import com.pico.spatial.core.lifecycle.Cancellable
import com.example.shatterrealm.domain.model.Q4
import com.example.shatterrealm.domain.usecase.UprightHeading
import kotlinx.coroutines.*
import android.util.Log

private class InterfaceAnchors {
    private val root=Entity()
    private val frame=Entity().apply { setName("LevelInterfaceFrame");enabled=false }
    private val camera=Entity().apply {
        setName("InitialInterfaceCameraProbe")
        components.set(AnchorComponent(trackingMode=AnchorComponent.TrackingMode.CONTINUOUS))
    }
    private val anchors=mutableMapOf<String,Entity>()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var updates:Cancellable?=null
    private var ready=false
    private var focused=false
    fun focus(value:Boolean) { focused=value }
    init { root.addChild(frame);root.addChild(camera) }
    fun attach(content:SpatialViewContent) {
        content.addEntity(root)
        var frames=0
        updates=content.subscribe(SceneEvents.Update::class.java) {
            scope.launch {
                if(ready) return@launch
                if(!focused) { frames=0;return@launch }
                if(++frames<3) return@launch
                val position=camera.convertPositionTo(Vector3.ZERO,null)
                if(position==Vector3.ZERO) return@launch // Wait for the initial camera target, not default Entity pose.
                val rotation=camera.convertRotationTo(Quat(),null)
                val yaw=UprightHeading.yaw(Q4(rotation.x.toDouble(),rotation.y.toDouble(),rotation.z.toDouble(),rotation.w.toDouble()))
                val level=Quat(Vector3(0f,1f,0f),yaw.toFloat())
                frame.pose().position=root.convertPositionFrom(position,null)
                frame.pose().quaternion=root.convertRotationFrom(level,null)
                ready=true;frame.enabled=true
                updates?.cancel();updates=null;camera.destroy()
                Log.i("ShatterRealm","Level interface captured: position=$position yaw=$yaw; head pitch/roll discarded")
                anchors.forEach { (id,entity) -> logUpright(id,entity) }
            }
        }
    }
    fun add(id:String,panel:Entity,position:Vector3,rotation:Quat=Quat()) {
        if(id in anchors) return
        val anchor=Entity().apply {
            setName("Interface-$id")
            pose().position=position;pose().quaternion=rotation
        }
        frame.addChild(anchor);anchor.addChild(panel);anchors[id]=anchor
        if(ready) logUpright(id,anchor)
    }
    private fun logUpright(id:String,entity:Entity) {
        val q=entity.convertRotationTo(Quat(),null)
        val up=q.rotateVector(Vector3(0f,1f,0f))
        val right=q.rotateVector(Vector3(1f,0f,0f))
        Log.i("ShatterRealm","Interface upright $id up=$up rightY=${right.y}")
    }
    fun visible(id:String,value:Boolean) { anchors[id]?.enabled=value }
    fun close() { scope.cancel();updates?.cancel();root.destroy() }
}
@Composable fun ExperienceScreen() {
    val context=LocalContext.current
    val chinese=LocalConfiguration.current.locales[0].language=="zh"
    val vm:ExperienceViewModel=viewModel(factory=ExperienceViewModelFactory(context))
    val state by vm.state.collectAsState()
    LaunchedEffect(state.exitRequested) { if(state.exitRequested) closeStage() }
    val runtime=remember(vm) { RealmRuntime(context.applicationContext,vm) }
    val anchors=remember(runtime) { InterfaceAnchors() }
    val focused by LocalSpatialContainerStateManager.current.isFocused
    SideEffect { anchors.focus(focused) }
    DisposableEffect(runtime) { onDispose { runtime.close();anchors.close() } }
    SpatialView(modifier=Modifier.fillMaxSize(),attachments={
        AttachmentPanel("admission") { val live by vm.state.collectAsState();Admission(live,LocalConfiguration.current.locales[0].language=="zh",vm::event) }
        AttachmentPanel("reset") { val live by vm.state.collectAsState();ResetNotice(live,LocalConfiguration.current.locales[0].language=="zh",vm::event) }
        AttachmentPanel("controller") { ControllerNotice(LocalConfiguration.current.locales[0].language=="zh") }
        AttachmentPanel("counter") { val live by vm.state.collectAsState();val zh=LocalConfiguration.current.locales[0].language=="zh";Counter(live,zh) { vm.item(it)?.label(zh) ?: it } }
        AttachmentPanel("shelf") {
            val live by vm.state.collectAsState()
            Shelf(live,LocalConfiguration.current.locales[0].language=="zh",vm::event)
        }
    },update={ _,attachments ->
        attachments.entity("admission")?.let { anchors.add("admission",it,Vector3(0f,0f,-1.1f)) }
        attachments.entity("reset")?.let { anchors.add("reset",it,Vector3(0f,0f,-1.1f)) }
        attachments.entity("controller")?.let { anchors.add("controller",it,Vector3(0f,0f,-1.1f)) }
        attachments.entity("counter")?.let { anchors.add("counter",it,Vector3(0f,.7743f,-1.1059f)) }
        attachments.entity("shelf")?.let { anchors.add("shelf",it,Vector3(.5324f,0f,-.3728f),Quat(Vector3(0f,1f,0f),-.9599311f)) }
        anchors.visible("admission",(state.logo || !state.accepted) && !state.controllerActive)
        anchors.visible("controller",state.controllerActive)
        anchors.visible("reset",state.resetConfirmation && !state.controllerActive)
        anchors.visible("counter",state.accepted && !state.logo)
        anchors.visible("shelf",state.accepted && !state.logo)
    }) { content,_ ->
        anchors.attach(content)
        runtime.attach(content)
    }
}
