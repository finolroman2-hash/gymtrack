package com.example.gymtrack.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class WeekRange(val start: Long, val end: Long)

object WeekBounds {

    /**
     * Semana de programación (lunes 00:00 local → siguiente lunes),
     * alineada con [TrainingCycleCalculator].
     */
    fun forTimestamp(
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): WeekRange {
        val monday = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val start = monday.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = monday.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return WeekRange(start = start, end = end)
    }

    fun current(
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): WeekRange = forTimestamp(now, zone)

    fun isPastWeek(
        timestamp: Long,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean = forTimestamp(timestamp, zone).start < current(now, zone).start
}
