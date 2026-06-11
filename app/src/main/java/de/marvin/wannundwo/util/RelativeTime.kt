package de.marvin.wannundwo.util

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Returns a human-readable relative or absolute label for a Timestamp.
 *
 * Examples:
 *  – Future:  "in 2h 30min"  /  "morgen 09:00"  /  "Di., 17.06. 14:30"
 *  – Past:    "vor 5min"     /  "gestern 14:30"  /  "Mi., 04.06. 10:00"
 */
fun Timestamp.toRelativeLabel(): String {
    val date = toDate()
    val nowMs = System.currentTimeMillis()
    val diffMs = date.time - nowMs
    val absDiffMs = kotlin.math.abs(diffMs)
    val isFuture = diffMs > 0

    // Within 60 seconds
    if (absDiffMs < 60_000L) return "jetzt"

    // Within 60 minutes
    if (absDiffMs < 60 * 60_000L) {
        val mins = (absDiffMs / 60_000).toInt()
        return if (isFuture) "in ${mins}min" else "vor ${mins}min"
    }

    // Within 6 hours → show hours + minutes
    if (absDiffMs < 6 * 60 * 60_000L) {
        val hours = (absDiffMs / 3_600_000).toInt()
        val mins = ((absDiffMs % 3_600_000) / 60_000).toInt()
        val label = if (mins > 0) "${hours}h ${mins}min" else "${hours}h"
        return if (isFuture) "in $label" else "vor $label"
    }

    val timeStr = SimpleDateFormat("HH:mm", Locale.GERMAN).format(date)

    // Today / tomorrow / yesterday
    val calNow = Calendar.getInstance()
    val calDate = Calendar.getInstance().apply { time = date }
    val todayDay = calNow.get(Calendar.DAY_OF_YEAR)
    val dateDay = calDate.get(Calendar.DAY_OF_YEAR)
    val sameYear = calNow.get(Calendar.YEAR) == calDate.get(Calendar.YEAR)
    if (sameYear) {
        return when (dateDay - todayDay) {
            0 -> "heute $timeStr"
            1 -> "morgen $timeStr"
            -1 -> "gestern $timeStr"
            else -> SimpleDateFormat("EEE, dd.MM. HH:mm", Locale.GERMAN).format(date)
        }
    }

    return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN).format(date)
}
