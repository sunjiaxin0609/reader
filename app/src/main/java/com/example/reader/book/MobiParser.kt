package com.example.reader.book

import com.example.reader.data.Chapter
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlin.text.Charsets.UTF_8

object MobiParser {

    fun parse(path: String): BookParser.ParsedBook {
        val data = File(path).readBytes()
        if (data.size < 100) throw UnsupportedBookException("MOBI 文件不完整")

        val recordCount = readU16(data, 60)
        if (recordCount < 2) throw UnsupportedBookException("不是有效的 MOBI 文件")

        val record0 = record(data, 0, recordCount)
            ?: throw UnsupportedBookException("MOBI 记录表损坏")
        if (record0.size < 24) throw UnsupportedBookException("MOBI 头部损坏")

        val compression = readU16(record0, 0)
        if (compression == HUFFDIC) {
            throw UnsupportedBookException("不支持 HUFFDIC 压缩的 MOBI，请先用 Calibre 转成 EPUB 再导入")
        }
        if (compression != 1 && compression != 2) {
            throw UnsupportedBookException("不支持的 MOBI 压缩方式：$compression")
        }

        val mobiOffset = 16
        if (mobiOffset + 8 > record0.size ||
            String(record0, mobiOffset, 4, UTF_8) != "MOBI"
        ) {
            throw UnsupportedBookException("未找到 MOBI 头部")
        }
        val mobiLength = readU32(record0, mobiOffset + 4).toInt()
        if (mobiLength <= 0 || mobiOffset + mobiLength > record0.size) {
            throw UnsupportedBookException("MOBI 头部长度异常")
        }
        val encoding = readU32(record0, mobiOffset + 12)

        var title = ""
        var author = ""
        var drm = false

        val exthOffset = mobiOffset + mobiLength
        if (exthOffset + 12 <= record0.size &&
            String(record0, exthOffset, 4, UTF_8) == "EXTH"
        ) {
            val count = readU32(record0, exthOffset + 8).toInt()
            var cursor = exthOffset + 12
            repeat(count.coerceAtMost(1024)) {
                if (cursor + 8 > record0.size) return@repeat
                val type = readU32(record0, cursor)
                val length = readU32(record0, cursor + 4).toInt()
                if (length < 8 || cursor + length > record0.size) return@repeat
                val payload = record0.copyOfRange(cursor + 8, cursor + length)
                when (type.toInt()) {
                    503 -> title = decodeString(payload, encoding)
                    504 -> author = decodeString(payload, encoding)
                    406, 407 -> drm = true
                }
                cursor += length
            }
        }

        if (drm) throw UnsupportedBookException("检测到 DRM 加密，无法读取这本 MOBI")

        val firstImage = if (mobiOffset + 0x5C + 4 <= record0.size) {
            readU32(record0, mobiOffset + 0x5C)
        } else {
            0L
        }
        var textCount = if (firstImage in 1L until recordCount.toLong()) {
            firstImage.toInt() - 1
        } else {
            recordCount - 1
        }
        if (textCount < 1 || textCount > recordCount - 1) textCount = recordCount - 1

        val rawBytes = ByteArrayOutputStream()
        for (index in 1..textCount) {
            val textRecord = record(data, index, recordCount) ?: continue
            rawBytes.write(decodeTextRecord(textRecord, compression))
        }

        val text = BookParser.htmlToText(decodeString(rawBytes.toByteArray(), encoding))
        val chapters = BookParser.splitChapters(text)

        return BookParser.ParsedBook(
            title = title.ifBlank { File(path).nameWithoutExtension },
            author = author,
            chapters = chapters.ifEmpty { listOf(Chapter("正文", text.trim())) }
        )
    }

    private fun decodeTextRecord(record: ByteArray, compression: Int): ByteArray {
        if (record.size < 2) return ByteArray(0)
        if (compression == 1 && record[0] == '<'.code.toByte()) return record

        val flags = readU16(record, 0)
        val trailing = flags and 0x000F
        var offset = 2
        var multibyte = false

        if (flags and 0x8000 != 0) {
            if (record.size <= offset) return ByteArray(0)
            val extraFlags = record[offset].toInt() and 0xFF
            offset++
            multibyte = extraFlags and 0x04 != 0
        }

        val end = record.size - trailing * (if (multibyte) 2 else 1)
        if (end <= offset) return ByteArray(0)
        val payload = record.copyOfRange(offset, end)
        return if (compression == 2) decompressPalmDoc(payload) else payload
    }

    private fun decompressPalmDoc(source: ByteArray): ByteArray {
        val output = ArrayList<Byte>(source.size * 2)
        var index = 0
        while (index < source.size) {
            val code = source[index++].toInt() and 0xFF
            when {
                code == 0 -> output.add(0)
                code <= 8 -> repeat(code) {
                    if (index < source.size) output.add(source[index++])
                }
                code <= 0x7F -> output.add(code.toByte())
                code <= 0xBF -> {
                    if (index >= source.size) break
                    val next = source[index++].toInt() and 0xFF
                    val value = ((code and 0x3F) shl 8) or next
                    output.add((value and 0xFF).toByte())
                    output.add((value shr 8).toByte())
                }
                else -> {
                    if (index >= source.size) break
                    val next = source[index++].toInt() and 0xFF
                    val pair = (code shl 8) or next
                    val length = (pair and 0x07) + 3
                    val distance = (pair shr 3) and 0x7FF
                    if (distance <= 0 || distance > output.size) {
                        repeat(length) { output.add(32) }
                    } else {
                        repeat(length) { output.add(output[output.size - distance]) }
                    }
                }
            }
        }
        return ByteArray(output.size) { output[it] }
    }

    private fun decodeString(bytes: ByteArray, encoding: Long): String {
        if (bytes.isEmpty()) return ""
        if (encoding == 65001L) return String(bytes, UTF_8)
        val strictUtf8 = try {
            UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            null
        }
        return strictUtf8 ?: String(bytes, Charset.forName("windows-1252"))
    }

    private fun record(data: ByteArray, index: Int, count: Int): ByteArray? {
        val entry = 78 + index * 8
        if (entry + 8 > data.size) return null
        val start = readU32(data, entry).toInt()
        val end = if (index + 1 < count) readU32(data, entry + 8).toInt() else data.size
        if (start < 0 || end > data.size || end <= start) return null
        return data.copyOfRange(start, end)
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int {
        if (offset + 2 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    }

    private fun readU32(bytes: ByteArray, offset: Int): Long {
        if (offset + 4 > bytes.size) return 0L
        return ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)
    }

    private const val HUFFDIC = 17480
}
