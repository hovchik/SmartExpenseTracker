package com.flowsense.app.util

import java.text.SimpleDateFormat
import java.util.*

object DateUtils {
    // SimpleDateFormat is NOT thread-safe — use ThreadLocal to avoid corruption
    // when multiple coroutines or threads format dates concurrently.
    private val dateFormat = ThreadLocal.withInitial { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    private val shortDateFormat = ThreadLocal.withInitial { SimpleDateFormat("MMM dd", Locale.getDefault()) }
    private val timeFormat = ThreadLocal.withInitial { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    private val dayFormat = ThreadLocal.withInitial { SimpleDateFormat("EEE", Locale.getDefault()) }
    private val monthFormat = ThreadLocal.withInitial { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }

    fun formatDate(timestamp: Long): String = dateFormat.get()!!.format(Date(timestamp))
    fun formatShortDate(timestamp: Long): String = shortDateFormat.get()!!.format(Date(timestamp))
    fun formatTime(timestamp: Long): String = timeFormat.get()!!.format(Date(timestamp))
    fun formatDay(timestamp: Long): String = dayFormat.get()!!.format(Date(timestamp))
    fun formatMonth(timestamp: Long): String = monthFormat.get()!!.format(Date(timestamp))

    fun formatDateTime(timestamp: Long): String =
        "${dateFormat.get()!!.format(Date(timestamp))} ${timeFormat.get()!!.format(Date(timestamp))}"

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

    fun getDaysInRange(start: Long, end: Long): List<Long> {
        if (start > end) return emptyList()
        val days = mutableListOf<Long>()
        val cal = Calendar.getInstance()
        cal.timeInMillis = start
        while (cal.timeInMillis <= end) {
            days.add(cal.timeInMillis)
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return days
    }

    fun isToday(timestamp: Long): Boolean {
        val today = getStartOfDay()
        val endToday = getEndOfDay()
        return timestamp in today..endToday
    }

    fun daysAgo(days: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        return getStartOfDay(cal.timeInMillis)
    }
}
