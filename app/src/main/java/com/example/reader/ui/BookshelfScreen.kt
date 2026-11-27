package com.example.reader.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reader.data.Book
import com.example.reader.vm.BookshelfViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(onOpenBook: (Long) -> Unit, onOpenProfile: () -> Unit) {
    val viewModel: BookshelfViewModel = viewModel()
    val books by viewModel.books.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()
    val context = LocalContext.current

    var query by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Book?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.import(uris) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val visibleBooks = remember(books, query) {
        books.filter {
            it.title.contains(query, true) || it.author.contains(query, true)
        }
    }

    Scaffold(
        bottomBar = {
            ReaderBottomNav(
                selected = 0,
                onShelf = {},
                onProfile = onOpenProfile
            )
        },
        topBar = {
            TopAppBar(
                title = { Text("我的书架") },
                actions = {
                    IconButton(onClick = { launcher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.Add, contentDescription = "导入图书")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text("搜索书名或作者") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )

            if (isImporting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (visibleBooks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (books.isEmpty()) "还没有书，点右上角导入 TXT、EPUB、Markdown 或 MOBI" else "没有匹配的书",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items = visibleBooks, key = { it.id }) { book ->
                        BookCard(
                            book = book,
                            onClick = { onOpenBook(book.id) },
                            onDelete = { pendingDelete = book }
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除这本书？") },
            text = { Text("《${book.title}》的原文和书签都会删除，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(book)
                    pendingDelete = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun BookCard(book: Book, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = buildString {
                        append(book.format.uppercase())
                        if (book.author.isNotBlank()) append(" · ${book.author}")
                        if (book.lastReadAt > 0) {
                            val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                .format(Date(book.lastReadAt))
                            append(" · 最近阅读 $date")
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }
            if (book.progress > 0f) {
                LinearProgressIndicator(
                    progress = { book.progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
                Text(
                    text = "已读 ${(book.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
