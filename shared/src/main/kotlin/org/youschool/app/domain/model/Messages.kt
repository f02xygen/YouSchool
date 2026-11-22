package org.youschool.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MessageThread(
    val threadId: Long,
    val msgNum: Long = 0,
    val senderId: Long = 0,
    val senderFio: String = "",
    val subject: String = "",
    val msgPreview: String = "",
    val showDate: Long = 0,
    val newReplayCount: Int = 0,
    val dlgType: Int = 0,
    val isAllowReplay: Int = 0,
    val addrCnt: Int = 0,
    val closeDate: Long? = null,
    val senderInvalid: Boolean = false,
    val adminIds: List<Long> = emptyList()
) {
    val title: String get() = subject.ifBlank { senderFio }.ifBlank { "Переписка" }

    fun canSend(prsId: Long, noReplyId: Long): Boolean {
        val group = dlgType == 2
        val owner = senderId == prsId || prsId in adminIds
        return (closeDate == null || closeDate == 0L) && senderId != noReplyId &&
            (if (group) addrCnt > 0 && (isAllowReplay != 0 || owner) else !senderInvalid)
    }
}

@Serializable
data class SchoolMessage(
    val msgId: Long,
    val msgNum: Long,
    val senderId: Long = 0,
    val senderFio: String = "",
    val msg: String = "",
    val showDate: Long = 0,
    val attachCount: Int = 0
)

@Serializable
data class MessageRecipient(
    val prsId: Long,
    val fio: String,
    val groupName: String = "",
    val out: Boolean = false
)

@Serializable
data class SendMessageResponse(val msgId: Long, val prsCount: Int = 0) {
    fun requireSuccess() {
        check(msgId > 0 && prsCount >= 0) {
            when {
                msgId == -5L || prsCount == -6 -> "Диалог закрыт"
                msgId == -6L || prsCount == -8 -> "Диалог удалён"
                else -> "Сервер не разрешил отправку сообщения"
            }
        }
    }
}
