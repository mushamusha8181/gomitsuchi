package jp.mushamusha.gomireminder

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.util.UUID

data class CollectionRule(
    val id: String = UUID.randomUUID().toString(),
    val area: String,
    val trashType: String,
    val enabled: Boolean = true,
    val pattern: RepeatPattern = RepeatPattern.WEEKLY,
    val weekdays: Set<Int> = setOf(DayOfWeek.MONDAY.value),
    val nth: Int = 1,
    val previousDayTime: LocalTime = LocalTime.of(19, 0),
    val morningTime: LocalTime = LocalTime.of(7, 0),
    val notifyPreviousDay: Boolean = true,
    val notifyMorning: Boolean = true,
    val skipDates: Set<LocalDate> = emptySet(),
    val extraDates: Set<LocalDate> = emptySet()
)

enum class RepeatPattern { WEEKLY, NTH_WEEKDAY }
enum class AlarmKind { PREVIOUS_DAY, MORNING, SNOOZE }

object ScheduleMatcher {
    fun matches(rule: CollectionRule, date: LocalDate): Boolean {
        if (!rule.enabled) return false
        if (date in rule.extraDates) return true
        if (date in rule.skipDates) return false
        return matchesRule(rule, date)
    }

    fun matchesRule(rule: CollectionRule, date: LocalDate): Boolean = when (rule.pattern) {
        RepeatPattern.WEEKLY -> date.dayOfWeek.value in rule.weekdays
        RepeatPattern.NTH_WEEKDAY -> {
            val weekday = rule.weekdays.firstOrNull() ?: DayOfWeek.MONDAY.value
            if (date.dayOfWeek.value != weekday) false
            else if (rule.nth in 1..5) {
                val first = YearMonth.from(date).atDay(1)
                    .with(TemporalAdjusters.nextOrSame(DayOfWeek.of(weekday)))
                    .plusWeeks((rule.nth - 1).toLong())
                date == first && date.month == first.month
            } else {
                val last = YearMonth.from(date).atEndOfMonth()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.of(weekday)))
                date == last
            }
        }
    }

    fun nextOccurrence(rule: CollectionRule, from: LocalDate = LocalDate.now(), days: Long = 370): LocalDate? {
        for (i in 0..days) {
            val d = from.plusDays(i)
            if (matches(rule, d)) return d
        }
        return null
    }
}
