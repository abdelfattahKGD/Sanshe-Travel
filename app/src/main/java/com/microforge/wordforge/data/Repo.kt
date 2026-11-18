package com.microforge.wordforge.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate

/** box 0 = seen but not learned, 1..6 = Leitner boxes (5+ counts as "mastered"). */
data class Progress(
    val id: String,
    val box: Int,
    val due: Long,
    val correct: Int,
    val wrong: Int,
    val last: Long,
    val starred: Boolean,
)

data class DayStat(
    val day: String,
    val newWords: Int,
    val newSentences: Int,
    val answers: Int,
    val correct: Int,
    val wrong: Int,
)

/** How well an item is known. This is what the UI shows, so "heard it" and "memorized it" are never confused. */
enum class Status(val label: String, val emoji: String) {
    NEW("جديدة", "⚪"),
    HARD("صعبة", "🔴"),
    LEARNING("بتتعلّم", "🟠"),
    MEMORIZED("محفوظة", "🟢"),
    MASTERED("متقنة", "🏆"),
}

/**
 * Spaced repetition (Leitner). Rules that keep "memorized" honest:
 *  - Only an answer ("I knew it" / correct choice) moves an item. Listening or browsing never does.
 *  - Boxes: new -> 2 on first correct, then +1 per correct answer *after the due date*.
 *  - Answering correctly BEFORE the due date earns nothing (no cramming the same card ten times).
 *  - A wrong answer sends the item back to box 1 and it returns in 10 minutes.
 *  - MEMORIZED = box 4+ (recalled correctly after gaps of 1, 2 and 4+ days). MASTERED = box 6.
 */
object Srs {
    private val days = intArrayOf(0, 1, 2, 4, 8, 16, 32)
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val RETRY_MS = 10L * 60 * 1000

    fun statusOf(p: Progress?): Status = when {
        p == null || p.box == 0 -> Status.NEW
        p.box == 1 -> Status.HARD
        p.box <= 3 -> Status.LEARNING
        p.box <= 5 -> Status.MEMORIZED
        else -> Status.MASTERED
    }

    fun isEarly(p: Progress?, now: Long): Boolean = p != null && p.box >= 1 && p.due > now

    fun next(id: String, old: Progress?, correct: Boolean, now: Long): Progress {
        if (correct && old != null && old.box >= 1 && old.due > now) {
            return old.copy(correct = old.correct + 1, last = now)
        }
        val box0 = old?.box ?: 0
        val newBox = if (correct) {
            if (box0 == 0) 2 else minOf(6, box0 + 1)
        } else {
            1
        }
        val due = if (correct) now + days[newBox] * DAY_MS else now + RETRY_MS
        return Progress(
            id = id,
            box = newBox,
            due = due,
            correct = (old?.correct ?: 0) + (if (correct) 1 else 0),
            wrong = (old?.wrong ?: 0) + (if (correct) 0 else 1),
            last = now,
            starred = old?.starred ?: false,
        )
    }

    /** "بعد 4 أيام", "بعد 10 دقيقة"... */
    fun dueText(due: Long, now: Long): String {
        val diff = due - now
        if (diff <= 0) return "دلوقتي"
        val min = diff / 60000
        if (min < 60) return "بعد ${maxOf(min, 1L)} دقيقة"
        val hours = min / 60
        if (hours < 24) return "بعد $hours ساعة"
        val d = (hours + 12) / 24
        return when (d) {
            1L -> "بعد يوم"
            2L -> "بعد يومين"
            in 3L..10L -> "بعد $d أيام"
            else -> "بعد $d يوم"
        }
    }

    /** What happens if the user answers "I knew it" right now (shown under the button). */
    fun previewCorrect(old: Progress?, now: Long): String =
        if (isEarly(old, now)) "من غير تقدّم (معادها لسه)" else dueText(next("", old, true, now).due, now)

    /** What happens if the user answers "not yet". */
    fun previewWrong(now: Long): String = dueText(now + RETRY_MS, now)
}

private const val CUSTOM_TABLE =
    "CREATE TABLE IF NOT EXISTS custom(id TEXT PRIMARY KEY, en TEXT NOT NULL, ar TEXT NOT NULL, " +
        "ex TEXT NOT NULL DEFAULT '', added INTEGER NOT NULL DEFAULT 0)"

private class Db(ctx: Context) : SQLiteOpenHelper(ctx, "wordforge.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE progress(id TEXT PRIMARY KEY, box INTEGER NOT NULL, due INTEGER NOT NULL, " +
                "correct INTEGER NOT NULL, wrong INTEGER NOT NULL, last INTEGER NOT NULL, " +
                "starred INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE daily(day TEXT PRIMARY KEY, newWords INTEGER NOT NULL DEFAULT 0, " +
                "newSentences INTEGER NOT NULL DEFAULT 0, answers INTEGER NOT NULL DEFAULT 0, " +
                "correct INTEGER NOT NULL DEFAULT 0, wrong INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE TABLE kv(k TEXT PRIMARY KEY, v TEXT NOT NULL)")
        db.execSQL(CUSTOM_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema changes go here (always keep the user's progress!).
        if (oldVersion < 2) db.execSQL(CUSTOM_TABLE)
    }
}

/**
 * Single source of truth for the user's progress. Keeps everything in memory (a few thousand rows at most)
 * and writes through to SQLite. Composables read [tick] to get recomposed after any change.
 */
class Repo(ctx: Context) {
    private val helper = Db(ctx)
    private val db: SQLiteDatabase get() = helper.writableDatabase

    val progress = HashMap<String, Progress>()

    var tick by mutableIntStateOf(0)
        private set

    init {
        db.rawQuery("SELECT id, box, due, correct, wrong, last, starred FROM progress", null).use { c ->
            while (c.moveToNext()) {
                progress[c.getString(0)] = Progress(
                    c.getString(0), c.getInt(1), c.getLong(2), c.getInt(3), c.getInt(4), c.getLong(5),
                    c.getInt(6) == 1,
                )
            }
        }
    }

    fun today(): String = LocalDate.now().toString()

    // ---------- answering ----------

    fun record(item: Item, correct: Boolean) {
        val now = System.currentTimeMillis()
        val old = progress[item.id]
        val isNew = old == null || old.box == 0
        val p = Srs.next(item.id, old, correct, now)
        save(p)
        bumpDaily(isNew, item.kind, correct)
        tick++
    }

    fun toggleStar(id: String) {
        val old = progress[id] ?: Progress(id, 0, 0L, 0, 0, 0L, false)
        save(old.copy(starred = !old.starred))
        tick++
    }

    private fun save(p: Progress) {
        progress[p.id] = p
        val cv = ContentValues().apply {
            put("id", p.id)
            put("box", p.box)
            put("due", p.due)
            put("correct", p.correct)
            put("wrong", p.wrong)
            put("last", p.last)
            put("starred", if (p.starred) 1 else 0)
        }
        db.insertWithOnConflict("progress", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun bumpDaily(isNew: Boolean, kind: Kind, correct: Boolean) {
        val day = today()
        db.execSQL("INSERT OR IGNORE INTO daily(day) VALUES(?)", arrayOf<Any?>(day))
        val sets = mutableListOf("answers = answers + 1")
        sets.add(if (correct) "correct = correct + 1" else "wrong = wrong + 1")
        if (isNew) sets.add(if (kind == Kind.WORD) "newWords = newWords + 1" else "newSentences = newSentences + 1")
        db.execSQL("UPDATE daily SET ${sets.joinToString(", ")} WHERE day = ?", arrayOf<Any?>(day))
    }

    // ---------- sessions ----------

    /** Due items first (shuffled), then brand-new items in content order. */
    fun buildSession(kind: Kind?, topicId: String?, newLimit: Int, dueLimit: Int): List<Item> {
        val now = System.currentTimeMillis()
        fun matches(i: Item) = (kind == null || i.kind == kind) && (topicId == null || i.topicId == topicId)
        val due = progress.values
            .filter { it.box >= 1 && it.due <= now }
            .mapNotNull { Library.item(it.id) }
            .filter { matches(it) }
            .shuffled()
            .take(dueLimit)
        val newKind = kind ?: Kind.WORD
        val fresh = Library.items
            .filter { matches(it) && it.kind == newKind && (progress[it.id]?.box ?: 0) == 0 }
            .sortedBy { if (it.topicId == "t12") 0 else 1 }
            .take(newLimit)
        return due + fresh
    }

    fun dueCount(kind: Kind?): Int {
        val now = System.currentTimeMillis()
        return progress.values.count {
            it.box >= 1 && it.due <= now && (kind == null || Library.item(it.id)?.kind == kind)
        }
    }

    // ---------- stats ----------

    fun statusOf(id: String): Status = Srs.statusOf(progress[id])

    fun countByStatus(kind: Kind? = null, topicId: String? = null): Map<Status, Int> {
        val m = HashMap<Status, Int>()
        for (st in Status.entries) m[st] = 0
        for (item in Library.items) {
            if (kind != null && item.kind != kind) continue
            if (topicId != null && item.topicId != topicId) continue
            val st = Srs.statusOf(progress[item.id])
            m[st] = (m[st] ?: 0) + 1
        }
        return m
    }

    /** Items that are really memorized (box 4+), not merely seen. */
    fun memorized(kind: Kind?, topicId: String? = null): Int {
        val m = countByStatus(kind, topicId)
        return (m[Status.MEMORIZED] ?: 0) + (m[Status.MASTERED] ?: 0)
    }

    /** Items the user has answered at least once (learning, memorized or mastered, or marked hard). */
    fun started(kind: Kind?, topicId: String? = null): Int {
        val m = countByStatus(kind, topicId)
        return Library.count(topicId, kind) - (m[Status.NEW] ?: 0)
    }

    fun todayStat(): DayStat = dayStats(1).first()

    /** Last [n] days (oldest first), zero-filled. */
    fun dayStats(n: Int): List<DayStat> {
        val start = LocalDate.now().minusDays((n - 1).toLong())
        val map = HashMap<String, DayStat>()
        db.rawQuery(
            "SELECT day, newWords, newSentences, answers, correct, wrong FROM daily WHERE day >= ?",
            arrayOf(start.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                map[c.getString(0)] = DayStat(
                    c.getString(0), c.getInt(1), c.getInt(2), c.getInt(3), c.getInt(4), c.getInt(5),
                )
            }
        }
        return (0 until n).map { i ->
            val d = start.plusDays(i.toLong()).toString()
            map[d] ?: DayStat(d, 0, 0, 0, 0, 0)
        }
    }

    /** Returns (answers, correct) over all time. */
    fun totals(): Pair<Int, Int> {
        db.rawQuery("SELECT COALESCE(SUM(answers),0), COALESCE(SUM(correct),0) FROM daily", null).use { c ->
            if (c.moveToFirst()) return Pair(c.getInt(0), c.getInt(1))
        }
        return Pair(0, 0)
    }

    fun streak(): Int {
        val days = HashSet<String>()
        db.rawQuery("SELECT day FROM daily WHERE answers > 0", null).use { c ->
            while (c.moveToNext()) days.add(c.getString(0))
        }
        var d = LocalDate.now()
        if (!days.contains(d.toString())) d = d.minusDays(1)
        var n = 0
        while (days.contains(d.toString())) {
            n++
            d = d.minusDays(1)
        }
        return n
    }

    // ---------- settings (goals) ----------

    fun getInt(key: String, default: Int): Int {
        db.rawQuery("SELECT v FROM kv WHERE k = ?", arrayOf(key)).use { c ->
            if (c.moveToFirst()) return c.getString(0).toIntOrNull() ?: default
        }
        return default
    }

    fun setInt(key: String, value: Int) {
        val cv = ContentValues().apply {
            put("k", key)
            put("v", value.toString())
        }
        db.insertWithOnConflict("kv", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        tick++
    }

    // ---------- "كلماتي": words the user adds (with their own meaning) ----------

    private fun customItem(id: String, en: String, ar: String, ex: String) = Item(
        id = id, kind = Kind.WORD, topicId = "t12", groupId = "my", level = "B1",
        en = en, ar = ar, pos = "", example = ex,
    )

    fun loadCustomWords(): List<Item> {
        val out = ArrayList<Item>()
        db.rawQuery("SELECT id, en, ar, ex FROM custom ORDER BY added", null).use { c ->
            while (c.moveToNext()) out.add(customItem(c.getString(0), c.getString(1), c.getString(2), c.getString(3)))
        }
        return out
    }

    /** Returns false if the word or meaning is empty or the word is already in "كلماتي". */
    fun addCustomWord(en: String, ar: String, example: String): Boolean {
        val e = en.trim()
        val a = ar.trim()
        if (e.isEmpty() || a.isEmpty()) return false
        if (Library.items.any { it.topicId == "t12" && it.en.equals(e, ignoreCase = true) }) return false
        val id = "cw" + System.currentTimeMillis()
        val cv = ContentValues()
        cv.put("id", id)
        cv.put("en", e)
        cv.put("ar", a)
        cv.put("ex", example.trim())
        cv.put("added", System.currentTimeMillis())
        db.insertWithOnConflict("custom", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        Library.addCustom(customItem(id, e, a, example.trim()))
        tick++
        return true
    }

    fun deleteCustomWord(id: String) {
        db.execSQL("DELETE FROM custom WHERE id = ?", arrayOf<Any?>(id))
        db.execSQL("DELETE FROM progress WHERE id = ?", arrayOf<Any?>(id))
        progress.remove(id)
        Library.removeCustom(id)
        tick++
    }

    /** Words the user marked "I know this" while analyzing a book (hidden from the new-words list). */
    fun knownWordSet(): Set<String> {
        val out = HashSet<String>()
        db.rawQuery("SELECT k FROM kv WHERE k LIKE 'kw_%'", null).use { c ->
            while (c.moveToNext()) out.add(c.getString(0).removePrefix("kw_"))
        }
        return out
    }

    // ---------- variety: what each exercise shows next ----------

    fun getStr(key: String, default: String = ""): String {
        db.rawQuery("SELECT v FROM kv WHERE k = ?", arrayOf(key)).use { c ->
            if (c.moveToFirst()) return c.getString(0)
        }
        return default
    }

    fun setStr(key: String, value: String) {
        val cv = ContentValues()
        cv.put("k", key)
        cv.put("v", value)
        db.insertWithOnConflict("kv", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /**
     * Picks [n] items for one exercise ([key]) with variety:
     *  - never repeats what this exercise showed recently (it remembers its own history),
     *  - mixes ~30% items that are due for review, ~30% weak ones, and the rest brand-new items from the library,
     *  - when the whole library has been cycled through, it starts a new cycle.
     */
    fun pickFrom(pool: List<Item>, n: Int, key: String): List<Item> {
        if (pool.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val historyKey = "recent_$key"
        val recent = getStr(historyKey).split(",").filter { it.isNotEmpty() }.toMutableList()
        val lastRounds = recent.takeLast(n * 2).toSet()
        val allRecent = recent.toSet()

        fun isDue(x: Item): Boolean {
            val pr = progress[x.id] ?: return false
            return pr.box >= 1 && pr.due <= now
        }

        val due = pool.filter { isDue(it) && it.id !in lastRounds }.shuffled()
        val dueIds = due.map { it.id }.toSet()
        val weak = pool.filter {
            val st = Srs.statusOf(progress[it.id])
            it.id !in dueIds && it.id !in allRecent && (st == Status.HARD || st == Status.LEARNING)
        }.shuffled()
        val fresh = pool.filter {
            it.id !in dueIds && it.id !in allRecent && Srs.statusOf(progress[it.id]) == Status.NEW
        }.shuffled()

        val chosen = LinkedHashMap<String, Item>()
        fun take(src: List<Item>, limit: Int) {
            var added = 0
            for (x in src) {
                if (added >= limit || chosen.size >= n) break
                if (x.id !in chosen) {
                    chosen[x.id] = x
                    added++
                }
            }
        }
        take(due, (n * 3 + 9) / 10)
        take(weak, (n * 3 + 9) / 10)
        take(fresh, n)
        if (chosen.size < n) take(pool.filter { it.id !in allRecent && it.id !in chosen }.shuffled(), n)
        if (chosen.size < n) {
            // The whole pool has been used: forget the old history and start a new cycle.
            recent.clear()
            take(pool.filter { it.id !in chosen && it.id !in lastRounds }.shuffled(), n)
            take(pool.filter { it.id !in chosen }.shuffled(), n)
        }

        val result = chosen.values.toList().shuffled()
        recent.addAll(result.map { it.id })
        val cap = maxOf(40, pool.size * 6 / 10)
        val kept = if (recent.size > cap) recent.takeLast(cap) else recent
        setStr(historyKey, kept.joinToString(","))
        return result
    }

    fun pick(kind: Kind, n: Int, key: String, maxWords: Int = 99): List<Item> =
        pickFrom(Library.items.filter { it.kind == kind && it.en.trim().split(" ").size <= maxWords }, n, key)

    /** Everything the user has done (progress, daily stats, settings) as one JSON text, for the backup file. */
    fun exportJson(): String {
        val root = org.json.JSONObject()
        root.put("app", "SansheTurtle")
        root.put("format", 1)
        root.put("exported", System.currentTimeMillis())
        val prog = org.json.JSONArray()
        for (p in progress.values) {
            val o = org.json.JSONObject()
            o.put("id", p.id)
            o.put("box", p.box)
            o.put("due", p.due)
            o.put("correct", p.correct)
            o.put("wrong", p.wrong)
            o.put("last", p.last)
            o.put("starred", if (p.starred) 1 else 0)
            prog.put(o)
        }
        root.put("progress", prog)
        val daily = org.json.JSONArray()
        db.rawQuery("SELECT day, newWords, newSentences, answers, correct, wrong FROM daily", null).use { c ->
            while (c.moveToNext()) {
                val o = org.json.JSONObject()
                o.put("day", c.getString(0))
                o.put("newWords", c.getInt(1))
                o.put("newSentences", c.getInt(2))
                o.put("answers", c.getInt(3))
                o.put("correct", c.getInt(4))
                o.put("wrong", c.getInt(5))
                daily.put(o)
            }
        }
        root.put("daily", daily)
        val kv = org.json.JSONObject()
        db.rawQuery("SELECT k, v FROM kv", null).use { c ->
            while (c.moveToNext()) kv.put(c.getString(0), c.getString(1))
        }
        root.put("kv", kv)
        val customArr = org.json.JSONArray()
        db.rawQuery("SELECT id, en, ar, ex, added FROM custom", null).use { c ->
            while (c.moveToNext()) {
                val o = org.json.JSONObject()
                o.put("id", c.getString(0))
                o.put("en", c.getString(1))
                o.put("ar", c.getString(2))
                o.put("ex", c.getString(3))
                o.put("added", c.getLong(4))
                customArr.put(o)
            }
        }
        root.put("custom", customArr)
        return root.toString()
    }

    /** Replaces the current progress with a backup made by [exportJson]. Returns false (and changes nothing) if the file is not valid. */
    fun importJson(text: String): Boolean {
        try {
            val root = org.json.JSONObject(text)
            if (root.optString("app") != "SansheTurtle") return false
            val prog = root.getJSONArray("progress")
            val daily = root.getJSONArray("daily")
            val kv = root.getJSONObject("kv")
            val customArr = root.optJSONArray("custom")
            val rows = ArrayList<Progress>()
            for (i in 0 until prog.length()) {
                val o = prog.getJSONObject(i)
                rows.add(
                    Progress(
                        o.getString("id"), o.getInt("box"), o.getLong("due"), o.getInt("correct"),
                        o.getInt("wrong"), o.getLong("last"), o.getInt("starred") == 1,
                    )
                )
            }
            db.beginTransaction()
            try {
                db.execSQL("DELETE FROM progress")
                db.execSQL("DELETE FROM daily")
                db.execSQL("DELETE FROM kv")
                db.execSQL("DELETE FROM custom")
                progress.clear()
                if (customArr != null) {
                    for (i in 0 until customArr.length()) {
                        val o = customArr.getJSONObject(i)
                        val cv = ContentValues()
                        cv.put("id", o.getString("id"))
                        cv.put("en", o.getString("en"))
                        cv.put("ar", o.getString("ar"))
                        cv.put("ex", o.optString("ex", ""))
                        cv.put("added", o.optLong("added", 0L))
                        db.insertWithOnConflict("custom", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                    }
                }
                for (r in rows) save(r)
                for (i in 0 until daily.length()) {
                    val o = daily.getJSONObject(i)
                    val cv = ContentValues()
                    cv.put("day", o.getString("day"))
                    cv.put("newWords", o.getInt("newWords"))
                    cv.put("newSentences", o.getInt("newSentences"))
                    cv.put("answers", o.getInt("answers"))
                    cv.put("correct", o.getInt("correct"))
                    cv.put("wrong", o.getInt("wrong"))
                    db.insertWithOnConflict("daily", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                }
                val keys = kv.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val cv = ContentValues()
                    cv.put("k", k)
                    cv.put("v", kv.getString(k))
                    db.insertWithOnConflict("kv", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            Library.setCustom(loadCustomWords())
            tick++
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun resetProgress() {
        db.execSQL("DELETE FROM progress")
        db.execSQL("DELETE FROM daily")
        progress.clear()
        tick++
    }

    companion object {
        const val GOAL_WORDS = "goal_words"
        const val GOAL_SENTENCES = "goal_sentences"
        const val GOAL_ANSWERS = "goal_answers"
        const val TTS_RATE = "tts_rate"
        const val TTS_AUTO = "tts_auto"
        const val TTS_ACCENT = "tts_accent"
    }
}
