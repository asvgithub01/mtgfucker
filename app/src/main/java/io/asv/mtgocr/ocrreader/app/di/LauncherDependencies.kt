package io.asv.mtgocr.ocrreader.app.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import io.asv.mtgocr.ocrreader.data.legacy.LegacyLauncherRepository
import io.asv.mtgocr.ocrreader.feature.launcher.LauncherViewModel
import kotlinx.coroutines.Dispatchers

class LauncherDependencies(context: Context) {
    private val repository = LegacyLauncherRepository(context, Dispatchers.IO)
    val factory = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass == LauncherViewModel::class.java)
            @Suppress("UNCHECKED_CAST")
            return LauncherViewModel(repository, extras.createSavedStateHandle()) as T
        }
    }
}
