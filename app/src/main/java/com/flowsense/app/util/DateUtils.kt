package com.flowsense.app.util

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    // SimpleDateFormat is NOT thread-safe, so each thread gets its own instances.
    // They are rebuilt when the default locale changes (e.g. the per-app language
    // setting), otherwise month/day names would stay in the old language.
    private class Formats(val locale: Locale) {
        val date = SimpleDateFormat("MMM dd, yyyy", locale)
        val shortDate = SimpleDateFormat("MMM dd", locale)
        val time = SimpleDateFormat("hh:mm a", locale)
        val day = SimpleDateFormat("EEE", locale)
        val month = SimpleDateFormat("MMMM yyyy", locale)
    }

    private val formats = ThreadLocal<Formats>()

    private fun f(): Formats {
        val locale = Locale.getDefault()
        val cached = formats.get()
        // TimeZone can also change at runtime (travel); keep formatters in sync with it.
        val tz = TimeZone.getDefault()
        if (cached != null && cached.locale == locale && cached.date.timeZone == tz) return cached
        return Formats(locale).also { formats.set(it) }
    }

    fun formatDate(timestamp: Long): String = f().date.format(Date(timestamp))
    fun formatShortDate(timestamp: Long): String = f().shortDate.format(Date(timestamp))
    fun formatTime(timestamp: Long): String = f().time.format(Date(timestamp))
    fun formatDay(timestamp: Long): String = f().day.format(Date(timestamp))
    fun formatMonth(timestamp: Long): String = f().month.format(Date(timestamp))

    fun formatDateTime(timestamp: Long): String {
        val fm = f()
        val d = Date(timestamp)
        return "${fm.date.format(d)} ${fm.time.format(d)}"
    }

    /**
     * Converts a value coming from a Compose `DatePicker`/`DateRangePicker`
     * (which reports the selected calendar date as **UTC midnight**) into a
     * local-timezone instant on the same calendar date (local noon).
     *
     * Feeding the raw picker millis straight into [getStartOfDay]/[getEndOfDay]
     * shifts the day backward in negative-UTC-offset timezones; normalising to
     * local noon first keeps the intended calendar day in every timezone.
     */
    fun pickerUtcToLocalMillis(utcMidnightMillis: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMidnightMillis
        }
        return Calendar.getInstance().apply {
            clear()
            set(
                utc.get(Calendar.YEAR),
                utc.get(Calendar.MONTH),
                utc.get(Calendar.DAY_OF_MONTH),
                12, 0, 0
            )
        }.timeInMillis
    }

    fun getStartOfDay(timestamp: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getEndOfDay(timestamp: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    fun getStartOfWeek(timestamp: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        // Walk backward to the first day of the week to avoid
        // Calendar.set(DAY_OF_WEEK) quirks at week boundaries.
        var guard = 7
        while (cal.get(Calendar.DAY_OF_WEEK) != cal.firstDayOfWeek && guard-- > 0) {
            cal.add(Calendar.DATE, -1)
        }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getEndOfWeek(timestamp: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = getStartOfWeek(timestamp)
        cal.add(Calendar.DATE, 6)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    fun getStartOfMonth(timestamp: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getEndOfMonth(timestamp: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    /** Upper bound on [getDaysInRange] to prevent OOM from pathological inputs. */
    private const val MAX_DAYS_IN_RANGE = 10 * 366

    fun getDaysInRange(start: Long, end: Long): List<Long> {
        if (start > end) return emptyList()
        val days = ArrayList<Long>(64)
        val cal = Calendar.getInstance()
        cal.timeInMillis = start
        while (cal.timeInMillis <= end && days.size < MAX_DAYS_IN_RANGE) {
            days.add(cal.timeInMillis)
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return days
    }

    fun isToday(timestamp: Long): Boolean {
        val now = System.currentTimeMillis()
        return timestamp in getStartOfDay(now)..getEndOfDay(now)
    }

    fun daysAgo(days: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        return getStartOfDay(cal.timeInMillis)
    }
}
