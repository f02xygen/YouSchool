package org.youschool.app.ui.util
import androidx.compose.runtime.Composable
object DesktopHaptic {
    fun click() {}
    fun tick() {}
    fun confirm() {}
    fun reject() {}
}
@Composable fun rememberHaptic() = DesktopHaptic
