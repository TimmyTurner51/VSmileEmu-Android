package com.vsmileemu.android.controller

import android.view.KeyEvent

enum class ControllerAction(val displayName: String, val defaultKeyCode: Int) {
    ENTER("Start / Enter", KeyEvent.KEYCODE_BUTTON_START),
    BACK("Select / Back", KeyEvent.KEYCODE_BUTTON_SELECT),
    HELP("Help", KeyEvent.KEYCODE_BUTTON_L1),
    ABC("ABC Button", KeyEvent.KEYCODE_BUTTON_R1),
    RED("Red Button", KeyEvent.KEYCODE_BUTTON_Y),
    YELLOW("Yellow Button", KeyEvent.KEYCODE_BUTTON_B),
    BLUE("Blue Button", KeyEvent.KEYCODE_BUTTON_A),
    GREEN("Green Button", KeyEvent.KEYCODE_BUTTON_X)
}

enum class ColorButtonLayout(val displayName: String) {
    GRID("2 × 2 Grid"),
    HORIZONTAL("Horizontal Row"),
    DIAMOND("Diamond Cross")
}

enum class ColorButtonAnchor(val displayName: String) {
    LEFT("Left"),
    CENTER("Center"),
    RIGHT("Right")
}

data class ControllerKeyOption(val keyCode: Int, val label: String)

val controllerKeyOptions = listOf(
    ControllerKeyOption(KeyEvent.KEYCODE_BUTTON_A, "A / Cross"),
    ControllerKeyOption(KeyEvent.KEYCODE_BUTTON_B, "B / Circle"),
    ControllerKeyOption(KeyEvent.KEYCODE_BUTTON_X, "X / Square"),
    ControllerKeyOption(KeyEvent.KEYCODE_BUTTON_Y, "Y / Triangle"),
    ControllerKeyOption(KeyEvent.KEYCODE_BUTTON_L1, "L1"),
    ControllerKeyOption(KeyEvent.KEYCODE_BUTTON_R1, "R1"),
    ControllerKeyOption(KeyEvent.KEYCODE_BUTTON_START, "Start"),
    ControllerKeyOption(KeyEvent.KEYCODE_BUTTON_SELECT, "Select"),
    ControllerKeyOption(KeyEvent.KEYCODE_BUTTON_THUMBL, "Left Stick Click"),
    ControllerKeyOption(KeyEvent.KEYCODE_BUTTON_THUMBR, "Right Stick Click")
)

