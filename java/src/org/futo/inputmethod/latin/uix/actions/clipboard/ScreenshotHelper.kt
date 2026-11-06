package org.futo.inputmethod.latin.uix.actions.clipboard

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.actions.throwIfDebug
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.getSettingFlow
import org.futo.inputmethod.latin.uix.setSetting

val SupportsAddingScreenshots = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

/**
 * MediaStore id of the newest screenshot that has been processed. Persisted so that screenshots
 * taken while the keyboard process was not running are still picked up. -1 means that the next
 * time screenshots are enabled, only screenshots taken from then on are added.
 */
val ClipboardLastScreenshotId = SettingsKey(
    longPreferencesKey("clipboard_last_screenshot_id"),
    -1L
)

interface ScreenshotListener {
    /** [timestamp] is when the screenshot was taken, in milliseconds */
    fun onScreenshotAdded(mime: String, uri: Uri, timestamp: Long)
    fun onScreenshotChange(uri: Uri, checkTrashed: suspend () -> Boolean)
}

/**
 * Remembers the most recently captured screenshot so it can be offered for quick pasting in the
 * suggestion bar, like recently copied text.
 */
object RecentScreenshot {
    const val SUGGESTION_WINDOW_MS = 3L * 60L * 1000L

    @Volatile private var entry: ClipboardEntry? = null

    /** Emits whenever a new screenshot was added, so the suggestion bar can be refreshed */
    val updates = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun set(entry: ClipboardEntry) {
        this.entry = entry
        updates.tryEmit(Unit)
    }

    fun clearIfMatches(item: ClipboardEntry) {
        val curr = entry ?: return
        if(item.backingFile != null && curr.backingFile == item.backingFile) entry = null
    }

    /** Returns the latest screenshot if it was taken within the suggestion window */
    fun get(): ClipboardEntry? {
        val curr = entry ?: return null
        val age = System.currentTimeMillis() - curr.timestamp
        return if(age in 0..SUGGESTION_WINDOW_MS) curr else null
    }
}

class ScreenshotHelper(
    val context: Context,
    val lifecycleScope: LifecycleCoroutineScope,
    val listener: ScreenshotListener
) {
    companion object {
        private val TAG = "ScreenshotHelper"

        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_MEDIA_IMAGES
            else Manifest.permission.READ_EXTERNAL_STORAGE

        private val pathKey = when {
            Build.VERSION.SDK_INT >= 29 -> MediaStore.Images.Media.RELATIVE_PATH
            else -> MediaStore.Images.Media.DATA
        }

        // Screenshots are stored in a "Screenshots" folder on almost all devices (Pictures/Screenshots,
        // DCIM/Screenshots, ...), some use "Screenshot" or only name the file "Screenshot_...".
        // Both casings are listed in case LIKE is case-sensitive on some devices.
        private val screenshotSelection = listOf(
            "$pathKey LIKE ?", "$pathKey LIKE ?",
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?", "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        ).joinToString(" OR ", prefix = "(", postfix = ")")

        private val screenshotSelectionArgs = arrayOf(
            "%Screenshot%", "%screenshot%",
            "Screenshot%", "screenshot%"
        )
    }

    // Returns true if we can confirm that the given URI exists and is not trashed
    internal fun safeQueryExists(uri: Uri): Boolean {
        return try {
            if(Build.VERSION.SDK_INT >= 30) {
                // Using the IS_TRASHED column introduced in API 30
                contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.IS_TRASHED),
                    null, null, null
                )?.use { cursor ->
                    cursor.count > 0 && cursor.moveToFirst() && run {
                        val idx = cursor.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED)
                        (idx == -1) || (cursor.getInt(idx) == 0)
                    }
                } == true
            } else {
                contentResolver.query(
                    uri,
                    arrayOf(MediaStore.MediaColumns._ID),
                    null, null, null
                )?.use { cursor ->
                    cursor.count > 0 && cursor.moveToFirst()
                } == true
            }
        } catch (e: Exception) {
            // Assume IllegalStateException / SecurityException means it's gone
            false
        }
    }

    private val contentResolver = context.contentResolver
    private var lastSeenId: Long = -1L
    private var observer: ContentObserver? = null
    private val queryMutex = Mutex()

    private fun registerObserver() {
        lifecycleScope.launch { catchUp() }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)

                if(uri != null) {
                    listener.onScreenshotChange(uri) { withContext(Dispatchers.IO) {
                        !safeQueryExists(uri)
                    } }
                }


                lifecycleScope.launch {
                    // Give the screenshot app a moment to finish writing the file
                    delay(300L)
                    handleNewScreenshot()
                }
            }
        }
        this.observer = observer

        try {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            Log.d(TAG, "ContentObserver registered")
        }catch(e: Exception) {
            this.observer = null
            throwIfDebug(e)
        }
    }

    private fun unregisterObserver() {
        observer?.let {
            try {
                contentResolver.unregisterContentObserver(it)
            }catch(e: Exception) {
                throwIfDebug(e)
            }
        }
        observer = null
    }

    private fun hasPermission() = ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    private var settingsObservingJob: Job? = null

    fun start() {
        if(settingsObservingJob != null) return

        settingsObservingJob = lifecycleScope.launch {
            context.getSettingFlow(ClipboardSaveScreenshots).collect {
                if(it) {
                    recheck()
                } else {
                    if(observer != null) unregisterObserver()

                    // When re-enabled later, don't import every screenshot taken in the meantime
                    queryMutex.withLock {
                        lastSeenId = -1L
                        if(context.getSetting(ClipboardLastScreenshotId) != -1L) {
                            context.setSetting(ClipboardLastScreenshotId, -1L)
                        }
                    }
                }
            }
        }
    }

    /**
     * Starts observing screenshots if enabled. Safe to call often, e.g. after the permission
     * was granted or when the clipboard is opened.
     */
    fun recheck() {
        if(settingsObservingJob == null) return
        if(observer != null || !SupportsAddingScreenshots) return
        if(!context.getSetting(ClipboardSaveScreenshots) || !hasPermission()) return

        registerObserver()
    }

    fun onDestroy() {
        unregisterObserver()
        settingsObservingJob?.cancel()
        settingsObservingJob = null
    }

    // Picks up screenshots that were taken while we were not observing (e.g. the keyboard process
    // was not running), or only establishes a starting point if screenshots were just enabled
    private suspend fun catchUp(): Unit = withContext(Dispatchers.IO) {
        if(!SupportsAddingScreenshots) return@withContext
        if(!context.getSetting(ClipboardSaveScreenshots)) return@withContext

        queryMutex.withLock {
            val persisted = context.getSetting(ClipboardLastScreenshotId)
            if(persisted < 0L) {
                processLocked(dry = true)
            } else {
                val newest = queryNewestScreenshotId()
                lastSeenId = if(newest != null && newest < persisted) {
                    // The media database was likely rebuilt, so old ids are meaningless now
                    newest
                } else {
                    persisted
                }
                processLocked(dry = false)
            }
        }
    }

    private suspend fun queryNewestScreenshotId(): Long? = withContext(Dispatchers.IO) {
        try {
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, screenshotSelection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, screenshotSelectionArgs)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Images.Media._ID} DESC")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    putInt(ContentResolver.QUERY_ARG_LIMIT, 1)
                }
            }

            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                queryArgs,
                null
            )?.use { cursor ->
                if(cursor.moveToFirst()) {
                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                } else {
                    null
                }
            }
        } catch(e: Exception) {
            null
        }
    }

    internal suspend fun handleNewScreenshot(): Unit = withContext(Dispatchers.IO) {
        if(!SupportsAddingScreenshots) return@withContext
        if(!context.getSetting(ClipboardSaveScreenshots)) return@withContext

        queryMutex.withLock { processLocked(dry = false) }
    }

    // Must hold queryMutex
    private suspend fun processLocked(dry: Boolean) {
        val previousLastSeenId = lastSeenId
        queryAndNotify(dry)

        if(dry || lastSeenId != previousLastSeenId) {
            context.setSetting(ClipboardLastScreenshotId, lastSeenId.coerceAtLeast(0L))
        }
    }

    private fun queryAndNotify(dry: Boolean) {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_ADDED,
        )

        val selection = "$screenshotSelection AND ${MediaStore.Images.Media._ID} > ?"

        try {
            val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(
                    ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    screenshotSelectionArgs + lastSeenId.toString()
                )

                if(dry) {
                    putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.Images.Media._ID} DESC")

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        putInt(ContentResolver.QUERY_ARG_LIMIT, 1)
                    }
                } else {
                    putString(
                        ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                        "${MediaStore.Images.Media._ID} ASC"
                    )
                }
            }

            contentResolver.query(
                uri,
                projection,
                queryArgs,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return

                val idIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val dateIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)

                do {
                    val id = cursor.getLong(idIdx)
                    if(id <= lastSeenId) continue

                    lastSeenId = id
                    if(dry) break

                    val mime = cursor.getString(mimeIdx) ?: continue
                    val itemUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )

                    val now = System.currentTimeMillis()
                    val timestamp = if(dateIdx != -1 && !cursor.isNull(dateIdx)) {
                        // DATE_ADDED is in seconds
                        (cursor.getLong(dateIdx) * 1000L).takeIf { it in 1..now } ?: now
                    } else {
                        now
                    }

                    listener.onScreenshotAdded(mime, itemUri, timestamp)
                } while(cursor.moveToNext())
            }
        }catch(e: Exception) {
            if (e.message?.let {
                it.contains("volume", ignoreCase = true)
                    && it.contains("not found", ignoreCase = true)
            } == true) {
                return
            }

            throwIfDebug(e)
        }
    }
}
