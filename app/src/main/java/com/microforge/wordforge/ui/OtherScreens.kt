package com.microforge.wordforge.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microforge.wordforge.data.Library
import com.microforge.wordforge.data.Repo
import com.microforge.wordforge.data.Status

object StatsScreen : Screen {
    override val key = "stats"
    override val title = "الإحصائيات"

    @Composable
    override fun Content(env: Env) {
        val repo = env.repo
        @Suppress("UNUSED_VARIABLE") val tick = repo.tick
        val counts = repo.countByStatus()
        val memorized = (counts[Status.MEMORIZED] ?: 0) + (counts[Status.MASTERED] ?: 0)
        val (answers, correct) = repo.totals()
        val accuracy = if (answers == 0) 0 else correct * 100 / answers
        val week = repo.dayStats(7)
        val maxAnswers = (week.maxOfOrNull { it.answers } ?: 0).coerceAtLeast(1)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Appear(0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("🔥", repo.streak().toString(), "أيام متتالية", Amber)
                    StatCard("🟢", memorized.toString(), "محفوظة فعلًا", Good)
                    StatCard("🎯", "$accuracy%", "الدقة", Sky)
                }
            }

            Appear(1) {
                SectionCard("🧩 حالة كل المحتوى") {
                    StatusBar(counts)
                    for (st in Status.entries) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${st.emoji} ${st.label}", Modifier.weight(1f))
                            Text(
                                (counts[st] ?: 0).toString(),
                                fontWeight = FontWeight.ExtraBold,
                                color = statusColor(st),
                            )
                        }
                    }
                    Text(
                        "المحفوظة = افتكرتها صح 3 مرات على فترات متباعدة، مش مجرد سماع.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Appear(2) {
                SectionCard("📅 آخر 7 أيام (عدد الإجابات)") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        for (d in week) {
                            val h = if (d.answers == 0) 3 else (d.answers * 90 / maxAnswers).coerceAtLeast(8)
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                Text(d.answers.toString(), style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(4.dp))
                                GrowBar(h.dp, if (d.answers == 0) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) else Amber)
                                Spacer(Modifier.height(4.dp))
                                Text(d.day.substring(5), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            Appear(3) {
                SectionCard("📚 التقدّم حسب الموضوع") {
                    for (t in Library.topics.filter { it.available }) {
                        val total = Library.count(t.id)
                        val known = repo.memorized(null, t.id)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row {
                                Text("${t.emoji} ${t.ar}", Modifier.weight(1f))
                                Text("$known / $total", fontWeight = FontWeight.Bold)
                            }
                            ProgressBar(if (total == 0) 0f else known.toFloat() / total, color = topicColor(t.num))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GrowBar(target: Dp, color: Color) {
    var t by remember { mutableStateOf(0.dp) }
    LaunchedEffect(target) { t = target }
    val shown by animateDpAsState(t, tween(700), label = "growbar")
    Box(
        Modifier
            .width(22.dp)
            .height(shown)
            .background(color, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
    )
}

@Composable
private fun RowScope.StatCard(emoji: String, value: String, label: String, tint: Color) {
    Card(
        Modifier.weight(1f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.16f)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(emoji, fontSize = 24.sp)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

object SettingsScreen : Screen {
    override val key = "settings"
    override val title = "الإعدادات"

    private val rates = floatArrayOf(0.7f, 1.0f, 1.25f)
    private val rateLabels = listOf("🐢 بطيء", "🙂 عادي", "🐇 سريع")

    @Composable
    override fun Content(env: Env) {
        val repo = env.repo
        val speaker = env.speaker
        @Suppress("UNUSED_VARIABLE") val tick = repo.tick
        var confirmReset by remember { mutableStateOf(false) }
        val rateIdx = repo.getInt(Repo.TTS_RATE, 1).coerceIn(0, 2)
        val auto = repo.getInt(Repo.TTS_AUTO, 1) == 1
        val primary = MaterialTheme.colorScheme.primary

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Appear(0) {
                SectionCard("🔊 الصوت") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (speaker.ready) "✅" else "⚠️", fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            speaker.status,
                            Modifier.weight(1f),
                            color = if (speaker.ready) Good else Bad,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Button(
                        onClick = { speaker.say("Hello! Sanshe Turtle is ready.") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("🔊 اختبار الصوت") }
                    if (!speaker.ready) {
                        Text(
                            "الصوت بيشتغل من محرك النطق بتاع الموبايل. جرّب بالترتيب:",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { speaker.installVoiceData() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("⬇️ تنزيل بيانات الصوت الإنجليزي") }
                        OutlinedButton(
                            onClick = { speaker.openTtsSettings() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("⚙️ إعدادات النطق في الموبايل") }
                        OutlinedButton(
                            onClick = { speaker.installGoogleTts() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text("🛒 تثبيت Google Text-to-Speech") }
                    }
                    Text("سرعة النطق", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rateLabels.forEachIndexed { i, label ->
                            Pill(
                                label,
                                primary,
                                filled = i == rateIdx,
                                onClick = {
                                    repo.setInt(Repo.TTS_RATE, i)
                                    speaker.setRate(rates[i])
                                    speaker.say("Hello, nice to meet you.")
                                },
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("نطق تلقائي في بطاقات المراجعة", Modifier.weight(1f))
                        Switch(
                            checked = auto,
                            onCheckedChange = { repo.setInt(Repo.TTS_AUTO, if (it) 1 else 0) },
                        )
                    }
                    Text(
                        "لو لسه مفيش صوت: اتأكد إن صوت الوسائط مش صفر وإن الموبايل مش على الصامت.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Appear(1) {
                SectionCard("🎯 أهداف اليوم") {
                    Stepper("كلمات جديدة", repo.getInt(Repo.GOAL_WORDS, 10), 5, 0, 50) { repo.setInt(Repo.GOAL_WORDS, it) }
                    Stepper("جمل جديدة", repo.getInt(Repo.GOAL_SENTENCES, 5), 5, 0, 50) { repo.setInt(Repo.GOAL_SENTENCES, it) }
                    Stepper("إجابات (مراجعة وتدريب)", repo.getInt(Repo.GOAL_ANSWERS, 20), 10, 0, 200) { repo.setInt(Repo.GOAL_ANSWERS, it) }
                }
            }

            Appear(2) {
                SectionCard("ℹ️ عن التطبيق") {
                    Text("Sanshe Turtle 🐢 — نسخة 0.2")
                    Text(
                        "شغّال أوفلاين بالكامل، وتقدّمك محفوظ على الموبايل بس.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = { confirmReset = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Bad),
            ) { Text("🗑️ مسح كل التقدّم", color = Color.White) }
        }

        if (confirmReset) {
            AlertDialog(
                onDismissRequest = { confirmReset = false },
                title = { Text("مسح التقدّم؟") },
                text = { Text("هيتمسح كل اللي حفظته والإحصائيات، ومينفعش ترجّعه.") },
                confirmButton = {
                    TextButton(onClick = {
                        repo.resetProgress()
                        confirmReset = false
                    }) { Text("امسح", color = Bad) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmReset = false }) { Text("إلغاء") }
                },
            )
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, step: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(onClick = { onChange((value - step).coerceAtLeast(min)) }) { Text("−") }
        Text(
            value.toString(),
            Modifier
                .width(48.dp)
                .padding(horizontal = 4.dp),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
        )
        OutlinedButton(onClick = { onChange((value + step).coerceAtMost(max)) }) { Text("+") }
    }
}

object SearchScreen : Screen {
    override val key = "search"
    override val title = "بحث"

    @Composable
    override fun Content(env: Env) {
        @Suppress("UNUSED_VARIABLE") val tick = env.repo.tick
        var query by remember { mutableStateOf("") }
        val q = query.trim()
        val results = if (q.length < 2) emptyList() else Library.items.filter {
            it.en.contains(q, ignoreCase = true) || it.ar.contains(q)
        }.take(100)

        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("ابحث بالإنجليزي أو العربي") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
            if (q.length >= 2 && results.isEmpty()) {
                Text("مفيش نتائج.", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(results, key = { _, r -> r.id }) { _, item -> ItemRow(item, env) }
            }
        }
    }
}

/** Placeholder for features planned in later phases (grammar, exams...). */
class SoonScreen(override val key: String, override val title: String, private val body: String) : Screen {
    @Composable
    override fun Content(env: Env) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            PopIn { Text("🔜", fontSize = 64.sp) }
            Spacer(Modifier.height(12.dp))
            Text("قريبًا", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text(body, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
