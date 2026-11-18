package com.microforge.wordforge.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

data class ImportedBook(val id: String, val title: String, val chars: Int)

class WordStat(val word: String, val count: Int)

class Analysis(
    val totalTokens: Int,
    val uniqueWords: Int,
    val knownPercent: Int,
    val newWords: List<WordStat>,
)

/** Books the user brings (plain text or EPUB). Everything stays on the phone, inside the app's private folder. */
object ImportedBooks {
    private fun dir(ctx: Context): File = File(ctx.filesDir, "books").apply { mkdirs() }
    private fun indexFile(ctx: Context): File = File(dir(ctx), "index.json")

    fun list(ctx: Context): List<ImportedBook> {
        val f = indexFile(ctx)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText(Charsets.UTF_8))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                ImportedBook(o.getString("id"), o.getString("title"), o.getInt("chars"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(ctx: Context, books: List<ImportedBook>) {
        val arr = JSONArray()
        for (b in books) {
            val o = JSONObject()
            o.put("id", b.id)
            o.put("title", b.title)
            o.put("chars", b.chars)
            arr.put(o)
        }
        indexFile(ctx).writeText(arr.toString(), Charsets.UTF_8)
    }

    private fun displayName(ctx: Context, uri: Uri): String {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) return c.getString(i) ?: "book.txt"
        }
        return uri.lastPathSegment ?: "book.txt"
    }

    /** Returns null if the file can't be read or has no real text. */
    fun importFrom(ctx: Context, uri: Uri): ImportedBook? {
        val name = displayName(ctx, uri)
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.size > 12_000_000) return null
        val looksZip = bytes.size > 3 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        val raw = if (name.lowercase().endsWith(".epub") || looksZip) epubText(bytes) else String(bytes, Charsets.UTF_8)
        val clean = raw.replace("\r", "")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        if (clean.length < 200) return null
        val capped = if (clean.length > 1_500_000) clean.substring(0, 1_500_000) else clean
        val id = "ib" + System.currentTimeMillis()
        File(dir(ctx), "$id.txt").writeText(capped, Charsets.UTF_8)
        val title = name.substringBeforeLast('.').ifBlank { "كتاب مستورد" }
        val book = ImportedBook(id, title, capped.length)
        save(ctx, list(ctx) + book)
        return book
    }

    fun readText(ctx: Context, id: String): String {
        val f = File(dir(ctx), "$id.txt")
        return if (f.exists()) f.readText(Charsets.UTF_8) else ""
    }

    fun delete(ctx: Context, id: String) {
        File(dir(ctx), "$id.txt").delete()
        save(ctx, list(ctx).filter { it.id != id })
    }

    private fun epubText(bytes: ByteArray): String {
        val sb = StringBuilder()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                val n = e.name.lowercase()
                val isPage = n.endsWith(".xhtml") || n.endsWith(".html") || n.endsWith(".htm")
                if (!e.isDirectory && isPage && !n.contains("toc") && !n.contains("nav")) {
                    sb.append(htmlToText(String(zin.readBytes(), Charsets.UTF_8))).append("\n\n")
                }
                e = zin.nextEntry
            }
        }
        return sb.toString()
    }

    private fun htmlToText(html: String): String {
        var t = html.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
        t = t.replace(Regex("(?i)</(p|div|h[1-6]|li)\\s*>|<br\\s*/?>"), "\n")
        t = t.replace(Regex("<[^>]+>"), " ")
        t = t.replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&rsquo;", "'").replace("&lsquo;", "'")
            .replace("&ldquo;", "\"").replace("&rdquo;", "\"").replace("&mdash;", "—").replace("&hellip;", "...")
            .replace(Regex("&#?\\w+;"), " ")
        return t
    }

    fun sentences(text: String): List<String> =
        text.replace(Regex("\\s+"), " ").trim()
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.length >= 3 }

    /** The book as "pages" of [perPage] sentences each. */
    fun pages(text: String, perPage: Int = 10): List<List<String>> = sentences(text).chunked(perPage)

    /**
     * Counts the words of the book and lists the ones the app doesn't teach yet, most frequent first.
     * Proper names (always capitalized in the middle of a sentence) and very common function words are skipped.
     */
    fun analyze(text: String): Analysis {
        val rx = Regex("[A-Za-z][A-Za-z'’-]*")
        val counts = HashMap<String, Int>()
        val lowerish = HashMap<String, Int>()
        val upperMid = HashMap<String, Int>()
        var total = 0
        var lastEnd = 0
        for (m in rx.findAll(text)) {
            val raw = m.value.trimEnd('\'', '’', '-')
            if (raw.length < 2) continue
            total++
            val w = raw.lowercase().replace('’', '\'')
            val gap = text.substring(lastEnd, m.range.first)
            val atStart = lastEnd == 0 || gap.contains('.') || gap.contains('!') || gap.contains('?') || gap.contains('\n')
            lastEnd = m.range.last + 1
            if (raw[0].isUpperCase() && !atStart) upperMid[w] = (upperMid[w] ?: 0) + 1
            else lowerish[w] = (lowerish[w] ?: 0) + 1
            counts[w] = (counts[w] ?: 0) + 1
        }

        // Merge forms of the same word: "walked" and "walks" count for "walk" when "walk" appears.
        for (w in counts.keys.sortedByDescending { it.length }) {
            val c = counts[w] ?: continue
            val base = Library.variants(w).firstOrNull { it != w && counts.containsKey(it) }
            if (base != null) {
                counts[base] = (counts[base] ?: 0) + c
                counts.remove(w)
            }
        }

        var knownTokens = 0
        val fresh = ArrayList<WordStat>()
        for ((w, c) in counts) {
            val properName = (upperMid[w] ?: 0) > 0 && (lowerish[w] ?: 0) == 0
            if (properName || w in STOP || Library.findWord(w) != null) {
                knownTokens += c
            } else if (w.length >= 3) {
                fresh.add(WordStat(w, c))
            }
        }
        fresh.sortWith(compareByDescending<WordStat> { it.count }.thenBy { it.word })
        val percent = if (total == 0) 0 else knownTokens * 100 / total
        return Analysis(total, counts.size, percent, fresh.take(500))
    }

    private val STOP: Set<String> = (
        "a an the and or but if so as of to in on at by for with from into onto over under about after before " +
            "between through during without within up down out off again then than there here when where why how what which who whom whose " +
            "this that these those it its is are was were be been being am do does did done doing have has had having " +
            "i me my mine we us our ours you your yours he him his she her hers they them their theirs " +
            "not no nor too very just only also even still yet already ever never always often sometimes " +
            "can could will would shall should may might must all any some each every both either neither more most less least much many few " +
            "other another such same own one two three four five first second said say says go goes went gone get got " +
            "make made know knew think thought take took come came see saw look looked want like going back now well " +
            "mr mrs ms miss sir yes oh dear while because until though although whether since upon among against " +
            "around away over like little good great long new old right way thing things man men woman women people day time"
        ).split(" ").toSet()
}
