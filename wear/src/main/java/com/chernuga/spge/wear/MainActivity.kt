package com.chernuga.spge.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.chernuga.spge.shared.Lesson
import com.chernuga.spge.shared.Periods
import com.chernuga.spge.shared.ScheduleIndex
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScheduleRepository.loadCache(this)
        setContent { ScheduleApp() }
    }
}

/** A single row in the flattened schedule list. */
private sealed class ListRow {
    abstract val key: String

    data class Header(val day: Int) : ListRow() {
        override val key get() = "day-$day"
    }

    data class LessonRow(val lesson: Lesson) : ListRow() {
        override val key get() =
            "l-${lesson.day}-${lesson.start}-${lesson.subject}-${lesson.room}"
    }
}

private val TYPE_LABELS = mapOf(
    ScheduleIndex.Type.CLASS to "Клас",
    ScheduleIndex.Type.TEACHER to "Учител",
    ScheduleIndex.Type.ROOM to "Стая"
)

@Composable
fun ScheduleApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // One pre-built index. Rebuilding is the expensive part, so it happens only
    // when the data actually changes, never on a filter change or a scroll.
    var index by remember { mutableStateOf(ScheduleIndex(ScheduleRepository.lessons)) }
    var updatedAt by remember { mutableStateOf(ScheduleRepository.updatedAt) }
    var loading by remember { mutableStateOf(index.isEmpty()) }

    var type by remember { mutableStateOf(Preferences.loadType(context)) }
    var selected by remember {
        mutableStateOf(
            Preferences.loadName(context, Preferences.loadType(context))
        )
    }
    var todayIndex by remember { mutableStateOf(currentDayIndex()) }

    val revision by ScheduleRepository.revision.collectAsState()

    // Rebuild the index only when new data lands.
    LaunchedEffect(revision) {
        val rebuilt = ScheduleIndex(ScheduleRepository.lessons)
        index = rebuilt
        updatedAt = ScheduleRepository.updatedAt

        // Drop a selection that no longer exists (e.g. after a refresh), else
        // default to whatever has lessons today, else the first entry.
        val names = rebuilt.names(type)
        if (selected == null || !rebuilt.has(type, selected)) {
            selected = names.firstOrNull { n ->
                rebuilt.lessons(type, n).any { it.day == todayIndex }
            } ?: names.firstOrNull()
            Preferences.saveName(context, type, selected)
        }
        loading = false
    }

    val enroll: () -> Unit = {
        scope.launch {
            val ok = ScheduleRepository.requestRefresh(context)
            if (!ok) {
                android.widget.Toast.makeText(
                    context, "Телефонът не е свързан",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
        Unit
    }

    MaterialTheme {
        val listState = rememberScalingLazyListState()

        // The picker is hosted here, as a sibling of the schedule Scaffold,
        // rather than inside the list. A ScalingLazyColumn (and the Scaffold it
        // sits in) needs bounded height, and nesting one inside a lazy item
        // crashes with "height should be bounded".
        var pickerOpen by remember { mutableStateOf(false) }

        if (pickerOpen) {
            PickerDialog(
                names = index.names(type),
                selected = selected,
                onDismiss = { pickerOpen = false },
                onPick = { name ->
                    selected = name
                    Preferences.saveName(context, type, name)
                    pickerOpen = false
                }
            )
        } else {
            Scaffold(
                timeText = { TimeText() },
                positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
                vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
            ) {
                when {
                    loading -> LoadingView()

                    index.isEmpty() -> EmptyView(onRefresh = enroll)

                    else -> ScheduleScreen(
                        state = listState,
                        index = index,
                        type = type,
                        selected = selected,
                        onOpenPicker = { pickerOpen = true },
                        onSelectType = { newType ->
                            type = newType
                            Preferences.saveType(context, newType)
                            // Restore this dimension's last choice, else pick
                            // the first that has lessons today.
                            val remembered = Preferences.loadName(context, newType)
                            val names = index.names(newType)
                            selected = if (remembered != null && index.has(newType, remembered)) {
                                remembered
                            } else {
                                names.firstOrNull { n ->
                                    index.lessons(newType, n).any { it.day == todayIndex }
                                } ?: names.firstOrNull()
                            }
                            Preferences.saveName(context, newType, selected)
                        },
                        todayIndex = todayIndex,
                        updatedAt = updatedAt,
                        onRefresh = enroll
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyView(onRefresh: () -> Unit) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Няма график",
                color = Palette.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        item {
            Text(
                text = "Отвори приложението на телефона и натисни „Send to watch“",
                color = Palette.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }
        item {
            Chip(
                label = { Text("Провери пак", fontSize = 12.sp) },
                onClick = onRefresh,
                icon = {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = ChipDefaults.primaryChipColors()
            )
        }
    }
}

@Composable
private fun ScheduleScreen(
    state: ScalingLazyListState,
    index: ScheduleIndex,
    type: ScheduleIndex.Type,
    selected: String?,
    onOpenPicker: () -> Unit,
    onSelectType: (ScheduleIndex.Type) -> Unit,
    todayIndex: Int,
    updatedAt: Long,
    onRefresh: () -> Unit
) {
    // A lookup in the pre-built index, not a scan of every lesson.
    val visible = remember(index, type, selected) { index.lessons(type, selected) }

    // Flatten headers and lessons once per (index, type, selection) rather than
    // per recomposition. The `items` DSL is a LazyListScope, so it cannot be
    // driven by a forEach emitting a variable number of rows.
    val rows = remember(visible) {
        val out = ArrayList<ListRow>(visible.size + 8)
        var lastDay = -1
        for (l in visible) {
            if (l.day != lastDay) {
                lastDay = l.day
                out.add(ListRow.Header(l.day))
            }
            out.add(ListRow.LessonRow(l))
        }
        out
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = state,
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        // ---- Filter bar ----
        // Type selector: three small chips, echoing the web app's radio row.
        item(key = "types") {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                ScheduleIndex.Type.values().forEach { t ->
                    val active = t == type
                    Chip(
                        label = { Text(TYPE_LABELS[t] ?: t.name, fontSize = 9.sp) },
                        onClick = { if (!active) onSelectType(t) },
                        colors = if (active) ChipDefaults.primaryChipColors()
                        else ChipDefaults.secondaryChipColors(),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
        }

        // Current selection, and the way into the picker.
        item(key = "current") {
            PickerChip(
                label = selected ?: "—",
                names = index.names(type),
                onOpen = onOpenPicker
            )
        }

        if (visible.isEmpty()) {
            item(key = "none") {
                Text(
                    text = "Няма часове",
                    color = Palette.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
        }

        // wear-compose's `items` takes a count rather than a list.
        items(count = rows.size, key = { rows[it].key }) { i ->
            when (val row = rows[i]) {
                is ListRow.Header -> DayHeader(row.day, row.day == todayIndex)
                is ListRow.LessonRow ->
                    LessonCard(row.lesson, isCurrent = isLessonNow(row.lesson, todayIndex))
            }
        }

        item(key = "footer") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (updatedAt > 0) "Обновено " + formatTime(updatedAt) else "Няма данни",
                    color = Palette.TextMuted,
                    fontSize = 9.sp
                )
                Spacer(Modifier.height(4.dp))
                Chip(
                    label = { Text("Обнови", fontSize = 10.sp) },
                    onClick = onRefresh,
                    icon = {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.height(30.dp)
                )
            }
        }
    }
}

/**
 * Opens a full-screen picker for the current dimension.
 *
 * <p>A list is used instead of next/previous arrows because a school has far
 * too many classes, teachers and rooms to step through one at a time.
 */
/**
 * The selector chip. It only reports that the user wants to choose; the dialog
 * itself is hosted at the top level, not here.
 *
 * <p>Rendering the dialog from inside this chip would nest a Scaffold within
 * the schedule list's lazy item scope, which measures badly and crashes.
 */
@Composable
private fun PickerChip(
    label: String,
    names: List<String>,
    onOpen: () -> Unit
) {
    Chip(
        label = {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryLabel = if (names.size > 1) {
            { Text("${names.size} · избери", fontSize = 8.sp) }
        } else null,
        onClick = { if (names.size > 1) onOpen() },
        colors = ChipDefaults.secondaryChipColors()
    )
}

@Composable
private fun DayHeader(day: Int, isToday: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 1.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = Periods.dayName(day),
            color = if (isToday) Palette.Current else Palette.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        if (isToday) {
            Spacer(Modifier.width(4.dp))
            Text(text = "• денес", color = Palette.Current, fontSize = 9.sp)
        }
    }
}

@Composable
private fun LessonCard(lesson: Lesson, isCurrent: Boolean) {
    // Both of these are memoised in Palette/Periods, so repeated cards and
    // scroll frames do not repeat the colour maths or string building.
    val accent = Palette.readable(lesson.color)
    val timeRange = Periods.range(lesson.start, lesson.end)
    val details = lessonDetails(lesson)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        accent.copy(alpha = if (isCurrent) 0.30f else 0.16f),
                        Palette.CardBase
                    )
                )
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = lesson.subject,
                    color = Palette.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = timeRange,
                    color = if (isCurrent) Palette.Current else Palette.TextMuted,
                    fontSize = 9.sp,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = details,
                color = Palette.TextMuted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------- helpers ----------

private fun lessonDetails(lesson: Lesson): String = buildString {
    append("🚪 ").append(lesson.room)
    if (lesson.teacher.isNotEmpty()) append("  👤 ").append(lesson.teacher)
}

private fun currentDayIndex(): Int {
    // Calendar.MONDAY == 2, so shift to a 0-based Monday index.
    val i = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY
    return if (i < 0) 6 else i
}

private fun currentMinutes(): Int {
    val c = Calendar.getInstance()
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

/** True when the lesson is running today at the current wall-clock time. */
private fun isLessonNow(lesson: Lesson, todayIndex: Int): Boolean {
    if (lesson.day != todayIndex) return false
    val now = currentMinutes()
    val start = Periods.toMinutes(Periods.start(lesson.start))
    val end = Periods.toMinutes(Periods.end(lesson.end))
    if (start < 0 || end < 0) return false
    return now in start until end
}

private val TIME_FORMAT = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun formatTime(epochMillis: Long): String = TIME_FORMAT.format(Date(epochMillis))
