package com.example.reader.data

data class Book(
    val id: Long = 0L,
    val title: String,
    val author: String = "",
    val path: String,
    val format: String,
    val addedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long = 0L,
    val chapterIndex: Int = 0,
    val charOffset: Int = 0,
    val progress: Float = 0f
)

data class Bookmark(
    val id: Long = 0L,
    val bookId: Long,
    val chapterIndex: Int,
    val charOffset: Int,
    val excerpt: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class Chapter(
    val title: String,
    val text: String
)

data class ReadingLog(
    val date: String,
    val bookId: Long,
    val seconds: Int
)

enum class ReaderTheme { LIGHT, SEPIA, DARK }

data class ReaderSettings(
    val fontSize: Int = 18,
    val lineHeightMultiplier: Float = 1.6f,
    val theme: ReaderTheme = ReaderTheme.LIGHT,
    val useSerif: Boolean = true
)
