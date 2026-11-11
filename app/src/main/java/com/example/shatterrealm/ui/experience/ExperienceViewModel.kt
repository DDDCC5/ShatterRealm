package com.example.shatterrealm.ui.experience

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.shatterrealm.data.repository.CatalogRepository
import com.example.shatterrealm.domain.usecase.ExperienceSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

interface ExperienceRuntime {
    fun select(id: String)
    fun reset()
    fun uiSound(name: String)
}
class ExperienceViewModel(private val repository: CatalogRepository,
    private val session: ExperienceSession): ViewModel() {
    private val mutable = MutableStateFlow(ExperienceUiState(accepted=session.accepted,items=repository.builtins(),counts=session.counts))
    val state = mutable.asStateFlow()
    var runtime: ExperienceRuntime? = null
    private val names = (mutable.items+session.bodies.map { it.item }).associateBy { it.id }.toMutableMap()
    init { viewModelScope.launch { delay(1200); mutable.update { it.copy(logo=false) }; refresh() } }
    private val MutableStateFlow<ExperienceUiState>.items get() = value.items
    fun event(event: ExperienceEvent) {
        if (!mutable.value.handsTracked || mutable.value.controllerActive) return
        if(mutable.value.resetConfirmation && event !in setOf(ExperienceEvent.ConfirmReset,ExperienceEvent.CancelReset)) return
        when(event) {
            ExperienceEvent.Agree -> { session.accept(); mutable.update { it.copy(accepted=true) }; runtime?.uiSound("confirm") }
            ExperienceEvent.Refresh -> if(session.accepted) refresh()
            ExperienceEvent.Exit -> mutable.update { it.copy(exitRequested=true) }
            ExperienceEvent.Reset -> if(session.accepted) mutable.update { it.copy(resetConfirmation=true) }
            ExperienceEvent.CancelReset -> mutable.update { it.copy(resetConfirmation=false) }
            ExperienceEvent.ConfirmReset -> if(session.accepted && mutable.value.resetConfirmation) {
                runtime?.reset(); session.reset();mutable.update { it.copy(counts=session.counts,resetConfirmation=false) };runtime?.uiSound("reset")
            }
            is ExperienceEvent.Select -> if(session.accepted && !mutable.value.busy && mutable.value.planeCount>0) {
                mutable.update { it.copy(selected=event.id,loadError=false) }; runtime?.select(event.id)
            }
        }
    }
    private fun refresh() {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy=true) }
        viewModelScope.launch {
            val scan = runCatching { repository.refresh() }.getOrElse { CatalogRepository.Scan(emptyList(),listOf("models")) }
            val current = repository.builtins() + scan.items
            current.forEach { names[it.id]=it }
            // Preserve names for already-thrown models even when their source file was removed.
            mutable.update { it.copy(items=current,busy=false,failedFiles=scan.failures) }
            runtime?.uiSound("refresh")
        }
    }
    fun saveScene(bodies: List<com.example.shatterrealm.domain.model.BodySnapshot>) { session.bodies=bodies }
    fun savedScene() = session.bodies
    fun nextInstance() = session.nextInstance()
    fun rememberItem(item:com.example.shatterrealm.domain.model.Item) {
        if(names.put(item.id,item)==null) mutable.update { it.copy(catalogRevision=it.catalogRevision+1) }
    }
    fun item(id: String) = names[id]
    fun controller(active: Boolean) { mutable.update { it.copy(controllerActive=active) } }
    fun hands(tracked: Boolean) { mutable.update { it.copy(handsTracked=tracked) } }
    fun planes(count: Int) { mutable.update { it.copy(planeCount=count,roomPlaneStatus=if(count>0) RoomPlaneStatus.READY else RoomPlaneStatus.EMPTY) } }
    fun roomStatus(status:RoomPlaneStatus) { mutable.update { it.copy(roomPlaneStatus=status) } }
    fun loading(value: Boolean) { mutable.update { it.copy(busy=value) } }
    fun error() { mutable.update { it.copy(busy=false,loadError=true) } }
    fun fractured(instance: Long, id: String, released: Boolean, plane: Boolean): Boolean {
        val success=session.fracture(instance,id,released,plane)
        if(success) mutable.update { it.copy(counts=session.counts) }
        return success
    }
}
