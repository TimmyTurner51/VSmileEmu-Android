package com.vsmileemu.android.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vsmileemu.android.EmulationActivity

enum class SettingsTab {
    GENERAL, EMULATION, CPU, AUDIO
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabbedSettingsScreen(
    showFps: Boolean,
    audioEnabled: Boolean,
    pixelScale: EmulationActivity.PixelScale,
    frameSkip: Boolean,
    fastMath: Boolean,
    onToggleFps: (Boolean) -> Unit,
    onToggleAudio: (Boolean) -> Unit,
    onChangeScale: (EmulationActivity.PixelScale) -> Unit,
    onToggleFrameSkip: (Boolean) -> Unit,
    onToggleFastMath: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(SettingsTab.GENERAL) }
    
    // Handle back button to close settings instead of exiting activity
    BackHandler(onBack = onBack)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emulator Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == SettingsTab.GENERAL,
                    onClick = { selectedTab = SettingsTab.GENERAL },
                    text = { Text("General") }
                )
                Tab(
                    selected = selectedTab == SettingsTab.EMULATION,
                    onClick = { selectedTab = SettingsTab.EMULATION },
                    text = { Text("Emulation") }
                )
                Tab(
                    selected = selectedTab == SettingsTab.CPU,
                    onClick = { selectedTab = SettingsTab.CPU },
                    text = { Text("CPU") }
                )
                Tab(
                    selected = selectedTab == SettingsTab.AUDIO,
                    onClick = { selectedTab = SettingsTab.AUDIO },
                    text = { Text("Audio") }
                )
            }
            
            // Tab Content
            when (selectedTab) {
                SettingsTab.GENERAL -> GeneralSettings(showFps, onToggleFps)
                SettingsTab.EMULATION -> EmulationSettings(
                    pixelScale,
                    frameSkip,
                    onChangeScale,
                    onToggleFrameSkip
                )
                SettingsTab.CPU -> CpuSettings(fastMath, onToggleFastMath)
                SettingsTab.AUDIO -> AudioSettings(audioEnabled, onToggleAudio)
            }
        }
    }
}

@Composable
fun GeneralSettings(
    showFps: Boolean,
    onToggleFps: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "General Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("FPS Counter", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Show frame rate overlay",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = showFps, onCheckedChange = onToggleFps)
                }
            }
        }
    }
}

@Composable
fun EmulationSettings(
    pixelScale: EmulationActivity.PixelScale,
    frameSkip: Boolean,
    onChangeScale: (EmulationActivity.PixelScale) -> Unit,
    onToggleFrameSkip: (Boolean) -> Unit
) {
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Emulation Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        
        // Frame Skip toggle
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Frame Skip", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Skip frames to maintain speed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = frameSkip, onCheckedChange = onToggleFrameSkip)
                }
            }
        }
        
        // Screen Scale options
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Screen Scale", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Choose display resolution",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                EmulationActivity.PixelScale.entries.forEach { scale ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onChangeScale(scale) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = pixelScale == scale,
                                onClick = null // Click handled by Surface
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = scale.displayName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = scale.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CpuSettings(
    fastMath: Boolean,
    onToggleFastMath: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "CPU Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Fast Math Mode", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Use faster floating-point calculations",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = fastMath, onCheckedChange = onToggleFastMath)
                }
            }
        }
        
        Text(
            "Note: Fast math is already enabled in the build",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
fun AudioSettings(
    audioEnabled: Boolean,
    onToggleAudio: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Audio Settings",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Audio", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Play game audio (27kHz stereo)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = audioEnabled, onCheckedChange = onToggleAudio)
                }
                
                if (audioEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "⚠️ Audio may crackle during heavy emulation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

