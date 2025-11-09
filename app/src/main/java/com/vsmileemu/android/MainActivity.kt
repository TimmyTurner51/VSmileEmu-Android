package com.vsmileemu.android

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.vsmileemu.android.data.preferences.AppPreferences
import com.vsmileemu.android.data.repository.RomRepository
import com.vsmileemu.android.ui.rombrowser.RomBrowserScreen
import com.vsmileemu.android.ui.rombrowser.RomBrowserViewModel
import com.vsmileemu.android.ui.setup.SetupWizardScreen
import com.vsmileemu.android.ui.theme.VSmileEmulatorTheme
import com.vsmileemu.android.util.CrashHandler
import com.vsmileemu.android.util.FileLogger
import com.vsmileemu.android.util.StorageHelper
import com.vsmileemu.android.util.copyToClipboard
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    private lateinit var appPreferences: AppPreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Log at ERROR level to ensure it shows up in logcat
        Log.e(TAG, "═══════════════════════════════════════════")
        Log.e(TAG, "MainActivity.onCreate() STARTED")
        Log.e(TAG, "═══════════════════════════════════════════")
        
        enableEdgeToEdge()
        
        appPreferences = AppPreferences(this)
        Log.i(TAG, "AppPreferences created")
        
        // Initialize file logger
        lifecycleScope.launch {
            val storageUri = appPreferences.getStorageUri()
            FileLogger.initialize(this@MainActivity, storageUri)
            FileLogger.log("MainActivity started")
        }
        
        // Check for previous crash
        val crashLog = CrashHandler.getLastCrash(this)
        if (crashLog != null) {
            Log.w(TAG, "Previous crash detected!")
            Log.w(TAG, "Crash log length: ${crashLog.length} characters")
            Log.w(TAG, "First 200 chars: ${crashLog.take(200)}")
        } else {
            Log.i(TAG, "No previous crash log found")
            val crashFile = java.io.File(filesDir, "last_crash.txt")
            Log.i(TAG, "Crash file exists: ${crashFile.exists()}, path: ${crashFile.absolutePath}")
        }
        
        setContent {
            VSmileEmulatorTheme {
                var showCrashDialog by remember { mutableStateOf(crashLog != null) }
                var currentCrashLog by remember { mutableStateOf(crashLog) }
                
                // Show crash dialog if there was a previous crash
                if (showCrashDialog && currentCrashLog != null) {
                    CrashReportDialog(
                        crashLog = currentCrashLog!!,
                        onDismiss = {
                            showCrashDialog = false
                            CrashHandler.clearLastCrash(this@MainActivity)
                        },
                        onCopy = {
                            copyToClipboard(currentCrashLog!!)
                        }
                    )
                }
                
                val setupCompleted by appPreferences.setupCompleted.collectAsStateWithLifecycle(false)
                var showSettings by remember { mutableStateOf(false) }
                
                if (!setupCompleted) {
                    // Show setup wizard for first-time users
                    SetupWizardScreen(
                        onSetupComplete = {
                            lifecycleScope.launch {
                                appPreferences.setSetupCompleted(true)
                            }
                        }
                    )
                } else if (showSettings) {
                    // Handle back button to return to ROM list
                    BackHandler {
                        showSettings = false
                    }
                    
                    // Show settings screen
                    val storageUri by appPreferences.storageUri.collectAsStateWithLifecycle(null)
                    val showFps by appPreferences.showFps.collectAsStateWithLifecycle(true)
                    
                    com.vsmileemu.android.ui.settings.AppSettingsScreen(
                        currentStoragePath = storageUri?.toString(),
                        showFps = showFps,
                        onBack = { showSettings = false },
                        onChangeStorageLocation = {
                            lifecycleScope.launch {
                                appPreferences.setSetupCompleted(false)
                                showSettings = false
                            }
                        },
                        onToggleFps = { enabled ->
                            lifecycleScope.launch {
                                appPreferences.setShowFps(enabled)
                            }
                        }
                    )
                } else {
                    // Show main app (ROM browser)
                    val storageHelper = StorageHelper(this@MainActivity)
                    val romRepository = RomRepository(this@MainActivity, storageHelper)
                    val viewModel = RomBrowserViewModel(romRepository, appPreferences)
                    
                    RomBrowserScreen(
                        viewModel = viewModel,
                        onRomSelected = { rom ->
                            // Launch emulation activity
                            val intent = Intent(this@MainActivity, EmulationActivity::class.java)
                            intent.putExtra("ROM_URI", rom.uri.toString())
                            intent.putExtra("ROM_NAME", rom.fileName)
                            startActivity(intent)
                        },
                        onSettingsClick = {
                            showSettings = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CrashReportDialog(
    crashLog: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("App Crashed Previously")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                Text("The app crashed. Here's the crash log:")
                Spacer(modifier = Modifier.height(8.dp))
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = crashLog,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCopy) {
                Text("Copy to Clipboard")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}



