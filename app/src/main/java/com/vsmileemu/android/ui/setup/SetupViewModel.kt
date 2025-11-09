package com.vsmileemu.android.ui.setup

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vsmileemu.android.data.preferences.AppPreferences
import com.vsmileemu.android.data.repository.RomRepository
import com.vsmileemu.android.util.StorageHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class SetupStep {
    WELCOME,
    STORAGE,
    SCANNING,
    BIOS,
    COMPLETE
}

data class SetupUiState(
    val currentStep: SetupStep = SetupStep.WELCOME,
    val storageUri: Uri? = null,
    val isScanning: Boolean = false,
    val scanProgress: String = "",
    val biosFound: Boolean = false,
    val romCount: Int = 0
)

class SetupViewModel(
    private val context: Context
) : ViewModel() {
    
    private val appPreferences = AppPreferences(context)
    private val storageHelper = StorageHelper(context)
    private val romRepository = RomRepository(context)
    
    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()
    
    fun nextStep() {
        val currentStep = _uiState.value.currentStep
        val nextStep = when (currentStep) {
            SetupStep.WELCOME -> SetupStep.STORAGE
            SetupStep.STORAGE -> {
                // Start scanning automatically
                startScanning()
                SetupStep.SCANNING
            }
            SetupStep.SCANNING -> SetupStep.BIOS
            SetupStep.BIOS -> SetupStep.COMPLETE
            SetupStep.COMPLETE -> SetupStep.COMPLETE
        }
        
        _uiState.update { it.copy(currentStep = nextStep) }
    }
    
    fun previousStep() {
        val currentStep = _uiState.value.currentStep
        val prevStep = when (currentStep) {
            SetupStep.WELCOME -> SetupStep.WELCOME
            SetupStep.STORAGE -> SetupStep.WELCOME
            SetupStep.SCANNING -> SetupStep.STORAGE
            SetupStep.BIOS -> SetupStep.STORAGE
            SetupStep.COMPLETE -> SetupStep.BIOS
        }
        
        _uiState.update { it.copy(currentStep = prevStep) }
    }
    
    fun setStorageUri(uri: Uri) {
        _uiState.update { it.copy(storageUri = uri) }
        viewModelScope.launch {
            appPreferences.setStorageUri(uri)
        }
    }
    
    fun useDefaultStorage() {
        // Use Documents/VSmileEMU as default
        val documentsDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOCUMENTS
        )
        val vsmileDir = File(documentsDir, "VSmileEMU")
        
        // Create directory if it doesn't exist
        if (!vsmileDir.exists()) {
            vsmileDir.mkdirs()
        }
        
        // For default storage, we'll use direct file access
        // In a real app with scoped storage, you'd still want to use SAF
        // This is simplified for the example
        val uri = Uri.fromFile(vsmileDir)
        setStorageUri(uri)
    }
    
    private fun startScanning() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            
            val storageUri = _uiState.value.storageUri ?: return@launch
            
            // Create folders
            _uiState.update { it.copy(scanProgress = "Creating folders...") }
            delay(500) // Slight delay for UI feedback
            
            val result = storageHelper.createFolders(storageUri)
            if (result.isFailure) {
                _uiState.update { 
                    it.copy(
                        isScanning = false,
                        scanProgress = "Error creating folders"
                    )
                }
                return@launch
            }
            
            // Check for BIOS
            _uiState.update { it.copy(scanProgress = "Checking for BIOS...") }
            delay(500)
            
            val biosFound = storageHelper.biosExists(storageUri)
            viewModelScope.launch {
                appPreferences.setBiosFound(biosFound)
            }
            
            // Scan for ROMs
            _uiState.update { it.copy(scanProgress = "Scanning for ROMs...") }
            delay(500)
            
            var romCount = 0
            romRepository.scanRoms(storageUri).collect { result ->
                result.onSuccess { roms ->
                    romCount = roms.size
                }
            }
            
            // Update state and move to next step
            _uiState.update {
                it.copy(
                    isScanning = false,
                    biosFound = biosFound,
                    romCount = romCount
                )
            }
            
            // Auto-advance to next step
            delay(500)
            nextStep()
        }
    }
    
    fun checkBios() {
        viewModelScope.launch {
            val storageUri = _uiState.value.storageUri ?: return@launch
            val biosFound = storageHelper.biosExists(storageUri)
            _uiState.update { it.copy(biosFound = biosFound) }
            appPreferences.setBiosFound(biosFound)
        }
    }
    
    fun completeSetup() {
        viewModelScope.launch {
            appPreferences.setSetupCompleted(true)
        }
    }
    
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SetupViewModel::class.java)) {
                return SetupViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}








