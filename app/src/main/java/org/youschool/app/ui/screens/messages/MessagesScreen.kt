package org.youschool.app.ui.screens.messages

import android.text.Html
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.youschool.app.domain.repository.SchoolRepository
import org.youschool.app.ui.theme.LocalBottomBarPadding
import java.text.DateFormat
import java.util.Date

internal fun messagePlainText(html: String): String = Html.fromHtml(html.replace("\n", "<br>"), Html.FROM_HTML_MODE_COMPACT).toString().trim()
private fun dateText(value: Long): String = if (value > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value)) else ""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(repository: SchoolRepository) {
    val vm: MessagesViewModel = viewModel { MessagesViewModel(repository) }
    val thread = vm.thread
    BackHandler(enabled = thread != null || vm.choosingRecipient) { vm.back() }
    Column(Modifier.fillMaxSize().padding(bottom = LocalBottomBarPadding.current).imePadding()) {
        TopAppBar(
            title = { Text(if (vm.choosingRecipient) "Получатель" else thread?.title ?: "Сообщения", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                if (thread != null || vm.choosingRecipient) IconButton(onClick = { vm.back() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                }
            },
            actions = {
                if (!vm.choosingRecipient) {
                    IconButton(onClick = { if (thread == null) vm.refreshThreads() else vm.loadHistory() }, enabled = !vm.loadingThreads && !vm.loadingMessages) {
                        Icon(Icons.Rounded.Refresh, "Обновить")
                    }
                    if (thread == null) IconButton(onClick = { vm.chooseRecipient() }) { Icon(Icons.Rounded.Edit, "Новое сообщение") }
                }
            }
        )
        when {
            vm.choosingRecipient -> {
                var query by rememberSaveable { mutableStateOf("") }
                OutlinedTextField(query, { query = it }, label = { Text("Имя или класс") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                if (vm.loadingRecipients || vm.creating) LinearProgressIndicator(Modifier.fillMaxWidth())
                vm.recipientError?.let { ErrorRow(it, { vm.loadRecipients() }) }
                val filtered = vm.recipients.filter { query.isBlank() || "${it.fio} ${it.groupName}".contains(query.trim(), ignoreCase = true) }
                LazyColumn(Modifier.weight(1f)) {
                    if (filtered.isEmpty() && !vm.loadingRecipients) item { EmptyText("Получатели не найдены") }
                    items(filtered, key = { it.prsId }) { person ->
                        ListItem(
                            headlineContent = { Text(person.fio) },
                            supportingContent = { if (person.groupName.isNotBlank()) Text(person.groupName) },
                            modifier = Modifier.clickable(enabled = !vm.creating) { vm.createThread(person) }
                        )
                        HorizontalDivider()
                    }
                }
            }
            thread == null -> {
                if (vm.loadingThreads) LinearProgressIndicator(Modifier.fillMaxWidth())
                vm.listError?.let { ErrorRow(it, { vm.refreshThreads() }) }
                LazyColumn(Modifier.weight(1f)) {
                    if (vm.threads.isEmpty() && !vm.loadingThreads && vm.listError == null) item { EmptyText("Пока нет переписок. Начните новое сообщение.") }
                    items(vm.threads, key = { it.threadId }) { item ->
                        ListItem(
                            headlineContent = { Text(item.title, fontWeight = if (item.newReplayCount > 0) FontWeight.Bold else FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(messagePlainText(item.msgPreview), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            trailingContent = { if (item.newReplayCount > 0) Badge { Text(item.newReplayCount.toString()) } },
                            modifier = Modifier.clickable { vm.openThread(item.threadId) }
                        )
                        HorizontalDivider()
                    }
                    if (vm.moreThreads && vm.threads.isNotEmpty()) item {
                        TextButton(onClick = { vm.refreshThreads(older = true) }, enabled = !vm.loadingThreads, modifier = Modifier.fillMaxWidth()) { Text("Загрузить ещё") }
                    }
                }
            }
            else -> {
                if (vm.loadingMessages) LinearProgressIndicator(Modifier.fillMaxWidth())
                vm.messageError?.let { ErrorRow(it, { vm.loadHistory() }) }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = true, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (vm.messages.isEmpty() && !vm.loadingMessages && vm.messageError == null) item { EmptyText("В этой переписке пока нет сообщений") }
                    items(vm.messages, key = { it.msgId }) { message ->
                        val own = message.senderId == vm.ownPrsId
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (own) Arrangement.End else Arrangement.Start) {
                            Surface(color = if (own) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(0.9f)) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(message.senderFio, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(messagePlainText(message.msg), style = MaterialTheme.typography.bodyLarge)
                                    if (message.attachCount > 0) Text("Вложения: ${message.attachCount}", style = MaterialTheme.typography.labelMedium)
                                    Text(dateText(message.showDate), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    if (vm.moreMessages && vm.messages.isNotEmpty()) item {
                        TextButton(onClick = { vm.loadHistory(older = true) }, enabled = !vm.loadingMessages, modifier = Modifier.fillMaxWidth()) { Text("Более ранние сообщения") }
                    }
                }
                val allowed = vm.noReplyId?.let { thread.canSend(vm.ownPrsId, it) } == true
                if (allowed) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom) {
                        OutlinedTextField(
                            value = vm.drafts[thread.threadId].orEmpty(), onValueChange = { vm.setDraft(thread.threadId, it) },
                            placeholder = { Text("Сообщение") }, maxLines = 5, enabled = !vm.sending,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { vm.send() }, enabled = !vm.sending && !vm.loadingMessages && vm.drafts[thread.threadId].orEmpty().isNotBlank()) {
                            if (vm.sending) CircularProgressIndicator(Modifier.size(24.dp)) else Icon(Icons.AutoMirrored.Rounded.Send, "Отправить")
                        }
                    }
                } else if (!vm.loadingMessages) {
                    Text("Отправка недоступна. Диалог закрыт, ответ запрещён или права не удалось загрузить.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ErrorRow(message: String, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = retry) { Text("Обновить") }
    }
}

@Composable
private fun EmptyText(text: String) { Text(text, modifier = Modifier.fillMaxWidth().padding(24.dp), style = MaterialTheme.typography.bodyLarge) }
