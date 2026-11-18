package org.youschool.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.youschool.app.domain.model.*
import org.youschool.app.core.database.entities.PendingHomeworkUploadEntity
import org.youschool.app.domain.repository.SchoolRepository
import org.youschool.app.ui.screens.messages.MessagesViewModel
import java.io.File
import java.io.IOException

internal abstract class UnusedRepository : SchoolRepository {
    override val positionState = kotlinx.coroutines.flow.MutableStateFlow(PositionState())
    override suspend fun initializePositions() {}
    override suspend fun switchPosition(positionUserId: Long): Result<Unit> = error("Unexpected repository call")
    override fun getMessageDrafts(scope: SchoolContext): Map<Long, String> = emptyMap()
    override fun saveMessageDraft(scope: SchoolContext, threadId: Long, text: String) {}

    override suspend fun getMessageThreads(before: Long?): Result<List<MessageThread>> = error("Unexpected repository call")
    override suspend fun getMessageThread(id: Long): Result<MessageThread> = error("Unexpected repository call")
    override suspend fun getMessages(id: Long, before: Long?): Result<List<SchoolMessage>> = error("Unexpected repository call")
    override suspend fun getMessageRecipients(): Result<List<MessageRecipient>> = error("Unexpected repository call")
    override suspend fun getNoReplyId(): Result<Long> = error("Unexpected repository call")
    override suspend fun createPrivateThread(recipientId: Long): Result<Long> = error("Unexpected repository call")
    override suspend fun sendMessage(threadId: Long, text: String, uid: String): Result<Unit> = error("Unexpected repository call")
    override suspend fun markMessageRead(id: Long): Result<Unit> = error("Unexpected repository call")
    override fun getLessonsForDay(dateMillis: Long): Flow<List<Lesson>> = error("Unexpected repository call")
    override fun getAllHomeworks(): Flow<List<Lesson>> = error("Unexpected repository call")
    override fun getUpcomingHomeworks(): Flow<List<Lesson>> = error("Unexpected repository call")
    override fun getAllGrades(): Flow<List<Grade>> = error("Unexpected repository call")
    override fun getNewGrades(): Flow<List<Grade>> = error("Unexpected repository call")
    override fun getSubjectAverages(): Flow<List<SubjectAverage>> = error("Unexpected repository call")
    override suspend fun markGradeAsRead(markKey: String) = error("Unexpected repository call")
    override suspend fun markAllGradesAsRead() = error("Unexpected repository call")
    override suspend fun unmarkAllGradesAsRead() {}
    override suspend fun restoreGradeReadStatus(markKeys: List<String>) = error("Unexpected repository call")
    override suspend fun downloadHomeworkFile(fileId: Long, fileName: String, variantId: Long?): Result<File> = error("Unexpected repository call")
    override suspend fun syncDayLessons(dateMillis: Long): Result<Unit> = error("Unexpected repository call")
    override suspend fun syncGrades(): Result<Unit> = error("Unexpected repository call")
    override suspend fun toggleHomeworkStatus(lessonId: Long, isDone: Boolean, comment: String?) = error("Unexpected repository call")
    override suspend fun syncPendingHomeworkStatus(): Result<Unit> = error("Unexpected repository call")
    override suspend fun enqueueHomeworkResultUpload(partId: Long, file: File): Result<PendingHomeworkUploadEntity> = error("Unexpected repository call")
    override fun getPendingHomeworkUploads(partId: Long): Flow<List<PendingHomeworkUploadEntity>> = error("Unexpected repository call")
    override suspend fun syncPendingHomeworkUploads(): Result<Unit> = error("Unexpected repository call")
    override suspend fun removePendingHomeworkUpload(uploadId: Long): Result<Unit> = error("Unexpected repository call")
    override fun getPendingSyncCount(): Flow<Int> = error("Unexpected repository call")
    override suspend fun syncAllPending(): Result<Unit> = error("Unexpected repository call")
    override suspend fun getHomeworkResultFiles(partId: Long): Result<List<HomeworkResultFile>> = error("Unexpected repository call")
    override suspend fun uploadHomeworkResult(partId: Long, file: File): Result<HomeworkResultFile> = error("Unexpected repository call")
    override suspend fun deleteHomeworkResult(partId: Long, fileId: Long): Result<Unit> = error("Unexpected repository call")
    override suspend fun login(login: String, rawPassword: String, captchaToken: String?): Result<UserProfile> = error("Unexpected repository call")
    override fun isUserLoggedIn(): Boolean = error("Unexpected repository call")
    override fun getUserProfile(): UserProfile? = error("Unexpected repository call")
    override fun logout() = error("Unexpected repository call")
}

private class MessageTestRepository : UnusedRepository() {
    val creation = CompletableDeferred<Long>()
    val sentIds = mutableListOf<String>()
    val thread = MessageThread(1, senderId = 2)
    override fun getUserProfile() = UserProfile(1, 1, "Тест", "Школа", "ROLE_STUDENT")
    override suspend fun getMessageThreads(before: Long?) = Result.success(listOf(thread))
    override suspend fun getMessageThread(id: Long) = Result.success(thread)
    override suspend fun getNoReplyId() = Result.success(99L)
    override suspend fun getMessages(id: Long, before: Long?) = Result.success(emptyList<SchoolMessage>())
    override suspend fun getMessageRecipients() = Result.success(emptyList<MessageRecipient>())
    override suspend fun createPrivateThread(recipientId: Long) = Result.success(creation.await())
    override suspend fun sendMessage(threadId: Long, text: String, uid: String): Result<Unit> {
        sentIds.add(uid)
        return Result.failure(IOException("Simulated network failure"))
    }
}

@RunWith(AndroidJUnit4::class)
class MessagesViewModelTest {
    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    @Test fun failedSendKeepsDraftAndCorrelationId() {
        main {
            val repository = MessageTestRepository()
            val vm = MessagesViewModel(repository)
            val store = ViewModelStore().apply { put("messages", vm) }
            try {
                vm.openThread(1)
                vm.drafts[1] = "Первая строка\nВторая строка"
                vm.send()
                assertEquals("Первая строка\nВторая строка", vm.drafts[1])
                assertNotNull(vm.messageError)
                assertFalse(vm.sending)
                vm.send()
                assertEquals(2, repository.sentIds.size)
                assertEquals(repository.sentIds[0], repository.sentIds[1])
            } finally { store.clear() }
        }
    }

    @Test fun backCancelsCreationWithoutOpeningLateResult() {
        val repository = MessageTestRepository()
        lateinit var vm: MessagesViewModel
        val store = ViewModelStore()
        main {
            vm = MessagesViewModel(repository)
            store.put("messages", vm)
            vm.chooseRecipient()
            vm.createThread(MessageRecipient(2, "Тестовый получатель"))
            assertTrue(vm.creating)
            vm.back()
            assertFalse(vm.choosingRecipient)
            assertFalse(vm.creating)
            assertNull(vm.thread)
            repository.creation.complete(42)
        }
        main {
            try { assertNull(vm.thread) } finally { store.clear() }
        }
    }
}
