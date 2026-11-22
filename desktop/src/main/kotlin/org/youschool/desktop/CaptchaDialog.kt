package org.youschool.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import netscape.javascript.JSObject
import java.net.URI

class CaptchaBridge(private val received: (String) -> Unit) {
    fun onToken(token: String) { if (token.isNotBlank()) received(token) }
}

/** Run the same reCAPTCHA challenge as Android, on the actual eSchool origin. */
@Composable
fun CaptchaDialog(onToken: (String) -> Unit, onDismiss: () -> Unit) {
    val latestToken by rememberUpdatedState(onToken)
    val bridge = remember { CaptchaBridge { token -> javax.swing.SwingUtilities.invokeLater { latestToken(token) } } }
    var webView by remember { mutableStateOf<WebView?>(null) }
    DisposableEffect(Unit) { onDispose { Platform.runLater { webView?.engine?.load(null); webView = null } } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Подтвердите вход") }, text = {
        SwingPanel(modifier = Modifier.width(420.dp).height(460.dp), factory = {
            JFXPanel().apply {
                Platform.setImplicitExit(false)
                Platform.runLater {
                    val view = WebView(); webView = view
                    view.engine.loadWorker.stateProperty().addListener { _, _, state ->
                        if (state == Worker.State.SUCCEEDED && runCatching { URI(view.engine.location).host == "app.eschool.center" }.getOrDefault(false)) {
                            (view.engine.executeScript("window") as JSObject).setMember("youschoolCaptcha", bridge)
                            view.engine.executeScript("""
                                document.body.innerHTML = '<div id="challenge" style="padding:24px"></div>';
                                window.youschoolReady = function() {
                                    grecaptcha.render('challenge', {sitekey:'6LfXOhYUAAAAAJ40iBnYX47lqtIkd2Wz-hAL9gNM', callback:function(token) { window.youschoolCaptcha.onToken(token); }});
                                };
                                if (window.grecaptcha && grecaptcha.render) { youschoolReady(); }
                                else { var script=document.createElement('script'); script.src='https://www.google.com/recaptcha/api.js?onload=youschoolReady&render=explicit'; document.head.appendChild(script); }
                            """.trimIndent())
                        }
                    }
                    scene = Scene(view)
                    view.engine.load("https://app.eschool.center/")
                }
            }
        })
    }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}
