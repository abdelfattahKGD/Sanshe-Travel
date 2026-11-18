package com.microforge.wordforge.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDirection
import com.microforge.wordforge.data.ImportedBooks
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microforge.wordforge.data.Book
import com.microforge.wordforge.data.GrammarQ
import com.microforge.wordforge.data.Kind
import com.microforge.wordforge.data.Library
import com.microforge.wordforge.data.Passage

private fun cleanToken(t: String): String = t.trim { !it.isLetter() && it != '\'' && it != '-' }

private fun isRead(env: Env, id: String): Boolean = env.repo.getInt("rd_$id", 0) == 1

private fun bestQuiz(env: Env, id: String): Int = env.repo.getInt("rq_$id", -1)

// ------------------------------------------------------------------ lists

@Composable
private fun PassageCard(p: Passage, env: Env, index: Int, label: String? = null) {
    val read = isRead(env, p.id)
    val best = bestQuiz(env, p.id)
    val color = levelColor(p.level)
    Appear(index) {
        Card(
            Modifier
                .fillMaxWidth()
                .clickable { env.nav.push(PassageScreen(p.id)) },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Text(p.emoji, fontSize = 24.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    En(p.en, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(p.ar, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        listOfNotNull(label, "${p.sentences.size} جملة").joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Pill(p.level, color, filled = false)
                    if (best >= 0) {
                        Pill("$best%", if (best >= 80) Good else Amber, filled = false)
                    } else if (read) {
                        Pill("✅ قرأت", Good, filled = false)
                    }
                }
            }
        }
    }
}

/** Stand-alone paragraphs, grouped by level. */
object ParagraphsScreen : Screen {
    override val key = "paragraphs"
    override val title = "الفقرات"

    @Composable
    override fun Content(env: Env) {
        @Suppress("UNUSED_VARIABLE") val tick = env.repo.tick
        val list = Library.paragraphs
        if (list.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("مفيش فقرات متاحة.")
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
                    "اقرأ فقرة كاملة بدل كلمة واحدة. اضغط على أي جملة لترجمتها، وعلى أي كلمة لمعناها، واستمع للنص بصوت بيظلّل الجملة اللي بتتقري.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            var last = ""
            list.forEachIndexed { index, p ->
                if (p.level != last) {
                    last = p.level
                    item(key = "lv-${p.level}") { Pill("المستوى ${p.level}", levelColor(p.level), Modifier.padding(top = 8.dp)) }
                }
                item(key = p.id) { PassageCard(p, env, index) }
            }
        }
    }
}

// ------------------------------------------------------------------ books

@Composable
private fun BookCover(b: Book, env: Env) {
    val color = levelColor(b.level)
    val done = b.chapters.count { isRead(env, it.id) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.65f))))
            .clickable { env.nav.push(BookScreen(b.id)) }
            .padding(14.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(b.emoji, fontSize = 36.sp)
                Spacer(Modifier.weight(1f))
                Pill(b.level, Color.White.copy(alpha = 0.3f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                En(
                    b.en,
                    Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                )
                Text(b.ar, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodySmall)
                ProgressBar(if (b.chapters.isEmpty()) 0f else done.toFloat() / b.chapters.size, color = Color.White, height = 6.dp)
                Text("$done / ${b.chapters.size} فصول", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** The bookshelf: classic stories retold at graded levels, plus the user's own imported books. */
object BooksScreen : Screen {
    override val key = "books"
    override val title = "الكتب"

    @Composable
    override fun Content(env: Env) {
        @Suppress("UNUSED_VARIABLE") val tick = env.repo.tick
        val context = LocalContext.current
        var imported by remember { mutableStateOf(ImportedBooks.list(context)) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val book = try {
                    ImportedBooks.importFrom(context, uri)
                } catch (e: Exception) {
                    null
                }
                if (book == null) {
                    env.speaker.toast("تعذّر استيراد الملف. استخدم ملف نصي (txt) أو epub فيه نص إنجليزي.")
                } else {
                    imported = ImportedBooks.list(context)
                    env.speaker.toast("اتضاف الكتاب ✅")
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "حكايات كلاسيكية مشهورة بنسخ مبسّطة على مستويات، من A1 لـ B1. اقرأ فصل فصل، واتعلّم كلماته.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemsIndexed(Library.books, key = { _, b -> b.id }) { i, b ->
                Appear(i) { BookCover(b, env) }
            }
            item(key = "imp-head", span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { picker.launch(arrayOf("text/plain", "application/epub+zip", "*/*")) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("📥", fontSize = 32.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("استورد كتابك", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                            Text(
                                "ملف txt أو epub بنص إنجليزي. هتقراه صفحة صفحة، ونطلعلك الكلمات الجديدة فيه.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            itemsIndexed(imported, key = { _, b -> b.id }) { _, b -> ImportedCover(b, env) }
        }
    }
}

class BookScreen(private val bookId: String) : Screen {
    override val key = "book-$bookId"
    override val title: String get() = Library.book(bookId)?.en ?: "الكتب"

    @Composable
    override fun Content(env: Env) {
        val b = Library.book(bookId) ?: return
        @Suppress("UNUSED_VARIABLE") val tick = env.repo.tick
        val color = levelColor(b.level)
        val done = b.chapters.count { isRead(env, it.id) }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.7f))))
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(b.emoji, fontSize = 44.sp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                En(b.en, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                Text(b.ar, color = Color.White.copy(alpha = 0.92f))
                            }
                        }
                        En(b.author, Modifier.fillMaxWidth(), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                        Text(b.desc, color = Color.White)
                        ProgressBar(if (b.chapters.isEmpty()) 0f else done.toFloat() / b.chapters.size, color = Color.White)
                        Text("قرأت $done من ${b.chapters.size} فصول", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            item(key = "note") {
                Text(
                    "النصوص دي إعادة صياغة مبسّطة بأسلوبنا للحكاية الأصلية (مش النص الأصلي بحروفه).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemsIndexed(b.chapters, key = { _, c -> c.id }) { i, c ->
                PassageCard(c, env, i, label = "الفصل ${i + 1}")
            }
        }
    }
}

// ------------------------------------------------------------------ the reader

/** One sentence: every word is tappable; tap elsewhere on the card to show or hide the Arabic translation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SentenceCard(
    en: String,
    ar: String,
    highlighted: Boolean,
    showAr: Boolean,
    glossKeys: Set<String>,
    onWord: (String) -> Unit,
    onSpeak: () -> Unit,
    onTranslate: () -> Unit,
    onToggle: () -> Unit,
) {
    Card(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // English flows left-to-right even though the app is RTL.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        for (token in en.split(" ").filter { it.isNotEmpty() }) {
                            val clean = cleanToken(token)
                            val isGloss = clean.isNotEmpty() && Library.variants(clean).any { it in glossKeys }
                            Text(
                                token,
                                Modifier
                                    .clickable { if (clean.isNotEmpty()) onWord(token) }
                                    .padding(vertical = 2.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isGloss) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                fontWeight = if (isGloss) FontWeight.Bold else FontWeight.Normal,
                                textDecoration = if (isGloss) TextDecoration.Underline else null,
                            )
                        }
                    }
                }
                if (showAr) {
                    if (ar.isEmpty()) {
                        TextButton(onClick = onTranslate) { Text("🌐 ترجم الجملة أونلاين") }
                    } else {
                        Text(ar, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text(
                        "اضغط على الجملة لإظهار الترجمة",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
            SoundButton(onClick = onSpeak, diameter = 38.dp)
        }
    }
}

@Composable
private fun WordDialog(raw: String, p: Passage, env: Env, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val tick = env.repo.tick
    val clean = cleanToken(raw)
    val vars = Library.variants(clean)
    val gloss = p.glossary.firstOrNull { it.word.lowercase() in vars }
    val item = Library.findWord(clean)
    val meaning = gloss?.ar ?: item?.ar
    val pos = gloss?.pos ?: item?.pos ?: ""
    val starred = item?.let { env.repo.progress[it.id]?.starred } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("تمام") } },
        title = { En(clean, Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (meaning != null) {
                    Text(meaning, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                } else {
                    Text("الكلمة دي مش في قاموس التطبيق لسه.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (pos.isNotEmpty()) En(pos, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onAdd(clean) }, shape = RoundedCornerShape(12.dp)) { Text("➕ أضف لكلماتي") }
                        OutlinedButton(onClick = { env.speaker.openTranslate(clean) }, shape = RoundedCornerShape(12.dp)) { Text("🌐 ترجم") }
                    }
                }
                if (item != null) StatusPill(env.repo.statusOf(item.id))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SoundButton(onClick = { env.speaker.say(clean) })
                    if (item != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (starred) "⭐ في المفضلة" else "☆ أضف للمفضلة",
                            Modifier
                                .clickable { env.repo.toggleStar(item.id) }
                                .padding(8.dp),
                        )
                    }
                }
            }
        },
    )
}

/** Reads one paragraph or chapter: tap a sentence for its translation, tap a word for its meaning, or listen to the whole text. */
class PassageScreen(private val passageId: String, private val direct: Passage? = null) : Screen {
    override val key = "passage-$passageId"
    override val title: String get() = (direct ?: Library.passage(passageId))?.en ?: ""

    @Composable
    override fun Content(env: Env) {
        val p = direct ?: Library.passage(passageId) ?: return
        val repo = env.repo
        @Suppress("UNUSED_VARIABLE") val tick = repo.tick
        var showAll by remember { mutableStateOf(false) }
        val revealed = remember { mutableStateListOf<Int>() }
        var current by remember { mutableIntStateOf(-1) }
        var playing by remember { mutableStateOf(false) }
        var dialogWord by remember { mutableStateOf<String?>(null) }
        var addWord by remember { mutableStateOf<String?>(null) }
        val listState = rememberLazyListState()
        val glossKeys = remember(p.id) { p.glossary.map { it.word.lowercase() }.toSet() }
        val color = levelColor(p.level)
        val read = isRead(env, p.id)

        DisposableEffect(Unit) { onDispose { env.speaker.stopSpeaking() } }
        LaunchedEffect(current) {
            if (current >= 0) listState.animateScrollToItem(current + 1)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.7f))))
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.emoji, fontSize = 36.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                En(p.en, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                Text(p.ar, color = Color.White.copy(alpha = 0.92f))
                            }
                            Pill(p.level, Color.White.copy(alpha = 0.3f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    if (playing) {
                                        env.speaker.stopSpeaking()
                                        playing = false
                                        current = -1
                                    } else {
                                        playing = true
                                        env.speaker.speakAll(
                                            p.sentences.map { it.first },
                                            onIndex = { current = it },
                                            onFinish = {
                                                playing = false
                                                current = -1
                                            },
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                            ) { Text(if (playing) "⏹ إيقاف" else "▶️ استمع للنص") }
                            Pill(
                                if (showAll) "🙈 إخفاء الترجمة" else "👁 كل الترجمة",
                                Color.White.copy(alpha = 0.3f),
                                onClick = { showAll = !showAll },
                            )
                        }
                    }
                }
            }

            itemsIndexed(p.sentences, key = { i, _ -> "s$i" }) { i, pair ->
                SentenceCard(
                    en = pair.first,
                    ar = pair.second,
                    highlighted = i == current,
                    showAr = showAll || i in revealed,
                    glossKeys = glossKeys,
                    onWord = { dialogWord = it },
                    onSpeak = { env.speaker.say(pair.first) },
                    onTranslate = { env.speaker.openTranslate(pair.first) },
                    onToggle = { if (i in revealed) revealed.remove(i) else revealed.add(i) },
                )
            }

            if (p.glossary.isNotEmpty()) item(key = "words") {
                SectionCard("🧩 كلمات جديدة في النص") {
                    for (g in p.glossary) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                En(g.word, Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                Text(g.ar, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                            }
                            SoundButton(onClick = { env.speaker.say(g.word) }, diameter = 34.dp)
                        }
                    }
                    if (Library.topic("t11") != null) {
                        OutlinedButton(
                            onClick = { env.nav.push(TopicScreen("t11", Kind.WORD)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("🧠 ذاكر كلمات القراءة") }
                    }
                }
            }

            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (p.quiz.isNotEmpty()) Button(
                        onClick = { env.nav.push(ReadingQuizScreen(p.id)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("📝 كويز الفهم (${p.quiz.size} أسئلة)", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = {
                            repo.setInt("rd_${p.id}", 1)
                            env.speaker.toast("اتسجّل إنك قرأت النص ✅")
                        },
                        enabled = !read,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text(if (read) "✅ قرأت النص" else "✅ خلّصت القراءة") }
                }
            }
        }

        val dw = dialogWord
        if (dw != null) {
            WordDialog(dw, p, env, onAdd = {
                addWord = it
                dialogWord = null
            }) { dialogWord = null }
        }
        val aw = addWord
        if (aw != null) {
            val example = p.sentences.firstOrNull { it.first.contains(aw, ignoreCase = true) }?.first ?: ""
            AddWordDialog(aw, example, env) { addWord = null }
        }
    }
}

// ------------------------------------------------------------------ comprehension quiz

private class RQ(val q: GrammarQ, val options: List<String>, val correctIndex: Int)

/** Multiple-choice comprehension questions about a text. The best score is saved. */
class ReadingQuizScreen(private val passageId: String) : Screen {
    override val key = "rquiz-$passageId"
    override val title = "كويز الفهم"

    @Composable
    override fun Content(env: Env) {
        val p = Library.passage(passageId) ?: return
        var attempt by remember { mutableIntStateOf(0) }
        key(attempt) { Body(env, p, onRestart = { attempt++ }) }
    }

    @Composable
    private fun Body(env: Env, p: Passage, onRestart: () -> Unit) {
        val qs = remember {
            p.quiz.shuffled().map { q ->
                val o = (q.wrong + q.answer).shuffled()
                RQ(q, o, o.indexOf(q.answer))
            }
        }
        var index by remember { mutableIntStateOf(0) }
        var score by remember { mutableIntStateOf(0) }
        var selected by remember { mutableIntStateOf(-1) }

        if (index >= qs.size) {
            val percent = if (qs.isEmpty()) 0 else score * 100 / qs.size
            LaunchedEffect(Unit) {
                val k = "rq_${p.id}"
                if (percent > env.repo.getInt(k, -1)) env.repo.setInt(k, percent)
                env.repo.setInt("rd_${p.id}", 1)
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
                    color = if (percent >= 70) Good else Amber,
                ) {
                    PopIn { Text(if (percent >= 70) "🏆" else "💪", fontSize = 48.sp) }
                }
                Text("نتيجتك: $score من ${qs.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (percent >= 70) "فهمت النص كويس 👏" else "ارجع للنص واقرأه تاني وجرّب.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onRestart, shape = RoundedCornerShape(14.dp)) { Text("🔁 إعادة") }
                    OutlinedButton(onClick = { env.nav.back() }, shape = RoundedCornerShape(14.dp)) { Text("رجوع للنص") }
                }
            }
            return
        }

        val q = qs[index]
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
                Column(Modifier.padding(20.dp)) {
                    En(q.q.q, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
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
                    ) { Text(if (index == qs.size - 1) "إنهاء" else "التالي", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/** Adds a word to "كلماتي" with the user's own meaning (they can look it up online first). */
@Composable
fun AddWordDialog(initialEn: String, example: String, env: Env, onDismiss: () -> Unit) {
    var en by remember { mutableStateOf(initialEn) }
    var ar by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة لكلماتي", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = en,
                    onValueChange = { en = it },
                    label = { Text("الكلمة") },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr, textAlign = TextAlign.Left),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ar,
                    onValueChange = { ar = it },
                    label = { Text("المعنى بالعربي") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (example.isNotEmpty()) {
                    En(example, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { env.speaker.openTranslate(en) }, shape = RoundedCornerShape(12.dp)) {
                    Text("🌐 ترجم أونلاين")
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = en.isNotBlank() && ar.isNotBlank(),
                onClick = {
                    val ok = env.repo.addCustomWord(en, ar, example)
                    env.speaker.toast(if (ok) "اتضافت لكلماتي ⭐" else "الكلمة دي موجودة عندك بالفعل")
                    onDismiss()
                },
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
