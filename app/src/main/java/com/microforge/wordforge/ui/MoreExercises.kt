package com.microforge.wordforge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microforge.wordforge.data.Item
import com.microforge.wordforge.data.Kind
import com.microforge.wordforge.data.Library
import com.microforge.wordforge.data.Status

// ------------------------------------------------------------------ helpers

/** Lower-case words without punctuation: "I'm here!" -> ["i'm", "here"]. */
private fun norm(s: String): List<String> =
    s.lowercase()
        .replace('’', '\'')
        .replace(Regex("[^a-z0-9' ]"), " ")
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }

private class Diff(val matchedTarget: Set<Int>, val matchedTyped: Set<Int>)

/** Longest-common-subsequence match between the right words and the typed words. */
private fun diff(target: List<String>, typed: List<String>): Diff {
    val n = target.size
    val m = typed.size
    val dp = Array(n + 1) { IntArray(m + 1) }
    for (i in n - 1 downTo 0) {
        for (j in m - 1 downTo 0) {
            dp[i][j] = if (target[i] == typed[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
    }
    val mt = HashSet<Int>()
    val my = HashSet<Int>()
    var i = 0
    var j = 0
    while (i < n && j < m) {
        if (target[i] == typed[j]) {
            mt.add(i)
            my.add(j)
            i++
            j++
        } else if (dp[i + 1][j] >= dp[i][j + 1]) {
            i++
        } else {
            j++
        }
    }
    return Diff(mt, my)
}

/** Items the user already met come first, so these exercises reinforce what was learned. */
private fun pickItems(env: Env, kind: Kind, n: Int, maxWords: Int = 99): List<Item> {
    val pool = Library.items.filter { it.kind == kind && it.en.split(" ").size <= maxWords }
    val seen = pool.filter { env.repo.statusOf(it.id) != Status.NEW }.shuffled()
    val fresh = pool.filter { env.repo.statusOf(it.id) == Status.NEW }.shuffled()
    return (seen + fresh).take(n)
}

@Composable
private fun ResultView(score: Int, total: Int, onNew: () -> Unit, env: Env) {
    val percent = if (total == 0) 0 else score * 100 / total
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
        Text("نتيجتك: $score من $total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(
            "الإجابات اتسجّلت في حفظك (من غير تقدّم لو لسه معاد مراجعتها مجاش).",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onNew, shape = RoundedCornerShape(14.dp)) { Text("🔁 جولة جديدة") }
            OutlinedButton(onClick = { env.nav.back() }, shape = RoundedCornerShape(14.dp)) { Text("رجوع") }
        }
    }
}

// ------------------------------------------------------------------ dictation

/** Listen and type. Right words are matched in order, so one typo doesn't ruin the whole sentence. */
object DictationScreen : Screen {
    override val key = "dictation"
    override val title = "إملاء"

    @Composable
    override fun Content(env: Env) {
        var kind by remember { mutableStateOf(Kind.SENTENCE) }
        var round by remember { mutableIntStateOf(0) }
        val primary = MaterialTheme.colorScheme.primary
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (k in listOf(Kind.SENTENCE, Kind.WORD)) {
                    Pill(
                        if (k == Kind.SENTENCE) "💬 جمل" else "📚 كلمات",
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
            key(kind, round) { DictationRound(env, kind, onNew = { round++ }) }
        }
    }
}

@Composable
private fun DictationRound(env: Env, kind: Kind, onNew: () -> Unit) {
    val items = remember { pickItems(env, kind, 8) }
    var index by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var checked by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf(false) }
    var correct by remember { mutableStateOf(false) }

    if (items.isEmpty()) {
        Text("مفيش محتوى للإملاء لسه.")
        return
    }
    if (index >= items.size) {
        ResultView(score, items.size, onNew, env)
        return
    }

    val item = items[index]
    LaunchedEffect(index) { env.speaker.say(item.en, auto = true) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "تمرين ${index + 1} من ${items.size}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Pill("✅ $score", Good, filled = false)
    }
    ProgressBar(index.toFloat() / items.size)

    Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("اسمع واكتب اللي سمعته بالإنجليزي", color = MaterialTheme.colorScheme.onSurfaceVariant)
                SoundButton(onClick = { env.speaker.say(item.en) }, diameter = 72.dp)
                OutlinedButton(onClick = { env.speaker.say(item.en, slow = true) }, shape = RoundedCornerShape(12.dp)) {
                    Text("🐢 أبطأ")
                }
                if (hint && !checked) {
                    En(
                        "يبدأ بـ: ${item.en.split(" ").first()} …  (${norm(item.en).size} كلمة)",
                        Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        align = TextAlign.Center,
                    )
                }
            }
        }

        OutlinedTextField(
            value = input,
            onValueChange = { if (!checked) input = it },
            label = { Text("اكتب هنا") },
            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr, textAlign = TextAlign.Left),
            enabled = !checked,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        if (!checked) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { hint = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("💡 تلميح") }
                Button(
                    onClick = {
                        val target = norm(item.en)
                        val d = diff(target, norm(input))
                        val ratio = if (target.isEmpty()) 0f else d.matchedTarget.size / target.size.toFloat()
                        correct = ratio >= 0.9f
                        if (correct) score++
                        env.repo.record(item, correct)
                        checked = true
                    },
                    enabled = input.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("✅ تحقّق", fontWeight = FontWeight.Bold) }
            }
        } else {
            val target = norm(item.en)
            val typed = norm(input)
            val d = diff(target, typed)
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (correct) "صح! 👏" else "فيه فروق، شوفها:",
                        color = if (correct) Good else Bad,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text("الصح:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    En(item.en, Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    Text(item.ar, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("اللي كتبته:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    En(
                        buildAnnotatedString {
                            typed.forEachIndexed { i, w ->
                                withStyle(SpanStyle(color = if (i in d.matchedTyped) Good else Bad, fontWeight = FontWeight.Bold)) {
                                    append(w)
                                }
                                append(" ")
                            }
                        },
                        Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    val missing = target.filterIndexed { i, _ -> i !in d.matchedTarget }
                    if (missing.isNotEmpty()) {
                        En("ناقص أو غلط: ${missing.joinToString(", ")}", Modifier.fillMaxWidth(), color = Bad)
                    }
                }
            }
            Button(
                onClick = {
                    index++
                    input = ""
                    checked = false
                    hint = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text(if (index == items.size - 1) "إنهاء" else "التالي", fontWeight = FontWeight.Bold) }
        }
    }
}

// ------------------------------------------------------------------ sentence builder

private class Tok(val id: Int, val text: String)

/** Put the shuffled words in order: trains word order, the "chunks" way. */
object SentenceBuilderScreen : Screen {
    override val key = "builder"
    override val title = "ترتيب الجمل"

    @Composable
    override fun Content(env: Env) {
        var round by remember { mutableIntStateOf(0) }
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            key(round) { BuilderRound(env, onNew = { round++ }) }
        }
    }
}

@Composable
private fun Chip(text: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tint)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        En(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuilderRound(env: Env, onNew: () -> Unit) {
    val items = remember { pickItems(env, Kind.SENTENCE, 8, 10) }
    var index by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var checked by remember { mutableStateOf(false) }
    var correct by remember { mutableStateOf(false) }

    if (items.isEmpty()) {
        Text("مفيش جمل كفاية لسه.")
        return
    }
    if (index >= items.size) {
        ResultView(score, items.size, onNew, env)
        return
    }

    val item = items[index]
    val bank = remember(index) {
        mutableStateListOf<Tok>().apply {
            addAll(item.en.split(" ").filter { it.isNotEmpty() }.mapIndexed { i, t -> Tok(i, t) }.shuffled())
        }
    }
    val chosen = remember(index) { mutableStateListOf<Tok>() }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "جملة ${index + 1} من ${items.size}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Pill("✅ $score", Good, filled = false)
    }
    ProgressBar(index.toFloat() / items.size)

    Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("رتّب الكلمات عشان تطلع الجملة دي بالإنجليزي:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(item.ar, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            }
        }

        // The word chips flow left-to-right even though the app is RTL, so the sentence reads naturally.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 84.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (tok in chosen.toList()) {
                        Chip(
                            tok.text,
                            if (!checked) MaterialTheme.colorScheme.primaryContainer
                            else if (correct) Good.copy(alpha = 0.3f) else Bad.copy(alpha = 0.3f),
                        ) {
                            if (!checked) {
                                chosen.remove(tok)
                                bank.add(tok)
                            }
                        }
                    }
                }
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (tok in bank.toList()) {
                    Chip(tok.text, MaterialTheme.colorScheme.surface) {
                        if (!checked) {
                            bank.remove(tok)
                            chosen.add(tok)
                        }
                    }
                }
            }
        }

        if (!checked) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        bank.addAll(chosen)
                        chosen.clear()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("🔄 من الأول") }
                Button(
                    onClick = {
                        correct = norm(chosen.joinToString(" ") { it.text }) == norm(item.en)
                        if (correct) score++
                        env.repo.record(item, correct)
                        checked = true
                        if (correct) env.speaker.say(item.en, auto = true)
                    },
                    enabled = bank.isEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("✅ تحقّق", fontWeight = FontWeight.Bold) }
            }
        } else {
            Text(
                if (correct) "صح! 👏" else "مش مظبوطة — دي الجملة الصح:",
                color = if (correct) Good else Bad,
                fontWeight = FontWeight.ExtraBold,
            )
            if (!correct) En(item.en, Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                SoundButton(onClick = { env.speaker.say(item.en) })
                Spacer(Modifier.padding(4.dp))
                Text("اسمعها", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = { index++; checked = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text(if (index == items.size - 1) "إنهاء" else "التالي", fontWeight = FontWeight.Bold) }
        }
    }
}
