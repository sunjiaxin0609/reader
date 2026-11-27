package com.example.reader.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun ReaderBottomNav(
    selected: Int,
    onShelf: () -> Unit,
    onProfile: () -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == 0,
            onClick = onShelf,
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
            label = { Text("书架") }
        )
        NavigationBarItem(
            selected = selected == 1,
            onClick = onProfile,
            icon = { Icon(Icons.Default.Person, contentDescription = null) },
            label = { Text("我的") }
        )
    }
}
