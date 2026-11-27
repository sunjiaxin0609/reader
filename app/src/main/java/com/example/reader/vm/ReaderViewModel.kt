package com.example.reader.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.reader.book.BookParser
import com.example.reader.data.Book
import com.example.reader.data.BookDatabase
import com.example.reader.data.Bookmark
import com.example.reader.data.Chapter
import com.example.reader.data.ReaderSettings
import com.example.reader.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ReaderUiState(
    val book: Book? = null,
    val chapters: List<Chapter> = emptyList(),
    val chapterIndex: Int = 0,
    val charOffset: Int = 0,
    val bookmarks: List<Bookmark> = emptyList(),
    val settings: ReaderSettings = ReaderSettings(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class ReaderViewModel(
    private val bookId: Long,
    app: Application
) : AndroidViewModel(app) {

    private val database = BookDatabase(app)
    private val settingsRepository = SettingsRepository(app)

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    init {
        settingsRepository.settings
            .onEach { settings -> _state.update { it.copy(settings = settings) } }
            .launchIn(viewModelScope)

        viewModelScope.launch(Dispatchers.IO) {
            val book = database.findBook(bookId)
            if (book == null) {
                _state.update { it.copy(isLoading = false, error = "书籍不存在") }
                return@launch
            }
            val parsed = runCatching { BookParser.parse(book.path, book.format) }
            if (parsed.isFailure) {
                _state.update {
                    it.copy(isLoading = false, error = "解析失败：${parsed.exceptionOrNull()?.message ?: "未知错误"}")
                }
                return@launch
            }
            val chapters = parsed.getOrThrow().chapters
            _state.update {
                it.copy(
                    book = book,
                    chapters = chapters,
                    chapterIndex = book.chapterIndex.coerceIn(0, chapters.lastIndex),
                    charOffset = book.charOffset,
                    bookmarks = database.bookmarks(bookId),
                    isLoading = false
                )
            }
        }
    }

    fun jumpTo(chapterIndex: Int, charOffset: Int, progress: Float = 0f) {
        val current = _state.value
        if (current.chapters.isEmpty()) return
        val targetChapter = chapterIndex.coerceIn(0, current.chapters.lastIndex)
        val textLength = current.chapters[targetChapter].text.length
        val targetOffset = charOffset.coerceIn(0, textLength)
        if (targetChapter == current.chapterIndex && targetOffset == current.charOffset) return

        _state.update { it.copy(chapterIndex = targetChapter, charOffset = targetOffset) }
        saveProgress(targetChapter, targetOffset, progress)
    }

    fun toggleBookmark() {
        val current = _state.value
        val book = current.book ?: return
        val chapter = current.chapters.getOrNull(current.chapterIndex) ?: return
        val existing = current.bookmarks.firstOrNull {
            it.chapterIndex == current.chapterIndex &&
                kotlin.math.abs(it.charOffset - current.charOffset) < 120
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (existing != null) {
                database.deleteBookmark(existing.id)
            } else {
                val start = current.charOffset.coerceIn(0, chapter.text.length)
                val end = (start + 60).coerceAtMost(chapter.text.length)
                val excerpt = chapter.text.substring(start, end).replace('\n', ' ').trim()
                database.insertBookmark(
                    Bookmark(
                        bookId = book.id,
                        chapterIndex = current.chapterIndex,
                        charOffset = current.charOffset,
                        excerpt = excerpt
                    )
                )
            }
            _state.update { it.copy(bookmarks = database.bookmarks(book.id)) }
        }
    }

    fun deleteBookmark(id: Long) {
        val bookId = _state.value.book?.id ?: return
        viewModelScope.launch(Dispatchers.IO) {
            database.deleteBookmark(id)
            _state.update { it.copy(bookmarks = database.bookmarks(bookId)) }
        }
    }

    fun recordReading(seconds: Int) {
        if (seconds <= 0) return
        val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        viewModelScope.launch(Dispatchers.IO) {
            database.addReadingTime(bookId, date, seconds)
        }
    }

    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        viewModelScope.launch {
            settingsRepository.update(transform)
        }
    }

    private fun saveProgress(chapterIndex: Int, charOffset: Int, progress: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            database.updateProgress(bookId, chapterIndex, charOffset, progress)
        }
    }
}
