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
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List as ListIcon
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
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
    val ready = ui.settings.groupId > 0 && ui.settings.authMode.isNotEmpty()

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
    var group by remember { mutableStateOf(ui.settings.groupId.toString()) }
    var rentree by remember { mutableStateOf(ui.settings.rentree) }
    val saveFields = {
        vm.saveSettings(ui.settings.copy(groupId = group.toIntOrNull() ?: ui.settings.groupId, rentree = rentree))
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeekScheduleScreen(ui: UiState, vm: AppViewModel) {
    val ctx = LocalContext.current
    val initialPage = Int.MAX_VALUE / 2
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { Int.MAX_VALUE })
    
    val currentWeekMonday = remember { LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    
    Column(Modifier.fillMaxSize()) {
        
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val pageMonday = currentWeekMonday.plusWeeks((page - initialPage).toLong())
            val pageSunday = pageMonday.plusDays(6)
            
            val zone = java.time.ZoneId.systemDefault()
            val startOfWeek = pageMonday.atStartOfDay(zone).toInstant().toEpochMilli()
            val endOfWeek = pageMonday.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
            
            val weekSessions = ui.allRawSessions.filter { it.start in startOfWeek until endOfWeek }
            val byDay = weekSessions.groupBy { 
                java.time.Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() 
            }
            
            val days = (0..6).map { pageMonday.plusDays(it.toLong()) }
            
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        "${pageMonday.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))} - ${pageSunday.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT))}",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                days.forEach { day ->
                    val daySessions = byDay[day] ?: emptyList()
                    if (daySessions.isNotEmpty() || day == LocalDate.now()) {
                        item {
                            Text(
                                day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        
                        val statsMap = ui.stats.associateBy { it.key }
                        val todayItems = Stats.today(ui.allRawSessions, ui.att, statsMap, ui.settings, ui.now, day)
                        val todayItemsMap = todayItems.associateBy { it.session.uid }
                        
                        val recos = todayItems.filter { it.badge == Badge.SKIP_SUGGESTED }
                        if (recos.isNotEmpty()) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFFE8F5E9)
                                    )
                                ) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(stringResource(R.string.reco_title), style = MaterialTheme.typography.titleMedium)
                                        recos.forEach {
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
                        
                        if (daySessions.isEmpty()) {
                            item { Text(stringResource(R.string.no_sessions_today)) }
                        } else {
                            val color = colorForDay(day.dayOfWeek)
                            items(daySessions.sortedBy { it.start }, key = { it.uid }) { s ->
                                val tItem = todayItemsMap[s.uid]
                                WeekSessionCard(
                                    s = s,
                                    typeLabel = typeName(ui.typeNames, s.typeId),
                                    status = ui.att[s.uid],
                                    badge = tItem?.badge,
                                    color = color,
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
    onDetails: () -> Unit,
    onSet: (Status?) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Column(
                Modifier
                    .background(color)
                    .padding(8.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(fmtTime(s.start), color = Color.White, fontWeight = FontWeight.Bold)
                Text(fmtTime(s.end), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.padding(12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.subject, style = MaterialTheme.typography.titleMedium)
                val place = if (s.location.isNotBlank()) s.location else if (s.online) stringResource(R.string.online) else ""
                Text(
                    listOf(typeLabel, place).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                )
                badge?.let { b ->
                    val (label, badgeColor) = when (b) {
                        Badge.SKIP_SUGGESTED -> R.string.badge_skip to Green
                        Badge.CAN_SKIP -> R.string.badge_can to Green
                        Badge.ATTEND -> R.string.badge_attend to Amber
                        Badge.IMPORTANT -> R.string.badge_important to MaterialTheme.colorScheme.primary
                    }
                    Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = badgeColor, fontWeight = FontWeight.Bold)
                }
                StatusChips(status, onSet)
                TextButton(onClick = onDetails) { Text(stringResource(R.string.details_btn)) }
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
    onDetails: () -> Unit,
    onSet: (Status?) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
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
                listOf(typeLabel, place).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            badge?.let { b ->
                val (label, color) = when (b) {
                    Badge.SKIP_SUGGESTED -> R.string.badge_skip to Green
                    Badge.CAN_SKIP -> R.string.badge_can to Green
                    Badge.ATTEND -> R.string.badge_attend to Amber
                    Badge.IMPORTANT -> R.string.badge_important to MaterialTheme.colorScheme.primary
                }
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
            }
            StatusChips(status, onSet)
            TextButton(onClick = onDetails) { Text(stringResource(R.string.details_btn)) }
        }
    }
}

@Composable
fun StatusChips(current: Status?, onSet: (Status?) -> Unit) {
    val options = listOf(
        Status.PRESENT to R.string.status_present,
        Status.ABSENT to R.string.status_absent,
        Status.JUSTIFIED to R.string.status_justified,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (st, label) ->
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
        items(groups, key = { it.first }) { (subject, rows) ->
            SubjectCard(
                subject = subject,
                rows = rows,
                availableExams = ui.availableExams,
                onPref = { vm.setPref(subject, it) },
                onExclude = { vm.setSubjectExcluded(subject, true) },
                onExamMappingChanged = { vm.setExamMapping(subject, it) },
            )
        }
    }
}

@Composable
fun SubjectCard(
    subject: String,
    rows: List<SubjectStats>,
    availableExams: List<String>,
    onPref: (Int) -> Unit,
    onExclude: () -> Unit,
    onExamMappingChanged: (String) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val first = rows.first()
    val mappedTitle = first.mappedExamTitle
    val isManual = first.isExamManual

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(subject, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onExclude) { Text(stringResource(R.string.exclude_subject)) }
            }
            rows.forEach { StatRow(it) }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val label = when {
                    mappedTitle != null && isManual -> stringResource(R.string.exam_label_manual, mappedTitle)
                    mappedTitle != null -> stringResource(R.string.exam_label_auto, mappedTitle)
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
            currentMappedTitle = mappedTitle,
            isManual = isManual,
            availableExams = availableExams,
            onDismiss = { showDialog = false },
            onSelect = { selected ->
                onExamMappingChanged(selected)
                showDialog = false
            },
        )
    }
}

@Composable
fun ExcludedSubjectsCard(subjects: List<String>, onInclude: (String) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.excluded_title), style = MaterialTheme.typography.titleMedium)
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

@Composable
private fun StatRow(st: SubjectStats) {
    val color = when {
        st.margin < 0 -> Red
        st.margin == 0 -> Amber
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
                stringResource(R.string.total_line, st.total, st.allowed)
            } else {
                stringResource(R.string.total_line_no_exam, st.total, st.allowed)
            }
            Text(totalText, style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(
                progress = { if (st.allowed > 0) (st.absent.coerceAtMost(st.allowed)) / st.allowed.toFloat() else if (st.absent > 0) 1f else 0f },
                color = color,
                modifier = Modifier.fillMaxWidth(),
            )
            val msg = when {
                st.margin >= 1 -> stringResource(R.string.margin_skips, st.margin)
                st.margin == 0 -> stringResource(R.string.margin_zero)
                else -> stringResource(R.string.margin_over)
            }
            Text(msg, color = color, fontWeight = FontWeight.Bold)
        }
        if (!st.examFound) {
            Text(stringResource(R.string.exam_not_found), style = MaterialTheme.typography.bodySmall, color = Amber)
        }
        if (st.pending > 0) {
            Text(stringResource(R.string.pending, st.pending), style = MaterialTheme.typography.bodySmall)
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
    var d by remember(ui.settings) { mutableStateOf(ui.settings) }
    var groupText by remember(ui.settings) { mutableStateOf(ui.settings.groupId.toString()) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AuthSection(ui, vm, onSignIn = onSignIn)
        OutlinedTextField(
            value = groupText,
            onValueChange = { groupText = it.filter(Char::isDigit).take(8) },
            label = { Text(stringResource(R.string.group_id)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
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
        if (courseSubjects.isNotEmpty()) {
            Text(stringResource(R.string.exam_mapping_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.exam_mapping_hint), style = MaterialTheme.typography.bodySmall)
            courseSubjects.forEach { subj ->
                key(subj) {
                    var showSubjDialog by remember { mutableStateOf(false) }
                    val currentMapped = ExamMatch.resolveExam(subj, ui.examMappings, ui.availableExams)
                    val label = when {
                        currentMapped.first != null && currentMapped.second -> stringResource(R.string.exam_label_manual, currentMapped.first!!)
                        currentMapped.first != null -> stringResource(R.string.exam_label_auto, currentMapped.first!!)
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
                            currentMappedTitle = currentMapped.first,
                            isManual = currentMapped.second,
                            availableExams = ui.availableExams,
                            onDismiss = { showSubjDialog = false },
                            onSelect = { selected ->
                                vm.setExamMapping(subj, selected)
                                showSubjDialog = false
                            },
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { vm.saveSettings(d.copy(groupId = groupText.toIntOrNull() ?: d.groupId)) }) {
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
}

@Composable
fun ExamSelectionDialog(
    courseSubject: String,
    currentMappedTitle: String?,
    isManual: Boolean,
    availableExams: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val autoMatch = remember(courseSubject, availableExams) {
        ExamMatch.findAutoMatch(courseSubject, availableExams)
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

                val autoLabel = if (autoMatch != null) {
                    stringResource(R.string.exam_auto, autoMatch)
                } else {
                    stringResource(R.string.exam_auto_none)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(ExamMatch.AUTO_MAPPING) }
                        .padding(vertical = 6.dp),
                ) {
                    RadioButton(
                        selected = !isManual,
                        onClick = { onSelect(ExamMatch.AUTO_MAPPING) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(autoLabel, style = MaterialTheme.typography.bodyMedium)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(ExamMatch.NONE_MAPPING) }
                        .padding(vertical = 6.dp),
                ) {
                    RadioButton(
                        selected = isManual && currentMappedTitle == null,
                        onClick = { onSelect(ExamMatch.NONE_MAPPING) },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.exam_none), style = MaterialTheme.typography.bodyMedium)
                }

                if (availableExams.isNotEmpty()) {
                    Text(
                        stringResource(R.string.exam_list_header),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    availableExams.forEach { examTitle ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(examTitle) }
                                .padding(vertical = 6.dp),
                        ) {
                            RadioButton(
                                selected = isManual && currentMappedTitle == examTitle,
                                onClick = { onSelect(examTitle) },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(examTitle, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.exam_none_in_schedule),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
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
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                            val auth = request.requestHeaders?.entries?.firstOrNull { it.key.equals("Authorization", ignoreCase = true) }?.value
                            if (auth != null) {
                                val token = if (auth.startsWith("Bearer ", ignoreCase = true)) {
                                    auth.substring(7).trim()
                                } else {
                                    auth.trim()
                                }
                                if (token.startsWith("eyJhbGciOiJIUzI1Ni")) {
                                    view.post {
                                        onTokenReceived(token)
                                    }
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }
                    }
                    CookieManager.getInstance().setAcceptCookie(true)
                    loadUrl("https://login.microsoftonline.com/common/oauth2/v2.0/authorize?client_id=39cd5b3d-08c6-4e1b-8730-6603bc77ba45&response_type=id_token+token&redirect_uri=https%3A%2F%2Fzeus.ionis-it.com%2FofficeConnect%2F&scope=openid+profile+email&nonce=12345")
                }
            }
        )
    }
}
