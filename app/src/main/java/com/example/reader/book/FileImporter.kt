package com.example.reader.book

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.reader.data.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class FileImporter(private val context: Context) {

    data class ImportResult(
        val books: List<Book>,
        val errors: List<String>
    )

    suspend fun import(uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        val books = mutableListOf<Book>()
        val errors = mutableListOf<String>()
        val directory = File(context.filesDir, "books").apply { mkdirs() }

        for (uri in uris) {
            val displayName = displayNameOf(uri)
            val extension = extensionOf(displayName, uri)
            if (extension !in SUPPORTED) {
                errors += "$displayName：不支持的格式"
                continue
            }
            val target = File(directory, "${UUID.randomUUID()}.$extension")
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("无法读取文件")
                input.use { source ->
                    target.outputStream().use { destination -> source.copyTo(destination) }
                }
                val parsed = BookParser.parse(target.absolutePath, extension)
                val fallbackTitle = displayName.substringBeforeLast('.', displayName)
                books += Book(
                    title = if (extension == "txt") fallbackTitle else parsed.title.ifBlank { fallbackTitle },
                    author = parsed.author,
                    path = target.absolutePath,
                    format = extension,
                    addedAt = System.currentTimeMillis()
                )
            } catch (error: Exception) {
                target.delete()
                errors += "$displayName：${error.message ?: "解析失败"}"
            }
        }
        ImportResult(books, errors)
    }

    private fun displayNameOf(uri: Uri): String {
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val cursor = context.contentResolver.query(uri, projection, null, null, null)
        val name = cursor?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        return name?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "未命名文件"
    }

    private fun extensionOf(displayName: String, uri: Uri): String {
        val fromName = displayName.substringAfterLast('.', "").lowercase()
        if (fromName in SUPPORTED) return fromName
        return when (val mime = context.contentResolver.getType(uri)?.lowercase()) {
            "application/epub+zip" -> "epub"
            "application/x-mobipocket-ebook", "application/vnd.amazon.ebook" -> "mobi"
            "text/markdown", "text/x-markdown" -> "md"
            else -> when {
                mime?.contains("epub") == true -> "epub"
                mime?.contains("mobi") == true || mime?.contains("amazon") == true -> "mobi"
                mime?.contains("markdown") == true -> "md"
                mime?.contains("text") == true || mime?.contains("plain") == true -> "txt"
                else -> ""
            }
        }
    }

    companion object {
        private val SUPPORTED = setOf("txt", "epub", "md", "markdown", "mobi", "azw")
    }
}
