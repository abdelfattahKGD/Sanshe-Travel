package com.microforge.wordforge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microforge.wordforge.data.Kind
import com.microforge.wordforge.data.Library
import com.microforge.wordforge.data.Repo

object HomeScreen : Screen {
    override val key = "home"
    override val title = "Word Forge"

    @Composable
    override fun Content(env: Env) {
        val repo = env.repo
        @Suppress("UNUSED_VARIABLE") val tick = repo.tick // reading it makes this screen refresh after every answer
        val today = repo.todayStat()
        val goalW = repo.getInt(Repo.GOAL_WORDS, 10)
        val goalS = repo.getInt(Repo.GOAL_SENTENCES, 5)
        val goalA = repo.getInt(Repo.GOAL_ANSWERS, 20)
        val totalItems = Library.items.size
        val learned = repo.learned(null)
        val fraction = if (totalItems == 0) 0f else learned.toFloat() / totalItems
        val dueW = repo.dueCount(Kind.WORD)
        val dueS = repo.dueCount(Kind.SENTENCE)
        val (answers, correct) = repo.totals()
        val accuracy = if (answers == 0) 0 else correct * 100 / answers

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "أهلًا بيك 👋",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        "🔥 ${repo.streak()} يوم متتالي",
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        "تقدّمك الكلي: $learned من $totalItems",
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    ProgressBar(fraction, color = Gold)
                }
            }

            SectionCard("✅ مهام النهاردة") {
                TodoRow("تعلّم كلمات جديدة", today.newWords, goalW)
                TodoRow("تعلّم جمل جديدة", today.newSentences, goalS)
                TodoRow("أجب على أسئلة (مراجعة وتدريب)", today.answers, goalA)
            }

            SectionCard("🚀 ابدأ دلوقتي") {
                Text(
                    "مستحق للمراجعة: $dueW كلمة و $dueS جملة",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { env.nav.push(ReviewScreen(Kind.WORD, null)) },
                        modifier = Modifier.weight(1f),
                    ) { Text("📚 كلمات") }
                    Button(
                        onClick = { env.nav.push(ReviewScreen(Kind.SENTENCE, null)) },
                        modifier = Modifier.weight(1f),
                    ) { Text("💬 جمل") }
                }
                OutlinedButton(
                    onClick = { env.nav.push(PracticeScreen) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("✏️ تدريب سريع (اختيار من متعدد)") }
            }

            SectionCard("📊 نظرة سريعة") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MiniStat("📘", repo.learned(Kind.WORD).toString(), "كلمات")
                    MiniStat("💬", repo.learned(Kind.SENTENCE).toString(), "جمل")
                    MiniStat("🏆", repo.mastered().toString(), "متقنة")
                    MiniStat("🎯", "$accuracy%", "الدقة")
                }
            }
        }
    }
}

@Composable
private fun TodoRow(label: String, done: Int, goal: Int) {
    val complete = done >= goal
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (complete) "✅" else "⬜")
            Spacer(Modifier.width(8.dp))
            Text(label, Modifier.weight(1f))
            Text("$done / $goal", fontWeight = FontWeight.Bold)
        }
        ProgressBar(if (goal == 0) 1f else done.toFloat() / goal, color = if (complete) Good else MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MiniStat(emoji: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 22.sp)
        Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Lists the topics; tapping an available one opens its words or sentences. */
class BrowseScreen(private val kind: Kind) : Screen {
    override val key = "browse-${kind.name}"
    override val title = if (kind == Kind.WORD) "الكلمات" else "الجمل"

    @Composable
    override fun Content(env: Env) {
        @Suppress("UNUSED_VARIABLE") val tick = env.repo.tick
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(Library.topics, key = { it.id }) { t ->
                val total = Library.count(t.id, kind)
                val learned = env.repo.learned(kind, t.id)
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = t.available) { env.nav.push(TopicScreen(t.id, kind)) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t.emoji, fontSize = 30.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "${t.num}. ${t.ar}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            En(
                                t.en,
                                Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (t.available) {
                                ProgressBar(if (total == 0) 0f else learned.toFloat() / total)
                                Text(
                                    "$learned / $total",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Text(
                                    "قريبًا 🔜",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** All items of one topic, grouped by sub-group. */
class TopicScreen(private val topicId: String, private val kind: Kind) : Screen {
    override val key = "topic-$topicId-${kind.name}"
    override val title: String get() = Library.topic(topicId)?.ar ?: ""

    @Composable
    override fun Content(env: Env) {
        val topic = Library.topic(topicId) ?: return
        @Suppress("UNUSED_VARIABLE") val tick = env.repo.tick
        val all = Library.items.filter { it.topicId == topicId && it.kind == kind }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "start") {
                Button(
                    onClick = { env.nav.push(ReviewScreen(kind, topicId)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (kind == Kind.WORD) "▶️ ذاكر كلمات الموضوع" else "▶️ ذاكر جمل الموضوع") }
            }
            for (g in topic.groups) {
                val inGroup = all.filter { it.groupId == g.id }
                if (inGroup.isEmpty()) continue
                item(key = "h-${g.id}") {
                    Column(Modifier.padding(top = 10.dp)) {
                        Text(
                            g.ar,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        En(
                            g.en,
                            Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(inGroup, key = { it.id }) { ItemRow(it, env) }
            }
        }
    }
}
