package com.example.reader.vm

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.reader.book.FileImporter
import com.example.reader.data.Book
import com.example.reader.data.BookDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class BookshelfViewModel(app: Application) : AndroidViewModel(app) {

    private val database = BookDatabase(app)
    private val importer = FileImporter(app)

    private val _books = MutableStateFlow<List<Book>>(emptyList())
    val books: StateFlow<List<Book>> = _books.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _books.value = database.allBooks()
        }
    }

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _isImporting.value = true
            val result = importer.import(uris)
            result.books.forEach { database.insertBook(it) }
            _books.value = database.allBooks()
            _isImporting.value = false
            val summary = buildString {
                if (result.books.isNotEmpty()) append("已导入 ${result.books.size} 本书")
                if (result.errors.isNotEmpty()) {
                    if (isNotEmpty()) append("；")
                    append(result.errors.joinToString("；"))
                }
            }
            _messages.emit(summary.ifBlank { "没有可导入的文件" })
        }
    }

    fun delete(book: Book) {
        viewModelScope.launch(Dispatchers.IO) {
            database.deleteBook(book.id)
            runCatching { File(book.path).delete() }
            _books.value = database.allBooks()
            withContext(Dispatchers.Main) {
                _messages.tryEmit("已删除《${book.title}》")
            }
        }
    }
}
