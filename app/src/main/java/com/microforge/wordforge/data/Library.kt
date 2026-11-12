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
)

/** All content, loaded once from assets/data. Adding a topic = adding a JSON file, no code change. */
object Library {
    var topics: List<Topic> = emptyList()
        private set
    var items: List<Item> = emptyList()
        private set
    private var byId: Map<String, Item> = emptyMap()

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
    }

    private fun read(ctx: Context, path: String): String =
        ctx.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
}
