package org.youschool.app.ui.screens.profile

import androidx.compose.runtime.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.youschool.app.core.auth.AuthManager
import org.youschool.app.core.network.EschoolApiClient

@Composable
fun OrganizationPicker(auth: AuthManager, api: EschoolApiClient, required: Boolean = false, onLogout: () -> Unit = { auth.clear() }) {
    val revision by auth.revision.collectAsState()
    val positionError by auth.positionError.collectAsState()
    var open by remember { mutableStateOf(required) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(required) { if (required) open = true }
    if (!required) ListItem(
        headlineContent = { Text("Организация и роль") },
        supportingContent = { Text("Ученик · ${auth.getSchoolName()}") },
        modifier = Modifier.clickable { open = true; scope.launch { api.refreshPositions().onFailure { error = it.message } } }
    )
    if (open) AlertDialog(
        onDismissRequest = { if (!required && !busy) open = false },
        title = { Text("Организация и роль") },
        text = { Column {
            Text("Выберите ученический профиль")
            if (auth.positions.isEmpty()) Text("Нет доступных ученических профилей. Обновите список или войдите снова.")
            auth.positions.forEach { position ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 4.dp).selectable(
                    selected = position.userId == auth.getUserId(), enabled = !busy, role = Role.RadioButton, onClick = {
                        busy = true; error = null
                        scope.launch {
                            api.changePosition(position.userId!!).onSuccess { open = false }.onFailure { error = it.message }
                            busy = false
                        }
                }), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RadioButton(selected = position.userId == auth.getUserId(), enabled = !busy, onClick = null)
                    Text("Ученик — ${position.orgName ?: position.orgFullName ?: "Организация"}", Modifier.padding(top = 12.dp))
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            (error ?: positionError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = { scope.launch { api.refreshPositions().onFailure { error = it.message } } }) { Text("Обновить") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { if (required) onLogout() else open = false }) { Text(if (required) "Выйти из аккаунта" else "Отмена") } }
    )
}
