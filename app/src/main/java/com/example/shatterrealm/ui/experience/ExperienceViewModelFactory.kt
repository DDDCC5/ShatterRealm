package com.example.shatterrealm.ui.experience

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.shatterrealm.data.repository.CatalogRepository
import com.example.shatterrealm.platform.SpatialApplication

class ExperienceViewModelFactory(private val context: Context): ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T: ViewModel> create(modelClass: Class<T>): T =
        ExperienceViewModel(CatalogRepository(context.applicationContext),
            (context.applicationContext as SpatialApplication).session) as T
}
