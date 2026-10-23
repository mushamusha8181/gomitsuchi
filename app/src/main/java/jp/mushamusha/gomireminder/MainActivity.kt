package jp.mushamusha.gomireminder

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity(), android.content.SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var prefs: AppPreferences
    private var rulesState by mutableStateOf<List<CollectionRule>>(emptyList())
    private var refreshNonce by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AppPreferences(this)
        NotificationHelper.ensureChannel(this)
        rulesState = prefs.loadRules()
        AlarmScheduler(this).rebuildAll()

        setContent {
            MaterialTheme {
                val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                    if (uri != null) runCatching { contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(prefs.exportJson()) } }
                }
                val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) runCatching {
                        val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("empty")
                        val oldIds = prefs.loadRules().map { it.id }
                        val scheduler = AlarmScheduler(this)
                        oldIds.forEach(scheduler::cancelRule)
                        rulesState = prefs.importJson(text)
                        scheduler.rebuildAll()
                    }
                }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                GomiApp(
                    rules = rulesState,
                    refreshKey = refreshNonce,
                    onRulesChanged = { newRules ->
                        val oldRules = rulesState.associateBy { it.id }
                        val newById = newRules.associateBy { it.id }
                        rulesState = newRules
                        prefs.saveRules(newRules)
                        prefs.clearTransientStateForMissingRules(newRules.map { it.id }.toSet())
                        val scheduler = AlarmScheduler(this)
                        val allIds = oldRules.keys + newById.keys
                        allIds.forEach { id ->
                            val old = oldRules[id]
                            val new = newById[id]
                            if (old == null || new == null || scheduleChanged(old, new)) {
                                scheduler.cancelRule(id)
                                if (new != null) scheduler.scheduleRule(new)
                            }
                        }
                        GomiWidgetProvider.updateAll(this)
                    },
                    onExport = { exportLauncher.launch("gomi-reminder-backup-${LocalDate.now()}.json") },
                    onImport = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    onExactAlarmSettings = { openExactAlarmSettings() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::prefs.isInitialized) prefs.registerListener(this)
    }

    override fun onStop() {
        if (::prefs.isInitialized) prefs.unregisterListener(this)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (::prefs.isInitialized) {
            rulesState = prefs.loadRules()
            AlarmScheduler(this).rebuildAll()
            refreshNonce++
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: android.content.SharedPreferences?, key: String?) {
        if (key?.startsWith("suppressed|") == true || key?.startsWith("snooze|") == true) refreshNonce++
    }

    private fun scheduleChanged(a: CollectionRule, b: CollectionRule): Boolean =
        a.enabled != b.enabled || a.pattern != b.pattern || a.weekdays != b.weekdays || a.nth != b.nth ||
            a.previousDayTime != b.previousDayTime || a.morningTime != b.morningTime ||
            a.notifyPreviousDay != b.notifyPreviousDay || a.notifyMorning != b.notifyMorning ||
            a.skipDates != b.skipDates || a.extraDates != b.extraDates

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= 31) {
            val alarm = getSystemService(AlarmManager::class.java)
            if (!alarm.canScheduleExactAlarms()) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GomiApp(
    rules: List<CollectionRule>,
    refreshKey: Int,
    onRulesChanged: (List<CollectionRule>) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onExactAlarmSettings: () -> Unit
) {
    var editing by remember { mutableStateOf<CollectionRule?>(null) }
    var adding by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CollectionRule?>(null) }
    val prefsContext = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { AppPreferences(prefsContext) }
    val next = remember(rules, refreshKey) {
        val today = LocalDate.now()
        rules.filter { it.enabled }.mapNotNull { r -> ScheduleMatcher.nextOccurrence(r, today)?.let { r to it } }
            .filterNot { (r, d) -> prefs.isSuppressed(r.id, d) }
            .minByOrNull { it.second }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("ゴミ通知") }, actions = { TextButton(onClick = { settingsOpen = true }) { Text("設定") } }) },
        floatingActionButton = { SmallFloatingActionButton(onClick = { adding = true }) { Text("＋") } }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(horizontal = 12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(Modifier.height(2.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("次のゴミの日", style = MaterialTheme.typography.labelLarge)
                    if (next == null) Text("予定はありません", style = MaterialTheme.typography.titleMedium)
                    else {
                        Text("${next.second.monthValue}/${next.second.dayOfMonth}  ${next.first.trashType}", style = MaterialTheme.typography.headlineSmall)
                        if (next.first.area.isNotBlank()) Text(next.first.area, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (rules.isEmpty()) {
                Text("右下の＋から収集ルールを登録してください。", modifier = Modifier.padding(6.dp))
            } else rules.forEach { rule ->
                val nextDate = ScheduleMatcher.nextOccurrence(rule)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(rule.trashType, style = MaterialTheme.typography.titleMedium)
                                Text(listOfNotNull(rule.area.takeIf { it.isNotBlank() }, nextDate?.let { "次回 ${it.monthValue}/${it.dayOfMonth}" }).joinToString(" ・ "), style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = rule.enabled, onCheckedChange = { checked -> onRulesChanged(rules.map { if (it.id == rule.id) it.copy(enabled = checked) else it }) })
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { editing = rule }) { Text("編集") }
                            TextButton(onClick = { deleting = rule }) { Text("削除") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }

    if (adding) RuleDialog(initial = null, onDismiss = { adding = false }, onSave = { onRulesChanged(rules + it); adding = false })
    editing?.let { current -> RuleDialog(initial = current, onDismiss = { editing = null }, onSave = { updated -> onRulesChanged(rules.map { if (it.id == updated.id) updated else it }); editing = null }) }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("削除しますか？") },
            text = { Text("${target.trashType} の収集ルールを削除します。") },
            confirmButton = { TextButton(onClick = { onRulesChanged(rules.filterNot { it.id == target.id }); deleting = null }) { Text("削除") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("キャンセル") } }
        )
    }

    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("設定") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("通知が遅れる場合は、Androidの『アラームとリマインダー』を許可すると精度が上がります。")
                    TextButton(onClick = onExactAlarmSettings) { Text("正確なアラーム設定を開く") }
                    TextButton(onClick = onExport) { Text("JSONバックアップを書き出す") }
                    TextButton(onClick = onImport) { Text("JSONバックアップを復元") }
                }
            },
            confirmButton = { TextButton(onClick = { settingsOpen = false }) { Text("閉じる") } }
        )
    }
}

@Composable
private fun RuleDialog(initial: CollectionRule?, onDismiss: () -> Unit, onSave: (CollectionRule) -> Unit) {
    var area by remember { mutableStateOf(initial?.area ?: "") }
    var trashType by remember { mutableStateOf(initial?.trashType ?: "燃やすゴミ") }
    var pattern by remember { mutableStateOf(initial?.pattern ?: RepeatPattern.WEEKLY) }
    var weekdays by remember { mutableStateOf(initial?.weekdays ?: setOf(DayOfWeek.MONDAY.value)) }
    var nth by remember { mutableIntStateOf(initial?.nth ?: 1) }
    var prevTime by remember { mutableStateOf((initial?.previousDayTime ?: LocalTime.of(19, 0)).format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var morningTime by remember { mutableStateOf((initial?.morningTime ?: LocalTime.of(7, 0)).format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var notifyPrev by remember { mutableStateOf(initial?.notifyPreviousDay ?: true) }
    var notifyMorning by remember { mutableStateOf(initial?.notifyMorning ?: true) }
    var skipText by remember { mutableStateOf(initial?.skipDates?.joinToString(",") ?: "") }
    var extraText by remember { mutableStateOf(initial?.extraDates?.joinToString(",") ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "収集ルールを追加" else "収集ルールを編集") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(area, { area = it }, label = { Text("地域") }, singleLine = true)
                OutlinedTextField(trashType, { trashType = it }, label = { Text("ゴミ種類") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(pattern == RepeatPattern.WEEKLY, { pattern = RepeatPattern.WEEKLY }); Text("毎週")
                    Spacer(Modifier.width(8.dp))
                    RadioButton(pattern == RepeatPattern.NTH_WEEKDAY, { pattern = RepeatPattern.NTH_WEEKDAY }); Text("第○週")
                }
                Text("曜日")
                val labels = listOf("月", "火", "水", "木", "金", "土", "日")
                labels.chunked(4).forEachIndexed { rowIndex, rowLabels ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowLabels.forEachIndexed { localIdx, label ->
                            val idx = rowIndex * 4 + localIdx
                            FilterChip(selected = (idx + 1) in weekdays, onClick = {
                                val day = idx + 1
                                weekdays = if (pattern == RepeatPattern.NTH_WEEKDAY) setOf(day) else if (day in weekdays) weekdays - day else weekdays + day
                            }, label = { Text(label) })
                        }
                    }
                }
                if (pattern == RepeatPattern.NTH_WEEKDAY) {
                    Text("週")
                    listOf(1,2,3,4,5,6).chunked(3).forEach { group ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            group.forEach { n ->
                                FilterChip(selected = nth == n, onClick = { nth = n }, label = { Text(if (n == 6) "最終" else "$n") })
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(notifyPrev, { notifyPrev = it }); Text("前日通知"); Spacer(Modifier.width(6.dp)); OutlinedTextField(prevTime, { prevTime = it }, modifier = Modifier.width(100.dp), label = { Text("HH:mm") }, singleLine = true) }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(notifyMorning, { notifyMorning = it }); Text("当日通知"); Spacer(Modifier.width(6.dp)); OutlinedTextField(morningTime, { morningTime = it }, modifier = Modifier.width(100.dp), label = { Text("HH:mm") }, singleLine = true) }
                OutlinedTextField(skipText, { skipText = it }, label = { Text("収集なし日 例: 2026-12-31,2027-01-01") })
                OutlinedTextField(extraText, { extraText = it }, label = { Text("振替収集日 例: 2027-01-02") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    require(trashType.isNotBlank()) { "ゴミ種類を入力してください" }
                    require(weekdays.isNotEmpty()) { "曜日を1つ以上選んでください" }
                    fun dates(text: String): Set<LocalDate> = text.split(',').mapNotNull { raw -> raw.trim().takeIf { it.isNotEmpty() }?.let { LocalDate.parse(it) } }.toSet()
                    val skip = dates(skipText); val extra = dates(extraText)
                    require(skip.intersect(extra).isEmpty()) { "収集なし日と振替収集日が重複しています" }
                    onSave(CollectionRule(
                        id = initial?.id ?: CollectionRule(area = area, trashType = trashType).id,
                        area = area.trim(), trashType = trashType.trim(), enabled = initial?.enabled ?: true,
                        pattern = pattern, weekdays = weekdays, nth = nth,
                        previousDayTime = LocalTime.parse(prevTime), morningTime = LocalTime.parse(morningTime),
                        notifyPreviousDay = notifyPrev, notifyMorning = notifyMorning,
                        skipDates = skip, extraDates = extra
                    ))
                }.onFailure { error = it.message ?: "入力を確認してください" }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}
