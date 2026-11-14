package com.microforge.wordforge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microforge.wordforge.data.GrammarLesson
import com.microforge.wordforge.data.GrammarQ
import com.microforge.wordforge.data.Kind
import com.microforge.wordforge.data.Library

fun levelColor(level: String): Color = when (level) {
    "A1" -> Leaf
    "A2" -> Sky
    "B1" -> Coral
    else -> Color(0xFF9B7ED8)
}

/** All grammar lessons grouped by level, with the best quiz score on each card. */
object GrammarListScreen : Screen {
    override val key = "grammar"
    override val title = "الجرامر"

    @Composable
    override fun Content(env: Env) {
        @Suppress("UNUSED_VARIABLE") val tick = env.repo.tick
        val lessons = Library.lessons
        if (lessons.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("مفيش دروس جرامر متاحة.")
            }
            return
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "intro") {
                Text(
                    "اتعلّم القاعدة، شوف أمثلتها وأخطاءها الشائعة، وبعدين اتدرّب عليها.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            var lastLevel = ""
            lessons.forEachIndexed { index, l ->
                if (l.level != lastLevel) {
                    lastLevel = l.level
                    item(key = "lv-${l.level}") {
                        Pill("المستوى ${l.level}", levelColor(l.level), Modifier.padding(top = 8.dp))
                    }
                }
                item(key = l.id) { LessonCard(l, env, index) }
            }
        }
    }
}

@Composable
private fun LessonCard(l: GrammarLesson, env: Env, index: Int) {
    val best = env.repo.getInt("gq_${l.id}", -1)
    val color = levelColor(l.level)
    Appear(index) {
        Card(
            Modifier
                .fillMaxWidth()
                .clickable { env.nav.push(GrammarLessonScreen(l.id)) },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Text("${l.num}", fontWeight = FontWeight.ExtraBold, color = color) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    En(l.en, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(l.ar, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (best >= 0) {
                    Pill(if (best >= 80) "✅ $best%" else "$best%", if (best >= 80) Good else Amber, filled = false)
                } else {
                    Pill("جديد", color, filled = false)
                }
            }
        }
    }
}

/** One lesson: explanation, formulas, examples with sound, common mistakes, and sentences from the app that use the rule. */
class GrammarLessonScreen(private val lessonId: String) : Screen {
    override val key = "glesson-$lessonId"
    override val title: String get() = Library.lesson(lessonId)?.let { "درس ${it.num}" } ?: "الجرامر"

    @Composable
    override fun Content(env: Env) {
        val l = Library.lesson(lessonId) ?: return
        val color = levelColor(l.level)
        val linked = remember(lessonId) {
            val rx = try {
                Regex(l.pattern, RegexOption.IGNORE_CASE)
            } catch (e: Exception) {
                null
            }
            if (rx == null || l.pattern.isEmpty()) emptyList()
            else Library.items.filter { it.kind == Kind.SENTENCE && rx.containsMatchIn(it.en) }.take(5)
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Appear(0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.7f))))
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("المستوى ${l.level}", Color.White.copy(alpha = 0.25f))
                        En(
                            l.en,
                            Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                        )
                        Text(l.ar, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            Appear(1) {
                SectionCard("📘 الشرح") {
                    for (line in l.explain) {
                        Row {
                            Text("•  ")
                            Text(line, Modifier.weight(1f))
                        }
                    }
                }
            }

            Appear(2) {
                SectionCard("🧩 الصيغة") {
                    for ((pattern, meaning) in l.formulas) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            En(pattern, Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold, color = color)
                            Text(meaning, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Appear(3) {
                SectionCard("💡 أمثلة") {
                    for ((en, ar) in l.examples) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                En(en, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(ar, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                            SoundButton(onClick = { env.speaker.say(en) }, diameter = 38.dp)
                        }
                    }
                }
            }

            if (l.mistakes.isNotEmpty()) {
                Appear(4) {
                    SectionCard("⚠️ أخطاء شائعة") {
                        for (m in l.mistakes) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                En("✗  ${m.wrong}", Modifier.fillMaxWidth(), color = Bad)
                                En("✓  ${m.right}", Modifier.fillMaxWidth(), color = Good, fontWeight = FontWeight.Bold)
                                Text(m.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            if (linked.isNotEmpty()) {
                Appear(5) {
                    SectionCard("🔗 جمل من التطبيق على نفس القاعدة") {
                        for (s in linked) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    En(s.en, Modifier.fillMaxWidth())
                                    Text(s.ar, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                SoundButton(onClick = { env.speaker.say(s.en) }, diameter = 34.dp)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { env.nav.push(GrammarQuizScreen(l.id)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("✏️ ابدأ التمرين (${l.quiz.size} أسئلة)", fontWeight = FontWeight.Bold) }
        }
    }
}

private class GQ(val q: GrammarQ, val options: List<String>, val correctIndex: Int)

/** Fill-in-the-blank quiz for one lesson. The best score is saved and shown on the lesson card. */
class GrammarQuizScreen(private val lessonId: String) : Screen {
    override val key = "gquiz-$lessonId"
    override val title = "تمرين الجرامر"

    @Composable
    override fun Content(env: Env) {
        val lesson = Library.lesson(lessonId) ?: return
        var attempt by remember { mutableIntStateOf(0) }
        key(attempt) { Body(env, lesson, onRestart = { attempt++ }) }
    }

    @Composable
    private fun Body(env: Env, lesson: GrammarLesson, onRestart: () -> Unit) {
        val qs = remember {
            lesson.quiz.shuffled().map { q ->
                val opts = (q.wrong + q.answer).shuffled()
                GQ(q, opts, opts.indexOf(q.answer))
            }
        }
        var index by remember { mutableIntStateOf(0) }
        var score by remember { mutableIntStateOf(0) }
        var selected by remember { mutableIntStateOf(-1) }

        if (index >= qs.size) {
            val percent = if (qs.isEmpty()) 0 else score * 100 / qs.size
            LaunchedEffect(Unit) {
                val k = "gq_${lesson.id}"
                if (percent > env.repo.getInt(k, -1)) env.repo.setInt(k, percent)
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            ) {
                ProgressRing(
                    fraction = percent / 100f,
                    diameter = 150.dp,
                    stroke = 14.dp,
                    color = if (percent >= 80) Good else Amber,
                ) {
                    PopIn { Text(if (percent >= 80) "🏆" else "💪", fontSize = 48.sp) }
                }
                Text("نتيجتك: $score من ${qs.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (percent >= 80) "ممتاز! القاعدة دي بقت معاك ✅" else "كويس، راجع الشرح وجرّب تاني.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onRestart, shape = RoundedCornerShape(14.dp)) { Text("🔁 إعادة") }
                    OutlinedButton(onClick = { env.nav.back() }, shape = RoundedCornerShape(14.dp)) { Text("رجوع للدرس") }
                }
            }
            return
        }

        val q = qs[index]
        val shown = if (selected == -1) q.q.q else q.q.q.replace("___", q.q.answer)
        val letters = listOf("A", "B", "C", "D", "E")

        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "سؤال ${index + 1} من ${qs.size}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Pill("✅ $score", Good, filled = false)
            }
            ProgressBar(index.toFloat() / qs.size)
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("كمّل الجملة:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    En(
                        shown,
                        Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                q.options.forEachIndexed { i, opt ->
                    val state = when {
                        selected == -1 -> OptState.IDLE
                        i == q.correctIndex -> OptState.CORRECT
                        i == selected -> OptState.WRONG
                        else -> OptState.IDLE
                    }
                    OptionTile(
                        letter = letters.getOrElse(i) { "?" },
                        text = opt,
                        english = true,
                        state = state,
                        enabled = selected == -1,
                        onClick = {
                            selected = i
                            if (i == q.correctIndex) score++
                            env.speaker.say(q.q.q.replace("___", q.q.answer), auto = true)
                        },
                    )
                }
                if (selected != -1) {
                    val ok = selected == q.correctIndex
                    Text(
                        if (ok) "صح! 👏" else "غلط — الإجابة الصح مظلّلة بالأخضر",
                        color = if (ok) Good else Bad,
                        fontWeight = FontWeight.Bold,
                    )
                    if (!ok) {
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        ) {
                            Text("💡 ${lesson.explain.first()}", Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Button(
                        onClick = {
                            index++
                            selected = -1
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text(if (index == qs.size - 1) "إنهاء" else "التالي", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
