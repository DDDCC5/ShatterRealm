package com.example.shatterrealm.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.example.shatterrealm.mainApp

class SpatialApplication : Application() {
    @Volatile internal var activeRuntime: RealmRuntime? = null
    val session = com.example.shatterrealm.domain.usecase.ExperienceSession()

    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
