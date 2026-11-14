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

/** All content, loaded once from assets/data. Adding a topic = adding a JSON file, no code change. */
object Library {
    var topics: List<Topic> = emptyList()
        private set
    var items: List<Item> = emptyList()
        private set
    var lessons: List<GrammarLesson> = emptyList()
        private set
    private var byId: Map<String, Item> = emptyMap()

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
        topics = topicList
        items = all
        byId = all.associateBy { it.id }
        loadGrammar(ctx)
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
