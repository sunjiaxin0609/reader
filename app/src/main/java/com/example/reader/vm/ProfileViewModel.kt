package com.example.reader.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.reader.data.BookDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class BookReadStat(
    val bookId: Long,
    val title: String,
    val seconds: Long,
    val progress: Float,
    val lastReadAt: Long
)

data class ProfileStats(
    val totalSeconds: Long = 0L,
    val todaySeconds: Long = 0L,
    val streakDays: Int = 0,
    val readDays: Int = 0,
    val bookCount: Int = 0,
    val weeklySeconds: List<Long> = List(7) { 0L },
    val bookStats: List<BookReadStat> = emptyList()
)

class ProfileViewModel(app: Application) : AndroidViewModel(app) {

    private val database = BookDatabase(app)

    private val _stats = MutableStateFlow(ProfileStats())
    val stats: StateFlow<ProfileStats> = _stats.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val books = database.allBooks()
            val log = database.readingLog()

            val secondsByBook = log.groupBy { it.bookId }
                .mapValues { entry -> entry.value.sumOf { it.seconds.toLong() } }
            val secondsByDate = log.groupBy { it.date }
                .mapValues { entry -> entry.value.sumOf { it.seconds.toLong() } }

            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val weekly = (6 downTo 0).map { offset ->
                val date = LocalDate.now().minusDays(offset.toLong())
                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
                secondsByDate[date] ?: 0L
            }

            var cursor = LocalDate.now()
            if (!secondsByDate.containsKey(cursor.format(DateTimeFormatter.ISO_LOCAL_DATE))) {
                cursor = cursor.minusDays(1)
            }
            var streak = 0
            while (secondsByDate.containsKey(cursor.format(DateTimeFormatter.ISO_LOCAL_DATE))) {
                streak++
                cursor = cursor.minusDays(1)
            }

            val bookStats = books.map { book ->
                BookReadStat(
                    bookId = book.id,
                    title = book.title,
                    seconds = secondsByBook[book.id] ?: 0L,
                    progress = book.progress,
                    lastReadAt = book.lastReadAt
                )
            }.filter { it.seconds > 0 || it.progress > 0f }
                .sortedByDescending { it.seconds }

            _stats.value = ProfileStats(
                totalSeconds = log.sumOf { it.seconds.toLong() },
                todaySeconds = secondsByDate[today] ?: 0L,
                streakDays = streak,
                readDays = secondsByDate.size,
                bookCount = bookStats.size,
                weeklySeconds = weekly,
                bookStats = bookStats
            )
        }
    }
}
