package com.microforge.wordforge.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microforge.wordforge.R
import com.microforge.wordforge.data.Kind
import com.microforge.wordforge.data.Library
import com.microforge.wordforge.data.Repo
import com.microforge.wordforge.data.Status
import com.microforge.wordforge.data.Topic

object HomeScreen : Screen {
    override val key = "home"
    override val title = "Sanshe Turtle"

    @Composable
    override fun Content(env: Env) {
        val repo = env.repo
        @Suppress("UNUSED_VARIABLE") val tick = repo.tick // reading it refreshes this screen after every answer
        val today = repo.todayStat()
        val goalW = repo.getInt(Repo.GOAL_WORDS, 10)
        val goalS = repo.getInt(Repo.GOAL_SENTENCES, 5)
        val goalA = repo.getInt(Repo.GOAL_ANSWERS, 20)
        val counts = repo.countByStatus()
        val total = Library.items.size
        val memorized = (counts[Status.MEMORIZED] ?: 0) + (counts[Status.MASTERED] ?: 0)
        val learning = (counts[Status.LEARNING] ?: 0) + (counts[Status.HARD] ?: 0)
        val due = repo.dueCount(null)
        var showInfo by remember { mutableStateOf(false) }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Appear(0) { Hero(repo.streak(), memorized, learning, total) }

            Appear(1) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("جاهز تذاكر؟ 🐢", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        Text(
                            if (due > 0) "عندك $due عنصر مستحق للمراجعة دلوقتي، ومعاهم كلمات جديدة."
                            else "مفيش مراجعات مستحقة دلوقتي. وقت كلمات جديدة!",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Button(
                            onClick = { env.nav.push(ReviewScreen(null, null)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) { Text("🧠 ابدأ جلسة المراجعة", fontWeight = FontWeight.Bold) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { env.nav.push(ReviewScreen(Kind.SENTENCE, null)) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                            ) { Text("💬 جمل") }
                            OutlinedButton(
                                onClick = { env.nav.push(PracticeScreen) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                            ) { Text("✏️ تدريب") }
                        }
                    }
                }
            }

            Appear(2) {
                SectionCard("🎯 مهام النهاردة") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        GoalRing("كلمات جديدة", today.newWords, goalW)
                        GoalRing("جمل جديدة", today.newSentences, goalS)
                        GoalRing("إجابات", today.answers, goalA)
                    }
                }
            }

            Appear(3) {
                SectionCard("🧩 حالة كلماتك وجملك") {
                    StatusBar(counts)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (st in Status.entries) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    (counts[st] ?: 0).toString(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = statusColor(st),
                                )
                                Text(st.emoji)
                                Text(st.label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    TextButton(onClick = { showInfo = true }) { Text("❓ إزاي أعرف إني حفظت الكلمة فعلًا؟") }
                }
            }

            Appear(4) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("📚 المواضيع", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(Library.topics, key = { it.id }) { t ->
                            TopicTile(t, Kind.WORD, env, Modifier.width(158.dp).height(128.dp))
                        }
                    }
                }
            }
        }
        if (showInfo) StatusInfoDialog { showInfo = false }
    }
}

@Composable
private fun Hero(streak: Int, memorized: Int, learning: Int, total: Int) {
    val inf = rememberInfiniteTransition(label = "float")
    val dy by inf.animateFloat(
        -6f,
        6f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dy",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Leaf, LeafDark)))
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.mascot),
                    contentDescription = null,
                    modifier = Modifier
                        .size(92.dp)
                        .graphicsLayer { translationY = dy * density }
                        .clip(CircleShape)
                        .border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "أهلًا بيك يا بطل 👋",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("🔥 $streak يوم", Color.White.copy(alpha = 0.22f))
                        Pill("🟠 $learning بتتعلّم", Color.White.copy(alpha = 0.22f))
                    }
                }
            }
            Text(
                "🟢 محفوظ فعلًا: $memorized من $total",
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            ProgressBar(if (total == 0) 0f else memorized.toFloat() / total, color = Amber, height = 10.dp)
        }
    }
}

@Composable
private fun GoalRing(label: String, done: Int, goal: Int) {
    val complete = goal > 0 && done >= goal
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ProgressRing(
            fraction = if (goal == 0) 1f else done.toFloat() / goal,
            diameter = 84.dp,
            stroke = 9.dp,
            color = if (complete) Good else MaterialTheme.colorScheme.primary,
        ) {
            if (complete) Text("✅", fontSize = 26.sp) else Text("$done/$goal", fontWeight = FontWeight.Bold)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Colorful topic card used on Home (small) and in the Browse grid (big). */
@Composable
fun TopicTile(t: Topic, kind: Kind, env: Env, modifier: Modifier = Modifier) {
    val c = topicColor(t.num)
    val total = Library.count(t.id, kind)
    val known = env.repo.memorized(kind, t.id)
    Box(
        modifier
            .graphicsLayer { alpha = if (t.available) 1f else 0.55f }
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(c, c.copy(alpha = 0.72f))))
            .clickable(enabled = t.available) { env.nav.push(TopicScreen(t.id, kind)) }
            .padding(14.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(t.emoji, fontSize = 32.sp)
                Spacer(Modifier.weight(1f))
                if (!t.available) Pill("قريبًا", Color.White.copy(alpha = 0.3f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${t.num}. ${t.ar}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                )
                if (t.available) {
                    ProgressBar(if (total == 0) 0f else known.toFloat() / total, color = Color.White, height = 6.dp)
                    Text("🟢 $known / $total", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** Topic grid for words or sentences. */
class BrowseScreen(private val kind: Kind) : Screen {
    override val key = "browse-${kind.name}"
    override val title = if (kind == Kind.WORD) "الكلمات" else "الجمل"

    @Composable
    override fun Content(env: Env) {
        @Suppress("UNUSED_VARIABLE") val tick = env.repo.tick
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    if (kind == Kind.WORD) "اختار موضوع وابدأ تذاكر كلماته" else "اختار موضوع وابدأ تذاكر جمله",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemsIndexed(Library.topics, key = { _, t -> t.id }) { i, t ->
                Appear(i) { TopicTile(t, kind, env, Modifier.fillMaxWidth().height(150.dp)) }
            }
        }
    }
}

private enum class ItemFilter(val label: String) {
    ALL("الكل"), NEW("جديدة"), LEARNING("بتتعلّم"), KNOWN("محفوظة");

    fun accepts(s: Status): Boolean = when (this) {
        ALL -> true
        NEW -> s == Status.NEW
        LEARNING -> s == Status.LEARNING || s == Status.HARD
        KNOWN -> s == Status.MEMORIZED || s == Status.MASTERED
    }
}

/** All items of one topic, grouped by sub-group, with a status filter. */
class TopicScreen(private val topicId: String, private val kind: Kind) : Screen {
    override val key = "topic-$topicId-${kind.name}"
    override val title: String get() = Library.topic(topicId)?.ar ?: ""

    @Composable
    override fun Content(env: Env) {
        val topic = Library.topic(topicId) ?: return
        @Suppress("UNUSED_VARIABLE") val tick = env.repo.tick
        var filter by remember { mutableStateOf(ItemFilter.ALL) }
        var showInfo by remember { mutableStateOf(false) }
        val all = Library.items.filter { it.topicId == topicId && it.kind == kind }
        val counts = env.repo.countByStatus(kind, topicId)
        val known = (counts[Status.MEMORIZED] ?: 0) + (counts[Status.MASTERED] ?: 0)
        val learning = (counts[Status.LEARNING] ?: 0) + (counts[Status.HARD] ?: 0)
        val fresh = counts[Status.NEW] ?: 0
        val color = topicColor(topic.num)
        val visible = all.filter { filter.accepts(env.repo.statusOf(it.id)) }

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
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(topic.emoji, fontSize = 40.sp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                topic.ar,
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                        Text(
                            "🟢 $known محفوظة   •   🟠 $learning بتتعلّم   •   ⚪ $fresh جديدة",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
                        ProgressBar(if (all.isEmpty()) 0f else known.toFloat() / all.size, color = Color.White)
                        Button(
                            onClick = { env.nav.push(ReviewScreen(kind, topicId)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text(if (kind == Kind.WORD) "▶️ ذاكر كلمات الموضوع" else "▶️ ذاكر جمل الموضوع", fontWeight = FontWeight.Bold) }
                    }
                }
            }
            item(key = "filters") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    for (f in ItemFilter.entries) {
                        Pill(f.label, color, filled = f == filter, onClick = { filter = f })
                    }
                    Spacer(Modifier.weight(1f))
                    Text("❓", Modifier.clickable { showInfo = true }.padding(8.dp), fontSize = 20.sp)
                }
            }
            if (visible.isEmpty()) {
                item(key = "empty") {
                    Text("مفيش عناصر في الفلتر ده دلوقتي.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            for (g in topic.groups) {
                val inGroup = visible.filter { it.groupId == g.id }
                if (inGroup.isEmpty()) continue
                item(key = "h-${g.id}") {
                    Column(Modifier.padding(top = 10.dp)) {
                        Text(
                            g.ar,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = color,
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
        if (showInfo) StatusInfoDialog { showInfo = false }
    }
}
