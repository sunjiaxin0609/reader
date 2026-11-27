package com.example.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.reader.ui.theme.ReaderAppTheme
import com.example.reader.vm.AppViewModel

@Composable
fun LocalReaderApp() {
    val appViewModel: AppViewModel = viewModel()
    val settings by appViewModel.settings.collectAsState()
    var openedBookId by remember { mutableStateOf<Long?>(null) }
    var selectedTab by remember { mutableStateOf(0) }

    ReaderAppTheme(settings = settings) {
        val bookId = openedBookId
        if (bookId != null) {
            ReaderScreen(bookId = bookId, onBack = { openedBookId = null })
        } else if (selectedTab == 0) {
            BookshelfScreen(
                onOpenBook = { openedBookId = it },
                onOpenProfile = { selectedTab = 1 }
            )
        } else {
            ProfileScreen(onShelf = { selectedTab = 0 })
        }
    }
}
