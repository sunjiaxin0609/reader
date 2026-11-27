package com.example.reader.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class BookDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE books (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                author TEXT NOT NULL DEFAULT '',
                path TEXT NOT NULL,
                format TEXT NOT NULL,
                added_at INTEGER NOT NULL,
                last_read_at INTEGER NOT NULL DEFAULT 0,
                chapter_index INTEGER NOT NULL DEFAULT 0,
                char_offset INTEGER NOT NULL DEFAULT 0,
                progress REAL NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE bookmarks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                book_id INTEGER NOT NULL,
                chapter_index INTEGER NOT NULL,
                char_offset INTEGER NOT NULL,
                excerpt TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_bookmarks_book ON bookmarks(book_id)")
        db.execSQL(CREATE_READING_LOG)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(CREATE_READING_LOG)
        }
    }

    fun insertBook(book: Book): Long = writableDatabase.insert("books", null, book.toValues())

    fun allBooks(): List<Book> {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM books ORDER BY last_read_at DESC, added_at DESC", null)
        return cursor.use { c ->
            val result = mutableListOf<Book>()
            while (c.moveToNext()) {
                result.add(c.toBook())
            }
            result
        }
    }

    fun findBook(id: Long): Book? {
        val cursor = readableDatabase.rawQuery("SELECT * FROM books WHERE id = ?", arrayOf(id.toString()))
        return cursor.use { c -> if (c.moveToNext()) c.toBook() else null }
    }

    fun deleteBook(id: Long) {
        writableDatabase.delete("bookmarks", "book_id = ?", arrayOf(id.toString()))
        writableDatabase.delete("books", "id = ?", arrayOf(id.toString()))
    }

    fun updateProgress(id: Long, chapterIndex: Int, charOffset: Int, progress: Float) {
        val values = ContentValues().apply {
            put("chapter_index", chapterIndex)
            put("char_offset", charOffset)
            put("progress", progress)
            put("last_read_at", System.currentTimeMillis())
        }
        writableDatabase.update("books", values, "id = ?", arrayOf(id.toString()))
    }

    fun bookmarks(bookId: Long): List<Bookmark> {
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM bookmarks WHERE book_id = ? ORDER BY chapter_index, char_offset",
            arrayOf(bookId.toString())
        )
        return cursor.use { c ->
            val result = mutableListOf<Bookmark>()
            while (c.moveToNext()) {
                result.add(
                    Bookmark(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        bookId = c.getLong(c.getColumnIndexOrThrow("book_id")),
                        chapterIndex = c.getInt(c.getColumnIndexOrThrow("chapter_index")),
                        charOffset = c.getInt(c.getColumnIndexOrThrow("char_offset")),
                        excerpt = c.getString(c.getColumnIndexOrThrow("excerpt")),
                        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
                    )
                )
            }
            result
        }
    }

    fun insertBookmark(bookmark: Bookmark): Long =
        writableDatabase.insert("bookmarks", null, bookmark.toValues())

    fun deleteBookmark(id: Long) {
        writableDatabase.delete("bookmarks", "id = ?", arrayOf(id.toString()))
    }

    fun addReadingTime(bookId: Long, date: String, seconds: Int) {
        if (seconds <= 0) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cursor = db.rawQuery(
                "SELECT seconds FROM reading_log WHERE date = ? AND book_id = ?",
                arrayOf(date, bookId.toString())
            )
            val existing = cursor.use { c -> if (c.moveToNext()) c.getInt(0) else null }
            if (existing == null) {
                val values = ContentValues().apply {
                    put("date", date)
                    put("book_id", bookId)
                    put("seconds", seconds)
                }
                db.insert("reading_log", null, values)
            } else {
                val values = ContentValues().apply { put("seconds", existing + seconds) }
                db.update(
                    "reading_log",
                    values,
                    "date = ? AND book_id = ?",
                    arrayOf(date, bookId.toString())
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun readingLog(): List<ReadingLog> {
        val cursor = readableDatabase.rawQuery(
            "SELECT date, book_id, seconds FROM reading_log ORDER BY date DESC",
            null
        )
        return cursor.use { c ->
            val result = mutableListOf<ReadingLog>()
            while (c.moveToNext()) {
                result += ReadingLog(
                    date = c.getString(c.getColumnIndexOrThrow("date")),
                    bookId = c.getLong(c.getColumnIndexOrThrow("book_id")),
                    seconds = c.getInt(c.getColumnIndexOrThrow("seconds"))
                )
            }
            result
        }
    }

    private fun Book.toValues() = ContentValues().apply {
        if (id != 0L) put("id", id)
        put("title", title)
        put("author", author)
        put("path", path)
        put("format", format)
        put("added_at", addedAt)
        put("last_read_at", lastReadAt)
        put("chapter_index", chapterIndex)
        put("char_offset", charOffset)
        put("progress", progress)
    }

    private fun Bookmark.toValues() = ContentValues().apply {
        put("book_id", bookId)
        put("chapter_index", chapterIndex)
        put("char_offset", charOffset)
        put("excerpt", excerpt)
        put("created_at", createdAt)
    }

    private fun android.database.Cursor.toBook() = Book(
        id = getLong(getColumnIndexOrThrow("id")),
        title = getString(getColumnIndexOrThrow("title")),
        author = getString(getColumnIndexOrThrow("author")),
        path = getString(getColumnIndexOrThrow("path")),
        format = getString(getColumnIndexOrThrow("format")),
        addedAt = getLong(getColumnIndexOrThrow("added_at")),
        lastReadAt = getLong(getColumnIndexOrThrow("last_read_at")),
        chapterIndex = getInt(getColumnIndexOrThrow("chapter_index")),
        charOffset = getInt(getColumnIndexOrThrow("char_offset")),
        progress = getFloat(getColumnIndexOrThrow("progress"))
    )

    companion object {
        private const val DATABASE_NAME = "local_reader.db"
        private const val DATABASE_VERSION = 2

        private const val CREATE_READING_LOG = """
            CREATE TABLE IF NOT EXISTS reading_log (
                date TEXT NOT NULL,
                book_id INTEGER NOT NULL,
                seconds INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (date, book_id)
            )
        """
    }
}
