@file:OptIn(ExperimentalMaterial3Api::class)

package com.skippy.app

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List as ListIcon
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.rotate
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults

import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters

private val Green = Color(0xFF2E7D32)
private val Amber = Color(0xFFEF6C00)
private val Red = Color(0xFFC62828)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3B5BDB),
    onPrimary = Color.White,
    secondaryContainer = Color(0xFFDDE4FF),
    background = Color(0xFFFAFAFD),
    surface = Color(0xFFFAFAFD),
)

/** Light theme only, whatever the system setting. */
@Composable
fun SkippyTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = LightColors, content = content)

// ---------------------------------------------------------------------------------------------
// Root
// ---------------------------------------------------------------------------------------------

@Composable
fun App(vm: AppViewModel) {
    val ui by vm.ui.collectAsState()
    val details by vm.details.collectAsState()
    val snack = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showWebView by rememberSaveable { mutableStateOf(false) }
    val onSignIn = { showWebView = true }
    val ready = ui.settings.groupIds.isNotEmpty() && ui.settings.authMode.isNotEmpty()

    // Refresh "now" every minute so sessions flip from upcoming to past while the app is open.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            vm.reload()
        }
    }
    LaunchedEffect(ui.message) {
        ui.message?.let {
            snack.showSnackbar(it)
            vm.clearMessage()
        }
    }

    val tabs = listOf(
        Icons.Default.Home to R.string.tab_week,
        Icons.AutoMirrored.Filled.ListIcon to R.string.tab_subjects,
        Icons.Default.DateRange to R.string.tab_sessions,
        Icons.Default.Settings to R.string.tab_settings,
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (ready) NavigationBar {
                tabs.forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (!ready) SetupScreen(ui, vm, onSignIn)
            else when (tab) {
                0 -> WeekScheduleScreen(ui, vm)
                1 -> SubjectsScreen(ui, vm)
                2 -> SessionsScreen(ui, vm)
                else -> SettingsScreen(ui, vm, onSignIn)
            }
            if (ui.syncing) {
                val p = ui.progress
                if (p != null && p.second > 0) {
                    LinearProgressIndicator(
                        progress = { p.first / p.second.toFloat() },
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
            }
        }
    }
    details?.let { DetailsDialog(it) { vm.closeDetails() } }
    if (showWebView) {
        MicrosoftAuthDialog(
            onTokenReceived = { token ->
                vm.saveManualToken(token)
                showWebView = false
            },
            onDismiss = { showWebView = false }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Setup + authentication
// ---------------------------------------------------------------------------------------------

@Composable
fun SetupScreen(ui: UiState, vm: AppViewModel, onSignIn: () -> Unit) {
    var group by remember { mutableStateOf(ui.settings.groupIds.firstOrNull()?.toString() ?: "") }
    var rentree by remember { mutableStateOf(ui.settings.rentree) }
    val saveFields = {
        val parsed = group.toIntOrNull()
        vm.saveSettings(ui.settings.copy(groupIds = if (parsed != null) setOf(parsed) else ui.settings.groupIds, rentree = rentree))
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.setup_body), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = group,
            onValueChange = { group = it.filter(Char::isDigit).take(8) },
            label = { Text(stringResource(R.string.group_id)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = rentree,
            onValueChange = { rentree = it },
            label = { Text(stringResource(R.string.rentree)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        AuthSection(ui, vm, before = saveFields, onSignIn = onSignIn)
    }
}

@Composable
fun AuthSection(ui: UiState, vm: AppViewModel, before: () -> Unit = {}, onSignIn: () -> Unit) {
    var token by remember { mutableStateOf("") }
    var debugToken by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(
                when (ui.settings.authMode) {
                    "msal" -> R.string.auth_msal
                    "manual" -> R.string.auth_manual
                    else -> R.string.auth_none
                }
            ),
            style = MaterialTheme.typography.labelLarge,
        )
        Button(onClick = { before(); onSignIn() }) { Text(stringResource(R.string.sign_in)) }
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text(stringResource(R.string.token_paste)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = { before(); vm.saveManualToken(token); token = "" },
            enabled = token.isNotBlank(),
        ) { Text(stringResource(R.string.save_token)) }
        if (ui.settings.authMode.isNotEmpty()) {
            TextButton(onClick = { vm.signOut() }) { Text(stringResource(R.string.sign_out)) }
            TextButton(onClick = { debugToken = TokenStore.load(ctx) ?: "None" }) { Text("Debug Token") }
        }
    }
    debugToken?.let { tok ->
        AlertDialog(
            onDismissRequest = { debugToken = null },
            confirmButton = { TextButton(onClick = { debugToken = null }) { Text("OK") } },
            dismissButton = {
                TextButton(onClick = {
                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(tok))
                }) { Text("Copier") }
            },
            title = { Text("Debug Token") },
            text = { Text(tok) }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Today
// ---------------------------------------------------------------------------------------------

private val shortDateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
private val fullDateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

/** One day of a week page, computed once per page instead of on every recomposition. */
private class WeekDay(
    val day: LocalDate,
    val sessions: List<Session>,
    val items: Map<String, TodayItem>,
    val recos: List<TodayItem>,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeekScheduleScreen(ui: UiState, vm: AppViewModel) {
    val initialPage = Int.MAX_VALUE / 2
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { Int.MAX_VALUE })
    
    val today = remember { LocalDate.now() }
    val currentWeekMonday = remember { today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    
    Column(Modifier.fillMaxSize()) {
        
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 2
        ) { page ->
            val pageMonday = currentWeekMonday.plusWeeks((page - initialPage).toLong())
            val title = remember(pageMonday) {
                "${pageMonday.format(shortDateFmt)} - ${pageMonday.plusDays(6).format(shortDateFmt)}"
            }
            val days = remember(pageMonday, ui.sessionsByDay, ui.cachedSchedules, ui.att, ui.statsByKey, ui.settings, ui.now) {
                (0..6).map { pageMonday.plusDays(it.toLong()) }
                    .mapNotNull { day ->
                        val daySessions = ui.sessionsByDay[day].orEmpty()
                        if (daySessions.isEmpty() && day != today) return@mapNotNull null
                        val items = ui.cachedSchedules?.get(day)
                            ?: Stats.forDay(daySessions, ui.att, ui.statsByKey, ui.settings, ui.now)
                        WeekDay(day, daySessions, items.associateBy { it.session.uid }, items.filter { it.badge == Badge.SKIP_SUGGESTED })
                    }
            }
            
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "title", contentType = "title") {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                days.forEach { wd ->
                    item(key = "day-${wd.day}", contentType = "dayHeader") {
                        Text(
                            wd.day.format(fullDateFmt),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    
                    if (wd.recos.isNotEmpty()) {
                        item(key = "reco-${wd.day}", contentType = "reco") {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFE8F5E9)
                                )
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(stringResource(R.string.reco_title), style = MaterialTheme.typography.titleMedium)
                                    wd.recos.forEach {
                                        Text(
                                            stringResource(
                                                R.string.reco_skip,
                                                "${it.session.subject} · ${typeName(ui.typeNames, it.session.typeId)}",
                                                fmtTime(it.session.start),
                                            ),
                                            fontWeight = FontWeight.SemiBold,
                                            color = Green,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    if (wd.sessions.isEmpty()) {
                        item(key = "empty-${wd.day}", contentType = "empty") { Text(stringResource(R.string.no_sessions_today)) }
                    } else {
                        val color = colorForDay(wd.day.dayOfWeek)
                        items(wd.sessions, key = { it.uid }, contentType = { "session" }) { s ->
                            WeekSessionCard(
                                s = s,
                                typeLabel = typeName(ui.typeNames, s.typeId),
                                status = ui.att[s.uid],
                                badge = wd.items[s.uid]?.badge,
                                color = color,
                                showStatusChips = ui.statsByKey.containsKey(Stats.key(s)),
                                onDetails = { vm.showDetails(s.uid) },
                                onSet = { vm.setStatus(s.uid, it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun colorForDay(dayOfWeek: DayOfWeek): Color = when (dayOfWeek) {
    DayOfWeek.MONDAY -> Color(0xFFEF6C00) // Orange
    DayOfWeek.TUESDAY -> Color(0xFF2E7D32) // Vert
    DayOfWeek.WEDNESDAY -> Color(0xFF1565C0) // Bleu
    DayOfWeek.THURSDAY -> Color(0xFF00838F) // Cyan
    DayOfWeek.FRIDAY -> Color(0xFF6A1B9A) // Violet
    DayOfWeek.SATURDAY -> Color(0xFFC62828) // Rouge
    DayOfWeek.SUNDAY -> Color(0xFF424242) // Gris
}

@Composable
fun WeekSessionCard(
    s: Session,
    typeLabel: String,
    status: Status?,
    badge: Badge?,
    color: Color,
    showStatusChips: Boolean = true,
    onDetails: () -> Unit,
    onSet: (Status?) -> Unit,
) {
    Card(onClick = onDetails, modifier = Modifier.fillMaxWidth()) {
        // The colored time strip is painted behind the row: no intrinsic measurement needed.
        val stripWidth = 64.dp
        Row(
            Modifier.drawBehind { drawRect(color, size = Size(stripWidth.toPx(), size.height)) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .width(stripWidth)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(fmtTime(s.start), color = Color.White, fontWeight = FontWeight.Bold)
                Text(fmtTime(s.end), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.padding(12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.subject, style = MaterialTheme.typography.titleMedium)
                val place = if (s.location.isNotBlank()) s.location else if (s.online) stringResource(R.string.online) else ""
                Text(
                    listOf(typeLabel, s.groups, place).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                )
                badge?.let { b ->
                    val (label, badgeColor) = when (b) {
                        Badge.SKIP_SUGGESTED -> R.string.badge_skip to Green
                        Badge.CAN_SKIP -> R.string.badge_can to Green
                        Badge.ATTEND -> R.string.badge_attend to Amber
                        Badge.IMPORTANT -> R.string.badge_important to MaterialTheme.colorScheme.primary
                        Badge.MUST_ATTEND -> R.string.badge_must_attend to Red
                    }
                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = badgeColor, fontWeight = FontWeight.Bold)
                }
                if (showStatusChips) {
                    StatusChips(status, onSet)
                }
            }
        }
    }
}

@Composable
fun SessionCard(
    s: Session,
    typeLabel: String,
    status: Status?,
    badge: Badge?,
    showDate: Boolean,
    showStatusChips: Boolean = true,
    onDetails: () -> Unit,
    onSet: (Status?) -> Unit,
) {
    Card(onClick = onDetails, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (showDate) fmtDate(s.start) + "  " else "") + "${fmtTime(s.start)}–${fmtTime(s.end)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Text(s.subject, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            val place = if (s.location.isNotBlank()) s.location else if (s.online) stringResource(R.string.online) else ""
            Text(
                listOf(typeLabel, s.groups, place).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            badge?.let { b ->
                val (label, color) = when (b) {
                    Badge.SKIP_SUGGESTED -> R.string.badge_skip to Green
                    Badge.CAN_SKIP -> R.string.badge_can to Green
                    Badge.ATTEND -> R.string.badge_attend to Amber
                    Badge.IMPORTANT -> R.string.badge_important to MaterialTheme.colorScheme.primary
                    Badge.MUST_ATTEND -> R.string.badge_must_attend to Red
                }
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
            }
            if (showStatusChips) {
                StatusChips(status, onSet)
            }
        }
    }
}

private val STATUS_OPTIONS = listOf(
    Status.PRESENT to R.string.status_present,
    Status.ABSENT to R.string.status_absent,
    Status.JUSTIFIED to R.string.status_justified,
)

@Composable
fun StatusChips(current: Status?, onSet: (Status?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        STATUS_OPTIONS.forEach { (st, label) ->
            FilterChip(
                selected = current == st,
                onClick = { onSet(if (current == st) null else st) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

@Composable
fun DetailsDialog(st: DetailsState, onClose: () -> Unit) {
    val d = st.details
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } },
        title = { Text(d?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.details_btn)) },
        text = {
            when {
                st.loading -> CircularProgressIndicator()
                st.error != null -> Text(st.error)
                d != null -> Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DetailLine(R.string.details_time, "${fmtDate(d.start)} ${fmtTime(d.start)}–${fmtTime(d.end)}")
                    DetailLine(R.string.details_code, d.code)
                    DetailLine(R.string.details_rooms, d.rooms.joinToString(", "))
                    DetailLine(R.string.details_teachers, d.teachers.joinToString(", "))
                    DetailLine(R.string.details_comment, d.comment)
                    DetailLine(R.string.details_link, d.url)
                }
                else -> {}
            }
        },
    )
}

@Composable
private fun DetailLine(label: Int, value: String) {
    if (value.isBlank()) return
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value)
    }
}

// ---------------------------------------------------------------------------------------------
// Subjects (each subject, then each activity type)
// ---------------------------------------------------------------------------------------------

@Composable
fun SubjectsScreen(ui: UiState, vm: AppViewModel) {
    val ctx = LocalContext.current
    val groups = remember(ui.stats) { ui.stats.groupBy { it.subject }.toList() }
    val examStarts = remember(ui.allRawSessions, ui.typeNames) {
        Stats.examAllStartTimes(ui.allRawSessions, ui.typeNames)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.rule_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (ui.alerts.isNotEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFDECEA))) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.alerts_title), style = MaterialTheme.typography.titleMedium, color = Red)
                    ui.alerts.forEach { Text(Notif.warnText(ctx, it, ui.settings.requiredPct)) }
                }
            }
        }
        if (ui.excludedSubjects.isNotEmpty()) item {
            ExcludedSubjectsCard(ui.excludedSubjects.sorted()) { vm.setSubjectExcluded(it, false) }
        }
        if (ui.hiddenSubjects.isNotEmpty()) item {
            HiddenSubjectsCard(ui.hiddenSubjects.sorted()) { vm.setSubjectHidden(it, false) }
        }
        items(groups, key = { it.first }) { (subject, rows) ->
            val origSubjs = rows.firstOrNull()?.originalSubjects?.ifEmpty { listOf(subject) } ?: listOf(subject)
            SubjectCard(
                subject = subject,
                rows = rows,
                availableExams = ui.availableExams,
                examMappings = ui.examMappings,
                examStarts = examStarts,
                onPref = { pref -> origSubjs.forEach { vm.setPref(it, pref) } },
                onExclude = { origSubjs.forEach { vm.setSubjectExcluded(it, true) } },
                onHide = { origSubjs.forEach { vm.setSubjectHidden(it, true) } },
                onExamMappingChanged = { selected -> (origSubjs + subject).distinct().forEach { vm.setExamMappings(it, selected) } },
                requiredPct = ui.settings.requiredPct,
            )
        }
    }
}

@Composable
fun SubjectCard(
    subject: String,
    rows: List<SubjectStats>,
    availableExams: List<String>,
    examMappings: Map<String, List<String>> = emptyMap(),
    examStarts: Map<String, List<Long>> = emptyMap(),
    onPref: (Int) -> Unit,
    onExclude: () -> Unit,
    onHide: () -> Unit,
    onExamMappingChanged: (List<String>) -> Unit,
    requiredPct: Int,
) {
    var showDialog by remember { mutableStateOf(false) }
    val first = rows.first()
    val origSubjs = first.originalSubjects
    val associatedExams = first.associatedExams
    val targetExam = first.targetExam
    var selectedExamLabel by remember(targetExam?.indexedLabel) {
        mutableStateOf(targetExam?.indexedLabel)
    }
    val activeExam = remember(associatedExams, selectedExamLabel, targetExam) {
        if (selectedExamLabel != null) {
            associatedExams.firstOrNull { it.indexedLabel == selectedExamLabel } ?: targetExam
        } else {
            null
        }
    }
    val displayRows = remember(rows, activeExam, targetExam) {
        if (activeExam != null && activeExam != targetExam) {
            rows.map { it.forExam(activeExam) }
        } else {
            rows
        }
    }
    val totalSt = remember(displayRows, requiredPct) { Stats.totalStats(displayRows, requiredPct) }
    val currentMapped = remember(subject, origSubjs, examMappings, availableExams) {
        val allKeys = (origSubjs + subject).distinct()
        val exams = allKeys.flatMap { ExamMatch.resolveExams(it, examMappings, availableExams).first }.distinct()
        val manual = allKeys.any { ExamMatch.resolveExams(it, examMappings, availableExams).second }
        exams to manual
    }
    val isManual = currentMapped.second

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(subject, style = MaterialTheme.typography.titleMedium)
            if (origSubjs.size > 1) {
                Text(
                    stringResource(R.string.merged_from_subjects, origSubjs.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onExclude,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(stringResource(R.string.exclude_subject), style = MaterialTheme.typography.labelSmall)
                }
                TextButton(
                    onClick = onHide,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(stringResource(R.string.hide_subject), style = MaterialTheme.typography.labelSmall)
                }
            }

            // Associated exams display with indexing and target highlight
            if (associatedExams.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        associatedExams.forEach { ex ->
                            val isTarget = ex.isNext || ex == targetExam
                            val isSelected = ex.indexedLabel == activeExam?.indexedLabel
                            val dateLabel = if (ex.start == Long.MAX_VALUE) {
                                " (${stringResource(R.string.exam_date_pending)})"
                            } else {
                                " (${fmtDate(ex.start)})"
                            }
                            SuggestionChip(
                                onClick = {
                                    selectedExamLabel = if (selectedExamLabel == ex.indexedLabel && !isTarget) {
                                        targetExam?.indexedLabel
                                    } else {
                                        ex.indexedLabel
                                    }
                                },
                                label = {
                                    Text(
                                        "${ex.indexedLabel}$dateLabel",
                                        fontWeight = if (isSelected || isTarget) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                    if (first.allExamsPassed && activeExam == null) {
                        Text(
                            stringResource(R.string.exams_completed),
                            style = MaterialTheme.typography.bodySmall,
                            color = Green,
                            fontWeight = FontWeight.Medium,
                        )
                    } else if (activeExam != null) {
                        val targetDate = if (activeExam.start == Long.MAX_VALUE) "" else " · ${fmtDate(activeExam.start)}"
                        Text(
                            stringResource(R.string.target_exam_line, activeExam.indexedLabel) + targetDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Total Subject section (60% sur la matière totale)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.total_subject_header),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    val rate = totalSt.rate
                    if (rate != null) {
                        Text(stringResource(R.string.stats_line, totalSt.present, totalSt.answered, rate))
                    } else {
                        Text(stringResource(R.string.no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (totalSt.total > 0) {
                        val totalText = if (totalSt.targetExam != null || totalSt.associatedExams.isNotEmpty()) {
                            pluralStringResource(R.plurals.total_line, totalSt.total, totalSt.total, totalSt.allowed)
                        } else {
                            pluralStringResource(R.plurals.total_line_no_exam, totalSt.total, totalSt.total, totalSt.allowed)
                        }
                        Text(totalText, style = MaterialTheme.typography.bodySmall)
                        val totalColor = if (totalSt.margin <= 0) Red else Green
                        LinearProgressIndicator(
                            progress = { if (totalSt.allowed > 0) (totalSt.absent.coerceAtMost(totalSt.allowed)) / totalSt.allowed.toFloat() else if (totalSt.absent > 0) 1f else 0f },
                            color = totalColor,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val msg = when {
                            totalSt.margin >= 1 -> pluralStringResource(R.plurals.margin_skips, totalSt.margin, totalSt.margin)
                            else -> stringResource(R.string.badge_must_attend)
                        }
                        Text(msg, color = totalColor, fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalDivider()

            // Per-type stats
            displayRows.forEach { StatRow(it) }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val currentLabels = associatedExams.map { it.indexedLabel }
                val label = when {
                    currentLabels.isNotEmpty() && isManual -> stringResource(R.string.exam_label_manual, currentLabels.joinToString(", "))
                    currentLabels.isNotEmpty() -> stringResource(R.string.exam_label_auto, currentLabels.joinToString(", "))
                    else -> stringResource(R.string.exam_label_none)
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (first.examFound) MaterialTheme.colorScheme.onSurface else Amber,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { showDialog = true }) {
                    Text(stringResource(R.string.exam_change_btn))
                }
            }

            Text(stringResource(R.string.pref_label), style = MaterialTheme.typography.labelMedium)
            val pref = rows.first().pref
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    0 to R.string.pref_important,
                    1 to R.string.pref_normal,
                    2 to R.string.pref_skip,
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = pref == value,
                        onClick = { onPref(value) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        }
    }

    if (showDialog) {
        ExamSelectionDialog(
            courseSubject = subject,
            currentMappedTitles = currentMapped.first,
            isManual = currentMapped.second,
            availableExams = availableExams,
            examStarts = examStarts,
            onDismiss = { showDialog = false },
            onSave = { selected ->
                onExamMappingChanged(selected)
                showDialog = false
            },
        )
    }
}

@Composable
fun ExcludedSubjectsCard(subjects: List<String>, onInclude: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${stringResource(R.string.excluded_title)} (${subjects.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = if (expanded) "Réduire" else "Développer",
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            }
            if (expanded) {
                Text(stringResource(R.string.excluded_hint), style = MaterialTheme.typography.bodySmall)
                subjects.forEach { subject ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(subject, Modifier.weight(1f))
                        TextButton(onClick = { onInclude(subject) }) { Text(stringResource(R.string.include_subject)) }
                    }
                }
            }
        }
    }
}

@Composable
fun HiddenSubjectsCard(subjects: List<String>, onUnhide: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${stringResource(R.string.hidden_title)} (${subjects.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = if (expanded) "Réduire" else "Développer",
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            }
            if (expanded) {
                Text(stringResource(R.string.hidden_hint), style = MaterialTheme.typography.bodySmall)
                subjects.forEach { subject ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(subject, Modifier.weight(1f))
                        TextButton(onClick = { onUnhide(subject) }) { Text(stringResource(R.string.unhide_subject)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(st: SubjectStats) {
    val color = when {
        st.margin <= 0 -> Red
        else -> Green
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(st.typeLabel, fontWeight = FontWeight.SemiBold)
        val rate = st.rate
        if (rate != null) {
            Text(stringResource(R.string.stats_line, st.present, st.answered, rate))
        } else {
            Text(stringResource(R.string.no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (st.total > 0) {
            val totalText = if (st.mappedExamTitle != null) {
                pluralStringResource(R.plurals.total_line, st.total, st.total, st.allowed)
            } else {
                pluralStringResource(R.plurals.total_line_no_exam, st.total, st.total, st.allowed)
            }
            Text(totalText, style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(
                progress = { if (st.allowed > 0) (st.absent.coerceAtMost(st.allowed)) / st.allowed.toFloat() else if (st.absent > 0) 1f else 0f },
                color = color,
                modifier = Modifier.fillMaxWidth(),
            )
            val msg = when {
                st.margin >= 1 -> pluralStringResource(R.plurals.margin_skips, st.margin, st.margin)
                else -> stringResource(R.string.badge_must_attend)
            }
            Text(msg, color = color, fontWeight = FontWeight.Bold)
        }
        if (!st.examFound) {
            Text(stringResource(R.string.exam_not_found), style = MaterialTheme.typography.bodySmall, color = Amber)
        }
        if (st.pending > 0) {
            Text(pluralStringResource(R.plurals.pending, st.pending, st.pending), style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Sessions
// ---------------------------------------------------------------------------------------------

@Composable
fun SessionsScreen(ui: UiState, vm: AppViewModel) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val list = when (filter) {
        0 -> ui.sessions.filter { it.end <= ui.now && ui.att[it.uid] == null }.sortedByDescending { it.start }
        1 -> ui.sessions.filter { it.end <= ui.now }.sortedByDescending { it.start }
        else -> ui.sessions.filter { it.end > ui.now }.sortedBy { it.start }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.filter_todo, R.string.filter_past, R.string.filter_upcoming).forEachIndexed { i, label ->
                FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(stringResource(label)) })
            }
        }
        if (list.isEmpty()) {
            Text(stringResource(R.string.empty_list), Modifier.padding(16.dp))
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(list, key = { it.uid }) { s ->
                SessionCard(
                    s, typeName(ui.typeNames, s.typeId), ui.att[s.uid], null, showDate = true,
                    showStatusChips = ui.statsByKey.containsKey(Stats.key(s)),
                    onDetails = { vm.showDetails(s.uid) },
                ) { vm.setStatus(s.uid, it) }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------------------------

@Composable
fun SettingsScreen(ui: UiState, vm: AppViewModel, onSignIn: () -> Unit) {
    LaunchedEffect(Unit) { vm.fetchGroupsIfNeeded() }
    var d by remember(ui.settings) { mutableStateOf(ui.settings) }
    
    var showSearchDialog by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AuthSection(ui, vm, onSignIn = onSignIn)
        Row(
            Modifier.fillMaxWidth().clickable { showSearchDialog = true }.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Groupes", style = MaterialTheme.typography.bodyLarge)
                Text("${d.groupIds.size} sélectionné(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedTextField(
            value = d.rentree,
            onValueChange = { d = d.copy(rentree = it) },
            label = { Text(stringResource(R.string.rentree)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (ui.settings.lastSync == 0L) stringResource(R.string.never_synced)
            else stringResource(R.string.last_sync, fmtDateTime(ui.settings.lastSync)),
            style = MaterialTheme.typography.bodySmall,
        )
        Stepper(stringResource(R.string.set_required), d.requiredPct, 10, 100, 5, "%") { d = d.copy(requiredPct = it) }
        Stepper(stringResource(R.string.set_reserve), d.reserve, 0, 3) { d = d.copy(reserve = it) }
        Stepper(stringResource(R.string.set_alert), d.alertAt, 0, 5) { d = d.copy(alertAt = it) }
        Stepper(stringResource(R.string.set_max_day), d.maxPerDay, 1, 5) { d = d.copy(maxPerDay = it) }
        Stepper(stringResource(R.string.set_morning_cutoff), d.morningCutoffHour, 6, 18, 1, "h") { d = d.copy(morningCutoffHour = it) }
        Stepper(stringResource(R.string.set_exception), d.exceptionThresholdPct, 50, 100, 5, "%") { d = d.copy(exceptionThresholdPct = it) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.set_notifications), Modifier.weight(1f))
            Switch(checked = d.notifications, onCheckedChange = { d = d.copy(notifications = it) })
        }
        OutlinedTextField(
            value = d.excluded,
            onValueChange = { d = d.copy(excluded = it) },
            label = { Text(stringResource(R.string.set_excluded)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.set_merge_exams), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.set_merge_exams_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = d.mergeSubjectsByExam,
                onCheckedChange = {
                    d = d.copy(mergeSubjectsByExam = it)
                    vm.saveSettings(d)
                }
            )
        }

        // Activity types: Zeus only gives a number (idType), the user gives it a name.
        val typeIds = ui.sessions.map { it.typeId }.distinct().sorted()
        if (typeIds.isNotEmpty()) {
            Text(stringResource(R.string.types_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.types_hint), style = MaterialTheme.typography.bodySmall)
            typeIds.forEach { id -> key(id) {
                val ofType = ui.sessions.filter { it.typeId == id }
                val examples = ofType.map { it.subject }.distinct().take(3).joinToString(", ")
                var name by remember { mutableStateOf(ui.typeNames[id] ?: "") }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; vm.setTypeName(id, it) },
                    label = { Text(stringResource(R.string.type_row, id, ofType.size, examples)) },
                    placeholder = { Text("Type $id") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            } }
        }

        // Exam Mappings section
        val courseSubjects = ui.allSubjects.filter { it !in ui.excludedSubjects }
        val examStarts = remember(ui.allRawSessions, ui.typeNames) {
            Stats.examAllStartTimes(ui.allRawSessions, ui.typeNames)
        }
        if (courseSubjects.isNotEmpty()) {
            Text(stringResource(R.string.exam_mapping_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.exam_mapping_hint), style = MaterialTheme.typography.bodySmall)
            courseSubjects.forEach { subj ->
                key(subj) {
                    var showSubjDialog by remember { mutableStateOf(false) }
                    val currentMapped = ExamMatch.resolveExams(subj, ui.examMappings, ui.availableExams)
                    val displayMapped = currentMapped.first.map { ExamMatch.displayExamKey(it, examStarts) }
                    val label = when {
                        displayMapped.isNotEmpty() && currentMapped.second -> stringResource(R.string.exam_label_manual, displayMapped.joinToString(", "))
                        displayMapped.isNotEmpty() -> stringResource(R.string.exam_label_auto, displayMapped.joinToString(", "))
                        else -> stringResource(R.string.exam_label_none)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(subj, fontWeight = FontWeight.SemiBold)
                            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { showSubjDialog = true }) {
                            Text(stringResource(R.string.exam_change_btn))
                        }
                    }
                    if (showSubjDialog) {
                        ExamSelectionDialog(
                            courseSubject = subj,
                            currentMappedTitles = currentMapped.first,
                            isManual = currentMapped.second,
                            availableExams = ui.availableExams,
                            examStarts = examStarts,
                            onDismiss = { showSubjDialog = false },
                            onSave = { selected ->
                                vm.setExamMappings(subj, selected)
                                showSubjDialog = false
                            },
                        )
                    }
                }
            }
        }

        // Manual subject merges section
        if (courseSubjects.size > 1) {
            Text(stringResource(R.string.manual_merges_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.manual_merges_hint), style = MaterialTheme.typography.bodySmall)
            courseSubjects.forEach { subj ->
                key("merge_$subj") {
                    val currentTarget = ui.subjectMerges[subj]
                    var showMergeDialog by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(subj, fontWeight = FontWeight.SemiBold)
                            val statusText = if (!currentTarget.isNullOrBlank()) {
                                "-> $currentTarget"
                            } else {
                                stringResource(R.string.none_or_unmerged)
                            }
                            Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!currentTarget.isNullOrBlank()) {
                            TextButton(onClick = { vm.setSubjectMerge(subj, null) }) {
                                Text(stringResource(R.string.unmerge_subject))
                            }
                        } else {
                            OutlinedButton(onClick = { showMergeDialog = true }) {
                                Text(stringResource(R.string.merge_with_subject))
                            }
                        }
                    }
                    if (showMergeDialog) {
                        val otherSubjects = courseSubjects.filter { it != subj && ui.subjectMerges[it] != subj }
                        AlertDialog(
                            onDismissRequest = { showMergeDialog = false },
                            title = { Text(stringResource(R.string.merge_subject_dialog_title, subj)) },
                            text = {
                                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    otherSubjects.forEach { targetSubj ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                vm.setSubjectMerge(subj, targetSubj)
                                                showMergeDialog = false
                                            }.padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(targetSubj, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showMergeDialog = false }) {
                                    Text(stringResource(R.string.close))
                                }
                            }
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { vm.saveSettings(d) }) {
                Text(stringResource(R.string.save_sync))
            }
            OutlinedButton(onClick = { vm.sync(false) }, enabled = !ui.syncing) {
                Text(stringResource(R.string.sync_now))
            }
        }
        OutlinedButton(onClick = { vm.sync(true) }, enabled = !ui.syncing) {
            Text(stringResource(R.string.sync_full))
        }
    }
    if (showSearchDialog) {
        SearchGroupsDialog(
            availableGroups = ui.availableGroups,
            initialSelection = d.groupIds,
            onDismiss = { showSearchDialog = false },
            onSave = { selectedIds ->
                d = d.copy(groupIds = selectedIds)
                vm.saveSettings(d)
                showSearchDialog = false
            }
        )
    }
}

@Composable
fun SearchGroupsDialog(
    availableGroups: List<ApiGroup>,
    initialSelection: Set<Int>,
    onDismiss: () -> Unit,
    onSave: (Set<Int>) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(initialSelection) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        TextField(
                            value = search,
                            onValueChange = { search = it },
                            placeholder = { Text("Recherche") },
                            singleLine = true,
                            colors = androidx.compose.material3.TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                    },
                    actions = {
                        IconButton(onClick = { onSave(selected) }) { Icon(Icons.Default.Check, contentDescription = "Valider") }
                    }
                )
            }
        ) { padding ->
            val filtered = remember(search, availableGroups) {
                availableGroups.filter { search.isBlank() || it.name.contains(search, ignoreCase = true) || it.path?.contains(search, ignoreCase = true) == true }.take(100)
            }
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(filtered) { g ->
                    val isSelected = selected.contains(g.id)
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            selected = if (isSelected) selected - g.id else selected + g.id
                        }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(g.name, style = MaterialTheme.typography.bodyLarge)
                            if (!g.path.isNullOrBlank()) {
                                Text(g.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Checkbox(checked = isSelected, onCheckedChange = null)
                    }
                }
            }
        }
    }
}

@Composable
fun ExamSelectionDialog(
    courseSubject: String,
    currentMappedTitles: List<String>,
    isManual: Boolean,
    availableExams: List<String>,
    examStarts: Map<String, List<Long>> = emptyMap(),
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
) {
    val autoMatches = remember(courseSubject, availableExams) {
        ExamMatch.findAllAutoMatches(courseSubject, availableExams)
    }
    var isAuto by remember { mutableStateOf(!isManual) }
    var isNone by remember { mutableStateOf(isManual && currentMappedTitles.isEmpty()) }
    var selectedExams by remember {
        val initialManual = currentMappedTitles.flatMap { mapped ->
            val (mTitle, mStart) = ExamMatch.parseExamKey(mapped)
            if (mStart != null) {
                listOf(mapped)
            } else {
                val matchingKeys = availableExams.filter { ExamMatch.parseExamKey(it).first == mTitle }
                matchingKeys.ifEmpty { listOf(mapped) }
            }
        }.toSet()
        mutableStateOf(if (isManual) initialManual else autoMatches.toSet())
    }
    var search by remember { mutableStateOf("") }
    var customExams by remember { mutableStateOf(emptySet<String>()) }
    val allSelectableExams = remember(availableExams, customExams) {
        (availableExams + customExams).distinct().sortedWith(
            compareBy<String> { ExamMatch.parseExamKey(it).first.lowercase() }
                .thenBy { ExamMatch.parseExamKey(it).second ?: Long.MAX_VALUE }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exam_select_title, courseSubject)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.exam_select_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val autoDisplay = remember(autoMatches, examStarts) {
                    autoMatches.map { ExamMatch.displayExamKey(it, examStarts) }
                }
                val autoLabel = if (autoDisplay.isNotEmpty()) {
                    stringResource(R.string.exam_auto, autoDisplay.joinToString(", "))
                } else {
                    stringResource(R.string.exam_auto_none)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            isAuto = true
                            isNone = false
                            selectedExams = autoMatches.toSet()
                        }
                        .padding(vertical = 6.dp),
                ) {
                    RadioButton(
                        selected = isAuto,
                        onClick = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(autoLabel, style = MaterialTheme.typography.bodyMedium)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            isNone = true
                            isAuto = false
                            selectedExams = emptySet()
                        }
                        .padding(vertical = 6.dp),
                ) {
                    RadioButton(
                        selected = isNone,
                        onClick = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.exam_none), style = MaterialTheme.typography.bodyMedium)
                }

                Text(
                    stringResource(R.string.exam_list_header),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text(stringResource(R.string.search_exams)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
                val filteredExams = remember(search, allSelectableExams, examStarts) {
                    if (search.isBlank()) allSelectableExams
                    else allSelectableExams.filter {
                        it.contains(search, ignoreCase = true) ||
                            ExamMatch.displayExamKey(it, examStarts).contains(search, ignoreCase = true)
                    }
                }
                val canAddCustom = search.isNotBlank() && allSelectableExams.none {
                    it.equals(search.trim(), ignoreCase = true) ||
                        ExamMatch.parseExamKey(it).first.equals(search.trim(), ignoreCase = true)
                }
                if (canAddCustom) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val trimmed = search.trim()
                                customExams = customExams + trimmed
                                isAuto = false
                                isNone = false
                                selectedExams = selectedExams + trimmed
                                search = ""
                            }
                            .padding(vertical = 6.dp),
                    ) {
                        Text(
                            stringResource(R.string.add_custom_exam, search.trim()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (filteredExams.isNotEmpty()) {
                    filteredExams.forEach { examTitle ->
                        val isChecked = !isNone && (if (isAuto) examTitle in autoMatches else examTitle in selectedExams)
                        val displayTitle = ExamMatch.displayExamKey(examTitle, examStarts)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val currentBase = if (isAuto) autoMatches.toSet() else selectedExams
                                    isAuto = false
                                    val next = if (isChecked) {
                                        currentBase - examTitle
                                    } else {
                                        currentBase + examTitle
                                    }
                                    selectedExams = next
                                    isNone = next.isEmpty()
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = null,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(displayTitle, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else if (!canAddCustom) {
                    Text(
                        stringResource(R.string.exam_none_in_schedule),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val result = when {
                        isAuto -> listOf(ExamMatch.AUTO_MAPPING)
                        isNone || selectedExams.isEmpty() -> listOf(ExamMatch.NONE_MAPPING)
                        else -> selectedExams.toList().sortedWith(
                            compareBy<String> { ExamMatch.parseExamKey(it).second ?: Long.MAX_VALUE }
                                .thenBy { ExamMatch.parseExamKey(it).first.lowercase() }
                        )
                    }
                    onSave(result)
                }
            ) {
                Text(stringResource(R.string.save_exam_mapping))
            }
        },
    )
}

@Composable
fun Stepper(label: String, value: Int, min: Int, max: Int, step: Int = 1, suffix: String = "", onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(onClick = { onChange((value - step).coerceAtLeast(min)) }) { Text("−") }
        Text("$value$suffix", Modifier.padding(horizontal = 12.dp), fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { onChange((value + step).coerceAtMost(max)) }) { Text("+") }
    }
}


@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MicrosoftAuthDialog(
    onTokenReceived: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                object : WebView(context) {
                    private var tokenDelivered = false

                    init {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                url?.let {
                                    if (it.contains("access_token=") && !tokenDelivered) {
                                        val fragment = it.substringAfter("#", "")
                                        val params = fragment.split("&").associate { param ->
                                            val key = param.substringBefore("=")
                                            val value = param.substringAfter("=", "")
                                            key to java.net.URLDecoder.decode(value, "UTF-8")
                                        }
                                        val accessToken = params["access_token"]
                                        if (accessToken != null) {
                                            tokenDelivered = true
                                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                                try {
                                                    val reqUrl = java.net.URL("https://zeus.ionis-it.com/api/User/OfficeLogin")
                                                    val conn = reqUrl.openConnection() as java.net.HttpURLConnection
                                                    conn.requestMethod = "POST"
                                                    conn.setRequestProperty("Content-Type", "application/json")
                                                    conn.doOutput = true
                                                    
                                                    val body = "{\"accessToken\": \"$accessToken\"}"
                                                    conn.outputStream.use { os ->
                                                        val input = body.toByteArray(Charsets.UTF_8)
                                                        os.write(input, 0, input.size)
                                                    }
                                                    
                                                    if (conn.responseCode == 200) {
                                                        val responseStr = conn.inputStream.bufferedReader().use { r -> r.readText() }
                                                        val token = if (responseStr.startsWith("\"") && responseStr.endsWith("\"")) {
                                                            responseStr.trim('"')
                                                        } else if (responseStr.startsWith("{")) {
                                                            org.json.JSONObject(responseStr).optString("token", responseStr)
                                                        } else {
                                                            responseStr
                                                        }
                                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                            onTokenReceived(token)
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        val msUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize?client_id=39cd5b3d-08c6-4e1b-8730-6603bc77ba45&response_type=id_token+token&redirect_uri=https%3A%2F%2Fzeus.ionis-it.com%2FofficeConnect%2F&scope=openid+profile+email&nonce=12345"
                        loadUrl(msUrl)
                    }
                }
            }
        )
    }
}
