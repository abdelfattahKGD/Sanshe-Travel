package com.microforge.wordforge.ui

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.Toast
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

    /** +1 = went deeper, -1 = went back, 0 = jumped from the drawer. Used to pick the slide direction. */
    var direction by mutableIntStateOf(0)
        private set
    private val stack = ArrayList<Screen>()

    val canGoBack: Boolean get() = depth > 0

    fun push(s: Screen) {
        stack.add(current)
        direction = 1
        current = s
        depth = stack.size
    }

    fun root(s: Screen) {
        stack.clear()
        direction = 0
        current = s
        depth = 0
    }

    fun back() {
        if (stack.isNotEmpty()) {
            direction = -1
            current = stack.removeAt(stack.lastIndex)
            depth = stack.size
        }
    }
}

/**
 * Offline text-to-speech through the phone's own engine.
 * It explains itself: [status] says exactly why there is no sound (no engine / no English voice data / ...),
 * and the helpers open the right system screens to fix it.
 */
class Speaker(ctx: Context) : TextToSpeech.OnInitListener {
    private val app = ctx.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var rate = 1.0f

    var ready by mutableStateOf(false)
        private set
    var status by mutableStateOf("جاري تجهيز الصوت…")
        private set

    init {
        try {
            tts = TextToSpeech(app, this)
        } catch (e: Exception) {
            status = "محرك النطق مش موجود على الموبايل ده"
        }
    }

    override fun onInit(code: Int) {
        val t = tts
        if (t == null || code != TextToSpeech.SUCCESS) {
            ready = false
            status = "مفيش محرك نطق (Text-to-Speech) شغّال على الموبايل"
            return
        }
        try {
            t.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
        } catch (e: Exception) {
        }
        var r = t.setLanguage(Locale.US)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) r = t.setLanguage(Locale.UK)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) r = t.setLanguage(Locale.ENGLISH)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            ready = false
            status = "اللغة الإنجليزية مش مثبّتة في محرك النطق — نزّل بيانات الصوت"
            return
        }
        t.setSpeechRate(rate)
        ready = true
        status = "الصوت شغّال ✅"
    }

    fun setRate(r: Float) {
        rate = r
        tts?.setSpeechRate(r)
    }

    /** [auto] = spoken automatically (stay silent when sound is unavailable instead of nagging with a toast). */
    fun say(text: String, auto: Boolean = false) {
        val t = tts
        if (t == null || !ready) {
            if (!auto) toast(status)
            return
        }
        val result = t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sanshe")
        if (result != TextToSpeech.SUCCESS && !auto) toast("تعذّر تشغيل الصوت — اتأكد إن صوت الوسائط مش صفر")
    }

    fun toast(msg: String) {
        main.post { Toast.makeText(app, msg, Toast.LENGTH_LONG).show() }
    }

    /** Opens Android's "download voice data" screen. */
    fun installVoiceData() = launch(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))

    fun openTtsSettings() {
        if (!launch(Intent("com.android.settings.TTS_SETTINGS"))) launch(Intent(Settings.ACTION_SETTINGS))
    }

    fun installGoogleTts() {
        if (!launch(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.tts")))) {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.tts")))
        }
    }

    private fun launch(intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
        true
    } catch (e: Exception) {
        false
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
