package org.youschool.app.core.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.youschool.app.core.network.NetworkMonitor
import org.youschool.app.domain.repository.SchoolRepository

data class SyncStatus(
    val isOnline: Boolean,
    val pendingCount: Int,
    val isSyncing: Boolean,
    val lastSyncError: String? = null
) {
    val isOfflineMode: Boolean get() = !isOnline || pendingCount > 0
}

class SyncManager(
    private val repository: SchoolRepository,
    private val networkMonitor: NetworkMonitor,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    val syncStatus: StateFlow<SyncStatus> = combine(
        networkMonitor.isOnline,
        repository.getPendingSyncCount(),
        _isSyncing,
        _lastError
    ) { isOnline, pendingCount, isSyncing, lastError ->
        val status = SyncStatus(
            isOnline = isOnline,
            pendingCount = pendingCount,
            isSyncing = isSyncing,
            lastSyncError = lastError
        )
        android.util.Log.d("SyncManager", "Status updated: $status, isOfflineMode=${status.isOfflineMode}")
        status
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = SyncStatus(
            isOnline = networkMonitor.isOnline.value,
            pendingCount = 0,
            isSyncing = false
        )
    )

    init {
        scope.launch {
            combine(networkMonitor.isOnline, repository.positionState) { online, position -> online && position.active != null }
                .distinctUntilChanged().collect { online ->
                if (online) {
                    triggerSync()
                }
            }
        }
    }

    fun triggerSync() {
        if (_isSyncing.value || !networkMonitor.isOnline.value) return
        scope.launch {
            _isSyncing.value = true
            _lastError.value = null
            try {
                val result = repository.syncAllPending()
                if (result.isFailure) {
                    _lastError.value = result.exceptionOrNull()?.message ?: "Ошибка синхронизации"
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _lastError.value = e.message
            } finally {
                _isSyncing.value = false
            }
        }
    }
}
