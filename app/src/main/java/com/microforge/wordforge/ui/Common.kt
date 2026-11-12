package com.microforge.wordforge.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microforge.wordforge.data.Item
import com.microforge.wordforge.data.Kind
import com.microforge.wordforge.data.Srs
import com.microforge.wordforge.data.Status
import kotlinx.coroutines.delay

/** English text inside an RTL app: always left-to-right, left-aligned unless [align] says otherwise. */
@Composable
fun En(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    align: TextAlign = TextAlign.Left,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontWeight = fontWeight,
        textAlign = align,
        style = style.copy(textDirection = TextDirection.Ltr),
    )
}

@Composable
fun En(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    align: TextAlign = TextAlign.Left,
) {
    En(AnnotatedString(text), modifier, style, color, fontWeight, align)
}

// ---------------------------------------------------------------- animations

/** Fades + slides its content in, [index] staggers the start so lists cascade in. */
@Composable
fun Appear(index: Int = 0, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(minOf(index, 8) * 45L)
        shown = true
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 5 },
    ) {
        content()
    }
}

/** Scales its content in with a springy bounce. */
@Composable
fun PopIn(content: @Composable () -> Unit) {
    val s = remember { Animatable(0f) }
    LaunchedEffect(Unit) { s.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f)) }
    Box(Modifier.graphicsLayer {
        scaleX = s.value
        scaleY = s.value
    }) { content() }
}

/** A bar that grows from empty to [fraction] when it first appears and animates on every change. */
@Composable
fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 8.dp,
) {
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(fraction) { target = fraction.coerceIn(0f, 1f) }
    val shown by animateFloatAsState(target, tween(700), label = "bar")
    LinearProgressIndicator(
        progress = { shown },
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2)),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
    )
}

/** A circular progress ring with [content] in the middle. */
@Composable
fun ProgressRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = 72.dp,
    stroke: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable () -> Unit = {},
) {
    var target by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(fraction) { target = fraction.coerceIn(0f, 1f) }
    val shown by animateFloatAsState(target, tween(900), label = "ring")
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val topLeft = Offset(s / 2, s / 2)
            val arc = Size(this.size.width - s, this.size.height - s)
            drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(s, cap = StrokeCap.Round))
            if (shown > 0.005f) {
                drawArc(color, -90f, 360f * shown, false, topLeft, arc, style = Stroke(s, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

// ---------------------------------------------------------------- small widgets

@Composable
fun Pill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val bg = if (filled) color else color.copy(alpha = 0.14f)
    val fg = if (filled) Color.White else color
    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(clickMod)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

fun statusColor(s: Status): Color = when (s) {
    Status.NEW -> Color(0xFF9AA5A0)
    Status.HARD -> Coral
    Status.LEARNING -> Amber
    Status.MEMORIZED -> Good
    Status.MASTERED -> Color(0xFFB8860B)
}

@Composable
fun StatusPill(s: Status, modifier: Modifier = Modifier) {
    Pill("${s.emoji} ${s.label}", statusColor(s), modifier, filled = false)
}

/** A stacked bar showing how many items are in each status. */
@Composable
fun StatusBar(counts: Map<Status, Int>, modifier: Modifier = Modifier) {
    val total = counts.values.sum()
    Row(
        modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (total > 0) {
            for (st in listOf(Status.MASTERED, Status.MEMORIZED, Status.LEARNING, Status.HARD, Status.NEW)) {
                val n = counts[st] ?: 0
                if (n > 0) Box(
                    Modifier
                        .weight(n.toFloat())
                        .fillMaxHeight()
                        .background(statusColor(st))
                )
            }
        }
    }
}

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            content()
        }
    }
}

/** Round "play sound" button that bounces when tapped. */
@Composable
fun SoundButton(onClick: () -> Unit, modifier: Modifier = Modifier, diameter: Dp = 40.dp) {
    var bump by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (bump) 1.3f else 1f, spring(dampingRatio = 0.4f), label = "sound")
    LaunchedEffect(bump) {
        if (bump) {
            delay(160)
            bump = false
        }
    }
    Box(
        modifier
            .size(diameter)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable {
                bump = true
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("🔊", fontSize = (diameter.value * 0.48f).sp)
    }
}

/** Explains exactly what each status means, so "heard it" is never mistaken for "memorized it". */
@Composable
fun StatusInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("تمام") } },
        title = { Text("إزاي أعرف إني حفظت الكلمة؟", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("سماع الكلمة أو تصفحها لوحده مبيغيّرش حالتها. الحالة بتتغيّر بس لما تجاوب:")
                Text("⚪ جديدة: لسه ما جاوبتش عليها.")
                Text("🔴 صعبة: غلطت فيها، هتيجيلك تاني بعد 10 دقايق.")
                Text("🟠 بتتعلّم: جاوبت صح بس لسه بدري (أول 3 مرات).")
                Text("🟢 محفوظة: افتكرتها صح 3 مرات على فترات متباعدة (يوم، يومين، 4 أيام).")
                Text("🏆 متقنة: افتكرتها صح 5 مرات متباعدة.")
                Text(
                    "ملحوظة: لو جاوبت صح قبل معاد مراجعتها مفيش تقدّم، عشان الحفظ يبقى حقيقي.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

// ---------------------------------------------------------------- item row

/** A word or sentence row: status stripe, status text with next review, sound + star buttons. */
@Composable
fun ItemRow(item: Item, env: Env, index: Int = 0) {
    val p = env.repo.progress[item.id]
    val status = Srs.statusOf(p)
    val starred = p?.starred == true
    val now = System.currentTimeMillis()
    val statusLine = when {
        status == Status.NEW -> "${status.emoji} ${status.label}"
        p != null && Srs.isEarly(p, now) -> "${status.emoji} ${status.label} · المراجعة ${Srs.dueText(p.due, now)}"
        else -> "${status.emoji} ${status.label} · جاهزة للمراجعة"
    }
    Appear(index) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(6.dp)
                        .background(statusColor(status))
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    val head = buildAnnotatedString {
                        append(item.en)
                        if (item.pos.isNotEmpty()) {
                            withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = Color(0xFF8A94A0))) {
                                append("   ${item.pos}")
                            }
                        }
                    }
                    En(
                        head,
                        Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(item.ar, style = MaterialTheme.typography.bodyLarge)
                    if (item.kind == Kind.WORD && item.example.isNotEmpty()) {
                        En(
                            item.example,
                            Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        statusLine,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor(status),
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SoundButton(onClick = { env.speaker.say(item.en) })
                    Text(
                        if (starred) "⭐" else "☆",
                        Modifier
                            .clickable { env.repo.toggleStar(item.id) }
                            .padding(6.dp),
                        fontSize = 20.sp,
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}
