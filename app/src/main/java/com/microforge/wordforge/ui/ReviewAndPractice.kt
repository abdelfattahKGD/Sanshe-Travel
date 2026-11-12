package com.microforge.wordforge.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microforge.wordforge.data.Item
import com.microforge.wordforge.data.Kind
import com.microforge.wordforge.data.Library
import com.microforge.wordforge.data.Repo
import com.microforge.wordforge.data.Srs

/**
 * Flashcards with spaced repetition. [kind]/[topicId] null = everything.
 * The buttons tell you exactly when the card comes back. Wrong cards return at the end of the queue.
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
                PopIn { Text(if (total == 0) "🎉" else "🏁", fontSize = 72.sp) }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (total == 0) "مفيش حاجة مستحقة دلوقتي" else "خلّصت الجلسة! 👏",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (answered > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("إجابات صح: $rightCount من $answered")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "اللي غلطت فيه هيرجعلك بعد 10 دقايق.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "ارجع بعدين، أو ذاكر موضوع جديد من قائمة الكلمات.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = { env.nav.back() }, shape = RoundedCornerShape(14.dp)) { Text("رجوع") }
            }
            return
        }

        val item = queue.first()
        val remaining = queue.distinctBy { it.id }.size
        val autoSpeak = repo.getInt(Repo.TTS_AUTO, 1) == 1
        LaunchedEffect(item.id) {
            if (autoSpeak) env.speaker.say(item.en, auto = true)
        }

        val now = System.currentTimeMillis()
        val old = repo.progress[item.id]
        val early = Srs.isEarly(old, now)
        val okText = if (early) "مفيش تقدّم (لسه بدري)" else "هتظهر ${Srs.previewCorrect(old, now)}"
        val badText = "تيجي تاني ${Srs.previewWrong(now)}"

        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("باقي $remaining", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                StatusPill(repo.statusOf(item.id))
            }
            ProgressBar(if (total == 0) 0f else (total - remaining).toFloat() / total)

            key(item.id) {
                val enter = remember { Animatable(0f) }
                LaunchedEffect(Unit) { enter.animateTo(1f, tween(380)) }
                val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(500), label = "flip")
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .graphicsLayer {
                            alpha = enter.value
                            translationX = -(1f - enter.value) * 140f * density
                            rotationY = rotation
                            cameraDistance = 14f * density
                        }
                        .clickable { flipped = !flipped }
                ) {
                    if (rotation <= 90f) {
                        CardShell(front = true) { FrontFace(item, env) }
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { rotationY = 180f }
                        ) {
                            CardShell(front = false) { BackFace(item) }
                        }
                    }
                }
            }

            if (!flipped) {
                Button(
                    onClick = { flipped = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("👀 إظهار المعنى", fontWeight = FontWeight.Bold) }
            } else {
                fun answer(correct: Boolean) {
                    repo.record(item, correct)
                    answered++
                    if (correct) rightCount++
                    queue.removeAt(0)
                    if (!correct) queue.add(item)
                    flipped = false
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { answer(false) },
                        modifier = Modifier
                            .weight(1f)
                            .height(66.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("❌ لسه", fontWeight = FontWeight.Bold)
                            Text(badText, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Button(
                        onClick = { answer(true) },
                        modifier = Modifier
                            .weight(1f)
                            .height(66.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Good),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("✅ عرفتها", fontWeight = FontWeight.Bold, color = Color.White)
                            Text(okText, style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardShell(front: Boolean, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = if (front) scheme.primaryContainer else scheme.surface),
        border = if (front) null else BorderStroke(2.dp, scheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        content()
    }
}

@Composable
private fun FrontFace(item: Item, env: Env) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        En(
            item.en,
            Modifier.fillMaxWidth(),
            style = if (item.kind == Kind.WORD) MaterialTheme.typography.displaySmall
            else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
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
        Spacer(Modifier.height(16.dp))
        SoundButton(onClick = { env.speaker.say(item.en) }, diameter = 60.dp)
        Spacer(Modifier.height(16.dp))
        Text("حاول تفتكر المعنى، وبعدين اقلب الكارت", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BackFace(item: Item) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            item.ar,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        En(item.en, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant, align = TextAlign.Center)
        if (item.example.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            En(
                item.example,
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge,
                align = TextAlign.Center,
            )
        }
    }
}

// ------------------------------------------------------------------ practice

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

private enum class OptState { IDLE, CORRECT, WRONG }

@Composable
private fun OptionTile(letter: String, text: String, english: Boolean, state: OptState, enabled: Boolean, onClick: () -> Unit) {
    val surface = MaterialTheme.colorScheme.surface
    val bg by animateColorAsState(
        when (state) {
            OptState.IDLE -> surface
            OptState.CORRECT -> Good.copy(alpha = 0.28f)
            OptState.WRONG -> Bad.copy(alpha = 0.28f)
        },
        tween(300),
        label = "optbg",
    )
    val scale by animateFloatAsState(if (state == OptState.CORRECT) 1.03f else 1f, spring(dampingRatio = 0.5f), label = "optscale")
    Card(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Text(letter, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            if (english) {
                En(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            }
            if (state == OptState.CORRECT) Text("✅")
            if (state == OptState.WRONG) Text("❌")
        }
    }
}

/** Quick multiple-choice rounds. Answers also feed the spaced-repetition progress (never before they're due). */
object PracticeScreen : Screen {
    override val key = "practice"
    override val title = "التدريب"

    @Composable
    override fun Content(env: Env) {
        var kind by remember { mutableStateOf(Kind.WORD) }
        var round by remember { mutableIntStateOf(0) }
        val pool = remember(kind) { Library.items.filter { it.kind == kind } }
        val primary = MaterialTheme.colorScheme.primary

        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (k in listOf(Kind.WORD, Kind.SENTENCE)) {
                    Pill(
                        if (k == Kind.WORD) "📚 كلمات" else "💬 جمل",
                        primary,
                        filled = kind == k,
                        onClick = {
                            if (kind != k) {
                                kind = k
                                round++
                            }
                        },
                    )
                }
            }
            if (pool.size < 4) {
                Text("مفيش محتوى كفاية للتدريب لسه.")
            } else {
                // key() restarts the round when the user presses "new round" or switches kind
                key(kind, round) {
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
            val percent = if (questions.isEmpty()) 0 else score * 100 / questions.size
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ProgressRing(
                    fraction = percent / 100f,
                    diameter = 150.dp,
                    stroke = 14.dp,
                    color = if (percent >= 70) Good else Amber,
                ) {
                    PopIn { Text(if (percent >= 70) "🏆" else "💪", fontSize = 48.sp) }
                }
                Text(
                    "نتيجتك: $score من ${questions.size}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    if (percent >= 70) "ممتاز! كمّل كده 🐢" else "كويس، وكل جولة بتفرّق. جرّب تاني!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onNewRound, shape = RoundedCornerShape(14.dp)) { Text("🔁 جولة جديدة") }
            }
            return
        }

        val q = questions[index]
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "سؤال ${index + 1} من ${questions.size}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Pill("✅ $score", Good, filled = false)
        }
        ProgressBar(index.toFloat() / questions.size)
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (q.toArabic) "إيه معنى:" else "إزاي نقولها بالإنجليزي:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (q.toArabic) {
                    En(q.item.en, Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                } else {
                    Text(q.item.ar, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val letters = listOf("A", "B", "C", "D")
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
                    english = !q.toArabic,
                    state = state,
                    enabled = selected == -1,
                    onClick = {
                        selected = i
                        val ok = i == q.correctIndex
                        if (ok) score++
                        env.repo.record(q.item, ok)
                        if (!q.toArabic) env.speaker.say(q.item.en, auto = true)
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
                Button(
                    onClick = {
                        index++
                        selected = -1
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text(if (index == questions.size - 1) "إنهاء" else "التالي", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
