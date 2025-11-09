package com.vsmileemu.android.ui.rombrowser

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vsmileemu.android.data.model.Rom
import com.vsmileemu.android.data.preferences.AppPreferences
import com.vsmileemu.android.data.repository.RomRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RomBrowserState(
    val roms: List<Rom> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val biosUri: Uri? = null,
    val hasBios: Boolean = false
)

class RomBrowserViewModel(
    private val romRepository: RomRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {
    
    private val _state = MutableStateFlow(RomBrowserState())
    val state: StateFlow<RomBrowserState> = _state.asStateFlow()
    
    init {
        loadRoms()
    }
    
    fun loadRoms() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            try {
                val storageUri = appPreferences.getStorageUri()
                
                if (storageUri == null) {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "No storage directory configured"
                    )
                    return@launch
                }
                
                val baseUri = Uri.parse(storageUri)
                
                // Check for BIOS
                val hasBios = romRepository.hasBios(baseUri)
                
                // Load ROMs
                val roms = romRepository.getRomList(baseUri)
                
                _state.value = _state.value.copy(
                    roms = roms,
                    hasBios = hasBios,
                    isLoading = false
                )
                
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to load ROMs: ${e.message}"
                )
            }
        }
    }
    
    fun launchRom(rom: Rom) {
        // This will be handled by the activity/navigation
    }
}


