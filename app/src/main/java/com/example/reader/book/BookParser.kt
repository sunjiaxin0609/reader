package com.example.reader.book

import android.util.Xml
import com.example.reader.data.Chapter
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.util.ArrayDeque
import java.util.zip.ZipFile
import kotlin.text.Charsets.UTF_8

object BookParser {

    data class ParsedBook(
        val title: String,
        val author: String,
        val chapters: List<Chapter>
    )

    fun parse(path: String, format: String): ParsedBook {
        return when (format.lowercase()) {
            "epub" -> parseEpub(path)
            "md", "markdown" -> parseMarkdown(path)
            "mobi", "azw" -> MobiParser.parse(path)
            else -> parseTxt(path)
        }
    }

    private fun parseTxt(path: String): ParsedBook {
        val bytes = File(path).readBytes()
        val text = decodeBytes(bytes)
        return ParsedBook(
            title = File(path).nameWithoutExtension,
            author = "",
            chapters = splitChapters(text)
        )
    }

    private fun parseMarkdown(path: String): ParsedBook {
        var text = decodeBytes(File(path).readBytes())
        var title = File(path).nameWithoutExtension
        var author = ""

        val frontMatter = Regex("""(?s)\A---\s*\n(.*?)\n---\s*(?:\n|$)""").find(text)
        if (frontMatter != null) {
            frontMatter.groupValues[1].lineSequence().forEach { line ->
                when {
                    line.startsWith("title:", true) ->
                        title = line.substringAfter(':').trim().trim('"', '\'')
                    line.startsWith("author:", true) ->
                        author = line.substringAfter(':').trim().trim('"', '\'')
                }
            }
            text = text.removeRange(frontMatter.range)
        }

        val chapters = mutableListOf<Chapter>()
        val builder = StringBuilder()
        var currentTitle: String? = null
        var inCode = false

        fun flushChapter() {
            val body = markdownToText(builder.toString()).trim()
            if (body.isNotEmpty()) {
                val heading = currentTitle
                chapters += Chapter(
                    title = heading ?: "正文",
                    text = if (heading.isNullOrBlank()) body else "$heading\n$body"
                )
            }
            builder.setLength(0)
        }

        text.lineSequence().forEach { raw ->
            val line = raw.trimEnd()
            val heading = Regex("""^(#{1,2})\s+(.*)$""").find(line)
            when {
                heading != null -> {
                    flushChapter()
                    currentTitle = heading.groupValues[2].trim().ifEmpty { "第 ${chapters.size + 1} 章" }
                }
                line.startsWith("```") -> {
                    inCode = !inCode
                    if (!inCode) builder.append('\n')
                }
                !inCode && line.matches(Regex("""(?i)^title:\s*(.+)$""")) -> {
                    title = line.substringAfter(':').trim().trim('"', '\'')
                }
                !inCode && line.matches(Regex("""(?i)^author:\s*(.+)$""")) -> {
                    author = line.substringAfter(':').trim().trim('"', '\'')
                }
                else -> builder.append(line).append('\n')
            }
        }
        flushChapter()

        return ParsedBook(
            title = title.ifBlank { File(path).nameWithoutExtension },
            author = author,
            chapters = chapters.ifEmpty { listOf(Chapter("正文", markdownToText(text).trim())) }
        )
    }

    private fun markdownToText(source: String): String {
        var s = source.replace(Regex("""(?m)^\s{0,3}#{3,6}\s+(.*)$""")) {
            it.groupValues[1] + "\n"
        }
        s = s.replace(Regex("""(?m)^\s*>+\s?"""), "")
        s = s.replace(Regex("""(?m)^\s*[-*+]\s+"""), "· ")
        s = s.replace(Regex("""(?m)^\s*\d+\.\s+""")) { it.value.trimStart() }
        s = s.replace(Regex("""(?m)^\s*(---+|\*\*\*+|___+)\s*$"""), "")
        s = s.replace(Regex("""!\[([^\]]*)\]\([^)]*\)""")) { it.groupValues[1] }
        s = s.replace(Regex("""\[([^\]]+)\]\([^)]*\)""")) { it.groupValues[1] }
        s = s.replace(Regex("""\*\*([^*]+)\*\*""")) { it.groupValues[1] }
        s = s.replace(Regex("""__([^_]+)__""")) { it.groupValues[1] }
        s = s.replace(Regex("""(?<!\*)\*([^*]+)\*(?!\*)""")) { it.groupValues[1] }
        s = s.replace(Regex("""(?<!_)_([^_]+)_(?!_)""")) { it.groupValues[1] }
        s = s.replace(Regex("""`([^`]+)`""")) { it.groupValues[1] }
        s = s.replace("\r\n", "\n").replace('\r', '\n')
        return s.lineSequence().map { it.trimEnd() }.joinToString("\n").trim()
    }

    internal fun splitChapters(text: String): List<Chapter> {
        val matches = CHAPTER_PATTERN.findAll(text).toList()
        if (matches.isEmpty()) return listOf(Chapter("正文", text.trim()))

        val result = mutableListOf<Chapter>()
        val preamble = text.substring(0, matches.first().range.first).trim()
        if (preamble.isNotEmpty()) result += Chapter("开篇", preamble)

        matches.forEachIndexed { index, match ->
            val start = match.range.first
            val end = if (index + 1 < matches.size) matches[index + 1].range.first else text.length
            val body = text.substring(start, end).trim()
            if (body.isNotEmpty()) {
                result += Chapter(match.groupValues[1].trim(), body)
            }
        }
        return result.ifEmpty { listOf(Chapter("正文", text.trim())) }
    }

    private fun parseEpub(path: String): ParsedBook {
        val zip = ZipFile(path)
        try {
            val opfPath = findOpfPath(zip)
            val meta = readOpf(zip, opfPath)
            val base = opfPath.substringBeforeLast('/', "")

            val chapters = mutableListOf<Chapter>()
            meta.hrefs.forEach { href ->
                val entry = zip.getEntry(resolveEntry(base, href))
                if (entry == null || entry.isDirectory) return@forEach
                val html = decodeBytes(zip.getInputStream(entry).use { it.readBytes() })
                val text = htmlToText(html)
                if (text.length >= 20) {
                    chapters += Chapter(headingFromHtml(html) ?: "第 ${chapters.size + 1} 节", text)
                }
            }

            if (chapters.isEmpty()) {
                zip.entries().asSequence().toList()
                    .filter { !it.isDirectory && it.name.endsWith(".html", true) }
                    .sortedBy { it.name }
                    .forEach { entry ->
                        val html = decodeBytes(zip.getInputStream(entry).use { it.readBytes() })
                        val text = htmlToText(html)
                        if (text.length >= 20) {
                            chapters += Chapter(headingFromHtml(html) ?: "第 ${chapters.size + 1} 节", text)
                        }
                    }
            }

            return ParsedBook(
                title = meta.title.ifBlank { File(path).nameWithoutExtension },
                author = meta.author,
                chapters = chapters.ifEmpty { listOf(Chapter("正文", "本书没有可显示的文本内容。")) }
            )
        } finally {
            zip.close()
        }
    }

    private fun findOpfPath(zip: ZipFile): String {
        val container = zip.getEntry("META-INF/container.xml")
        if (container != null) {
            val parser = Xml.newPullParser()
            parser.setInput(zip.getInputStream(container), "UTF-8")
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && localName(parser.name) == "rootfile") {
                    parser.getAttributeValue(null, "full-path")?.let { return it }
                }
                event = parser.next()
            }
        }
        return zip.entries().asSequence().toList()
            .firstOrNull { !it.isDirectory && it.name.endsWith(".opf", true) }?.name ?: ""
    }

    private data class OpfMeta(val title: String, val author: String, val hrefs: List<String>)

    private fun readOpf(zip: ZipFile, opfPath: String): OpfMeta {
        val entry = zip.getEntry(opfPath) ?: return OpfMeta("", "", emptyList())
        val parser = Xml.newPullParser()
        parser.setInput(zip.getInputStream(entry), "UTF-8")

        val manifest = mutableMapOf<String, String>()
        val spine = mutableListOf<String>()
        var title = ""
        var author = ""
        var inMetadata = false
        var inManifest = false
        var inSpine = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (localName(parser.name)) {
                    "metadata" -> inMetadata = true
                    "manifest" -> inManifest = true
                    "spine" -> inSpine = true
                    "title" -> if (inMetadata && title.isBlank()) {
                        title = runCatching { parser.nextText().trim() }.getOrDefault("")
                    }
                    "creator" -> if (inMetadata && author.isBlank()) {
                        author = runCatching { parser.nextText().trim() }.getOrDefault("")
                    }
                    "item" -> if (inManifest) {
                        val id = parser.getAttributeValue(null, "id")
                        val href = parser.getAttributeValue(null, "href")
                        if (id != null && href != null) manifest[id] = href
                    }
                    "itemref" -> if (inSpine) {
                        parser.getAttributeValue(null, "idref")?.let { spine += it }
                    }
                }
                XmlPullParser.END_TAG -> when (localName(parser.name)) {
                    "metadata" -> inMetadata = false
                    "manifest" -> inManifest = false
                    "spine" -> inSpine = false
                }
            }
            event = parser.next()
        }

        val hrefs = spine.mapNotNull { manifest[it] }.filter { href ->
            href.endsWith(".html", true) || href.endsWith(".xhtml", true) || href.endsWith(".htm", true)
        }
        return OpfMeta(title, author, hrefs)
    }

    private fun resolveEntry(base: String, href: String): String {
        val decoded = runCatching { URLDecoder.decode(href, "UTF-8") }.getOrDefault(href)
        val path = decoded.substringBefore('#')
        val combined = if (base.isBlank() || path.startsWith("/")) path else "$base/$path"
        val parts = ArrayDeque<String>()
        combined.split('/').forEach { part ->
            when {
                part.isBlank() || part == "." -> Unit
                part == ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(part)
            }
        }
        return parts.joinToString("/")
    }

    private fun headingFromHtml(html: String): String? {
        val match = Regex("""(?is)<h[1-3][^>]*>(.*?)</h[1-3]>""").find(html) ?: return null
        return htmlToText(match.groupValues[1]).lineSequence().firstOrNull()?.take(40)?.ifBlank { null }
    }

    internal fun htmlToText(html: String): String {
        var s = html.replace(Regex("""(?is)<(script|style)[^>]*>.*?</\1>"""), "")
        s = s.replace(Regex("""(?s)<!--.*?-->"""), "")
        s = s.replace(Regex("""(?i)<br\s*/?>"""), "\n")
        s = s.replace(Regex("""(?i)</(?:p|div|h[1-6]|li|blockquote|tr)>"""), "\n")
        s = s.replace(Regex("""(?s)<[^>]+>"""), "")
        s = decodeEntities(s).replace("\r\n", "\n").replace('\r', '\n')

        val builder = StringBuilder()
        var previousBlank = false
        s.lineSequence().map { it.trim() }.forEach { line ->
            if (line.isEmpty()) {
                if (!previousBlank && builder.isNotEmpty()) {
                    builder.append('\n')
                    previousBlank = true
                }
            } else {
                builder.append(line).append('\n')
                previousBlank = false
            }
        }
        return builder.toString().trim()
    }

    private fun decodeEntities(source: String): String =
        Regex("""&(#x[0-9a-fA-F]+|#\d+|[a-zA-Z]+);""").replace(source) { match ->
            val token = match.groupValues[1]
            when {
                token.startsWith("#x") ->
                    runCatching { token.removePrefix("#x").toInt(16).toChar().toString() }.getOrDefault(match.value)
                token.startsWith("#") ->
                    runCatching { token.removePrefix("#").toInt(10).toChar().toString() }.getOrDefault(match.value)
                else -> NAMED_ENTITIES[token] ?: match.value
            }
        }

    private fun localName(name: String): String = name.substringAfter(':')

    internal fun decodeBytes(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, UTF_8)
        }
        val decoder: CharsetDecoder = UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charset.forName("GB18030"))
        }
    }

    private val NAMED_ENTITIES = mapOf(
        "nbsp" to "\u00A0",
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "hellip" to "…",
        "mdash" to "—",
        "ndash" to "–"
    )

    private val CHAPTER_PATTERN =
        Regex("""(?m)^[ \t　]*(第[零〇一二三四五六七八九十百千万两0-9]+[章节回卷部篇集][^\n]{0,40})[ \t　]*$""")
}
