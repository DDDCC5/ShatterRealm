package com.example.shatterrealm.ui.experience

import com.example.shatterrealm.domain.model.*

enum class RoomPlaneStatus { STARTING, PAUSED, UNAVAILABLE, EMPTY, READY, ERROR }

data class ExperienceUiState(
    val logo: Boolean = true, val accepted: Boolean = false,
    val items: List<Item> = emptyList(), val counts: Counts = Counts(),
    val selected: String? = null, val controllerActive: Boolean = false,
    val handsTracked: Boolean = false, val planeCount: Int = 0,
    val roomPlaneStatus: RoomPlaneStatus = RoomPlaneStatus.STARTING,
    val busy: Boolean = false, val failedFiles: List<String> = emptyList(),
    val catalogRevision:Int=0, val loadError: Boolean = false, val resetConfirmation:Boolean=false, val exitRequested:Boolean=false
)
sealed interface ExperienceEvent {
    data object Agree: ExperienceEvent
    data object Exit: ExperienceEvent
    data object ConfirmReset: ExperienceEvent
    data object CancelReset: ExperienceEvent
    data object Refresh: ExperienceEvent
    data object Reset: ExperienceEvent
    data class Select(val id: String): ExperienceEvent
}
