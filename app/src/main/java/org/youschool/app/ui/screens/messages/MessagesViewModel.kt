package org.youschool.app.ui.screens.messages

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.youschool.app.domain.model.*
import org.youschool.app.domain.repository.SchoolRepository

class MessagesViewModel(private val repository: SchoolRepository) : ViewModel() {
    var threads by mutableStateOf(emptyList<MessageThread>()); private set
    var thread by mutableStateOf<MessageThread?>(null); private set
    var messages by mutableStateOf(emptyList<SchoolMessage>()); private set
    var recipients by mutableStateOf(emptyList<MessageRecipient>()); private set
    var choosingRecipient by mutableStateOf(false); private set
    var loadingThreads by mutableStateOf(false); private set
    var loadingMessages by mutableStateOf(false); private set
    var loadingRecipients by mutableStateOf(false); private set
    var sending by mutableStateOf(false); private set
    var creating by mutableStateOf(false); private set
    var moreThreads by mutableStateOf(true); private set
    var moreMessages by mutableStateOf(true); private set
    var listError by mutableStateOf<String?>(null); private set
    var messageError by mutableStateOf<String?>(null); private set
    var recipientError by mutableStateOf<String?>(null); private set
    var noReplyId by mutableStateOf<Long?>(null); private set
    private val draftPrefs = runCatching {
        val app = org.youschool.app.YouSchoolApp.instance
        app.getSharedPreferences("message_drafts_${app.authManager.contextKey()}", android.content.Context.MODE_PRIVATE)
    }.getOrNull()
    val drafts = mutableStateMapOf<Long, String>().apply {
        draftPrefs?.all?.forEach { (id, text) -> id.toLongOrNull()?.let { put(it, text.toString()) } }
    }
    fun setDraft(id: Long, text: String) { drafts[id] = text; draftPrefs?.edit()?.putString(id.toString(), text)?.apply() }

    private val sendIds = mutableMapOf<Long, Pair<String, String>>()
    private var threadCursor: Long? = null
    private var messageCursor: Long? = null
    private var historyJob: Job? = null
    private var readJob: Job? = null
    private var createJob: Job? = null
    val ownPrsId = repository.getUserProfile()?.prsId ?: -1L

    init { refreshThreads() }

    fun refreshThreads(older: Boolean = false) {
        if (loadingThreads) return
        loadingThreads = true
        listError = null
        viewModelScope.launch {
            repository.getMessageThreads(if (older) threadCursor else null).fold({ page ->
                threads = ((if (older) threads else emptyList()) + page).distinctBy { it.threadId }
                threadCursor = page.lastOrNull()?.msgNum
                moreThreads = page.size == 25
            }, { listError = "Не удалось загрузить переписки. Повторите попытку." })
            loadingThreads = false
        }
    }

    fun openThread(id: Long) {
        historyJob?.cancel()
        readJob?.cancel()
        choosingRecipient = false
        thread = threads.find { it.threadId == id } ?: MessageThread(threadId = id)
        messages = emptyList()
        noReplyId = null
        messageCursor = null
        loadHistory()
    }

    fun loadHistory(older: Boolean = false) {
        val id = thread?.threadId ?: return
        if (older && loadingMessages) return
        historyJob?.cancel()
        loadingMessages = true
        messageError = null
        historyJob = viewModelScope.launch {
            if (!older) {
                val detail = repository.getMessageThread(id)
                if (!isActive) return@launch
                if (detail.isFailure) {
                    messageError = "Не удалось загрузить переписку. Повторите попытку."
                    loadingMessages = false
                    return@launch
                }
                thread = detail.getOrThrow()
                noReplyId = repository.getNoReplyId().getOrNull()
            }
            repository.getMessages(id, if (older) messageCursor else null).fold({ page ->
                messages = ((if (older) messages else emptyList()) + page).distinctBy { it.msgId }.sortedByDescending { it.msgNum }
                messageCursor = page.minOfOrNull { it.msgNum }
                moreMessages = page.size == 25
                readJob?.cancel()
                readJob = viewModelScope.launch {
                    for (message in page.filter { it.senderId != ownPrsId }) {
                        if (repository.markMessageRead(message.msgId).isFailure) {
                            messageError = "Сообщения загружены, но отметка прочтения не сохранена. Обновите переписку."
                            break
                        }
                    }
                }
            }, { messageError = "Не удалось загрузить сообщения. Повторите попытку." })
            loadingMessages = false
        }
    }

    fun chooseRecipient() {
        choosingRecipient = true
        loadRecipients()
    }

    fun loadRecipients() {
        if (loadingRecipients) return
        loadingRecipients = true
        recipientError = null
        viewModelScope.launch {
            repository.getMessageRecipients().fold({ recipients = it.filter { person -> person.prsId != ownPrsId } }, {
                recipientError = "Не удалось загрузить получателей. Повторите попытку."
            })
            loadingRecipients = false
        }
    }

    fun createThread(recipient: MessageRecipient) {
        if (creating) return
        creating = true
        recipientError = null
        createJob = viewModelScope.launch {
            try {
                repository.createPrivateThread(recipient.prsId).fold({ openThread(it) }, {
                    recipientError = "Не удалось открыть переписку. Обновите список перед повторной попыткой."
                })
            } finally { creating = false }
        }
    }

    fun back() {
        createJob?.cancel()
        creating = false
        if (choosingRecipient) choosingRecipient = false else {
            historyJob?.cancel()
            readJob?.cancel()
            loadingMessages = false
            thread = null
            refreshThreads()
        }
    }

    fun send() {
        val target = thread ?: return
        val text = drafts[target.threadId].orEmpty()
        val noReply = noReplyId ?: return
        if (sending || text.isBlank() || !target.canSend(ownPrsId, noReply)) return
        val uid = sendIds[target.threadId]?.takeIf { it.first == text }?.second ?: System.currentTimeMillis().toString()
        sendIds[target.threadId] = text to uid
        sending = true
        messageError = null
        viewModelScope.launch {
            repository.sendMessage(target.threadId, text, uid).fold({
                if (drafts[target.threadId] == text) { drafts.remove(target.threadId); draftPrefs?.edit()?.remove(target.threadId.toString())?.apply() }
                sendIds.remove(target.threadId)
                if (thread?.threadId == target.threadId) loadHistory()
            }, {
                if (thread?.threadId == target.threadId) messageError =
                    "Отправка не подтверждена. Текст сохранён. Обновите переписку перед повторной отправкой."
            })
            sending = false
        }
    }
}
