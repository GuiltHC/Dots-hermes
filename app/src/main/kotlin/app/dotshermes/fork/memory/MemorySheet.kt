package app.dotshermes.fork.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dotshermes.R
import app.dotshermes.ui.components.GroupedCard
import app.dotshermes.ui.components.Hairline
import app.dotshermes.ui.components.SectionLabel
import app.dotshermes.ui.theme.Ink
import app.dotshermes.ui.theme.LocalDotPalette

/** Строка-вход на экран памяти (в шапке «Активности»). */
@Composable
fun MemoryEntryRow(onOpen: () -> Unit) {
    val palette = LocalDotPalette.current
    GroupedCard(Modifier.padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(palette.light), contentAlignment = Alignment.Center) { Text("🧠") }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.memory_title), style = MaterialTheme.typography.labelLarge, color = Ink.Primary)
                Text(stringResource(R.string.memory_entry_hint), style = MaterialTheme.typography.labelSmall, color = Ink.Secondary)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Ink.Tertiary)
        }
    }
}

/** Что в редакторе: новая запись или правка существующей ([old]). */
private data class Editing(val target: MemoryTarget, val old: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorySheet(viewModel: MemoryViewModel, onDismiss: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalDotPalette.current
    var editing by rememberSaveable(stateSaver = EditingSaver) { mutableStateOf<Editing?>(null) }
    LaunchedEffect(Unit) { viewModel.load() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ink.Grouped,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Ink.Tertiary) },
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
            Text(stringResource(R.string.memory_title), style = MaterialTheme.typography.headlineSmall, color = Ink.Primary, modifier = Modifier.padding(start = 4.dp))
            Text(stringResource(R.string.memory_intro), style = MaterialTheme.typography.bodySmall, color = Ink.Secondary,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp))

            when {
                state.unsupported -> Notice(stringResource(R.string.memory_unsupported))
                state.offline && !state.loaded -> Notice(stringResource(R.string.memory_offline))
                !state.loaded -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = palette.base, strokeWidth = 2.dp)
                }
                else -> {
                    state.error?.let { Notice(stringResource(R.string.memory_error, it), danger = true) }
                    MemoryTarget.entries.forEach { target ->
                        Section(state, target, onOpen = { old -> viewModel.clearError(); editing = Editing(target, old) })
                    }
                }
            }
        }
    }

    editing?.let { e ->
        EntryDialog(
            initial = e.old.orEmpty(),
            isNew = e.old == null,
            saving = state.saving,
            onSave = { text ->
                if (e.old == null) viewModel.add(e.target, text) else viewModel.replace(e.target, e.old, text)
                editing = null
            },
            onDelete = e.old?.let { old -> { viewModel.remove(e.target, old); editing = null } },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun Section(state: MemoryState, target: MemoryTarget, onOpen: (String?) -> Unit) {
    val palette = LocalDotPalette.current
    val entries = state.entries(target)
    val limit = state.limits[target]
    val used = state.used(target)
    SectionLabel(stringResource(if (target == MemoryTarget.User) R.string.memory_user else R.string.memory_agent))
    GroupedCard {
        if (limit != null) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                Text(stringResource(R.string.memory_usage, used, limit), style = MaterialTheme.typography.labelSmall,
                    color = if (used > limit * 0.9) Ink.Warning else Ink.Secondary)
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(Ink.Fill)) {
                    Box(Modifier.fillMaxWidth((used.toFloat() / limit).coerceIn(0f, 1f)).height(4.dp).clip(CircleShape).background(palette.base))
                }
            }
        }
        if (entries.isEmpty()) {
            Text(stringResource(R.string.memory_empty), style = MaterialTheme.typography.bodySmall, color = Ink.Secondary, modifier = Modifier.padding(16.dp))
        }
        entries.forEachIndexed { i, entry ->
            if (i > 0) Hairline()
            Text(
                entry, style = MaterialTheme.typography.bodyMedium, color = Ink.Primary, maxLines = 6, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clickable { onOpen(entry) }.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        Hairline(startIndent = 0.dp)
        Row(Modifier.fillMaxWidth().clickable { onOpen(null) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Add, null, tint = palette.deep, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.memory_add), style = MaterialTheme.typography.labelLarge, color = palette.deep)
        }
    }
}

@Composable
private fun Notice(text: String, danger: Boolean = false) {
    GroupedCard(Modifier.padding(top = 16.dp)) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (danger) Ink.Danger else Ink.Secondary, modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun EntryDialog(initial: String, isNew: Boolean, saving: Boolean, onSave: (String) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    val palette = LocalDotPalette.current
    var text by rememberSaveable { mutableStateOf(initial) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.memory_delete_title)) },
            text = { Text(initial, maxLines = 6, overflow = TextOverflow.Ellipsis) },
            confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.memory_delete), color = Ink.Danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.memory_new else R.string.memory_edit)) },
        text = {
            Box(Modifier.fillMaxWidth().heightIn(min = 120.dp).clip(RoundedCornerShape(12.dp)).background(Ink.Fill).padding(12.dp)) {
                BasicTextField(
                    value = text, onValueChange = { text = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink.Primary),
                    cursorBrush = SolidColor(palette.base), modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            val canSave = !saving && text.isNotBlank() && text.trim() != initial.trim()
            TextButton(onClick = { onSave(text) }, enabled = canSave) {
                Text(stringResource(R.string.dot_save), color = if (canSave) palette.deep else Ink.Tertiary)
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.memory_delete), color = Ink.Danger) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

private val EditingSaver = androidx.compose.runtime.saveable.listSaver<Editing?, String>(
    save = { e -> if (e == null) emptyList() else listOf(e.target.name, e.old ?: "\u0000") },
    restore = { l -> if (l.isEmpty()) null else Editing(MemoryTarget.valueOf(l[0]), l[1].takeIf { it != "\u0000" }) },
)
