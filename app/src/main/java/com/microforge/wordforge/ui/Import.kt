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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microforge.wordforge.data.Analysis
import com.microforge.wordforge.data.ImportedBook
import com.microforge.wordforge.data.ImportedBooks
import com.microforge.wordforge.data.Passage
import com.microforge.wordforge.data.WordStat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ImportedColor = Color(0xFF5C6BC0)

@Composable
fun ImportedCover(b: ImportedBook, env: Env) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(ImportedColor, ImportedColor.copy(alpha = 0.65f))))
            .clickable { env.nav.push(ImportedBookScreen(b.id)) }
            .padding(14.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("📄", fontSize = 36.sp)
                Spacer(Modifier.weight(1f))
                Pill("كتابك", Color.White.copy(alpha = 0.3f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                En(
                    b.title,
                    Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                )
                Text("≈ ${b.chars / 6} كلمة", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** A book the user imported: read it page by page, or analyze which words are new to them. */
class ImportedBookScreen(private val bookId: String) : Screen {
    override val key = "ibook-$bookId"
    override val title = "كتاب مستورد"

    @Composable
    override fun Content(env: Env) {
        val context = LocalContext.current
        @Suppress("UNUSED_VARIABLE") val tick = env.repo.tick
        val meta = remember { ImportedBooks.list(context).firstOrNull { it.id == bookId } } ?: return
        val pages = remember { ImportedBooks.pages(ImportedBooks.readText(context, bookId)) }
        var confirmDelete by remember { mutableStateOf(false) }

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
                        .background(Brush.linearGradient(listOf(ImportedColor, ImportedColor.copy(alpha = 0.7f))))
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        En(
                            meta.title,
                            Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                        )
                        Text("≈ ${meta.chars / 6} كلمة  •  ${pages.size} صفحة", color = Color.White)
                        Button(
                            onClick = { env.nav.push(BookAnalysisScreen(bookId)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("📊 حلّل الكلمات الجديدة", fontWeight = FontWeight.Bold) }
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("🗑 حذف الكتاب", color = Color.White) }
                    }
                }
            }
            item(key = "note") {
                Text(
                    "الصفحات من غير ترجمة جاهزة: اضغط على أي كلمة ليظهر معناها لو موجود، أو أضفها لكلماتك بمعناك، أو ترجمها أونلاين.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemsIndexed(pages, key = { i, _ -> "pg$i" }) { i, sentences ->
                val pageId = "imp_${bookId}_${i + 1}"
                val read = env.repo.getInt("rd_$pageId", 0) == 1
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            env.nav.push(
                                PassageScreen(
                                    pageId,
                                    Passage(
                                        id = pageId,
                                        level = "—",
                                        en = "${meta.title} – ${i + 1}",
                                        ar = "الصفحة ${i + 1}",
                                        emoji = "📄",
                                        sentences = sentences.map { Pair(it, "") },
                                        glossary = emptyList(),
                                        quiz = emptyList(),
                                    ),
                                )
                            )
                        },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("📄", fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("الصفحة ${i + 1}", fontWeight = FontWeight.Bold)
                            En(
                                sentences.first().take(90) + "…",
                                Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (read) Pill("✅ قرأت", Good, filled = false)
                    }
                }
            }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("حذف الكتاب؟") },
                text = { Text("هيتمسح الكتاب من التطبيق (الملف الأصلي على موبايلك مش هيتأثر).") },
                confirmButton = {
                    TextButton(onClick = {
                        ImportedBooks.delete(context, bookId)
                        confirmDelete = false
                        env.nav.back()
                    }) { Text("احذف", color = Bad) }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } },
            )
        }
    }
}

/** The sentence of the book that contains [word], to show the word in context. */
private fun exampleOf(text: String, word: String): String {
    val m = Regex("\\b" + Regex.escape(word) + "\\b", RegexOption.IGNORE_CASE).find(text) ?: return ""
    var s = m.range.first
    while (s > 0 && text[s - 1] !in ".!?\n") s--
    var e = m.range.last + 1
    while (e < text.length && text[e] !in ".!?\n") e++
    if (e < text.length) e++
    val sentence = text.substring(s, e).replace(Regex("\\s+"), " ").trim()
    return if (sentence.length > 220) sentence.substring(0, 220) + "…" else sentence
}

@Composable
private fun StatRow(stat: WordStat, example: String, env: Env, onAdd: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    En(stat.word, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Pill("×${stat.count}", MaterialTheme.colorScheme.primary, filled = false)
                }
                if (example.isNotEmpty()) {
                    En(
                        example,
                        Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SoundButton(onClick = { env.speaker.say(stat.word) }, diameter = 34.dp)
                Row {
                    Text("➕", Modifier.clickable { onAdd() }.padding(6.dp), fontSize = 18.sp)
                    Text("🌐", Modifier.clickable { env.speaker.openTranslate(stat.word) }.padding(6.dp), fontSize = 18.sp)
                    Text("✅", Modifier.clickable { env.repo.setInt("kw_${stat.word}", 1) }.padding(6.dp), fontSize = 18.sp)
                }
            }
        }
    }
}

/** Which words of the book are new to the user, most frequent first. */
class BookAnalysisScreen(private val bookId: String) : Screen {
    override val key = "ianalysis-$bookId"
    override val title = "تحليل الكلمات"

    @Composable
    override fun Content(env: Env) {
        val context = LocalContext.current
        val repo = env.repo
        @Suppress("UNUSED_VARIABLE") val tick = repo.tick
        val text = remember { ImportedBooks.readText(context, bookId) }
        var result by remember { mutableStateOf<Analysis?>(null) }
        LaunchedEffect(bookId) {
            result = withContext(Dispatchers.Default) { ImportedBooks.analyze(text) }
        }
        val a = result
        if (a == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("بحلّل الكتاب…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }

        val known = repo.knownWordSet()
        var limit by remember { mutableIntStateOf(40) }
        var addFor by remember { mutableStateOf<String?>(null) }
        val visible = a.newWords.filter { it.word !in known }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "stats") {
                SectionCard("📊 عن الكتاب") {
                    Row {
                        Text("عدد الكلمات", Modifier.weight(1f))
                        Text("${a.totalTokens}", fontWeight = FontWeight.Bold)
                    }
                    Row {
                        Text("كلمات مختلفة", Modifier.weight(1f))
                        Text("${a.uniqueWords}", fontWeight = FontWeight.Bold)
                    }
                    Row {
                        Text("نسبة اللي تعرفه تقريبًا", Modifier.weight(1f))
                        Text("${a.knownPercent}%", fontWeight = FontWeight.Bold, color = if (a.knownPercent >= 90) Good else Amber)
                    }
                    ProgressBar(a.knownPercent / 100f, color = if (a.knownPercent >= 90) Good else Amber)
                    Text(
                        "النسبة محسوبة من كلمات التطبيق وأشهر الكلمات الأساسية. لو فوق 90% الكتاب مناسب ليك.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "howto") {
                Text(
                    "➕ أضف الكلمة لكلماتك بمعناك   🌐 ترجمها أونلاين   ✅ أعرفها (تختفي من القائمة)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(visible.take(limit), key = { it.word }) { stat ->
                val example = remember(stat.word) { exampleOf(text, stat.word) }
                StatRow(stat, example, env, onAdd = { addFor = stat.word })
            }
            if (visible.size > limit) {
                item(key = "more") {
                    OutlinedButton(
                        onClick = { limit += 40 },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("عرض المزيد") }
                }
            }
        }

        val aw = addFor
        if (aw != null) AddWordDialog(aw, exampleOf(text, aw), env) { addFor = null }
    }
}
