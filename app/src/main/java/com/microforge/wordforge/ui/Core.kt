package com.microforge.wordforge.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.microforge.wordforge.data.Kind
import com.microforge.wordforge.data.Repo
import java.util.Locale

/**
 * Every screen in the app is a [Screen]. To add a new feature:
 *   1) write a class/object implementing Screen
 *   2) add one DrawerEntry line in [Registry]
 * Nothing else has to change.
 */
interface Screen {
    /** Unique per screen instance (include arguments), used to reset state when switching screens. */
    val key: String
    val title: String

    @Composable
    fun Content(env: Env)
}

class Env(val repo: Repo, val nav: Nav, val speaker: Speaker)

class Nav(start: Screen) {
    var current by mutableStateOf(start)
        private set
    var depth by mutableIntStateOf(0)
        private set
    private val stack = ArrayList<Screen>()

    val canGoBack: Boolean get() = depth > 0

    fun push(s: Screen) {
        stack.add(current)
        current = s
        depth = stack.size
    }

    fun root(s: Screen) {
        stack.clear()
        current = s
        depth = 0
    }

    fun back() {
        if (stack.isNotEmpty()) {
            current = stack.removeAt(stack.lastIndex)
            depth = stack.size
        }
    }
}

/** Offline text-to-speech using the phone's built-in engine. */
class Speaker(ctx: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(ctx.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            ready = true
        }
    }

    fun say(text: String) {
        if (ready) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wordforge")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

class DrawerEntry(val emoji: String, val title: String, val make: () -> Screen)

object Registry {
    val drawer: List<DrawerEntry> = listOf(
        DrawerEntry("🏠", "الرئيسية") { HomeScreen },
        DrawerEntry("📚", "الكلمات") { BrowseScreen(Kind.WORD) },
        DrawerEntry("💬", "الجمل") { BrowseScreen(Kind.SENTENCE) },
        DrawerEntry("🧠", "مراجعة البطاقات") { ReviewScreen(null, null) },
        DrawerEntry("✏️", "التدريب") { PracticeScreen },
        DrawerEntry("📖", "الجرامر") {
            SoonScreen("soon-grammar", "الجرامر", "دروس الجرامر بشرح بسيط وأمثلة من جمل التطبيق، مع تمارين لكل درس.")
        },
        DrawerEntry("📝", "الامتحانات") {
            SoonScreen("soon-exams", "الامتحانات", "امتحانات كاملة بوقت ودرجة، وامتحان نهاية كل مستوى.")
        },
        DrawerEntry("📊", "الإحصائيات") { StatsScreen },
        DrawerEntry("🔎", "بحث") { SearchScreen },
        DrawerEntry("⚙️", "الإعدادات") { SettingsScreen },
    )
}
