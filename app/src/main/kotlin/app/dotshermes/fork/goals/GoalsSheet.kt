package app.dotshermes.fork.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.dotshermes.R
import app.dotshermes.data.model.ActivitySnapshot
import app.dotshermes.data.model.CronAction
import app.dotshermes.data.model.CronJob
import app.dotshermes.ui.activity.humanSchedule
import app.dotshermes.ui.activity.relative
import app.dotshermes.ui.components.GroupedCard
import app.dotshermes.ui.components.Hairline
import app.dotshermes.ui.components.PillButton
import app.dotshermes.ui.components.SectionLabel
import app.dotshermes.ui.theme.Ink
import app.dotshermes.ui.theme.LocalDotPalette
import kotlinx.coroutines.delay
import java.time.Instant

/** Строка-вход на экран целей (в шапке «Активности»). */
@Composable
fun GoalsEntryRow(snapshot: ActivitySnapshot?, onOpen: () -> Unit) {
    val palette = LocalDotPalette.current
    val goals = snapshot?.crons.orEmpty().filter(Goals::isGoal)
    val active = goals.count { !it.finished && !it.paused }
    GroupedCard(Modifier.padding(top = 16.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(palette.light), contentAlignment = Alignment.Center) {
                Text(Goals.NAME_MARK)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.goals_title), style = MaterialTheme.typography.labelLarge, color = Ink.Primary)
                Text(
                    if (goals.isEmpty()) stringResource(R.string.goals_entry_empty)
                    else pluralStringResource(R.plurals.goals_active, active, active),
                    style = MaterialTheme.typography.labelSmall, color = Ink.Secondary,
                )
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Ink.Tertiary)
        }
    }
}

/**
 * Экран «Цели»: список фоновых целей (задачи Hermes с меткой 🎯) и форма новой цели.
 * [onSendToMain] отправляет сообщение агенту в главную переписку.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GoalsSheet(
    snapshot: ActivitySnapshot?,
    onRefresh: () -> Unit,
    onCron: (String, CronAction) -> Unit,
    onSendToMain: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) {
        while (true) {
            onRefresh()
            delay(15_000)
        }
    }
    val palette = LocalDotPalette.current
    var goal by rememberSaveable { mutableStateOf("") }
    var frequency by rememberSaveable { mutableStateOf(GoalFrequency.Daily) }
    var onlyFindings by rememberSaveable { mutableStateOf(true) }
    var sent by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    val goals = snapshot?.crons.orEmpty().filter(Goals::isGoal)
        .sortedWith(compareBy<CronJob> { it.finished }.thenBy { it.nextRunAt ?: Instant.MAX })

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ink.Grouped,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Ink.Tertiary) },
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
            Text(stringResource(R.string.goals_title), style = MaterialTheme.typography.headlineSmall, color = Ink.Primary, modifier = Modifier.padding(start = 4.dp))
            Text(stringResource(R.string.goals_intro), style = MaterialTheme.typography.bodySmall, color = Ink.Secondary,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp))

            SectionLabel(stringResource(R.string.goals_current))
            GroupedCard {
                when {
                    snapshot == null -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = palette.base, strokeWidth = 2.dp)
                    }
                    goals.isEmpty() -> Text(stringResource(R.string.goals_none), style = MaterialTheme.typography.bodySmall,
                        color = Ink.Secondary, modifier = Modifier.padding(16.dp))
                    else -> goals.forEachIndexed { i, job ->
                        if (i > 0) Hairline()
                        GoalRow(job, onCron, onDelete = { deleting = job.id })
                    }
                }
            }

            SectionLabel(stringResource(R.string.goals_new))
            GroupedCard {
                Column(Modifier.padding(16.dp)) {
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 88.dp).clip(RoundedCornerShape(12.dp)).background(Ink.Fill).padding(12.dp),
                    ) {
                        if (goal.isEmpty()) {
                            Text(stringResource(R.string.goals_hint), style = MaterialTheme.typography.bodyMedium, color = Ink.Secondary)
                        }
                        BasicTextField(
                            value = goal,
                            onValueChange = {
                                goal = it
                                sent = false
                            },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink.Primary),
                            cursorBrush = SolidColor(palette.base),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(stringResource(R.string.goals_frequency), style = MaterialTheme.typography.labelMedium, color = Ink.Secondary,
                        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GoalFrequency.entries.forEach { f ->
                            val selected = f == frequency
                            Box(
                                Modifier.clip(CircleShape)
                                    .background(if (selected) palette.base else Ink.Fill)
                                    .border(0.dp, Color.Transparent, CircleShape)
                                    .clickable { frequency = f }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(frequencyLabel(f), style = MaterialTheme.typography.labelLarge, color = if (selected) palette.onBase else Ink.Primary)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.goals_only_findings), style = MaterialTheme.typography.labelLarge, color = Ink.Primary)
                            Text(stringResource(R.string.goals_only_findings_hint), style = MaterialTheme.typography.labelSmall, color = Ink.Secondary)
                        }
                        Switch(
                            checked = onlyFindings, onCheckedChange = { onlyFindings = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = palette.base, checkedThumbColor = Color.White),
                        )
                    }
                    Spacer(Modifier.size(16.dp))
                    PillButton(
                        text = stringResource(if (sent) R.string.goals_sent else R.string.goals_create),
                        onClick = {
                            onSendToMain(Goals.createMessage(goal, frequency.schedule, onlyFindings))
                            goal = ""
                            sent = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        container = palette.base, content = palette.onBase,
                        enabled = goal.isNotBlank(),
                    )
                    if (sent) {
                        Text(stringResource(R.string.goals_sent_hint), style = MaterialTheme.typography.labelSmall, color = Ink.Secondary,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }

    val toDelete = goals.firstOrNull { it.id == deleting }
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.goals_delete_title, Goals.title(toDelete))) },
            text = { Text(stringResource(R.string.goals_delete_text)) },
            confirmButton = {
                TextButton(onClick = {
                    onSendToMain(Goals.deleteMessage(toDelete))
                    deleting = null
                }) { Text(stringResource(R.string.goals_delete_confirm), color = Ink.Danger) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun GoalRow(job: CronJob, onCron: (String, CronAction) -> Unit, onDelete: () -> Unit) {
    val palette = LocalDotPalette.current
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        val statusColor = when {
            job.lastStatus == null -> Ink.Tertiary
            job.lastStatus == "ok" -> Ink.Success
            else -> Ink.Danger
        }
        Box(Modifier.size(8.dp).background(statusColor, CircleShape))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(Goals.title(job), style = MaterialTheme.typography.labelLarge, color = if (job.finished) Ink.Secondary else Ink.Primary,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(goalSubtitle(job), style = MaterialTheme.typography.labelSmall, color = Ink.Secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (!job.finished) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(palette.light).clickable { onCron(job.id, CronAction.Run) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.PlayArrow, stringResource(R.string.cron_run_now), tint = palette.deep, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(4.dp))
            Switch(
                checked = !job.paused,
                onCheckedChange = { on -> onCron(job.id, if (on) CronAction.Resume else CronAction.Pause) },
                colors = SwitchDefaults.colors(checkedTrackColor = palette.base, checkedThumbColor = Color.White),
            )
        }
        Box(Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onDelete), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Delete, stringResource(R.string.goals_delete_confirm), tint = Ink.Tertiary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun goalSubtitle(job: CronJob): String {
    val last = job.lastRunAt?.let { stringResource(R.string.goals_last_check, relative(it)) }
    return when {
        job.finished -> listOfNotNull(stringResource(R.string.cron_finished), last).joinToString(" · ")
        job.paused -> listOf(stringResource(R.string.cron_paused), goalSchedule(job.schedule)).joinToString(" · ")
        else -> listOfNotNull(goalSchedule(job.schedule), job.nextRunAt?.let { stringResource(R.string.cron_next, relative(it)) }, last)
            .joinToString(" · ")
    }
}

/** Расписание по-человечески: «every 180m» → «каждые 3 ч», cron «0 9 * * *» → «каждый день в 9:00». */
@Composable
private fun goalSchedule(raw: String): String {
    Regex("""every (\d+)m""").matchEntire(raw.trim())?.let { m ->
        val minutes = m.groupValues[1].toInt()
        if (minutes >= 60 && minutes % 60 == 0) return humanSchedule("every ${minutes / 60}h")
    }
    Regex("""(\d{1,2}) (\d{1,2}) \* \* (\*|[0-6])""").matchEntire(raw.trim())?.let { m ->
        val time = "%d:%02d".format(m.groupValues[2].toInt(), m.groupValues[1].toInt())
        return if (m.groupValues[3] == "*") stringResource(R.string.goals_daily_at, time)
        else stringResource(R.string.goals_weekly_at, time)
    }
    return humanSchedule(raw)
}

@Composable
private fun frequencyLabel(f: GoalFrequency): String = stringResource(
    when (f) {
        GoalFrequency.Hourly -> R.string.goals_freq_hourly
        GoalFrequency.Every3h -> R.string.goals_freq_3h
        GoalFrequency.Daily -> R.string.goals_freq_daily
        GoalFrequency.Weekly -> R.string.goals_freq_weekly
    },
)
