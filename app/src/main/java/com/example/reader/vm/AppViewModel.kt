package com.example.reader.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.reader.data.ReaderSettings
import com.example.reader.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val settings: StateFlow<ReaderSettings> = SettingsRepository(app).settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderSettings())
}
