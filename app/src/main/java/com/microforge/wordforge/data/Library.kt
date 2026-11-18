package com.microforge.wordforge.data

import android.content.Context
import org.json.JSONObject

enum class Kind { WORD, SENTENCE }

data class Group(val id: String, val ar: String, val en: String)

data class Topic(
    val id: String,
    val num: Int,
    val ar: String,
    val en: String,
    val emoji: String,
    val available: Boolean,
    val groups: List<Group>,
)

/** One learnable item: a word (with pos + example) or a full sentence. */
data class Item(
    val id: String,
    val kind: Kind,
    val topicId: String,
    val groupId: String,
    val level: String,
    val en: String,
    val ar: String,
    val pos: String = "",
    val example: String = "",
) {
    /** The separate Arabic meanings of a word ("بيت / منزل" -> {"بيت","منزل"}), used to avoid ambiguous multiple choice. */
    val meanings: Set<String> by lazy {
        ar.replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("[\\u064B-\\u0652\\u0640]"), "")
            .split("/", "،")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /** True when two different words can both be translated with the same Arabic (house / home, fix / repair...). */
    fun conflictsWith(o: Item): Boolean =
        kind == Kind.WORD && o.kind == Kind.WORD && meanings.any { it in o.meanings }
}

data class GrammarQ(val q: String, val answer: String, val wrong: List<String>)

data class GrammarMistake(val wrong: String, val right: String, val note: String)

data class GrammarLesson(
    val id: String,
    val num: Int,
    val level: String,
    val en: String,
    val ar: String,
    val explain: List<String>,
    val formulas: List<Pair<String, String>>,
    val examples: List<Pair<String, String>>,
    val mistakes: List<GrammarMistake>,
    val quiz: List<GrammarQ>,
    val pattern: String,
)

data class Gloss(val word: String, val pos: String, val ar: String)

/** A paragraph or one chapter of a book: sentences with translations, new words, and comprehension questions. */
data class Passage(
    val id: String,
    val level: String,
    val en: String,
    val ar: String,
    val emoji: String,
    val sentences: List<Pair<String, String>>,
    val glossary: List<Gloss>,
    val quiz: List<GrammarQ>,
)

data class Book(
    val id: String,
    val level: String,
    val en: String,
    val ar: String,
    val emoji: String,
    val author: String,
    val desc: String,
    val chapters: List<Passage>,
)

/** All content, loaded once from assets/data. Adding a topic = adding a JSON file, no code change. */
object Library {
    var topics: List<Topic> = emptyList()
        private set
    var items: List<Item> = emptyList()
        private set
    var lessons: List<GrammarLesson> = emptyList()
        private set
    var paragraphs: List<Passage> = emptyList()
        private set
    var books: List<Book> = emptyList()
        private set
    private var baseTopics: List<Topic> = emptyList()
    private var baseItems: List<Item> = emptyList()
    private var custom: List<Item> = emptyList()
    private var byId: Map<String, Item> = emptyMap()
    private var wordByEn: Map<String, Item> = emptyMap()

    fun passage(id: String): Passage? =
        paragraphs.firstOrNull { it.id == id } ?: books.firstNotNullOfOrNull { b -> b.chapters.firstOrNull { it.id == id } }

    fun book(id: String): Book? = books.firstOrNull { it.id == id }

    /** Looks a word up in the app's vocabulary, also by its base form ("begged" -> "beg", "shelves" -> "shelf"). */
    fun findWord(raw: String): Item? = variants(raw).firstNotNullOfOrNull { wordByEn[it] }

    private val irregular = mapOf(
        "geese" to "goose", "mice" to "mouse", "men" to "man", "women" to "woman", "children" to "child",
        "feet" to "foot", "teeth" to "tooth", "fell" to "fall", "went" to "go", "saw" to "see", "took" to "take",
        "gave" to "give", "ran" to "run", "was" to "be", "were" to "be", "had" to "have", "said" to "say",
        "bought" to "buy", "found" to "find", "knew" to "know", "thought" to "think", "told" to "tell",
        "became" to "become", "woke" to "wake", "flew" to "fly", "sat" to "sit",
    )

    /** Possible base forms of a word as it appears in a text (keep in sync with tools/build_reading.py). */
    fun variants(raw: String): List<String> {
        val t = raw.lowercase().replace('’', '\'').trim { !it.isLetter() && it != '\'' && it != '-' }
        if (t.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        out.add(t)
        fun add(x: String) {
            if (x.length >= 2) out.add(x)
        }
        irregular[t]?.let { add(it) }
        if (t.endsWith("'s")) add(t.dropLast(2))
        if (t.endsWith("ies")) add(t.dropLast(3) + "y")
        if (t.endsWith("ves")) {
            add(t.dropLast(3) + "f")
            add(t.dropLast(3) + "fe")
        }
        if (t.endsWith("es")) add(t.dropLast(2))
        if (t.endsWith("s")) add(t.dropLast(1))
        if (t.endsWith("ied")) add(t.dropLast(3) + "y")
        if (t.endsWith("ed")) {
            add(t.dropLast(2))
            add(t.dropLast(1))
            if (t.length > 4 && t[t.length - 3] == t[t.length - 4]) add(t.dropLast(3))
        }
        if (t.endsWith("ing")) {
            add(t.dropLast(3))
            add(t.dropLast(3) + "e")
            if (t.length > 5 && t[t.length - 4] == t[t.length - 5]) add(t.dropLast(4))
        }
        if (t.endsWith("iest")) add(t.dropLast(4) + "y")
        if (t.endsWith("est")) add(t.dropLast(3))
        if (t.endsWith("er")) add(t.dropLast(2))
        if (t.endsWith("ly")) add(t.dropLast(2))
        return out.toList()
    }

    fun lesson(id: String): GrammarLesson? = lessons.firstOrNull { it.id == id }

    fun item(id: String): Item? = byId[id]

    fun topic(id: String): Topic? = topics.firstOrNull { it.id == id }

    fun count(topicId: String? = null, kind: Kind? = null): Int =
        items.count { (topicId == null || it.topicId == topicId) && (kind == null || it.kind == kind) }

    fun load(ctx: Context) {
        if (topics.isNotEmpty()) return
        val topicList = ArrayList<Topic>()
        val all = ArrayList<Item>()
        val root = JSONObject(read(ctx, "data/topics.json"))
        val arr = root.getJSONArray("topics")
        for (i in 0 until arr.length()) {
            val t = arr.getJSONObject(i)
            val id = t.getString("id")
            val file = if (t.isNull("file")) null else t.getString("file")
            val groups = ArrayList<Group>()
            if (file != null) {
                val d = JSONObject(read(ctx, "data/$file"))
                val gs = d.getJSONArray("groups")
                for (j in 0 until gs.length()) {
                    val g = gs.getJSONObject(j)
                    groups.add(Group(g.getString("id"), g.getString("ar"), g.getString("en")))
                }
                val ws = d.getJSONArray("words")
                for (j in 0 until ws.length()) {
                    val w = ws.getJSONObject(j)
                    all.add(
                        Item(
                            id = w.getString("id"), kind = Kind.WORD, topicId = id,
                            groupId = w.getString("g"), level = w.getString("lv"),
                            en = w.getString("en"), ar = w.getString("ar"),
                            pos = w.optString("pos", ""), example = w.optString("ex", ""),
                        )
                    )
                }
                val ss = d.getJSONArray("sentences")
                for (j in 0 until ss.length()) {
                    val s = ss.getJSONObject(j)
                    all.add(
                        Item(
                            id = s.getString("id"), kind = Kind.SENTENCE, topicId = id,
                            groupId = s.getString("g"), level = s.getString("lv"),
                            en = s.getString("en"), ar = s.getString("ar"),
                        )
                    )
                }
            }
            topicList.add(
                Topic(
                    id = id, num = t.getInt("num"), ar = t.getString("ar"), en = t.getString("en"),
                    emoji = t.getString("emoji"), available = file != null, groups = groups,
                )
            )
        }
        baseTopics = topicList
        baseItems = all
        rebuild()
        loadGrammar(ctx)
        loadReading(ctx)
    }

    /** "كلماتي": words the user added. Replaces the whole list (used at startup and after restoring a backup). */
    fun setCustom(list: List<Item>) {
        custom = list
        rebuild()
    }

    fun addCustom(item: Item) {
        custom = custom + item
        rebuild()
    }

    fun removeCustom(id: String) {
        custom = custom.filter { it.id != id }
        rebuild()
    }

    private fun rebuild() {
        items = baseItems + custom
        byId = items.associateBy { it.id }
        wordByEn = items.filter { it.kind == Kind.WORD }.associateBy { it.en.lowercase() }
        topics = if (custom.isEmpty()) baseTopics
        else baseTopics + Topic("t12", 12, "كلماتي", "My Words", "⭐", true, listOf(Group("my", "كلمات أضفتها", "Added by me")))
    }

    private fun passagesOf(arr: org.json.JSONArray): List<Passage> = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        val s = o.getJSONArray("s")
        val w = o.getJSONArray("w")
        val q = o.getJSONArray("q")
        Passage(
            id = o.getString("id"),
            level = o.getString("lv"),
            en = o.getString("en"),
            ar = o.getString("ar"),
            emoji = o.optString("e", "📖"),
            sentences = (0 until s.length()).map { Pair(s.getJSONArray(it).getString(0), s.getJSONArray(it).getString(1)) },
            glossary = (0 until w.length()).map {
                val g = w.getJSONArray(it)
                Gloss(g.getString(0), g.getString(1), g.getString(2))
            },
            quiz = (0 until q.length()).map {
                val x = q.getJSONObject(it)
                GrammarQ(x.getString("q"), x.getString("a"), strings(x.getJSONArray("w")))
            },
        )
    }

    private fun loadReading(ctx: Context) {
        val files = ctx.assets.list("data") ?: emptyArray()
        if (!files.contains("reading.json")) return
        val root = JSONObject(read(ctx, "data/reading.json"))
        paragraphs = passagesOf(root.getJSONArray("paragraphs"))
        val bs = root.getJSONArray("books")
        val list = ArrayList<Book>()
        for (i in 0 until bs.length()) {
            val b = bs.getJSONObject(i)
            list.add(
                Book(
                    id = b.getString("id"), level = b.getString("lv"), en = b.getString("en"), ar = b.getString("ar"),
                    emoji = b.getString("e"), author = b.getString("au"), desc = b.getString("d"),
                    chapters = passagesOf(b.getJSONArray("ch")),
                )
            )
        }
        books = list
    }

    private fun strings(a: org.json.JSONArray): List<String> = (0 until a.length()).map { a.getString(it) }

    private fun pairs(a: org.json.JSONArray): List<Pair<String, String>> =
        (0 until a.length()).map { Pair(a.getJSONArray(it).getString(0), a.getJSONArray(it).getString(1)) }

    private fun loadGrammar(ctx: Context) {
        val files = ctx.assets.list("data") ?: emptyArray()
        if (!files.contains("grammar.json")) return
        val arr = JSONObject(read(ctx, "data/grammar.json")).getJSONArray("lessons")
        val out = ArrayList<GrammarLesson>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val ms = o.getJSONArray("mistakes")
            val qs = o.getJSONArray("quiz")
            out.add(
                GrammarLesson(
                    id = o.getString("id"),
                    num = o.getInt("num"),
                    level = o.getString("lv"),
                    en = o.getString("en"),
                    ar = o.getString("ar"),
                    explain = strings(o.getJSONArray("explain")),
                    formulas = pairs(o.getJSONArray("formulas")),
                    examples = pairs(o.getJSONArray("examples")),
                    mistakes = (0 until ms.length()).map {
                        val m = ms.getJSONObject(it)
                        GrammarMistake(m.getString("w"), m.getString("r"), m.getString("n"))
                    },
                    quiz = (0 until qs.length()).map {
                        val q = qs.getJSONObject(it)
                        GrammarQ(q.getString("q"), q.getString("a"), strings(q.getJSONArray("w")))
                    },
                    pattern = o.optString("p", ""),
                )
            )
        }
        lessons = out
    }

    private fun read(ctx: Context, path: String): String =
        ctx.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
