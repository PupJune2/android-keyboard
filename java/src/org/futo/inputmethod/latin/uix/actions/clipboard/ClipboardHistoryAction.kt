package org.futo.inputmethod.latin.uix.actions.clipboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.collection.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.util.AtomicFile
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.permissions.PermissionsManager
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionHeaderSearch
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.DialogRequestItem
import org.futo.inputmethod.latin.uix.PersistentActionState
import org.futo.inputmethod.latin.uix.PersistentStateInitialization
import org.futo.inputmethod.latin.uix.QuickClip
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.actions.BugInfo
import org.futo.inputmethod.latin.uix.actions.BugViewerAction
import org.futo.inputmethod.latin.uix.actions.BugViewerState
import org.futo.inputmethod.latin.uix.actions.PasteAction
import org.futo.inputmethod.latin.uix.actions.fonttyper.SuperheroRenderer
import org.futo.inputmethod.latin.uix.actions.throwIfDebug
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.getSettingBlocking
import org.futo.inputmethod.latin.uix.getUnlockedSetting
import org.futo.inputmethod.latin.uix.isDirectBootUnlocked
import org.futo.inputmethod.latin.uix.setSetting
import org.futo.inputmethod.latin.uix.settings.DropDownPickerSettingItem
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.ScrollableList
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.SettingToggleRaw
import org.futo.inputmethod.latin.uix.settings.SettingsActivity
import org.futo.inputmethod.latin.uix.settings.UserSetting
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.pages.ParagraphText
import org.futo.inputmethod.latin.uix.settings.pages.PaymentSurface
import org.futo.inputmethod.latin.uix.settings.pages.PaymentSurfaceHeading
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.inputmethod.latin.uix.settings.userSettingDecorationOnly
import org.futo.inputmethod.latin.uix.settings.userSettingToggleDataStore
import org.futo.inputmethod.latin.uix.theme.Typography
import java.io.File
import java.security.MessageDigest
import java.util.Collections
import java.util.Locale

val ClipboardHistoryEnabled = SettingsKey(
    booleanPreferencesKey("enableClipboardHistory"),
    true
)

/**
 * When disabled (the default), the clipboard history is kept forever: nothing is removed unless
 * the user deletes it. When enabled, unpinned clips are removed according to the limits below.
 * Pinned clips are never removed automatically.
 */
val ClipboardAutoDeleteEnabled = SettingsKey(
    booleanPreferencesKey("clipboard_auto_delete_enabled"),
    false
)

/** Unpinned clips older than this many minutes are removed. 0 = no time limit. */
val ClipboardAutoDeleteAfterMinutes = SettingsKey(
    intPreferencesKey("clipboard_auto_delete_after_minutes"),
    24 * 60
)

/** Only this many of the newest unpinned clips are kept. 0 = no limit. */
val ClipboardAutoDeleteMaxItems = SettingsKey(
    intPreferencesKey("clipboard_auto_delete_max_items"),
    0
)

/** Unpinned images are removed (oldest first) once they use more than this. 0 = no limit. */
val ClipboardAutoDeleteImageStorageMb = SettingsKey(
    intPreferencesKey("clipboard_auto_delete_image_storage_mb"),
    0
)

val ClipboardAutoDeleteDurationOptions = listOf(
    15, 30, 60, 6 * 60, 12 * 60, 24 * 60, 3 * 24 * 60, 7 * 24 * 60, 14 * 24 * 60,
    30 * 24 * 60, 90 * 24 * 60, 365 * 24 * 60, 0
)
val ClipboardAutoDeleteMaxItemsOptions = listOf(10, 25, 50, 100, 250, 500, 1000, 0)
val ClipboardAutoDeleteImageStorageOptions = listOf(25, 50, 100, 250, 500, 1024, 2048, 0)

val ClipboardHistorySaveSensitive = SettingsKey(
    booleanPreferencesKey("clipboard_history_save_sensitive"),
    false
)

val ClipboardShowPinnedOnTop = SettingsKey(
    booleanPreferencesKey("clipboard_history_show_pinned_on_top"),
    true
)

val ClipboardSingleColumn = SettingsKey(
    booleanPreferencesKey("clipboard_history_single_column"),
    false
)

val ClipboardQuickClipsEnabled = SettingsKey(
    booleanPreferencesKey("clipboard_quick_clips_enabled"),
    true
)

val ClipboardSkipDeleteConfirmation = SettingsKey(
    booleanPreferencesKey("clipboard_skip_delete_confirmation"),
    false
)

val ClipboardSaveImages = SettingsKey(
    booleanPreferencesKey("clipboard_save_images"),
    true
)

val ClipboardSaveScreenshots = SettingsKey(
    booleanPreferencesKey("clipboard_save_screenshots"),
    false
)

/** Whether the user dismissed the "add screenshots to your clipboard" banner in the keyboard */
val ClipboardScreenshotPromptDismissed = SettingsKey(
    booleanPreferencesKey("clipboard_screenshot_prompt_dismissed"),
    false
)

val ClipboardLastBackup = SettingsKey(
    longPreferencesKey("clipboard_last_backup"),
    0
)


object UriSerializer : KSerializer<Uri> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Uri", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Uri) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Uri {
        return Uri.parse(decoder.decodeString())
    }
}

@Serializable
data class ClipboardEntry(
    val timestamp: Long,
    val pinned: Boolean,

    val text: String?,

    @Serializable(with = UriSerializer::class)
    val uri: Uri?,
    val mimeTypes: List<String>,

    val backingFile: String? = null,
    val sizeMb: Float? = null,
)

fun ClipboardEntry.getFile(context: Context): File? =
    backingFile?.let { File(context.clipboardDir, it) }

fun ClipboardEntry.fileSizeMb(context: Context): Float? =
    sizeMb ?: getFile(context)?.let {
        if(it.isFile) {
            it.length() / (1024f * 1024f)
        } else {
            null
        }
    }

internal fun sanitizeClipboardText(text: String, maxLength: Int = 64): String {
    var result = text.replace("\n", " ")
    if(result.length > maxLength) {
        result = result.substring(0, maxLength) + "..."
    }
    return result
}

object ClipboardThumbCache {
    private val cache = LruCache<String, ImageBitmap>(20)

    fun getOrPut(path: String, lambda: () -> ImageBitmap?): ImageBitmap?
        = cache[path] ?: lambda()?.also { cache.put(path, it) }

    fun clear() {
        cache.evictAll()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClipboardEntryView(modifier: Modifier, clipboardEntry: ClipboardEntry, onPaste: (ClipboardEntry) -> Unit, onRemove: (ClipboardEntry) -> Unit, onPin: (ClipboardEntry) -> Unit, bitmapOverride: ImageBitmap? = null) {
    val context = LocalContext.current

    val bitmap = remember(clipboardEntry) {
        if(clipboardEntry.text == null && clipboardEntry.backingFile != null) {
            val thumbnail = ClipboardUtil.thumbnailFor(File(context.clipboardDir, clipboardEntry.backingFile))
            if (thumbnail.exists() || bitmapOverride != null) {
                bitmapOverride ?: ClipboardThumbCache.getOrPut(thumbnail.name) {
                    BitmapFactory.decodeFile(thumbnail.absolutePath)?.asImageBitmap()
                }
            } else {
                null
            }
        } else {
            null
        }
    }

    val shape = RoundedCornerShape(8.dp)

    val color = if(clipboardEntry.pinned) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    val mainModifier = modifier
        .padding(2.dp)
        .combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(),
            enabled = true,
            onClick = { onPaste(clipboardEntry) },
            onLongClick = { onPin(clipboardEntry) }
        ).let {
            if(bitmap != null) {
                val maxHeight = 120.dp
                it
                    .requiredHeightIn(max = maxHeight)
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                    .requiredHeightIn(max = maxHeight)
                    .clip(RoundedCornerShape(8.dp))
                    .drawBehind {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val bitmapWidth = bitmap.width.toFloat()
                        val bitmapHeight = bitmap.height.toFloat()

                        val scale = maxOf(
                            canvasWidth / bitmapWidth,
                            canvasHeight / bitmapHeight
                        )

                        val scaledWidth = bitmapWidth * scale
                        val scaledHeight = bitmapHeight * scale

                        val offsetX = (canvasWidth - scaledWidth) / 2
                        val offsetY = (canvasHeight - scaledHeight) / 2

                        drawImage(
                            bitmap,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(bitmap.width, bitmap.height),
                            dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                            dstSize = IntSize(scaledWidth.toInt(), scaledHeight.toInt())
                        )

                        val height = 32.dp.toPx()
                        val brush = Brush.linearGradient(
                            0.0f to color,
                            1.0f to Color.Transparent,
                            start = Offset.Zero,
                            end = Offset(0.0f, height * 2.0f)
                        )

                        drawRect(brush)
                    }
            } else {
                it
            }
        }

    Surface(
        color = if(bitmap != null) Color.Transparent else color,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = mainModifier,
        shape = shape,
    ) {
        Column {
            Row(modifier = Modifier.padding(0.dp)) {
                IconButton(onClick = {
                    onPin(clipboardEntry)
                }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        painterResource(id = R.drawable.push_pin),
                        contentDescription = if(clipboardEntry.pinned) {
                            stringResource(R.string.action_clipboard_manager_unpin_item)
                        } else {
                            stringResource(R.string.action_clipboard_manager_pin_item)
                        },
                        tint = if(clipboardEntry.pinned) {
                            contentColorFor(color)
                        } else {
                            contentColorFor(color).copy(alpha = 0.5f)
                        },
                        modifier = Modifier.size(16.dp).rotate(
                            if(clipboardEntry.pinned) { 0f } else { 45f }
                        )
                    )
                }

                Spacer(modifier = Modifier.weight(1.0f))

                IconButton(onClick = {
                    onRemove(clipboardEntry)
                }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        painterResource(id = R.drawable.close),
                        contentDescription = stringResource(R.string.action_clipboard_manager_remove_item),
                        tint = contentColorFor(color),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if(clipboardEntry.text != null) {
                val text = remember(clipboardEntry.text) { sanitizeClipboardText(clipboardEntry.text) }
                Text(text, modifier = Modifier.padding(8.dp, 2.dp), style = Typography.SmallMl)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}




@Preview(showBackground = true)
@Composable
fun ClipboardEntryViewPreview() {
    val sampleText = listOf("This is an entry", "Copying text a lot", "hunter2", "https://www.example.com/forum/viewpost/1234573193.html?parameter=1234")
    LazyVerticalStaggeredGrid(
        modifier = Modifier.fillMaxWidth(),
        columns = StaggeredGridCells.Adaptive(160.dp),
        verticalItemSpacing = 4.dp,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            ClipboardEntryView(
                modifier = Modifier,
                clipboardEntry = ClipboardEntry(0L, false, null, null, listOf("image/png"), "[test]", 0.0f),
                onPin = {}, onPaste = {}, onRemove = {},
                bitmapOverride = ClipboardUtil.generateCheckerboardBitmap()
            )
        }
        items(sampleText.size) {
            ClipboardEntryView(modifier = Modifier, clipboardEntry = ClipboardEntry(0L, it % 2 == 0, sampleText[it], null, listOf()), onPin = {}, onPaste = {}, onRemove = {})
        }

        item {
            ClipboardEntryView(
                modifier = Modifier,
                clipboardEntry = ClipboardEntry(0L, false, null, null, listOf("image/png"), "[test]", 0.0f),
                onPin = {}, onPaste = {}, onRemove = {},
                bitmapOverride = ClipboardUtil.generateTestPatternBitmap()
            )
        }

        item {
            ClipboardEntryView(
                modifier = Modifier,
                clipboardEntry = ClipboardEntry(0L, false, null, null, listOf("image/png"), "[test]", 0.0f),
                onPin = {}, onPaste = {}, onRemove = {},
                bitmapOverride = SuperheroRenderer.render(LocalContext.current, "my clipboard image")?.asImageBitmap()
            )
        }

        items(sampleText.size / 2) {
            ClipboardEntryView(modifier = Modifier, clipboardEntry = ClipboardEntry(0L, it % 2 == 0, sampleText[it], null, listOf()), onPin = {}, onPaste = {}, onRemove = {})
        }

        item {
            ClipboardEntryView(
                modifier = Modifier,
                clipboardEntry = ClipboardEntry(0L, false, null, null, listOf("image/png"), "[test]", 0.0f),
                onPin = {}, onPaste = {}, onRemove = {},
                bitmapOverride = SuperheroRenderer.render(LocalContext.current, "hey")?.asImageBitmap()
            )
        }

        items(sampleText.size / 2) {
            ClipboardEntryView(modifier = Modifier, clipboardEntry = ClipboardEntry(0L, it % 2 == 0, sampleText[it], null, listOf()), onPin = {}, onPaste = {}, onRemove = {})
        }
    }
}

@Composable
private fun ClipboardSectionHeader(text: String) {
    Text(
        text,
        style = Typography.SmallMl,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 2.dp)
            .semantics { heading() }
    )
}

@Composable
private fun ClipboardScreenshotPrompt(onEnable: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(2.dp)
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.image),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.action_clipboard_manager_screenshot_prompt_title),
                    style = Typography.SmallMl
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.action_clipboard_manager_screenshot_prompt_text),
                style = Typography.Small,
                modifier = Modifier.padding(end = 8.dp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_clipboard_manager_screenshot_prompt_dismiss))
                }
                TextButton(onClick = onEnable) {
                    Text(stringResource(R.string.action_clipboard_manager_screenshot_prompt_enable))
                }
            }
        }
    }
}

/** Formats an auto-delete duration such as "30 minutes" or "2 weeks", or null for "never" */
internal fun formatClipboardDuration(minutes: Int, locale: Locale): String? {
    if(minutes <= 0) return null

    val hour = 60
    val day = 24 * hour
    val (amount, unit) = when {
        minutes % (365 * day) == 0 -> minutes / (365 * day) to MeasureUnit.YEAR
        minutes % (30 * day) == 0 -> minutes / (30 * day) to MeasureUnit.MONTH
        minutes % (7 * day) == 0 -> minutes / (7 * day) to MeasureUnit.WEEK
        minutes % day == 0 -> minutes / day to MeasureUnit.DAY
        minutes % hour == 0 -> minutes / hour to MeasureUnit.HOUR
        else -> minutes to MeasureUnit.MINUTE
    }

    return try {
        MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.WIDE).format(Measure(amount, unit))
    } catch(e: Exception) {
        "$amount ${unit.subtype}"
    }
}

const val ClipboardSettingsNavPath = "actions/clipboard_history"

// Images larger than this are not saved in the history
private const val MaxClipboardImageBytes = 10L * 1024L * 1024L

val DefaultClipboardEntry = ClipboardEntry(
    timestamp = 0L,
    pinned = true,
    text = "Clipboard entries will appear here",
    uri = null,
    mimeTypes = listOf()
)

const val ClipboardFileName = "clipboard.json"
val Context.clipboardFile get() = File(filesDir, ClipboardFileName)
val Context.clipboardDir get() = File(filesDir, "clipboardfiles")

@OptIn(ExperimentalCoroutinesApi::class)
private val ClipboardIOContext = Dispatchers.IO.limitedParallelism(1)

class ClipboardHistoryManager(val context: Context, val coroutineScope: LifecycleCoroutineScope) : PersistentActionState {
    var clipboardIOFailureReason = ""
    val clipboardIOFailure = mutableStateOf(false)

    companion object {
        val onClipboardImportedFlow = MutableSharedFlow<File>()
    }

    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    val clipboardHistory = mutableStateListOf<ClipboardEntry>()

    // Primary file
    val clipboardFile = context.clipboardFile
    val atomicClipboardFile = AtomicFile(clipboardFile)

    // Backup in case primary gets corrupted somehow
    val clipboardFileBak = File(context.filesDir, "$ClipboardFileName.backup")
    val clipboardFileBakLegacy = File(context.filesDir, "$ClipboardFileName.bak")

    // Temporary file used during saving, after writing we delete previous backup, move primary to backup, move swap to primary
    val clipboardFileSwap = File(context.filesDir, "$ClipboardFileName.swap")

    var clipboardLoaded = false

    private val screenshotHelper = ScreenshotHelper(context, coroutineScope, object : ScreenshotListener {
        override fun onScreenshotAdded(mime: String, uri: Uri, timestamp: Long) {
            if(!context.getSetting(ClipboardHistoryEnabled)) return
            onImageAdded(listOf(mime), uri, timestamp = timestamp, keepUri = true, isScreenshot = true)
        }

        override fun onScreenshotChange(
            uri: Uri,
            checkTrashed: suspend () -> Boolean
        ) {
            coroutineScope.launch(Dispatchers.Main) {
                // When a screenshot is deleted from the gallery, remove it from the history too,
                // unless the user explicitly pinned it.
                val item = clipboardHistory.find { it.uri == uri && !it.pinned } ?: return@launch
                if(checkTrashed()) {
                    onRemove(item)
                }
            }
        }
    })

    override suspend fun onDeviceUnlocked() {
        loadClipboard()
        screenshotHelper.recheck()
    }

    /** Called whenever the clipboard history window is opened */
    fun onWindowOpened() {
        screenshotHelper.recheck()
        coroutineScope.launch {
            if(pruneOldItems()) saveClipboard()
        }
    }

    /** Asks for the media permission (if needed) and enables saving screenshots to the history */
    fun enableScreenshots() {
        val permission = ScreenshotHelper.permission
        val enable = {
            coroutineScope.launch { context.setSetting(ClipboardSaveScreenshots, true) }
        }

        if(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            enable()
        } else {
            PermissionsManager.get(context).requestPermissions({ granted ->
                if(granted) enable()
            }, null, permission)
        }
    }

    // Names of image files that are being imported and don't have a history entry yet, so that
    // pruning does not delete them as unreferenced files
    private val pendingFiles: MutableSet<String> = Collections.synchronizedSet(HashSet())

    private fun isPendingFile(name: String): Boolean = synchronized(pendingFiles) {
        pendingFiles.any { name == it || name == ClipboardUtil.thumbnailForName(it) }
    }

    private class ImportedImage(val file: File, val mimeType: String, val sizeMb: Float)

    // Copies the image into our own storage (deduplicated by content hash) and generates
    // a thumbnail for it. The returned file is registered in pendingFiles, the caller must remove it.
    private suspend fun importImage(mimeTypes: List<String>, uri: Uri): ImportedImage? = withContext(Dispatchers.IO) {
        val targetMime = mimeTypes.firstOrNull { it == "image/png" }
            ?: mimeTypes.firstOrNull { it == "image/jpeg" || it == "image/jpg" }
            ?: mimeTypes.firstOrNull { it == "image/webp" }
            ?: return@withContext null

        context.cacheDir.mkdirs()
        val tempFile = File.createTempFile("clipboard_img", ".tmp", context.cacheDir)
        try {
            val md = MessageDigest.getInstance("MD5")
            var totalBytes = 0L

            val stream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            stream.use { input ->
                tempFile.outputStream().use { out ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break

                        totalBytes += bytesRead
                        if (totalBytes > MaxClipboardImageBytes) return@withContext null

                        md.update(buffer, 0, bytesRead)
                        out.write(buffer, 0, bytesRead)
                    }
                }
            }

            if(totalBytes == 0L) return@withContext null

            val md5Hex = md.digest().joinToString("") { "%02x".format(it) }
            val extension = when (targetMime) {
                "image/png" -> "png"
                "image/jpeg", "image/jpg" -> "jpg"
                "image/webp" -> "webp"
                else -> "img"
            }

            context.clipboardDir.mkdirs()
            val finalFile = File(context.clipboardDir, "$md5Hex.$extension")
            pendingFiles.add(finalFile.name)
            try {
                if (!finalFile.exists() && !tempFile.renameTo(finalFile)) {
                    tempFile.copyTo(finalFile, overwrite = true)
                }
                ClipboardUtil.generateThumbnail(finalFile)
            } catch(e: Exception) {
                pendingFiles.remove(finalFile.name)
                throw e
            }

            ImportedImage(finalFile, targetMime, totalBytes / (1024f * 1024f))
        } finally {
            tempFile.delete()
        }
    }

    internal fun onImageAdded(
        mimeTypes: List<String>,
        uri: Uri,
        timestamp: Long = System.currentTimeMillis(),
        keepUri: Boolean = false,
        isScreenshot: Boolean = false
    ) {
        coroutineScope.launch(Dispatchers.Main) {
            var imported: ImportedImage? = null
            try {
                imported = importImage(mimeTypes, uri) ?: return@launch
                val fileName = imported.file.name

                val isAlreadyPinned = clipboardHistory.any { it.backingFile == fileName && it.pinned }
                clipboardHistory.removeAll { it.backingFile == fileName }

                val newEntry = ClipboardEntry(
                    timestamp = timestamp,
                    pinned = isAlreadyPinned,
                    text = null,
                    uri = if(keepUri) uri else null,
                    backingFile = fileName,
                    sizeMb = imported.sizeMb,
                    mimeTypes = listOf(imported.mimeType)
                )
                clipboardHistory.add(newEntry)

                if(isScreenshot) RecentScreenshot.set(newEntry)

                onEntryAdded()
            } catch(e: Exception) {
                if(e is CancellationException) throw e

                // We may get exceptions here from opening uri, invalid image, etc
                // so let's just ignore them
                throwIfDebug(e)
            } finally {
                imported?.let { pendingFiles.remove(it.file.name) }
            }
        }
    }

    private fun onEntryAdded() {
        if(clipboardLoaded && context.getSetting(ClipboardAutoDeleteEnabled)) {
            coroutineScope.launch {
                pruneOldItems()
                saveClipboard()
            }
        } else {
            saveClipboard()
        }
    }

    private val primaryClipChangedListener = object : ClipboardManager.OnPrimaryClipChangedListener {
            override fun onPrimaryClipChanged() {
                if (!context.getSettingBlocking(ClipboardHistoryEnabled)) return

                val clip = try {
                    clipboardManager.primaryClip
                } catch(_: Exception) {
                    null
                }

                val uri = clip?.getItemAt(0)?.uri
                val mimeTypes = List(clip?.description?.mimeTypeCount ?: 0) {
                    clip?.description?.getMimeType(it)
                }.filterNotNull()

                // Text is only possible if no URI is specified, or a text mimetype is specified
                var textChrSeq = if(uri == null || mimeTypes.any { it.startsWith("text/") }){
                    clip?.getItemAt(0)?.coerceToText(context)
                } else null

                // Ignore massive entries
                if(textChrSeq != null && textChrSeq.length > 500_000) {
                    textChrSeq = null
                }

                val text = textChrSeq?.toString()

                // Nothing to save here
                if(text == null && uri == null) return

                val timestamp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    clip?.description?.timestamp
                } else {
                    null
                } ?: System.currentTimeMillis()


                val canSaveSensitive = context.getSetting(ClipboardHistorySaveSensitive)
                val canSaveImages = context.getSetting(ClipboardSaveImages)
                val isSensitive = clip?.description?.extras?.getBoolean(
                    ClipDescription.EXTRA_IS_SENSITIVE, false
                ) == true

                if(isSensitive && !canSaveSensitive) return

                if(text != null) {
                    val isAlreadyPinned = clipboardHistory.firstOrNull {
                        (it.text != null && it.text == text) && it.pinned
                    }?.pinned == true

                    clipboardHistory.removeAll { it.text != null && it.text == text }

                    val newEntry = ClipboardEntry(
                        timestamp = timestamp,
                        pinned = isAlreadyPinned,
                        text = text,
                        uri = null,
                        mimeTypes = mimeTypes
                    )
                    clipboardHistory.add(newEntry)

                    onEntryAdded()
                }else if (uri != null && canSaveImages) {
                    onImageAdded(mimeTypes, uri, timestamp)
                }
            }
        }

    init {
        coroutineScope.launch {
            loadClipboard()

            withContext(Dispatchers.Main) {
                clipboardManager.addPrimaryClipChangedListener(primaryClipChangedListener)
            }

            // Started only after the history is loaded, so that screenshots imported on startup
            // are not overwritten by the history being loaded from disk
            screenshotHelper.start()

            onClipboardImportedFlow.collectLatest {
                coroutineScope.ensureActive()
                onClipboardImported(it)
            }
        }

        // Enforce time-based auto-delete even while the keyboard stays open for a long time
        coroutineScope.launch {
            while(true) {
                delay(60_000L)
                if(hasExpiredItems() && pruneOldItems()) saveClipboard()
            }
        }
    }

    private fun hasExpiredItems(): Boolean {
        if(!clipboardLoaded || !context.getSetting(ClipboardAutoDeleteEnabled)) return false
        val minutesToKeep = context.getSetting(ClipboardAutoDeleteAfterMinutes)
        if(minutesToKeep <= 0) return false

        val minimumTimestamp = System.currentTimeMillis() - minutesToKeep * 60L * 1000L
        return clipboardHistory.any { !it.pinned && it.timestamp < minimumTimestamp }
    }

    private suspend fun onClipboardImported(file: File) {
        migrateLegacyClipboardBak()
        val data = decodeFile(file).map {
            // Restore all saved items
            it.copy(timestamp = System.currentTimeMillis())
        }

        withContext(Dispatchers.Main) {
            clipboardHistory.clear()
            clipboardHistory.addAll(data)
            clipboardLoaded = true
        }

        saveClipboard()
    }

    /**
     * Removes unpinned items according to the auto-delete settings, and cleans up duplicates,
     * entries whose image file is gone and image files that are no longer referenced.
     *
     * With auto-delete disabled (the default), no clips are removed: the history is kept forever.
     *
     * Returns true if the history was changed and should be saved.
     */
    suspend fun pruneOldItems(): Boolean {
        // Without the full history loaded we can't tell which files are still referenced
        if(!clipboardLoaded) return false
        return pruneOldItemsInternal()
    }

    private suspend fun pruneOldItemsInternal(): Boolean = withContext(Dispatchers.Main) {
        val historyBefore = clipboardHistory.toList()

        val autoDelete = context.getSetting(ClipboardAutoDeleteEnabled)

        if(autoDelete) {
            val minutesToKeep = context.getSetting(ClipboardAutoDeleteAfterMinutes)
            if(minutesToKeep > 0) {
                val minimumTimestamp = System.currentTimeMillis() - minutesToKeep * 60L * 1000L
                clipboardHistory.removeAll {
                    (!it.pinned) && (it.timestamp < minimumTimestamp)
                }
            }
        }

        // Remove duplicates of entries, if any appeared
        // Duplicates will have same timestamp, same text, etc
        val set = clipboardHistory.toSet()
        if(set.size < clipboardHistory.size) {
            clipboardHistory.clear()
            clipboardHistory.addAll(set)
        }

        if(autoDelete) {
            val maxItems = context.getSetting(ClipboardAutoDeleteMaxItems)
            if(maxItems > 0) {
                val numUnpinnedItems = clipboardHistory.count { !it.pinned }
                val numItemsToRemove = numUnpinnedItems - maxItems
                for(i in 0 until numItemsToRemove) {
                    // The list is ordered oldest to newest
                    val idx = clipboardHistory.indexOfFirst { !it.pinned }
                    if(idx == -1) break
                    clipboardHistory.removeAt(idx)
                }
            }
        }

        // Remove entries with nonexistent files
        clipboardHistory.removeAll {
            it.backingFile != null && it.getFile(context).let { it == null || !it.isFile }
        }

        if(autoDelete) {
            // Limit the storage used by unpinned images, removing the oldest ones first
            val megabytesToKeep = context.getSetting(ClipboardAutoDeleteImageStorageMb)
            if(megabytesToKeep > 0) {
                val removalCandidates = clipboardHistory.filter { it.backingFile != null && !it.pinned }
                    .sortedBy { -it.timestamp }
                val removalItems = mutableListOf<ClipboardEntry>()

                var quotaUsed = 0.0f
                for (candidate in removalCandidates) {
                    val size = candidate.fileSizeMb(context)
                    if (size != null) {
                        quotaUsed += size
                        if (quotaUsed > megabytesToKeep) removalItems.add(candidate)
                    } else {
                        removalItems.add(candidate)
                    }
                }

                removalItems.forEach { clipboardHistory.remove(it) }
            }
        }

        // Remove unreferenced files
        val stillReferenced = clipboardHistory
            .mapNotNull { it.backingFile }
            .flatMap { listOf(it, ClipboardUtil.thumbnailForName(it)) }
            .toHashSet()

        context.clipboardDir.listFiles()?.forEach {
            if(!stillReferenced.contains(it.name) && !isPendingFile(it.name)) {
                it.delete()
            }
        }

        val historyAfter = clipboardHistory.toList()
        if(historyAfter.size != historyBefore.size) {
            val remaining = historyAfter.toHashSet()
            historyBefore.forEach { if(it !in remaining) RecentScreenshot.clearIfMatches(it) }
        }

        historyBefore != historyAfter
    }

    private fun migrateLegacyClipboardBak() {
        // AtomicFile interprets a (filename + ".bak") file entirely differently, we
        // need to make sure it's gone before any AtomicFile operations.
        if(clipboardFileBakLegacy.exists()) { clipboardFileBakLegacy.renameTo(clipboardFileBak) }
    }

    var saveClipboardLoadJob: Job? = null
    var lastClipboardWritten: List<ClipboardEntry>? = null
    internal fun saveClipboard(): Job? {
        if(!context.isDirectBootUnlocked) return null
        if(!clipboardLoaded) {
            if(saveClipboardLoadJob?.isActive == true) return null

            val currentEntries = clipboardHistory.toList()
            saveClipboardLoadJob = coroutineScope.launch {
                loadClipboard()

                if(clipboardLoaded) {
                    clipboardHistory.addAll(currentEntries)
                    saveClipboard()
                } else {
                    clipboardIOFailure.value = true
                }
            }

            return saveClipboardLoadJob
        }

        return coroutineScope.launch(context = ClipboardIOContext) {
            try {
                val list = withContext(Dispatchers.Main) { clipboardHistory.toList() }
                val json = Json.encodeToString(list).toByteArray()

                if(list == lastClipboardWritten) return@launch

                // Once every day only
                val shouldBackup = (System.currentTimeMillis() - context.getSetting(ClipboardLastBackup)) > 1000L * 60L * 60L * 24L * 1L
                var backupSucceeded = false


                withContext(NonCancellable) {
                    synchronized(atomicClipboardFile) {
                        migrateLegacyClipboardBak()

                        // Produce a backup
                        if (clipboardFile.exists() && shouldBackup) {
                            val existingData = atomicClipboardFile.readFully()

                            val isValid = try {
                                decodeData(existingData).isNotEmpty()
                            } catch (_: Exception) {
                                false
                            }
                            if (isValid) {
                                val atomicBackup = AtomicFile(clipboardFileBak)

                                val stream = atomicBackup.startWrite()
                                try {
                                    stream.write(existingData)
                                    stream.flush()
                                    atomicBackup.finishWrite(stream)
                                    backupSucceeded = true
                                } catch (e: Exception) {
                                    atomicBackup.failWrite(stream)
                                }
                            }
                        }

                        val stream = atomicClipboardFile.startWrite()
                        try {
                            stream.write(json)
                            stream.flush()
                            atomicClipboardFile.finishWrite(stream)
                            lastClipboardWritten = list
                        } catch (e: Exception) {
                            atomicClipboardFile.failWrite(stream)
                            throw e
                        }
                    }
                }

                if(backupSucceeded) {
                    context.setSetting(ClipboardLastBackup, System.currentTimeMillis())
                }

                // Finally validate it can be read
                if (decodeFile(clipboardFile) != list) {
                    throw Exception("Saved file data does not match expected data")
                }

                clipboardIOFailure.value = false
            } catch (e: Exception) {
                if(e is CancellationException) return@launch

                clipboardIOFailure.value = true
                clipboardIOFailureReason = e.toString()
                reportError("saveClipboard", e)
            }
        }
    }

    fun deleteClipboard() {
        listOf(clipboardFile, clipboardFileSwap, clipboardFileBak).forEach {
            if(it.exists()) it.delete()
        }
    }

    private fun decodeData(data: ByteArray): List<ClipboardEntry> =
        Json.decodeFromString<List<ClipboardEntry>>(data.decodeToString())

    private fun decodeFile(file: File): List<ClipboardEntry> =
        decodeData(AtomicFile(file).readFully())

    private fun reportError(during: String, e: Exception) {
        BugViewerState.pushBug(BugInfo("ClipboardHistoryManager", """
Clipboard IO error during $during

Cause: ${e.message}

Stack trace: ${e.stackTrace.map { it.toString() }}

--- main data start --- snip ---
${if(clipboardFile.exists()) { clipboardFile.readText() } else { "File does not exist" }}
--- main data end --- snip ---


--- bak data start --- snip ---
${if(clipboardFileBak.exists()) { clipboardFileBak.readText() } else { "File does not exist" }}
--- bak data end --- snip ---

--- swap data start --- snip ---
${if(clipboardFileSwap.exists()) { clipboardFileSwap.readText() } else { "File does not exist" }}
--- swap data end --- snip ---
"""))
    }

    private suspend fun loadClipboard() = withContext(ClipboardIOContext) {
        if(!context.isDirectBootUnlocked) {
            clipboardIOFailureReason = "Direct Boot not unlocked"
            clipboardIOFailure.value = true
            return@withContext
        }

        val clipboardSetting = context.getUnlockedSetting(ClipboardHistoryEnabled)
        if(clipboardSetting == null) {
            clipboardIOFailureReason = "Settings not unlocked"
            clipboardIOFailure.value = true
            return@withContext
        }

        try {
            var pruned = false
            if(clipboardSetting == false) {
                deleteClipboard()
            } else if (clipboardFile.exists()) {
                migrateLegacyClipboardBak()
                val data = synchronized(atomicClipboardFile) {
                    try {
                        decodeFile(clipboardFile)
                    } catch(e: Exception) {
                        reportError("loadClipboard main, trying bak", e)
                        if(clipboardFileBak.exists()) {
                            decodeFile(clipboardFileBak)
                        } else {
                            throw e
                        }
                    }
                }

                clipboardHistory.clear()
                clipboardHistory.addAll(data)
                pruned = pruneOldItemsInternal()
            } else {
                clipboardHistory.add(DefaultClipboardEntry)
            }

            clipboardLoaded = true
            clipboardIOFailureReason = ""
            clipboardIOFailure.value = false

            // Persist removals right away so that auto-deleted clips don't linger on disk
            if(pruned) saveClipboard()
        } catch (e: Exception) {
            e.printStackTrace()
            clipboardIOFailureReason = "Exception: ${e.message}"
            clipboardIOFailure.value = true

            reportError("loadClipboard", e)
        }
    }

    fun onPaste(item: ClipboardEntry) {
        val itemPos = clipboardHistory.indexOf(item).coerceAtLeast(0)
        clipboardHistory.removeAll { it == item }
        clipboardHistory.add(itemPos, item.copy(timestamp = System.currentTimeMillis()))

        saveClipboard()
    }

    fun onTogglePin(item: ClipboardEntry) {
        var itemPos = clipboardHistory.indexOf(item).coerceAtLeast(0)
        clipboardHistory.removeAll { it == item }

        if(context.getSetting(ClipboardShowPinnedOnTop)) {
            // With this setting, unpinning can cause the position to dramatically change, so it's
            // better to just always reinsert into the final position which is more expected.
            // (the final position is visually the first due to reverse iteration)
            itemPos = clipboardHistory.size
        }

        clipboardHistory.add(itemPos, item.copy(
                pinned = !item.pinned,

                // Updating timestamp is necessary to prevent the following situation:
                // 1. Item is past its expiration time but not removed because it's pinned
                // 2. User unpins it (possibly by accident!)
                // 3. Item immediately gets deleted because it's past its expiration time
                timestamp = System.currentTimeMillis(),
            ))

        saveClipboard()
    }

    fun onRemove(item: ClipboardEntry) {
        // Clear the clipboard if the item being removed is the current one
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // TODO: URI
            try {
                if ((item.text != null) && item.text == clipboardManager.primaryClip?.getItemAt(0)
                        ?.coerceToText(context)?.toString()
                ) {
                    clipboardManager.clearPrimaryClip()
                    QuickClip.markQuickClipDismissed()
                }
            } catch(e: Exception) {
                e.printStackTrace()
            }
        }
        clipboardHistory.removeAll { it == item }
        RecentScreenshot.clearIfMatches(item)
        saveClipboard()
    }

    override suspend fun cleanUp() {
        saveClipboard()?.join()
    }

    override fun close() {
        clipboardManager.removePrimaryClipChangedListener(primaryClipChangedListener)
        screenshotHelper.onDestroy()
    }

}

fun String.toFNV1aHash(): Long {
    val fnvPrime: Long = 1099511628211L
    var hash: Long = -3750763034362895579L

    for (byte in this.toByteArray()) {
        hash = hash xor byte.toLong()
        hash *= fnvPrime
    }

    return hash
}

@OptIn(ExperimentalFoundationApi::class)
val ClipboardHistoryAction = Action(
    icon = R.drawable.clipboard_manager,
    name = R.string.action_clipboard_manager_title,
    simplePressImpl = null,
    canShowKeyboard = true,
    persistentState = { manager ->
        ClipboardHistoryManager(manager.getContext(), manager.getLifecycleScope())
    },
    altPressImpl = PasteAction.simplePressImpl,
    persistentStateInitialization = PersistentStateInitialization.OnKeyboardLoad,
    windowImpl = { manager, persistent ->
        val searching = mutableStateOf(false)
        val searchText = mutableStateOf("")
        val unlocked = !manager.isDeviceLocked()
        val clipboardHistoryManager = persistent as ClipboardHistoryManager

        clipboardHistoryManager.onWindowOpened()
        object : ActionWindow() {
            @Composable
            override fun windowName(): String {
                return stringResource(R.string.action_clipboard_manager_title)
            }

            @Composable
            override fun WindowTitleBar(rowScope: RowScope) = with(rowScope) {
                if(searching.value) {
                    ActionHeaderSearch(searchText, Modifier.weight(1.0f),
                        placeholder = stringResource(R.string.action_clipboard_manager_enter_your_search))
                    return
                }

                Text(windowName(), modifier = Modifier.align(Alignment.CenterVertically))
                Spacer(modifier = Modifier.weight(1.0f))

                val resources = LocalResources.current

                val clipboardHistory = useDataStore(ClipboardHistoryEnabled, blocking = true)
                if(!clipboardHistory.value) return

                if(unlocked
                    && !clipboardHistoryManager.clipboardIOFailure.value
                ) {
                    val context = LocalContext.current
                    IconButton(onClick = {
                        SettingsActivity.openToNavDest(context, ClipboardSettingsNavPath)
                    }) {
                        Icon(
                            painterResource(id = R.drawable.settings),
                            contentDescription = stringResource(R.string.action_clipboard_manager_settings_title)
                        )
                    }
                    IconButton(onClick = {
                        searching.value = true
                    }) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = stringResource(R.string.action_clipboard_manager_search)
                        )
                    }
                    IconButton(onClick = {
                        val numUnpinnedItems =
                            clipboardHistoryManager.clipboardHistory.count { !it.pinned }
                        if (clipboardHistoryManager.clipboardHistory.isEmpty()) {
                            manager.requestDialog(
                                resources.getString(R.string.action_clipboard_manager_disable_text),
                                listOf(
                                    DialogRequestItem(resources.getString(R.string.action_clipboard_manager_cancel_action_button)) {},
                                    DialogRequestItem(resources.getString(R.string.action_clipboard_manager_disable_button)) {
                                        clipboardHistory.setValue(false)
                                    },
                                ),
                                {}
                            )
                        } else if (numUnpinnedItems == 0) {
                            manager.requestDialog(
                                resources.getString(R.string.action_clipboard_manager_unpin_all_items_text),
                                listOf(
                                    DialogRequestItem(resources.getString(R.string.action_clipboard_manager_cancel_action_button)) {},
                                    DialogRequestItem(resources.getString(R.string.action_clipboard_manager_unpin_all_items_button)) {
                                        clipboardHistoryManager.clipboardHistory.toList().forEach {
                                            if (it.pinned) {
                                                clipboardHistoryManager.onTogglePin(it)
                                            }
                                        }
                                    },
                                ),
                                {}
                            )
                        } else {
                            manager.requestDialog(
                                resources.getString(R.string.action_clipboard_manager_clear_unpinned_items_text),
                                listOf(
                                    DialogRequestItem(resources.getString(R.string.action_clipboard_manager_cancel_action_button)) {},
                                    DialogRequestItem(resources.getString(R.string.action_clipboard_manager_clear_unpinned_items_button)) {
                                        clipboardHistoryManager.clipboardHistory.toList().forEach {
                                            if (!it.pinned) {
                                                clipboardHistoryManager.onRemove(it)
                                            }
                                        }
                                    },
                                ),
                                {}
                            )
                        }
                    }) {
                        Icon(
                            painterResource(id = R.drawable.close),
                            contentDescription = stringResource(R.string.action_clipboard_manager_clear_clipboard)
                        )
                    }
                }
            }

            @Composable
            override fun WindowContents(keyboardShown: Boolean) {
                val view = LocalView.current
                val context = LocalContext.current
                val resources = LocalResources.current
                val clipboardHistory = useDataStore(ClipboardHistoryEnabled, blocking = true)
                if(!unlocked) {
                    ScrollableList {
                        PaymentSurface(isPrimary = true) {
                            PaymentSurfaceHeading(title = stringResource(R.string.action_clipboard_manager_error_device_locked_title))

                            ParagraphText(stringResource(R.string.action_clipboard_manager_error_device_locked_text))
                        }
                    }
                } else if(clipboardHistoryManager.clipboardIOFailure.value) {
                    ScrollableList {
                        PaymentSurface(isPrimary = true) {
                            PaymentSurfaceHeading(title = stringResource(R.string.action_clipboard_manager_error_general_title))
                            ParagraphText(
                                stringResource(
                                    R.string.action_clipboard_manager_error_general_text,
                                    clipboardHistoryManager.clipboardIOFailureReason
                                ))
                            Button(onClick = {
                                manager.activateAction(BugViewerAction)
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.action_clipboard_manager_inspect_error_via_bugs_action))
                            }
                            Button(onClick = {
                                clipboardHistoryManager.saveClipboard()
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.action_clipboard_manager_retry_saving_loading))
                            }
                            Button(onClick = {
                                    manager.requestDialog(
                                        resources.getString(R.string.action_clipboard_manager_delete_corrupted_clipboard_text),
                                        listOf(
                                            DialogRequestItem(resources.getString(R.string.action_clipboard_manager_cancel_action_button)) {},
                                            DialogRequestItem(resources.getString(R.string.action_clipboard_manager_delete_corrupted_clipboard_button)) {
                                                clipboardHistoryManager.clipboardIOFailure.value = false
                                                clipboardHistory.setValue(false)
                                                clipboardHistoryManager.deleteClipboard()
                                            },
                                        ),
                                        {}
                                    )

                                }, modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                                Text(resources.getString(R.string.action_clipboard_manager_delete_corrupted_clipboard_button))
                            }
                        }
                    }
                } else if(!clipboardHistory.value) {
                    ScrollableList {
                        PaymentSurface(isPrimary = true) {
                            PaymentSurfaceHeading(title = stringResource(R.string.action_clipboard_manager_error_clipboard_history_disabled_title))
                            ParagraphText(stringResource(R.string.action_clipboard_manager_error_clipboard_history_disabled_text_v3))
                            Button(onClick = {
                                    clipboardHistory.setValue(true)
                                }, modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.action_clipboard_manager_enable_clipboard_history_button))
                            }
                        }
                    }
                } else {
                    
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val searchQuery = searchText.value
                        if(searching.value && searchQuery.isBlank()) {
                            return
                        }

                        val showPinnedOnTop = useDataStoreValue(ClipboardShowPinnedOnTop)

                        // Indices refer to positions in the (oldest-first) history so that the
                        // keys of existing items stay stable when new clips are added
                        val clipboardList = clipboardHistoryManager.clipboardHistory.withIndex().filter {
                            !searching.value
                                    || it.value.text?.contains(searchQuery, ignoreCase = true) == true
                                    || it.value.mimeTypes.any { mime ->
                                mime.contains(
                                    searchQuery,
                                    ignoreCase = true
                                )
                            }
                        }.asReversed() // Newest first

                        val pinnedList = if(showPinnedOnTop) clipboardList.filter { it.value.pinned } else emptyList()
                        val otherList = if(showPinnedOnTop) clipboardList.filter { !it.value.pinned } else clipboardList
                        val showSectionHeaders = pinnedList.isNotEmpty() && otherList.isNotEmpty()

                        val screenshotPromptDismissed = useDataStore(ClipboardScreenshotPromptDismissed)
                        val showScreenshotPrompt = SupportsAddingScreenshots
                                && !searching.value
                                && !useDataStoreValue(ClipboardSaveScreenshots)
                                && !screenshotPromptDismissed.value

                        if(clipboardList.isEmpty() && searching.value && searchQuery.isNotEmpty() && clipboardHistoryManager.clipboardHistory.isNotEmpty()) {
                            Text(
                                stringResource(R.string.action_clipboard_manager_no_clips_found),
                                style = Typography.Body.Medium.copy(fontStyle = FontStyle.Italic),
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth()
                            )
                            return
                        }

                        val useSingleColumn = useDataStoreValue(ClipboardSingleColumn)
                        val columns = if(useSingleColumn) {
                            StaggeredGridCells.Fixed(1)
                        } else {
                            StaggeredGridCells.Adaptive(140.dp)
                        }

                        val onPaste: (ClipboardEntry) -> Unit = {
                            if(it.text != null) {
                                manager.typeText(it.text)
                            } else if(it.backingFile != null && it.mimeTypes.isNotEmpty()) {
                                val uri = ClipboardProviderState.uriForFile(
                                    File(context.clipboardDir, it.backingFile),
                                    it.mimeTypes.first()
                                )
                                manager.typeUri(uri, it.mimeTypes, true)
                            }

                            clipboardHistoryManager.onPaste(it)
                            manager.performHapticAndAudioFeedback(Constants.CODE_OUTPUT_TEXT, view)
                        }

                        val onRemove: (ClipboardEntry) -> Unit = {
                            if(context.getSetting(ClipboardSkipDeleteConfirmation) && !it.pinned) {
                                clipboardHistoryManager.onRemove(it)
                                manager.performHapticAndAudioFeedback(Constants.CODE_TAB, view)
                            } else {
                                manager.requestDialog(
                                    if(it.backingFile != null && it.text == null) {
                                        resources.getString(R.string.action_clipboard_manager_remove_item_confirm_dialog_image)
                                    } else {
                                        resources.getString(
                                            R.string.action_clipboard_manager_remove_item_confirm_dialog,
                                            sanitizeClipboardText(it.text ?: "", 24)
                                        )
                                    },
                                    listOf(
                                        DialogRequestItem(
                                            resources.getString(R.string.action_clipboard_manager_cancel_action_button)
                                        ) { },
                                        DialogRequestItem(
                                            resources.getString(R.string.action_clipboard_manager_remove_item)
                                        ) {
                                            clipboardHistoryManager.onRemove(it)
                                            manager.performHapticAndAudioFeedback(
                                                Constants.CODE_TAB,
                                                view
                                            )
                                        }
                                    )
                                ) { }
                            }
                        }

                        val onPin: (ClipboardEntry) -> Unit = {
                            clipboardHistoryManager.onTogglePin(it)
                            manager.performHapticAndAudioFeedback(Constants.CODE_TAB, view)
                        }

                        LazyVerticalStaggeredGrid(
                            modifier = Modifier.fillMaxWidth(),
                            columns = columns,
                            verticalItemSpacing = 4.dp,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if(showScreenshotPrompt) {
                                item(key = "screenshot_prompt", span = StaggeredGridItemSpan.FullLine) {
                                    ClipboardScreenshotPrompt(
                                        onEnable = { clipboardHistoryManager.enableScreenshots() },
                                        onDismiss = { screenshotPromptDismissed.setValue(true) }
                                    )
                                }
                            }

                            if(showSectionHeaders) {
                                item(key = "header_pinned", span = StaggeredGridItemSpan.FullLine) {
                                    ClipboardSectionHeader(stringResource(R.string.action_clipboard_manager_section_pinned))
                                }
                            }

                            items(pinnedList.size, key = { pinnedList[it].index }) {
                                ClipboardEntryView(
                                    modifier = Modifier.animateItem(),
                                    clipboardEntry = pinnedList[it].value,
                                    onPaste = onPaste, onRemove = onRemove, onPin = onPin
                                )
                            }

                            if(showSectionHeaders) {
                                item(key = "header_recent", span = StaggeredGridItemSpan.FullLine) {
                                    ClipboardSectionHeader(stringResource(R.string.action_clipboard_manager_section_recent))
                                }
                            }

                            items(otherList.size, key = { otherList[it].index }) {
                                ClipboardEntryView(
                                    modifier = Modifier.animateItem(),
                                    clipboardEntry = otherList[it].value,
                                    onPaste = onPaste, onRemove = onRemove, onPin = onPin
                                )
                            }
                        }
                    }
                }
            }
        }
    },

    settingsMenu = UserSettingsMenu(
        title = R.string.action_clipboard_manager_settings_title,
        navPath = ClipboardSettingsNavPath,
        registerNavPath = true,
        settings = listOf(
            userSettingToggleDataStore(
                title = R.string.action_clipboard_manager_settings_show_quick_clips,
                setting = ClipboardQuickClipsEnabled
            ),

            userSettingToggleDataStore(
                title = R.string.typing_settings_enable_clipboard_history,
                setting = ClipboardHistoryEnabled
            ).copy(searchTags = R.string.typing_settings_enable_clipboard_history_tags),

            userSettingToggleDataStore(
                title = R.string.action_clipboard_manager_settings_save_images,
                setting = ClipboardSaveImages
            ).copy(visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) }),

            UserSetting(
                name = R.string.action_clipboard_manager_settings_save_screenshots,
                subtitle = R.string.action_clipboard_manager_settings_save_screenshots_subtitle_v2,
                component = {
                    val context = LocalContext.current
                    val (enabled, setEnabled) = useDataStore(ClipboardSaveScreenshots)

                    val permission = ScreenshotHelper.permission
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        permission
                    ) == PackageManager.PERMISSION_GRANTED

                    val launcher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { isGranted: Boolean ->
                        setEnabled(isGranted)
                    }

                    SettingToggleRaw(
                        title = stringResource(R.string.action_clipboard_manager_settings_save_screenshots),
                        subtitle = when {
                            enabled && !hasPermission -> stringResource(R.string.action_clipboard_manager_settings_save_screenshots_permission_missing)
                            enabled -> stringResource(R.string.action_clipboard_manager_settings_save_screenshots_subtitle_enabled)
                            else -> stringResource(R.string.action_clipboard_manager_settings_save_screenshots_subtitle_v2)
                        },
                        enabled = enabled,
                        setValue = {
                            if(!it) setEnabled(false) else {
                                if (hasPermission) {
                                    setEnabled(true)
                                } else {
                                    launcher.launch(permission)
                                }
                            }
                        }
                    )
                },
                visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) && SupportsAddingScreenshots },
                appearInSearchIfVisibilityCheckFailed = SupportsAddingScreenshots
            ),

            userSettingDecorationOnly {
                ScreenTitle(stringResource(R.string.action_clipboard_manager_settings_auto_delete_section))
            }.copy(visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) }),

            UserSetting(
                name = R.string.action_clipboard_manager_settings_auto_delete,
                subtitle = R.string.action_clipboard_manager_settings_auto_delete_subtitle_off,
                component = {
                    SettingToggleDataStore(
                        title = stringResource(R.string.action_clipboard_manager_settings_auto_delete),
                        subtitle = stringResource(R.string.action_clipboard_manager_settings_auto_delete_subtitle_on),
                        disabledSubtitle = stringResource(R.string.action_clipboard_manager_settings_auto_delete_subtitle_off),
                        setting = ClipboardAutoDeleteEnabled
                    )
                },
                visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) }
            ),

            UserSetting(
                name = R.string.action_clipboard_manager_settings_auto_delete_after,
                component = {
                    val setting = useDataStore(ClipboardAutoDeleteAfterMinutes)
                    val never = stringResource(R.string.action_clipboard_manager_settings_auto_delete_never)
                    val locale = LocalConfiguration.current.locales[0]

                    DropDownPickerSettingItem(
                        label = stringResource(R.string.action_clipboard_manager_settings_auto_delete_after),
                        options = ClipboardAutoDeleteDurationOptions,
                        selection = setting.value,
                        onSet = { setting.setValue(it) },
                        getDisplayName = { formatClipboardDuration(it, locale) ?: never },
                    )
                },
                visibilityCheck = {
                    useDataStoreValue(ClipboardHistoryEnabled) && useDataStoreValue(ClipboardAutoDeleteEnabled)
                }
            ),

            UserSetting(
                name = R.string.action_clipboard_manager_settings_maximum_clips,
                component = {
                    val setting = useDataStore(ClipboardAutoDeleteMaxItems)
                    val noLimit = stringResource(R.string.action_clipboard_manager_settings_auto_delete_no_limit)

                    DropDownPickerSettingItem(
                        label = stringResource(R.string.action_clipboard_manager_settings_maximum_clips),
                        options = ClipboardAutoDeleteMaxItemsOptions,
                        selection = setting.value,
                        onSet = { setting.setValue(it) },
                        getDisplayName = { if(it <= 0) noLimit else it.toString() },
                    )
                },
                visibilityCheck = {
                    useDataStoreValue(ClipboardHistoryEnabled) && useDataStoreValue(ClipboardAutoDeleteEnabled)
                }
            ),

            UserSetting(
                name = R.string.action_clipboard_manager_settings_image_storage_limit,
                component = {
                    val setting = useDataStore(ClipboardAutoDeleteImageStorageMb)
                    val noLimit = stringResource(R.string.action_clipboard_manager_settings_auto_delete_no_limit)

                    DropDownPickerSettingItem(
                        label = stringResource(R.string.action_clipboard_manager_settings_image_storage_limit),
                        options = ClipboardAutoDeleteImageStorageOptions,
                        selection = setting.value,
                        onSet = { setting.setValue(it) },
                        getDisplayName = {
                            when {
                                it <= 0 -> noLimit
                                it % 1024 == 0 -> "${it / 1024} GB"
                                else -> "$it MB"
                            }
                        },
                    )
                },
                visibilityCheck = {
                    useDataStoreValue(ClipboardHistoryEnabled) && useDataStoreValue(ClipboardAutoDeleteEnabled)
                }
            ),

            userSettingDecorationOnly {
                ScreenTitle(stringResource(R.string.action_clipboard_manager_settings_other_section))
            }.copy(visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) }),

            UserSetting(
                name = R.string.action_clipboard_manager_settings_save_sensitive_clips,
                subtitle = R.string.action_clipboard_manager_settings_save_sensitive_clips_subtitle,
                component = {
                    SettingToggleDataStore(
                        title = stringResource(R.string.action_clipboard_manager_settings_save_sensitive_clips),
                        subtitle = stringResource(R.string.action_clipboard_manager_settings_save_sensitive_clips_subtitle),
                        setting = ClipboardHistorySaveSensitive
                    )
                },
                visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) }
            ),

            userSettingToggleDataStore(
                title = R.string.action_clipboard_manager_settings_show_pinned_above_others,
                setting = ClipboardShowPinnedOnTop
            ).copy(visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) }),

            userSettingToggleDataStore(
                title = R.string.action_clipboard_manager_settings_list_layout,
                setting = ClipboardSingleColumn
            ).copy(visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) }),

            userSettingToggleDataStore(
                title = R.string.action_clipboard_manager_settings_skip_delete_confirmation,
                setting = ClipboardSkipDeleteConfirmation
            ).copy(visibilityCheck = { useDataStoreValue(ClipboardHistoryEnabled) }),
        )
    )
)
