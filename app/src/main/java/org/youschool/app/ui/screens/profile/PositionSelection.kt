package org.youschool.app.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.youschool.app.domain.model.PositionState

@Composable
fun PositionSelection(
    state: PositionState,
    onSelect: (Long) -> Unit,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Организация и роль", style = MaterialTheme.typography.headlineSmall)
            Text("Выберите ученический профиль. Расписание, оценки и сообщения относятся к выбранной организации.",
                style = MaterialTheme.typography.bodyLarge)
            if (state.busy || !state.initialized) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Подтверждаем организацию…")
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.positions.forEach { position ->
                val selected = state.active?.positionId == position.userId
                Row(Modifier.fillMaxWidth().selectable(selected, enabled = !state.busy,
                    role = Role.RadioButton, onClick = { onSelect(position.userId) }).heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected, onClick = null, enabled = !state.busy)
                    Text(position.label, modifier = Modifier.padding(start = 12.dp))
                }
                HorizontalDivider()
            }
            if (!state.busy) {
                if (state.positions.isEmpty()) Button(onClick = onRetry) { Text("Повторить загрузку") }
                else if (state.error != null) TextButton(onClick = onRetry) { Text("Обновить список") }
                if (onBack != null) TextButton(onClick = onBack) { Text("Назад") }
                TextButton(onClick = onLogout) { Text("Выйти из аккаунта") }
            }
        }
    }
}
