package jp.mushamusha.gomireminder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("gomi_reminder", Context.MODE_PRIVATE)

    fun registerListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    fun loadRules(): List<CollectionRule> {
        val raw = prefs.getString(KEY_RULES, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) add(ruleFromJson(arr.getJSONObject(i)))
            }.distinctBy { it.id }
        } catch (_: Exception) {
            prefs.edit().putString(KEY_RULES, "[]").apply()
            emptyList()
        }
    }

    fun saveRules(rules: List<CollectionRule>) {
        val arr = JSONArray()
        rules.distinctBy { it.id }.forEach { arr.put(ruleToJson(it)) }
        prefs.edit().putString(KEY_RULES, arr.toString()).apply()
    }

    fun isSuppressed(ruleId: String, date: LocalDate): Boolean =
        prefs.getBoolean(suppressedKey(ruleId, date), false)

    fun setSuppressed(ruleId: String, date: LocalDate, value: Boolean) {
        prefs.edit().apply {
            if (value) putBoolean(suppressedKey(ruleId, date), true)
            else remove(suppressedKey(ruleId, date))
        }.apply()
    }

    fun putSnooze(ruleId: String, date: LocalDate, atEpochMillis: Long) {
        prefs.edit().putLong(snoozeKey(ruleId, date), atEpochMillis).apply()
    }

    fun getSnooze(ruleId: String, date: LocalDate): Long? {
        val key = snoozeKey(ruleId, date)
        if (!prefs.contains(key)) return null
        val value = prefs.getLong(key, -1L)
        if (value <= 0L) {
            prefs.edit().remove(key).apply()
            return null
        }
        return value
    }

    fun clearSnooze(ruleId: String, date: LocalDate) {
        prefs.edit().remove(snoozeKey(ruleId, date)).apply()
    }

    fun exportJson(): String {
        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("rules", JSONArray().apply { loadRules().forEach { put(ruleToJson(it)) } })
        return root.toString(2)
    }

    fun importJson(text: String): List<CollectionRule> {
        val root = JSONObject(text)
        require(root.optInt("schemaVersion", 1) == 1) { "Unsupported schema" }
        val arr = root.getJSONArray("rules")
        val parsed = buildList {
            for (i in 0 until arr.length()) add(ruleFromJson(arr.getJSONObject(i)))
        }
        require(parsed.distinctBy { it.id }.size == parsed.size) { "Duplicate rule id" }
        saveRules(parsed)
        return parsed
    }

    fun clearTransientStateForMissingRules(validIds: Set<String>) {
        val edit = prefs.edit()
        prefs.all.keys.filter { it.startsWith("suppressed|") || it.startsWith("snooze|") }
            .forEach { key ->
                val id = key.split('|').getOrNull(1)
                if (id == null || id !in validIds) edit.remove(key)
            }
        edit.apply()
    }

    private fun ruleToJson(r: CollectionRule) = JSONObject().apply {
        put("id", r.id); put("area", r.area); put("trashType", r.trashType); put("enabled", r.enabled)
        put("pattern", r.pattern.name); put("weekdays", JSONArray(r.weekdays.sorted()))
        put("nth", r.nth); put("previousDayTime", r.previousDayTime.toString()); put("morningTime", r.morningTime.toString())
        put("notifyPreviousDay", r.notifyPreviousDay); put("notifyMorning", r.notifyMorning)
        put("skipDates", JSONArray(r.skipDates.sorted().map { it.toString() }))
        put("extraDates", JSONArray(r.extraDates.sorted().map { it.toString() }))
    }

    private fun ruleFromJson(o: JSONObject): CollectionRule {
        fun intSet(name: String): Set<Int> {
            val a = o.optJSONArray(name) ?: JSONArray()
            return buildSet { for (i in 0 until a.length()) add(a.getInt(i)) }
        }
        fun dateSet(name: String): Set<LocalDate> {
            val a = o.optJSONArray(name) ?: JSONArray()
            return buildSet { for (i in 0 until a.length()) add(LocalDate.parse(a.getString(i))) }
        }
        return CollectionRule(
            id = o.getString("id"),
            area = o.optString("area"),
            trashType = o.optString("trashType"),
            enabled = o.optBoolean("enabled", true),
            pattern = runCatching { RepeatPattern.valueOf(o.optString("pattern", "WEEKLY")) }.getOrDefault(RepeatPattern.WEEKLY),
            weekdays = intSet("weekdays").ifEmpty { setOf(1) },
            nth = o.optInt("nth", 1).coerceIn(1, 6),
            previousDayTime = runCatching { LocalTime.parse(o.optString("previousDayTime", "19:00")) }.getOrDefault(LocalTime.of(19, 0)),
            morningTime = runCatching { LocalTime.parse(o.optString("morningTime", "07:00")) }.getOrDefault(LocalTime.of(7, 0)),
            notifyPreviousDay = o.optBoolean("notifyPreviousDay", true),
            notifyMorning = o.optBoolean("notifyMorning", true),
            skipDates = dateSet("skipDates"),
            extraDates = dateSet("extraDates")
        )
    }

    companion object {
        private const val KEY_RULES = "rules_json"
        private fun suppressedKey(id: String, date: LocalDate) = "suppressed|$id|$date"
        private fun snoozeKey(id: String, date: LocalDate) = "snooze|$id|$date"
    }
}
