package com.microforge.wordforge.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microforge.wordforge.data.GrammarLesson
import com.microforge.wordforge.data.Item
import com.microforge.wordforge.data.Kind
import com.microforge.wordforge.data.Library
import kotlinx.coroutines.delay

private class ExamDef(
    val id: String,
    val title: String,
    val emoji: String,
    val color: Color,
    val pool: List<Item>,
    val grammar: List<GrammarLesson>,
    val questions: Int,
    val minutes: Int,
) {
    val subtitle: String get() = "$questions سؤال • $minutes دقيقة"
}

private fun examDefs(): List<ExamDef> {
    val all = Library.items
    val lessons = Library.lessons
    val list = ArrayList<ExamDef>()
    list.add(ExamDef("lvA1", "امتحان المستوى A1", "🌱", Leaf, all.filter { it.level == "A1" }, lessons.filter { it.level == "A1" }, 20, 12))
    list.add(ExamDef("lvA2", "امتحان المستوى A2", "🌿", Sky, all.filter { it.level == "A2" }, lessons.filter { it.level == "A2" }, 20, 12))
    list.add(
        ExamDef(
            "lvB1", "امتحان المستوى B1", "🌳", Coral,
            all.filter { it.level == "B1" || it.level == "B2" }, lessons.filter { it.level == "B1" }, 20, 12,
        )
    )
    list.add(ExamDef("all", "الامتحان الشامل", "🏆", Amber, all, lessons, 30, 20))
    for (t in Library.topics.filter { it.available }) {
        list.add(ExamDef("t${t.num}", "امتحان: ${t.ar}", t.emoji, topicColor(t.num), all.filter { it.topicId == t.id }, emptyList(), 15, 8))
    }
    return list
}

/** label = "كلمات" / "جمل" / "جرامر" (used for the result breakdown). */
private class ExQ(
    val prompt: String,
    val promptEn: Boolean,
    val options: List<String>,
    val optionsEn: Boolean,
    val correctIndex: Int,
    val label: String,
)

private fun makeExam(def: ExamDef): List<ExQ> {
    val out = ArrayList<ExQ>()
    val grammar = def.grammar.flatMap { it.quiz }.shuffled()
    val gCount = if (grammar.isEmpty()) 0 else minOf(grammar.size, def.questions / 3)
    val vCount = def.questions - gCount
    val pool = def.pool
    for (item in pool.shuffled().take(vCount + 6)) {
        if (out.size >= vCount) break
        val toArabic = (0..1).random() == 0
        fun text(i: Item) = if (toArabic) i.ar else i.en
        val correct = text(item)
        val wrong = pool.filter { it.kind == item.kind && it.id != item.id && !item.conflictsWith(it) }.map { text(it) }.distinct().filter { it != correct }.shuffled().take(3)
        if (wrong.size < 2) continue
        val options = (wrong + correct).shuffled()
        out.add(
            ExQ(
                prompt = if (toArabic) item.en else item.ar,
                promptEn = toArabic,
                options = options,
                optionsEn = !toArabic,
                correctIndex = options.indexOf(correct),
                label = if (item.kind == Kind.WORD) "كلمات" else "جمل",
            )
        )
    }
    for (q in grammar.take(gCount)) {
        val options = (q.wrong + q.answer).shuffled()
        out.add(
            ExQ(
                prompt = q.q,
                promptEn = true,
                options = options,
                optionsEn = true,
                correctIndex = options.indexOf(q.answer),
                label = "جرامر",
            )
        )
    }
    return out.shuffled()
}

/** Text that is left-to-right when it's English and normal (RTL) when it's Arabic. */
@Composable
private fun Mixed(
    text: String,
    english: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
) {
    if (english) {
        En(text, modifier.fillMaxWidth(), style = style, color = color, fontWeight = fontWeight)
    } else {
        Text(text, modifier, style = style, color = color, fontWeight = fontWeight)
    }
}

object ExamListScreen : Screen {
    override val key = "exams"
    override val title = "الامتحانات"

    @Composable
    override fun Content(env: Env) {
        @Suppress("UNUSED_VARIABLE") val tick = env.repo.tick
        val defs = remember { examDefs() }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "intro") {
                Text(
                    "امتحان بوقت محدد، من غير إجابات فورية. في الآخر هتشوف درجتك وأخطاءك. الامتحانات مبتغيّرش حالة الكلمات (محفوظة / بتتعلّم).",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(key = "h-main") { Pill("امتحانات المستوى", MaterialTheme.colorScheme.primary, Modifier.padding(top = 6.dp)) }
            defs.forEachIndexed { index, d ->
                if (d.id == "t1") {
                    item(key = "h-topics") { Pill("امتحانات المواضيع", MaterialTheme.colorScheme.primary, Modifier.padding(top = 8.dp)) }
                }
                item(key = d.id) { ExamCard(d, env, index) }
            }
        }
    }
}

@Composable
private fun ExamCard(d: ExamDef, env: Env, index: Int) {
    val best = env.repo.getInt("exam_${d.id}", -1)
    Appear(index) {
        Card(
            Modifier
                .fillMaxWidth()
                .clickable { env.nav.push(ExamRunScreen(d.id)) },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(d.color.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) { Text(d.emoji, fontSize = 24.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(d.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(d.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (best >= 0) {
                    Pill("الأفضل $best%", if (best >= 75) Good else Amber, filled = false)
                } else {
                    Pill("ابدأ", d.color, filled = false)
                }
            }
        }
    }
}

class ExamRunScreen(private val examId: String) : Screen {
    override val key = "exam-$examId"
    override val title = "الامتحان"

    @Composable
    override fun Content(env: Env) {
        val def = examDefs().firstOrNull { it.id == examId } ?: return
        var attempt by remember { mutableIntStateOf(0) }
        key(attempt) { Body(env, def, onRestart = { attempt++ }) }
    }

    @Composable
    private fun Body(env: Env, def: ExamDef, onRestart: () -> Unit) {
        val qs = remember { makeExam(def) }
        if (qs.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("مفيش أسئلة كفاية للامتحان ده.")
            }
            return
        }
        val answers = remember { mutableStateListOf<Int>().apply { repeat(qs.size) { add(-1) } } }
        var index by remember { mutableIntStateOf(0) }
        var secondsLeft by remember { mutableIntStateOf(def.minutes * 60) }
        var finished by remember { mutableStateOf(false) }
        var confirm by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            while (secondsLeft > 0 && !finished) {
                delay(1000)
                secondsLeft--
            }
            finished = true
        }

        if (finished) {
            Result(env, def, qs, answers, onRestart)
            return
        }

        val q = qs[index]
        val letters = listOf("A", "B", "C", "D", "E")
        val unanswered = answers.count { it == -1 }

        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "سؤال ${index + 1} من ${qs.size}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Pill("⏱ %d:%02d".format(secondsLeft / 60, secondsLeft % 60), if (secondsLeft < 60) Bad else def.color, filled = false)
            }
            ProgressBar((index + 1).toFloat() / qs.size, color = def.color)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(qs.size) { i ->
                    val scheme = MaterialTheme.colorScheme
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    i == index -> scheme.primary
                                    answers[i] != -1 -> scheme.primaryContainer
                                    else -> scheme.surfaceVariant
                                }
                            )
                            .clickable { index = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${i + 1}",
                            fontSize = 12.sp,
                            color = if (i == index) scheme.onPrimary else scheme.onSurface,
                        )
                    }
                }
            }
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(q.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Mixed(q.prompt, q.promptEn, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                q.options.forEachIndexed { i, opt ->
                    ExamOption(
                        letter = letters.getOrElse(i) { "?" },
                        text = opt,
                        english = q.optionsEn,
                        selected = answers[index] == i,
                        onClick = { answers[index] = i },
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { if (index > 0) index-- },
                    enabled = index > 0,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("السابق") }
                Button(
                    onClick = {
                        if (index < qs.size - 1) {
                            index++
                        } else if (unanswered > 0) {
                            confirm = true
                        } else {
                            finished = true
                        }
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text(if (index == qs.size - 1) "إنهاء الامتحان" else "التالي", fontWeight = FontWeight.Bold) }
            }
        }

        if (confirm) {
            AlertDialog(
                onDismissRequest = { confirm = false },
                title = { Text("إنهاء الامتحان؟") },
                text = { Text("فيه $unanswered سؤال من غير إجابة. تحب تنهي الامتحان دلوقتي؟") },
                confirmButton = {
                    TextButton(onClick = {
                        confirm = false
                        finished = true
                    }) { Text("إنهاء") }
                },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text("كمّل الامتحان") } },
            )
        }
    }

    @Composable
    private fun Result(env: Env, def: ExamDef, qs: List<ExQ>, answers: List<Int>, onRestart: () -> Unit) {
        val correct = qs.indices.count { answers[it] == qs[it].correctIndex }
        val percent = correct * 100 / qs.size
        val prevBest = remember { env.repo.getInt("exam_${def.id}", -1) }
        LaunchedEffect(Unit) {
            if (percent > env.repo.getInt("exam_${def.id}", -1)) env.repo.setInt("exam_${def.id}", percent)
        }
        val grade = when {
            percent >= 90 -> "ممتاز 🏆"
            percent >= 75 -> "جيد جدًا 👏"
            percent >= 60 -> "جيد 🙂"
            else -> "محتاج مراجعة 💪"
        }
        val labels = qs.map { it.label }.distinct()

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ProgressRing(
                fraction = percent / 100f,
                diameter = 160.dp,
                stroke = 14.dp,
                color = if (percent >= 75) Good else Amber,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$percent%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    Text("$correct / ${qs.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(grade, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            if (prevBest >= 0) {
                Text(
                    if (percent > prevBest) "🎉 أحسن درجة ليك لحد دلوقتي (قبلها $prevBest%)" else "أحسن درجة ليك: $prevBest%",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SectionCard("📊 التفصيل") {
                for (label in labels) {
                    val idx = qs.indices.filter { qs[it].label == label }
                    val ok = idx.count { answers[it] == qs[it].correctIndex }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f))
                        Text("$ok / ${idx.size}", fontWeight = FontWeight.Bold)
                    }
                    ProgressBar(ok.toFloat() / idx.size)
                }
            }
            val wrong = qs.indices.filter { answers[it] != qs[it].correctIndex }
            if (wrong.isNotEmpty()) {
                SectionCard("❌ الأسئلة اللي غلطت فيها (${wrong.size})") {
                    for (i in wrong) {
                        val q = qs[i]
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Mixed(q.prompt, q.promptEn, fontWeight = FontWeight.Bold)
                            val mine = answers[i]
                            if (mine == -1) {
                                Text("لم تجب", color = Bad, style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Mixed("✗  ${q.options[mine]}", q.optionsEn, color = Bad, style = MaterialTheme.typography.bodyMedium)
                            }
                            Mixed("✓  ${q.options[q.correctIndex]}", q.optionsEn, color = Good, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Text("كل الإجابات صح! 🎯", color = Good, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onRestart, shape = RoundedCornerShape(14.dp)) { Text("🔁 امتحان جديد") }
                OutlinedButton(onClick = { env.nav.back() }, shape = RoundedCornerShape(14.dp)) { Text("رجوع") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ExamOption(letter: String, text: String, english: Boolean, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) scheme.primaryContainer else scheme.surface, tween(200), label = "exopt")
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = if (selected) BorderStroke(2.dp, scheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (selected) scheme.primary else scheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(letter, fontWeight = FontWeight.Bold, color = if (selected) scheme.onPrimary else scheme.onSurface)
            }
            Spacer(Modifier.width(12.dp))
            if (english) {
                En(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
