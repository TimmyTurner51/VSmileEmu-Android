package com.vsmileemu.android.ui.emulation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vsmileemu.android.native_bridge.ControllerInput
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * V.Smile Virtual Controller
 * Beautiful on-screen controller matching the V.Smile gamepad layout
 *
 * Layout:
 *   [HELP]   🕹️   [ENTER]
 *   [BACK]        [ABC]
 *   
 *    🔴 Red    🟡 Yellow
 *    🔵 Blue   🟢 Green
 */

@Composable
fun VirtualController(
    onInputChange: (ControllerInput) -> Unit,
    opacity: Float = 0.7f,
    size: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    var currentInput by remember { mutableStateOf(ControllerInput.EMPTY) }
    
    // Update callback whenever input changes
    LaunchedEffect(currentInput) {
        onInputChange(currentInput)
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()  // Only fill width, respect height from parent
            .alpha(opacity)
    ) {
        // Left side - Face buttons + Joystick
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = (32 * size).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((16 * size).dp)
        ) {
            // Top row: HELP and ENTER
            Row(
                horizontalArrangement = Arrangement.spacedBy((80 * size).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundButton(
                    text = "HELP",
                    color = Color(0xFF9E9E9E),
                    size = size,
                    pressed = currentInput.help,
                    onPressedChange = { pressed ->
                        currentInput = currentInput.copy(help = pressed)
                    }
                )
                
                RoundButton(
                    text = "ENTER",
                    color = Color(0xFF4CAF50),
                    size = size,
                    pressed = currentInput.enter,
                    onPressedChange = { pressed ->
                        currentInput = currentInput.copy(enter = pressed)
                    }
                )
            }
            
            // Middle: Joystick
            Spacer(modifier = Modifier.height((16 * size).dp))
            
            VirtualJoystick(
                size = size,
                onJoystickMove = { x, y ->
                    currentInput = currentInput.copy(joystickX = x, joystickY = y)
                }
            )
            
            Spacer(modifier = Modifier.height((16 * size).dp))
            
            // Bottom row: BACK and ABC
            Row(
                horizontalArrangement = Arrangement.spacedBy((80 * size).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundButton(
                    text = "BACK",
                    color = Color(0xFFFF9800),
                    size = size,
                    pressed = currentInput.back,
                    onPressedChange = { pressed ->
                        currentInput = currentInput.copy(back = pressed)
                    }
                )
                
                RoundButton(
                    text = "ABC",
                    color = Color(0xFF2196F3),
                    size = size,
                    pressed = currentInput.abc,
                    onPressedChange = { pressed ->
                        currentInput = currentInput.copy(abc = pressed)
                    }
                )
            }
        }
        
        // Right side - Color buttons
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = (32 * size).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((16 * size).dp)
        ) {
            // Top row: Red and Yellow
            Row(
                horizontalArrangement = Arrangement.spacedBy((16 * size).dp)
            ) {
                ColorButton(
                    color = Color(0xFFE53935),
                    text = "🔴",
                    size = size,
                    pressed = currentInput.red,
                    onPressedChange = { pressed ->
                        currentInput = currentInput.copy(red = pressed)
                    }
                )
                
                ColorButton(
                    color = Color(0xFFFFC107),
                    text = "🟡",
                    size = size,
                    pressed = currentInput.yellow,
                    onPressedChange = { pressed ->
                        currentInput = currentInput.copy(yellow = pressed)
                    }
                )
            }
            
            // Bottom row: Blue and Green
            Row(
                horizontalArrangement = Arrangement.spacedBy((16 * size).dp)
            ) {
                ColorButton(
                    color = Color(0xFF2196F3),
                    text = "🔵",
                    size = size,
                    pressed = currentInput.blue,
                    onPressedChange = { pressed ->
                        currentInput = currentInput.copy(blue = pressed)
                    }
                )
                
                ColorButton(
                    color = Color(0xFF43A047),
                    text = "🟢",
                    size = size,
                    pressed = currentInput.green,
                    onPressedChange = { pressed ->
                        currentInput = currentInput.copy(green = pressed)
                    }
                )
            }
        }
    }
}

/**
 * Round button with text label
 */
@Composable
private fun RoundButton(
    text: String,
    color: Color,
    size: Float,
    pressed: Boolean,
    onPressedChange: (Boolean) -> Unit
) {
    val buttonSize = (64 * size).dp
    val pressedAlpha = if (pressed) 1.0f else 0.7f
    
    Box(
        modifier = Modifier
            .size(buttonSize)
            .clip(CircleShape)
            .background(color.copy(alpha = pressedAlpha))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPressedChange(true)
                        tryAwaitRelease()
                        onPressedChange(false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = (10 * size).sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Large color button (for Red, Yellow, Blue, Green)
 */
@Composable
private fun ColorButton(
    color: Color,
    text: String,
    size: Float,
    pressed: Boolean,
    onPressedChange: (Boolean) -> Unit
) {
    val buttonSize = (72 * size).dp
    val pressedScale = if (pressed) 0.9f else 1.0f
    
    Box(
        modifier = Modifier
            .size(buttonSize * pressedScale)
            .clip(CircleShape)
            .background(color)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPressedChange(true)
                        tryAwaitRelease()
                        onPressedChange(false)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = (32 * size).sp
        )
    }
}

/**
 * Virtual joystick with analog movement
 */
@Composable
private fun VirtualJoystick(
    size: Float,
    onJoystickMove: (x: Int, y: Int) -> Unit
) {
    var stickOffset by remember { mutableStateOf(Offset.Zero) }
    val joystickSize = (120 * size).dp
    val stickSize = (50 * size).dp
    val maxDistance = with(androidx.compose.ui.platform.LocalDensity.current) {
        (joystickSize / 2 - stickSize / 2).toPx()
    }
    
    // Convert offset to V.Smile range (-5 to +5)
    LaunchedEffect(stickOffset) {
        val x = (stickOffset.x / maxDistance * 5).toInt().coerceIn(-5, 5)
        val y = (stickOffset.y / maxDistance * 5).toInt().coerceIn(-5, 5)
        onJoystickMove(x, -y) // Invert Y for natural up/down
    }
    
    Box(
        modifier = Modifier
            .size(joystickSize)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        stickOffset = Offset.Zero
                    },
                    onDragCancel = {
                        stickOffset = Offset.Zero
                    }
                ) { change, dragAmount ->
                    change.consume()
                    val newOffset = stickOffset + dragAmount
                    val distance = hypot(newOffset.x, newOffset.y)
                    
                    stickOffset = if (distance <= maxDistance) {
                        newOffset
                    } else {
                        val angle = kotlin.math.atan2(newOffset.y, newOffset.x)
                        Offset(
                            maxDistance * kotlin.math.cos(angle),
                            maxDistance * kotlin.math.sin(angle)
                        )
                    }
                }
            }
    ) {
        // Joystick base
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            // Outer circle
            drawCircle(
                color = Color.Gray.copy(alpha = 0.5f),
                radius = this.size.minDimension / 2,
                style = Stroke(width = 4f)
            )
            
            // Center indicator
            drawCircle(
                color = Color.Gray.copy(alpha = 0.3f),
                radius = 8f
            )
        }
        
        // Joystick stick
        Box(
            modifier = Modifier
                .offset(
                    x = with(androidx.compose.ui.platform.LocalDensity.current) {
                        stickOffset.x.toDp()
                    },
                    y = with(androidx.compose.ui.platform.LocalDensity.current) {
                        stickOffset.y.toDp()
                    }
                )
                .align(Alignment.Center)
                .size(stickSize)
                .clip(CircleShape)
                .background(Color(0xFF607D8B))
        )
    }
}






