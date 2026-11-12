package com.microforge.wordforge.ui

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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microforge.wordforge.data.Kind
import com.microforge.wordforge.data.Library
import com.microforge.wordforge.data.Repo

object StatsScreen : Screen {
    override val key = "stats"
    override val title = "الإحصائيات"

    @Composable
    override fun Content(env: Env) {
        val repo = env.repo
        @Suppress("UNUSED_VARIABLE") val tick = repo.tick
        val (answers, correct) = repo.totals()
        val accuracy = if (answers == 0) 0 else correct * 100 / answers
        val week = repo.dayStats(7)
        val maxAnswers = (week.maxOfOrNull { it.answers } ?: 0).coerceAtLeast(1)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("🔥", repo.streak().toString(), "أيام متتالية")
                StatCard("📘", repo.learned(Kind.WORD).toString(), "كلمات متعلّمة")
                StatCard("💬", repo.learned(Kind.SENTENCE).toString(), "جمل متعلّمة")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("🏆", repo.mastered().toString(), "متقنة")
                StatCard("🎯", "$accuracy%", "الدقة")
                StatCard("✍️", answers.toString(), "إجابات")
            }

            SectionCard("آخر 7 أيام (عدد الإجابات)") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    for (d in week) {
                        val h = if (d.answers == 0) 2 else (d.answers * 90 / maxAnswers).coerceAtLeast(6)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                            Text(d.answers.toString(), style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(4.dp))
                            Box(
                                Modifier
                                    .width(22.dp)
                                    .height(h.dp)
                                    .background(
                                        if (d.answers == 0) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f) else Gold,
                                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                                    )
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(d.day.substring(5), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            SectionCard("التقدّم حسب الموضوع") {
                for (t in Library.topics.filter { it.available }) {
                    val total = Library.count(t.id)
                    val learned = repo.learned(null, t.id)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row {
                            Text("${t.emoji} ${t.ar}", Modifier.weight(1f))
                            Text("$learned / $total", fontWeight = FontWeight.Bold)
                        }
                        ProgressBar(if (total == 0) 0f else learned.toFloat() / total)
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.StatCard(emoji: String, value: String, label: String) {
    Card(
        Modifier.weight(1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(emoji, fontSize = 22.sp)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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

    @Composable
    override fun Content(env: Env) {
        val repo = env.repo
        @Suppress("UNUSED_VARIABLE") val tick = repo.tick
        var confirmReset by remember { mutableStateOf(false) }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard("🎯 أهداف اليوم") {
                Stepper("كلمات جديدة", repo.getInt(Repo.GOAL_WORDS, 10), 5, 0, 50) { repo.setInt(Repo.GOAL_WORDS, it) }
                Stepper("جمل جديدة", repo.getInt(Repo.GOAL_SENTENCES, 5), 5, 0, 50) { repo.setInt(Repo.GOAL_SENTENCES, it) }
                Stepper("إجابات (مراجعة وتدريب)", repo.getInt(Repo.GOAL_ANSWERS, 20), 10, 0, 200) { repo.setInt(Repo.GOAL_ANSWERS, it) }
            }
            SectionCard("ℹ️ عن التطبيق") {
                Text("Word Forge — نسخة 0.1 (المرحلة 1)")
                Text(
                    "التطبيق شغّال أوفلاين بالكامل، وتقدّمك محفوظ على الموبايل بس.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { confirmReset = true },
                modifier = Modifier.fillMaxWidth(),
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
                items(results, key = { it.id }) { ItemRow(it, env) }
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
            Text("🔜", fontSize = 56.sp)
            Spacer(Modifier.height(12.dp))
            Text("قريبًا", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(body, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
