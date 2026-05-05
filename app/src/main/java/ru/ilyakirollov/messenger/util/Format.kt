package ru.ilyakirollov.messenger.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object Format {
    private val timeOnly = SimpleDateFormat("HH:mm", Locale("ru"))
    private val dateOnly = SimpleDateFormat("d MMM", Locale("ru"))
    private val fullDate = SimpleDateFormat("d MMMM yyyy", Locale("ru"))

    fun chatTimestamp(date: Date?): String {
        date ?: return ""
        val cal = Calendar.getInstance().apply { time = date }
        val today = Calendar.getInstance()
        return if (cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        ) {
            timeOnly.format(date)
        } else if (cal.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
            dateOnly.format(date)
        } else {
            fullDate.format(date)
        }
    }

    fun timeOnly(date: Date?): String = date?.let { timeOnly.format(it) }.orEmpty()

    fun voiceDuration(durationMs: Long): String {
        val totalSec = TimeUnit.MILLISECONDS.toSeconds(durationMs)
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }

    fun fileSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val k = 1024.0
        val units = listOf("Б", "КБ", "МБ", "ГБ")
        var idx = 0
        var value = bytes.toDouble()
        while (value >= k && idx < units.lastIndex) {
            value /= k
            idx++
        }
        return "%.1f %s".format(value, units[idx])
    }
}
