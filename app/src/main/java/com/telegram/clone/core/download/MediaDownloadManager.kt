package com.telegram.clone.core.download

import android.content.Context
import android.util.Log
import com.telegram.clone.core.settings.AppSettingsManager
import com.telegram.clone.core.util.GallerySaver
import com.telegram.clone.data.repository.TelegramRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Bulk media download engine (Nekogram-style "download all / selected").
 *
 * - Accepts a queue of [DownloadTask]s from the chat room (selected messages)
 *   or from the "download all media" action (SearchChatMessages results).
 * - Downloads run with bounded parallelism (configurable 1-8 connections,
 *   pushed to TDLib via the `connections_for_media_download` option as well).
 * - Progress is exposed as a [StateFlow] map so any screen can render a live
 *   progress panel.
 * - Finished files are copied into the public gallery via [GallerySaver].
 * - Files that are already fully downloaded locally are saved immediately.
 */
class MediaDownloadManager private constructor(context: Context) {

    companion object {
        private const val TAG = "MediaDownloadManager"

        /** Hard cap per single download: 60 minutes (large videos). */
        private const val DOWNLOAD_TIMEOUT_MS = 60L * 60_000L

        @Volatile
        private var INSTANCE: MediaDownloadManager? = null

        fun getInstance(context: Context): MediaDownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MediaDownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    enum class Status { QUEUED, DOWNLOADING, SAVING, DONE, FAILED, CANCELLED }

    data class DownloadTask(
        val fileId: Int,
        val fileName: String,
        val isVideo: Boolean,
        val chatId: Long,
        val messageId: Long
    )

    data class DownloadItem(
        val id: String,
        val fileId: Int,
        val fileName: String,
        val isVideo: Boolean,
        val chatId: Long,
        val status: Status,
        val progress: Int,
        val resultMessage: String? = null
    )

    private val repository = TelegramRepository.getInstance()
    private val settings = AppSettingsManager.getInstance(context)
    private val appContext = context.applicationContext

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()

    /** Bounded-parallelism gate: honours the live "download connections" setting. */
    private val slotMutex = Mutex()
    private var activeSlots = 0

    private suspend fun awaitSlot() {
        while (true) {
            var acquired = false
            slotMutex.withLock {
                if (activeSlots < connections()) {
                    activeSlots++
                    acquired = true
                }
            }
            if (acquired) return
            delay(150)
        }
    }

    private suspend fun releaseSlot() {
        slotMutex.withLock { activeSlots-- }
    }

    private val _items =
        MutableStateFlow<Map<String, DownloadItem>>(LinkedHashMap())
    val items: StateFlow<Map<String, DownloadItem>> = _items.asStateFlow()

    /** Number of items currently downloading or queued. */
    val activeCount: Int
        get() = _items.value.values.count {
            it.status == Status.QUEUED || it.status == Status.DOWNLOADING || it.status == Status.SAVING
        }

    /** Number of items finished successfully since the last clear. */
    val doneCount: Int
        get() = _items.value.values.count { it.status == Status.DONE }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Enqueues one file. Returns the item id (or null when the file was
     * rejected — e.g. no remote file / duplicate already tracked).
     */
    fun enqueue(task: DownloadTask): String? {
        if (task.fileId == 0) return null
        val id = taskKey(task)
        val existing = _items.value[id]
        if (existing != null &&
            existing.status in listOf(Status.QUEUED, Status.DOWNLOADING, Status.SAVING, Status.DONE)
        ) {
            return id // already queued / running / saved
        }

        _items.value = _items.value + (id to DownloadItem(
            id = id,
            fileId = task.fileId,
            fileName = task.fileName,
            isVideo = task.isVideo,
            chatId = task.chatId,
            status = Status.QUEUED,
            progress = 0
        ))

        val job = scope.launch {
            var acquiredSlot = false
            try {
                awaitSlot()
                acquiredSlot = true
                runItem(id, task)
            } finally {
                // Only release a slot that was actually acquired — if the
                // coroutine is cancelled while waiting in [awaitSlot], no
                // slot was taken and activeSlots must not go negative.
                if (acquiredSlot) releaseSlot()
            }
        }
        jobs[id] = job
        return id
    }

    /** Enqueues a batch; returns how many were actually queued. */
    fun enqueueAll(tasks: List<DownloadTask>): Int {
        var queued = 0
        tasks.forEach { if (enqueue(it) != null) queued++ }
        return queued
    }

    /** Cancels a running/queued download (and the TDLib fetch itself). */
    fun cancel(itemId: String) {
        jobs.remove(itemId)?.cancel()
        val current = _items.value[itemId] ?: return
        repository.cancelDownloadFile(current.fileId)
        update(itemId) {
            it.copy(status = Status.CANCELLED, resultMessage = "Dibatalkan")
        }
    }

    /** Cancels every active download. */
    fun cancelAll() {
        _items.value.values
            .filter { it.status in listOf(Status.QUEUED, Status.DOWNLOADING, Status.SAVING) }
            .forEach { cancel(it.id) }
    }

    /** Removes finished / cancelled / failed entries from the panel. */
    fun clearFinished() {
        _items.value = _items.value.filterValues {
            it.status in listOf(Status.QUEUED, Status.DOWNLOADING, Status.SAVING)
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun connections(): Int =
        settings.downloadConnections.value.coerceIn(1, 8)

    private fun taskKey(task: DownloadTask): String =
        "${task.fileId}_${task.messageId}"

    private fun update(itemId: String, transform: (DownloadItem) -> DownloadItem) {
        val current = _items.value
        val item = current[itemId] ?: return
        _items.value = current + (itemId to transform(item))
    }

    private suspend fun runItem(itemId: String, task: DownloadTask) {
        try {
            update(itemId) { it.copy(status = Status.DOWNLOADING, progress = 0) }

            // Subscribe FIRST so no UpdateFile can be missed, then ask TDLib
            // to download. If the file is already local this resolves fast.
            val completed = CompletableDeferred<TdApi.File>()

            val collector = scope.launch {
                repository.fileFlow.collect { file ->
                    if (file.id != task.fileId) return@collect
                    val expected = if (file.expectedSize > 0) file.expectedSize else file.size.toLong()
                    val pct = if (expected > 0) {
                        ((file.local.downloadedPrefixSize * 100L) / expected).toInt().coerceIn(0, 100)
                    } else 0
                    update(itemId) { it.copy(progress = pct) }
                    if (file.local.isDownloadingCompleted && !file.local.path.isNullOrEmpty()) {
                        completed.complete(file)
                    }
                }
            }

            if (task.fileId != 0) {
                repository.downloadFile(task.fileId, priority = 16)
            }

            val file = withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) { completed.await() }
            collector.cancel()

            val path = file?.local?.path
            if (path.isNullOrEmpty() || !File(path).exists()) {
                update(itemId) {
                    it.copy(status = Status.FAILED, resultMessage = "Tamat masa / gagal")
                }
                return
            }

            update(itemId) { it.copy(status = Status.SAVING, progress = 100) }
            val message = GallerySaver.saveToGallery(
                context = appContext,
                sourcePath = path,
                isVideo = task.isVideo,
                originalName = task.fileName,
                asDocument = false
            )
            val success = message == "Disimpan ke galeri"
            update(itemId) {
                it.copy(
                    status = if (success) Status.DONE else Status.FAILED,
                    resultMessage = message
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            update(itemId) { it.copy(status = Status.CANCELLED, resultMessage = "Dibatalkan") }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $itemId", e)
            update(itemId) {
                it.copy(status = Status.FAILED, resultMessage = e.message ?: "Ralat")
            }
        }
    }

    /**
     * Pushes the user's parallel-connection preference into TDLib so real
     * downloads use the same concurrency as this queue. Non-fatal on error:
     * older TDLib builds simply ignore the option.
     */
    fun applyTdLibDownloadConnections() {
        val value = connections()
        repository.setOptionInt("connections_for_media_download", value)
    }

    /** Small helper used by the settings screen to show a human label. */
    fun statusLabel(status: Status): String = when (status) {
        Status.QUEUED -> "Menunggu"
        Status.DOWNLOADING -> "Memuat turun"
        Status.SAVING -> "Menyimpan"
        Status.DONE -> "Selesai"
        Status.FAILED -> "Gagal"
        Status.CANCELLED -> "Dibatalkan"
    }
}
