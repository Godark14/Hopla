package app.hopla.domain

import app.hopla.data.JumpSession
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class Dashboard(
    val todayJumps: Int = 0,
    val todayKcal: Int = 0,
    val todayMinutes: Int = 0,
    val weekJumps: List<Int> = List(7) { 0 },
    val lastWeekJumps: List<Int> = List(7) { 0 },
    val weekKcal: Int = 0,
    val weekMinutes: Int = 0,
    val bestSession: Int = 0,
    val streak: Int = 0,
    val todayIndex: Int = 0,
) {
    val weekTotal: Int get() = weekJumps.sum()
}

object Stats {
    val dayLabels = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")

    fun dashboard(sessions: List<JumpSession>, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): Dashboard {
        fun JumpSession.date(): LocalDate = Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate()

        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val lastMonday = monday.minusWeeks(1)
        val week = IntArray(7)
        val lastWeek = IntArray(7)
        var weekKcal = 0.0
        var weekSec = 0
        var todayJumps = 0
        var todayKcal = 0.0
        var todaySec = 0
        val activeDays = HashSet<LocalDate>()

        sessions.forEach { s ->
            val d = s.date()
            activeDays += d
            when {
                !d.isBefore(monday) && d.isBefore(monday.plusDays(7)) -> {
                    week[d.dayOfWeek.value - 1] += s.jumps
                    weekKcal += s.kcal
                    weekSec += s.durationSec
                }
                !d.isBefore(lastMonday) && d.isBefore(monday) -> lastWeek[d.dayOfWeek.value - 1] += s.jumps
            }
            if (d == today) {
                todayJumps += s.jumps
                todayKcal += s.kcal
                todaySec += s.durationSec
            }
        }

        // Série : jours consécutifs avec au moins une séance (aujourd'hui pas encore fait = on ne casse pas la série)
        var streak = 0
        var cursor = if (today in activeDays) today else today.minusDays(1)
        while (cursor in activeDays) {
            streak++
            cursor = cursor.minusDays(1)
        }

        return Dashboard(
            todayJumps = todayJumps,
            todayKcal = todayKcal.toInt(),
            todayMinutes = todaySec / 60,
            weekJumps = week.toList(),
            lastWeekJumps = lastWeek.toList(),
            weekKcal = weekKcal.toInt(),
            weekMinutes = weekSec / 60,
            bestSession = sessions.maxOfOrNull { it.jumps } ?: 0,
            streak = streak,
            todayIndex = today.dayOfWeek.value - 1,
        )
    }

    fun formatClock(totalSec: Long): String {
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }

    /** 1 234 / 12,3k — format français. */
    fun compact(n: Int): String = when {
        n >= 10_000 && n % 1000 < 50 -> "${n / 1000}k"
        n >= 10_000 -> String.format(Locale.FRANCE, "%.1fk", n / 1000f)
        else -> String.format(Locale.FRANCE, "%,d", n)
    }
}
