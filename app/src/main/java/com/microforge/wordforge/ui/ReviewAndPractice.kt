package com.microforge.wordforge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microforge.wordforge.data.Item
import com.microforge.wordforge.data.Kind
import com.microforge.wordforge.data.Library

/**
 * Flashcards with spaced repetition. [kind]/[topicId] null = everything.
 * Wrong cards go back to the end of the queue until answered correctly.
 */
class ReviewScreen(private val kind: Kind?, private val topicId: String?) : Screen {
    override val key = "review-${kind?.name}-$topicId"
    override val title = "مراجعة البطاقات"

    @Composable
    override fun Content(env: Env) {
        val repo = env.repo
        val queue = remember { mutableStateListOf<Item>().apply { addAll(repo.buildSession(kind, topicId, 10, 30)) } }
        val total = remember { queue.size }
        var flipped by remember { mutableStateOf(false) }
        var answered by remember { mutableIntStateOf(0) }
        var rightCount by remember { mutableIntStateOf(0) }

        if (queue.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(if (total == 0) "🎉" else "🏁", fontSize = 56.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (total == 0) "مفيش حاجة مستحقة دلوقتي" else "خلّصت الجلسة!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (answered > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("إجابات صح: $rightCount من $answered")
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = { env.nav.back() }) { Text("رجوع") }
            }
            return
        }

        val item = queue.first()
        val progressDone = if (total == 0) 0f else (total - queue.size).coerceAtLeast(0).toFloat() / total

        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("باقي ${queue.size} بطاقة", color = MaterialTheme.colorScheme.onSurfaceVariant)
            ProgressBar(progressDone)

            Card(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable { flipped = !flipped },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    En(
                        item.en,
                        Modifier.fillMaxWidth(),
                        style = if (item.kind == Kind.WORD) MaterialTheme.typography.displaySmall
                        else MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        align = TextAlign.Center,
                    )
                    if (item.pos.isNotEmpty()) {
                        En(
                            item.pos,
                            Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            align = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "🔊",
                        Modifier
                            .clickable { env.speaker.say(item.en) }
                            .padding(10.dp),
                        fontSize = 28.sp,
                    )
                    if (flipped) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            item.ar,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        if (item.example.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            En(
                                item.example,
                                Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                align = TextAlign.Center,
                            )
                        }
                    } else {
                        Spacer(Modifier.height(12.dp))
                        Text("اضغط على الكارت لإظهار المعنى", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            if (!flipped) {
                Button(onClick = { flipped = true }, modifier = Modifier.fillMaxWidth()) { Text("إظهار المعنى") }
            } else {
                fun answer(correct: Boolean) {
                    repo.record(item, correct)
                    answered++
                    if (correct) rightCount++
                    queue.removeAt(0)
                    if (!correct) queue.add(item)
                    flipped = false
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { answer(false) }, modifier = Modifier.weight(1f)) {
                        Text("❌ لسه")
                    }
                    Button(
                        onClick = { answer(true) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Good),
                    ) { Text("✅ عرفتها", color = Color.White) }
                }
            }
        }
    }
}

private class Question(val item: Item, val toArabic: Boolean, val options: List<String>, val correctIndex: Int)

private fun makeQuestions(pool: List<Item>, count: Int): List<Question> {
    val picked = pool.shuffled().take(count)
    return picked.map { item ->
        val toArabic = (0..1).random() == 0
        fun text(i: Item) = if (toArabic) i.ar else i.en
        val correct = text(item)
        val wrong = pool.map { text(it) }.distinct().filter { it != correct }.shuffled().take(3)
        val options = (wrong + correct).shuffled()
        Question(item, toArabic, options, options.indexOf(correct))
    }
}

/** Quick multiple-choice rounds. Answers also feed the spaced-repetition progress. */
object PracticeScreen : Screen {
    override val key = "practice"
    override val title = "التدريب"

    @Composable
    override fun Content(env: Env) {
        var kind by remember { mutableStateOf(Kind.WORD) }
        var round by remember { mutableIntStateOf(0) }
        val pool = remember(kind) { Library.items.filter { it.kind == kind } }

        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (k in listOf(Kind.WORD, Kind.SENTENCE)) {
                    val selected = kind == k
                    val label = if (k == Kind.WORD) "📚 كلمات" else "💬 جمل"
                    if (selected) {
                        Button(onClick = {}, modifier = Modifier.weight(1f)) { Text(label) }
                    } else {
                        OutlinedButton(
                            onClick = { kind = k; round++ },
                            modifier = Modifier.weight(1f),
                        ) { Text(label) }
                    }
                }
            }
            if (pool.size < 4) {
                Text("مفيش محتوى كفاية للتدريب لسه.")
            } else {
                // key() restarts the round when the user presses "new round" or switches kind
                androidx.compose.runtime.key(kind, round) {
                    Round(env, pool, onNewRound = { round++ })
                }
            }
        }
    }

    @Composable
    private fun Round(env: Env, pool: List<Item>, onNewRound: () -> Unit) {
        val questions = remember { makeQuestions(pool, 10) }
        var index by remember { mutableIntStateOf(0) }
        var score by remember { mutableIntStateOf(0) }
        var selected by remember { mutableIntStateOf(-1) }

        if (index >= questions.size) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("🏁", fontSize = 56.sp)
                Text("نتيجتك: $score من ${questions.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(onClick = onNewRound) { Text("جولة جديدة") }
            }
            return
        }

        val q = questions[index]
        Text("سؤال ${index + 1} من ${questions.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        ProgressBar(index.toFloat() / questions.size)
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (q.toArabic) "إيه معنى:" else "إزاي نقولها بالإنجليزي:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (q.toArabic) {
                    En(q.item.en, Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                } else {
                    Text(q.item.ar, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            q.options.forEachIndexed { i, opt ->
                val bg = when {
                    selected == -1 -> MaterialTheme.colorScheme.surfaceVariant
                    i == q.correctIndex -> Good.copy(alpha = 0.35f)
                    i == selected -> Bad.copy(alpha = 0.35f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = selected == -1) {
                            selected = i
                            val ok = i == q.correctIndex
                            if (ok) score++
                            env.repo.record(q.item, ok)
                            if (!q.toArabic) env.speaker.say(q.item.en)
                        },
                    colors = CardDefaults.cardColors(containerColor = bg),
                ) {
                    Box(Modifier.padding(16.dp)) {
                        if (q.toArabic) {
                            Text(opt, style = MaterialTheme.typography.bodyLarge)
                        } else {
                            En(opt, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            if (selected != -1) {
                Button(
                    onClick = { index++; selected = -1 },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (index == questions.size - 1) "إنهاء" else "التالي") }
            }
        }
    }
}
