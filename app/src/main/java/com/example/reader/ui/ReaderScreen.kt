package com.example.reader.ui

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reader.data.ReaderTheme
import com.example.reader.ui.theme.readerBackgroundColor
import com.example.reader.ui.theme.readerTextColor
import com.example.reader.vm.ReaderViewModel
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.floor
import kotlin.math.min

private enum class ReaderSheet { CHAPTERS, BOOKMARKS, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(bookId: Long, onBack: () -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: ReaderViewModel = viewModel(
        key = "reader_$bookId",
        factory = ReaderViewModelFactory(bookId, application)
    )
    val state by viewModel.state.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, bookId) {
        val accumulated = AtomicLong(0L)
        val lastTick = AtomicLong(System.currentTimeMillis())
        val active = AtomicBoolean(true)

        fun tick() {
            val now = System.currentTimeMillis()
            val previous = lastTick.getAndSet(now)
            if (active.get() && previous > 0) {
                val delta = now - previous
                if (delta in 1..60_000L) accumulated.addAndGet(delta)
            }
        }

        fun flush() {
            tick()
            val seconds = accumulated.getAndSet(0L) / 1000
            if (seconds > 0) viewModel.recordReading(seconds.toInt())
        }

        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val task = scheduler.scheduleAtFixedRate({ tick(); flush() }, 10, 10, TimeUnit.SECONDS)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    active.set(true)
                    lastTick.set(System.currentTimeMillis())
                }
                Lifecycle.Event.ON_PAUSE -> {
                    active.set(false)
                    flush()
                }
                Lifecycle.Event.ON_DESTROY -> flush()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            task.cancel(false)
            scheduler.shutdownNow()
            flush()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<ReaderSheet?>(null) }
    var sliderValue by remember { mutableFloatStateOf(0f) }

    BackHandler {
        when {
            sheet != null -> sheet = null
            showMenu -> showMenu = false
            else -> onBack()
        }
    }

    fun progressOf(chapterIndex: Int, offset: Int): Float {
        val chapter = state.chapters.getOrNull(chapterIndex) ?: return 0f
        val fraction = if (offset >= chapter.text.length) {
            1f
        } else {
            offset.coerceAtLeast(0) / chapter.text.length.toFloat()
        }
        return ((chapterIndex + fraction) / state.chapters.size.coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    fun jumpToProgress(progress: Float) {
        val count = state.chapters.size
        if (count == 0) return
        val raw = (progress * count).coerceIn(0f, count.toFloat())
        val chapterIndex = min(floor(raw).toInt(), count - 1)
        val fraction = raw - chapterIndex
        val length = state.chapters[chapterIndex].text.length
        viewModel.jumpTo(chapterIndex, (length * fraction).toInt(), progress)
    }

    LaunchedEffect(state.chapterIndex, state.charOffset, state.chapters.size) {
        sliderValue = progressOf(state.chapterIndex, state.charOffset)
    }

    val settings = state.settings
    val backgroundColor = readerBackgroundColor(settings.theme)
    val textStyle = TextStyle(
        fontSize = settings.fontSize.sp,
        lineHeight = (settings.fontSize * settings.lineHeightMultiplier).sp,
        fontFamily = if (settings.useSerif) FontFamily.Serif else FontFamily.SansSerif,
        color = readerTextColor(settings.theme)
    )

    Scaffold(containerColor = backgroundColor) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(backgroundColor)
        ) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.error != null -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.error ?: "出错了")
                    Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                        Text("返回书架")
                    }
                }

                else -> {
                    val chapters = state.chapters
                    val chapter = chapters.getOrNull(state.chapterIndex)
                    if (chapter != null) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (showMenu) {
                                TopAppBar(
                                    title = {
                                        Text(
                                            chapter.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    navigationIcon = {
                                        IconButton(onClick = onBack) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回书架")
                                        }
                                    },
                                    actions = {
                                        val bookmarked = state.bookmarks.any {
                                            it.chapterIndex == state.chapterIndex &&
                                                kotlin.math.abs(it.charOffset - state.charOffset) < 120
                                        }
                                        IconButton(onClick = { viewModel.toggleBookmark() }) {
                                            Icon(
                                                if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                                contentDescription = "书签"
                                            )
                                        }
                                        IconButton(onClick = { sheet = ReaderSheet.CHAPTERS }) {
                                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "章节目录")
                                        }
                                        IconButton(onClick = { sheet = ReaderSheet.BOOKMARKS }) {
                                            Icon(
                                                Icons.Default.BookmarkBorder,
                                                contentDescription = "书签列表"
                                            )
                                        }
                                    }
                                )
                            }

                            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                                val density = LocalDensity.current
                                val horizontalPaddingPx = with(density) { 40.dp.toPx() }.toInt()
                                val verticalPaddingPx = with(density) { 24.dp.toPx() }.toInt()
                                val widthPx = with(density) { maxWidth.toPx() }.toInt() - horizontalPaddingPx
                                val heightPx = with(density) { maxHeight.toPx() }.toInt() - verticalPaddingPx

                                val pages = rememberPages(chapter.text, textStyle, widthPx, heightPx)
                                val found = pages.indexOfFirst { state.charOffset in it }
                                val pageIndex = when {
                                    found >= 0 -> found
                                    pages.isNotEmpty() && state.charOffset > pages.last().last -> pages.lastIndex
                                    else -> 0
                                }
                                val safeIndex = pageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                                val range = pages.getOrNull(safeIndex) ?: (0 until chapter.text.length)

                                fun turnPage(next: Boolean) {
                                    val target = safeIndex + if (next) 1 else -1
                                    when {
                                        target in pages.indices -> {
                                            val offset = pages[target].first
                                            viewModel.jumpTo(
                                                state.chapterIndex,
                                                offset,
                                                progressOf(state.chapterIndex, offset)
                                            )
                                        }

                                        next && state.chapterIndex < chapters.lastIndex -> {
                                            val nextChapter = state.chapterIndex + 1
                                            viewModel.jumpTo(nextChapter, 0, progressOf(nextChapter, 0))
                                        }

                                        !next && state.chapterIndex > 0 -> {
                                            val previousChapter = state.chapterIndex - 1
                                            val end = chapters[previousChapter].text.length
                                            viewModel.jumpTo(
                                                previousChapter,
                                                end,
                                                progressOf(previousChapter, end)
                                            )
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(Unit) {
                                            detectTapGestures { offset ->
                                                val x = offset.x
                                                val width = size.width.toFloat()
                                                when {
                                                    x < width * 0.3f -> turnPage(false)
                                                    x > width * 0.7f -> turnPage(true)
                                                    else -> showMenu = !showMenu
                                                }
                                            }
                                        }
                                        .pointerInput(Unit) {
                                            var totalDrag = 0f
                                            detectHorizontalDragGestures(
                                                onDragStart = { totalDrag = 0f },
                                                onHorizontalDrag = { _, delta -> totalDrag += delta },
                                                onDragEnd = {
                                                    if (totalDrag < -80f) turnPage(true)
                                                    else if (totalDrag > 80f) turnPage(false)
                                                }
                                            )
                                        }
                                ) {
                                    Text(
                                        text = chapter.text.substring(
                                            range.first,
                                            min(range.last, chapter.text.length)
                                        ),
                                        style = textStyle,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 20.dp, vertical = 12.dp)
                                    )
                                    Text(
                                        text = "${safeIndex + 1}/${pages.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 4.dp)
                                    )
                                }
                            }

                            if (showMenu) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = {
                                        val target = state.chapterIndex - 1
                                        if (target >= 0) {
                                            viewModel.jumpTo(target, 0, progressOf(target, 0))
                                        }
                                    }) { Text("上一章") }

                                    Slider(
                                        value = sliderValue.coerceIn(0f, 1f),
                                        onValueChange = { sliderValue = it },
                                        onValueChangeFinished = { jumpToProgress(sliderValue) },
                                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                    )

                                    TextButton(onClick = {
                                        val target = state.chapterIndex + 1
                                        if (target < chapters.size) {
                                            viewModel.jumpTo(target, 0, progressOf(target, 0))
                                        }
                                    }) { Text("下一章") }

                                    IconButton(onClick = { sheet = ReaderSheet.SETTINGS }) {
                                        Icon(Icons.Default.Settings, contentDescription = "阅读设置")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    sheet?.let { current ->
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            Column(
                modifier = Modifier
                    .fillMaxHeight(0.75f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                when (current) {
                    ReaderSheet.CHAPTERS -> {
                        Text("章节目录", style = MaterialTheme.typography.titleMedium)
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
                            itemsIndexed(state.chapters) { index, item ->
                                val selected = index == state.chapterIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primaryContainer
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            viewModel.jumpTo(index, 0, progressOf(index, 0))
                                            sheet = null
                                        }
                                        .padding(14.dp)
                                ) {
                                    Text(
                                        item.title,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    ReaderSheet.BOOKMARKS -> {
                        Text("我的书签", style = MaterialTheme.typography.titleMedium)
                        if (state.bookmarks.isEmpty()) {
                            Text(
                                "还没有书签，阅读时点顶部书签按钮添加",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
                                items(items = state.bookmarks, key = { it.id }) { bookmark ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.jumpTo(
                                                    bookmark.chapterIndex,
                                                    bookmark.charOffset,
                                                    progressOf(bookmark.chapterIndex, bookmark.charOffset)
                                                )
                                                sheet = null
                                            }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                state.chapters.getOrNull(bookmark.chapterIndex)?.title
                                                    ?: "第 ${bookmark.chapterIndex + 1} 章",
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                            Text(
                                                bookmark.excerpt,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        IconButton(onClick = { viewModel.deleteBookmark(bookmark.id) }) {
                                            Icon(Icons.Default.Close, contentDescription = "删除书签")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    ReaderSheet.SETTINGS -> {
                        Text("阅读设置", style = MaterialTheme.typography.titleMedium)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("正文字号", modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                viewModel.updateSettings {
                                    it.copy(fontSize = (it.fontSize - 1).coerceIn(12, 32))
                                }
                            }) { Icon(Icons.Default.Remove, contentDescription = "减小字号") }
                            Text("${settings.fontSize} sp")
                            IconButton(onClick = {
                                viewModel.updateSettings {
                                    it.copy(fontSize = (it.fontSize + 1).coerceIn(12, 32))
                                }
                            }) { Icon(Icons.Default.Add, contentDescription = "增大字号") }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("行距", modifier = Modifier.weight(1f))
                            Slider(
                                value = settings.lineHeightMultiplier,
                                valueRange = 1.2f..2.4f,
                                steps = 5,
                                onValueChange = { value ->
                                    viewModel.updateSettings {
                                        it.copy(lineHeightMultiplier = value)
                                    }
                                },
                                modifier = Modifier.weight(2f)
                            )
                        }

                        Text("背景主题", modifier = Modifier.padding(top = 20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.theme == ReaderTheme.LIGHT,
                                onClick = { viewModel.updateSettings { it.copy(theme = ReaderTheme.LIGHT) } },
                                label = { Text("纸张") }
                            )
                            FilterChip(
                                selected = settings.theme == ReaderTheme.SEPIA,
                                onClick = { viewModel.updateSettings { it.copy(theme = ReaderTheme.SEPIA) } },
                                label = { Text("旧书") }
                            )
                            FilterChip(
                                selected = settings.theme == ReaderTheme.DARK,
                                onClick = { viewModel.updateSettings { it.copy(theme = ReaderTheme.DARK) } },
                                label = { Text("夜间") }
                            )
                        }

                        Text("正文字体", modifier = Modifier.padding(top = 20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = settings.useSerif,
                                onClick = { viewModel.updateSettings { it.copy(useSerif = true) } },
                                label = { Text("衬线") }
                            )
                            FilterChip(
                                selected = !settings.useSerif,
                                onClick = { viewModel.updateSettings { it.copy(useSerif = false) } },
                                label = { Text("黑体") }
                            )
                        }
                    }
                }
            }
        }
    }
}

private class ReaderViewModelFactory(
    private val bookId: Long,
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReaderViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReaderViewModel(bookId, application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
