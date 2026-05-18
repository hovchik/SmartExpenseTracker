package com.flowsense.app.service.scheduler

import android.content.ContentValues
import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.work.*
import com.flowsense.app.FlowSenseApp
import com.flowsense.app.data.model.*
import com.flowsense.app.service.ai.AiExpenseEngine
import com.flowsense.app.service.notification.ExpenseNotificationHelper
import com.flowsense.app.util.CurrencyUtils
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * WorkManager worker that runs daily and, on the configured day of the month,
 * generates a PDF report of the previous month's transactions and analysis,
 * then saves it to the device Downloads folder and posts a notification.
 */
class MonthlyReportWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "MonthlyReportWorker"
        const val WORK_NAME = "monthly_report_pdf"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonthlyReportWorker>(1, TimeUnit.DAYS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Monthly report worker enqueued (daily check)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Monthly report worker cancelled")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val app = applicationContext as? FlowSenseApp ?: return Result.failure()
            val repo = app.repository
            repo.awaitInitialization()

            val settings = repo.appData.value.settings

            if (!settings.monthlyReportEnabled) return Result.success()

            val forceSend = inputData.getBoolean("force_send", false)

            val today = Calendar.getInstance()
            val todayDayOfMonth = today.get(Calendar.DAY_OF_MONTH)
            if (!forceSend && todayDayOfMonth != settings.monthlyReportDayOfMonth) return Result.success()

            // When forced (manual trigger), report on current month; otherwise previous month
            val reportCal = Calendar.getInstance().apply {
                if (!forceSend) add(Calendar.MONTH, -1)
            }
            val reportMonthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(reportCal.time)

            // Check if already generated for this month (skip for manual triggers)
            if (!forceSend && settings.lastMonthlyReportSentMonth == reportMonthKey) {
                Log.d(TAG, "Report already generated for $reportMonthKey, skipping")
                return Result.success()
            }

            // Generate the report for the target month
            val startCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, reportCal.get(Calendar.YEAR))
                set(Calendar.MONTH, reportCal.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val endCal = if (forceSend) {
                // For current month, use today as end date
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
            } else {
                Calendar.getInstance().apply {
                    set(Calendar.YEAR, reportCal.get(Calendar.YEAR))
                    set(Calendar.MONTH, reportCal.get(Calendar.MONTH))
                    set(Calendar.DAY_OF_MONTH, startCal.getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
            }

            val currencyCode = settings.currencyCode
            val aiEngine = AiExpenseEngine()
            val allTransactions = repo.appData.value.transactions
                .filter { !it.isDeleted }

            var report = aiEngine.generateReport(
                allTransactions,
                ReportPeriod.MONTHLY,
                startCal.timeInMillis,
                endCal.timeInMillis,
                currencyCode
            )

            // Enrich with AI insight if AI provider is available
            report = enrichWithAiInsight(app, report, currencyCode)

            // Generate PDF
            val monthLabel = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(reportCal.time)
            val pdfFile = generateReportPdf(report, currencyCode, monthLabel)

            // Save to Downloads folder via MediaStore
            val savedUri = savePdfToDownloads(pdfFile, monthLabel)

            // Mark this month as done (only for automatic scheduled runs)
            if (!forceSend) {
                repo.updateSettings(settings.copy(lastMonthlyReportSentMonth = reportMonthKey))
            }

            // Post a notification — tapping opens the saved PDF
            ExpenseNotificationHelper.postMonthlyReportNotification(
                context = applicationContext,
                monthLabel = monthLabel,
                pdfUri = savedUri
            )

            Log.d(TAG, "Monthly report generated and saved for $reportMonthKey: $savedUri")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate monthly report", e)
            Result.retry()
        }
    }

    /**
     * Saves the PDF file to the public Downloads directory via MediaStore (scoped storage).
     * Returns the content URI of the saved file.
     */
    private fun savePdfToDownloads(pdfFile: File, monthLabel: String): Uri? {
        val fileName = "FlowSense_Report_${monthLabel.replace(" ", "_")}.pdf"
        val resolver = applicationContext.contentResolver

        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/FlowSense")
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { outputStream ->
                pdfFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        // Clean up the temp cache file
        pdfFile.delete()
        return uri
    }

    /**
     * Attempts to get an AI-generated insight for the report.
     * Falls back gracefully if AI is unavailable.
     */
    private suspend fun enrichWithAiInsight(
        app: FlowSenseApp,
        report: ExpenseReport,
        currencyCode: String
    ): ExpenseReport {
        try {
            val selector = app.aiProviderSelector ?: return report
            val provider = selector.getActiveProvider()
            if (!provider.isAvailable()) return report

            val promptAdapter = com.flowsense.app.ai.provider.PromptAdapter()
            val prompt = promptAdapter.createReportInsightPrompt(
                periodLabel = "monthly",
                totalExpenses = report.totalExpenses,
                totalIncome = report.totalIncome,
                categoryBreakdown = report.categoryBreakdown,
                topMerchants = report.topMerchants,
                transactionCount = report.transactionCount,
                currencyCode = currencyCode,
                comparisonWithPrevious = report.comparisonWithPrevious,
                dayOfWeekSpending = report.dayOfWeekSpending
            )

            val result = provider.generateAnalysis(
                com.flowsense.app.ai.provider.AnalysisInput(
                    prompt = prompt,
                    totalExpenses = report.totalExpenses,
                    totalIncome = report.totalIncome,
                    transactionCount = report.transactionCount,
                    currencyCode = currencyCode,
                    type = com.flowsense.app.ai.provider.AnalysisType.REPORT
                )
            )

            return if (result.success && result.text.isNotBlank()) {
                report.copy(aiInsight = promptAdapter.parseInsight(result.text))
            } else report
        } catch (e: Exception) {
            Log.w(TAG, "AI insight generation failed: ${e.message}")
            return report
        }
    }

    private fun generateReportPdf(
        report: ExpenseReport,
        currencyCode: String,
        periodLabel: String
    ): File {
        val pageWidth = 595  // A4 in pts at 72 dpi
        val pageHeight = 842
        val leftMargin = 40f
        val rightMargin = 40f
        val contentWidth = pageWidth - leftMargin - rightMargin
        val lineHeight = 18f
        val topMargin = 50f

        val document = PdfDocument()
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = topMargin

        // Color palette for charts
        val chartColors = intArrayOf(
            android.graphics.Color.parseColor("#E91E63"),
            android.graphics.Color.parseColor("#2196F3"),
            android.graphics.Color.parseColor("#9C27B0"),
            android.graphics.Color.parseColor("#FF9800"),
            android.graphics.Color.parseColor("#4CAF50"),
            android.graphics.Color.parseColor("#607D8B"),
            android.graphics.Color.parseColor("#F44336"),
            android.graphics.Color.parseColor("#3F51B5"),
            android.graphics.Color.parseColor("#00BCD4"),
            android.graphics.Color.parseColor("#795548")
        )

        val paintTitle = Paint().apply {
            color = android.graphics.Color.parseColor("#1565C0")
            textSize = 24f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintHeader = Paint().apply {
            color = android.graphics.Color.parseColor("#1565C0")
            textSize = 14f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintNormal = Paint().apply {
            color = android.graphics.Color.parseColor("#212121")
            textSize = 11f
            isAntiAlias = true
        }
        val paintBold = Paint().apply {
            color = android.graphics.Color.parseColor("#212121")
            textSize = 11f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintSubtle = Paint().apply {
            color = android.graphics.Color.parseColor("#757575")
            textSize = 10f
            isAntiAlias = true
        }
        val paintValue = Paint().apply {
            color = android.graphics.Color.parseColor("#1B5E20")
            textSize = 18f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintExpense = Paint().apply {
            color = android.graphics.Color.parseColor("#C62828")
            textSize = 18f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val paintBar = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        val paintBarBg = Paint().apply {
            color = android.graphics.Color.parseColor("#EEEEEE")
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        val paintPie = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }
        val paintPieStroke = Paint().apply {
            color = android.graphics.Color.WHITE
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val paintDivider = Paint().apply {
            color = android.graphics.Color.parseColor("#E0E0E0")
            strokeWidth = 1f
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > pageHeight - 50) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                canvas = page.canvas
                y = topMargin
            }
        }

        fun drawText(text: String, paint: Paint = paintNormal, extraSpacing: Float = 0f) {
            ensureSpace(lineHeight + extraSpacing)
            y += lineHeight + extraSpacing
            canvas.drawText(text, leftMargin, y, paint)
        }

        fun drawDivider() {
            y += 10f
            ensureSpace(12f)
            canvas.drawLine(leftMargin, y, pageWidth - rightMargin, y, paintDivider)
            y += 6f
        }

        // ════════════════════════════════════════════════════════════════
        // TITLE & PERIOD
        // ════════════════════════════════════════════════════════════════
        drawText("FlowSense Monthly Report", paintTitle, 10f)
        drawText(periodLabel, paintHeader)
        drawDivider()

        // ════════════════════════════════════════════════════════════════
        // FINANCIAL SUMMARY (card-style layout)
        // ════════════════════════════════════════════════════════════════
        drawText("FINANCIAL SUMMARY", paintHeader, 6f)
        y += 8f
        ensureSpace(60f)

        // Draw summary in a grid-like layout
        val col1x = leftMargin
        val col2x = leftMargin + contentWidth / 2

        y += lineHeight
        canvas.drawText("Total Expenses", col1x, y, paintSubtle)
        canvas.drawText("Total Income", col2x, y, paintSubtle)
        y += 20f
        canvas.drawText(CurrencyUtils.format(report.totalExpenses, currencyCode), col1x, y, paintExpense)
        canvas.drawText(CurrencyUtils.format(report.totalIncome, currencyCode), col2x, y, paintValue)

        y += lineHeight + 8f
        canvas.drawText("Net Balance", col1x, y, paintSubtle)
        canvas.drawText("Avg Daily Spend", col2x, y, paintSubtle)
        y += 20f
        val balancePaint = if (report.netBalance >= 0) paintValue else paintExpense
        canvas.drawText(CurrencyUtils.format(report.netBalance, currencyCode), col1x, y, balancePaint)
        canvas.drawText(CurrencyUtils.format(report.averageDailySpend, currencyCode), col2x, y, paintNormal)

        y += lineHeight + 4f
        canvas.drawText("Transactions: ${report.transactionCount}", col1x, y, paintNormal)
        if (report.comparisonWithPrevious != 0.0) {
            val sign = if (report.comparisonWithPrevious > 0) "+" else ""
            canvas.drawText("vs Previous: $sign${String.format("%.1f", report.comparisonWithPrevious)}%", col2x, y, paintNormal)
        }

        if (report.totalIncome > 0) {
            val savingsRate = ((report.totalIncome - report.totalExpenses) / report.totalIncome * 100)
                .coerceIn(0.0, 100.0).roundToInt()
            y += lineHeight
            canvas.drawText("Savings Rate: $savingsRate%", col1x, y, paintBold)
        }

        drawDivider()

        // ════════════════════════════════════════════════════════════════
        // PIE CHART — Category Breakdown
        // ════════════════════════════════════════════════════════════════
        if (report.categoryBreakdown.isNotEmpty()) {
            drawText("SPENDING BY CATEGORY", paintHeader, 6f)
            y += 10f

            val pieRadius = 70f
            val pieCenterX = leftMargin + pieRadius + 10f
            val pieCenterY = y + pieRadius + 5f
            val legendX = pieCenterX + pieRadius + 30f

            ensureSpace(pieRadius * 2 + 20f)

            // Draw pie chart
            val sortedCategories = report.categoryBreakdown.entries.sortedByDescending { it.value }
            var startAngle = -90f
            val oval = android.graphics.RectF(
                pieCenterX - pieRadius, pieCenterY - pieRadius,
                pieCenterX + pieRadius, pieCenterY + pieRadius
            )

            sortedCategories.forEachIndexed { index, (_, amt) ->
                val sweepAngle = if (report.totalExpenses > 0)
                    (amt / report.totalExpenses * 360f).toFloat() else 0f
                paintPie.color = chartColors[index % chartColors.size]
                canvas.drawArc(oval, startAngle, sweepAngle, true, paintPie)
                canvas.drawArc(oval, startAngle, sweepAngle, true, paintPieStroke)
                startAngle += sweepAngle
            }

            // Legend next to pie
            var legendY = pieCenterY - pieRadius + 12f
            val legendLineHeight = 16f
            sortedCategories.take(8).forEachIndexed { index, (cat, amt) ->
                val pct = if (report.totalExpenses > 0) (amt / report.totalExpenses * 100).roundToInt() else 0
                // Color square
                paintPie.color = chartColors[index % chartColors.size]
                canvas.drawRect(legendX, legendY - 8f, legendX + 10f, legendY + 2f, paintPie)
                // Label
                canvas.drawText(
                    "$cat: ${CurrencyUtils.format(amt, currencyCode)} ($pct%)",
                    legendX + 14f, legendY, paintNormal
                )
                legendY += legendLineHeight
            }

            y = pieCenterY + pieRadius + 15f

            // Category bars below the pie
            val barMaxWidth = contentWidth - 20f
            val barHeight = 10f
            val maxAmount = report.categoryBreakdown.values.maxOrNull() ?: 1.0

            sortedCategories.forEach { (cat, amt) ->
                val pct = if (report.totalExpenses > 0) (amt / report.totalExpenses * 100).roundToInt() else 0
                ensureSpace(lineHeight + barHeight + 8f)
                y += lineHeight
                canvas.drawText("$cat ($pct%)", leftMargin, y, paintNormal)
                canvas.drawText(CurrencyUtils.format(amt, currencyCode), pageWidth - rightMargin - paintNormal.measureText(CurrencyUtils.format(amt, currencyCode)), y, paintNormal)
                y += 4f
                // Background bar
                canvas.drawRoundRect(
                    leftMargin, y, leftMargin + barMaxWidth, y + barHeight,
                    5f, 5f, paintBarBg
                )
                // Filled bar
                val frac = (amt / maxAmount).toFloat().coerceIn(0f, 1f)
                paintBar.color = chartColors[sortedCategories.indexOf(java.util.AbstractMap.SimpleEntry(cat, amt)) % chartColors.size]
                val filledWidth = (barMaxWidth * frac).coerceAtLeast(3f)
                canvas.drawRoundRect(
                    leftMargin, y, leftMargin + filledWidth, y + barHeight,
                    5f, 5f, paintBar
                )
                y += barHeight + 4f
            }

            drawDivider()
        }

        // ════════════════════════════════════════════════════════════════
        // BAR CHART — Day of Week Spending (compact)
        // ════════════════════════════════════════════════════════════════
        if (report.dayOfWeekSpending.isNotEmpty() && report.dayOfWeekSpending.values.any { it > 0 }) {
            val dayOrder = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            val maxDaySpend = report.dayOfWeekSpending.values.maxOrNull() ?: 1.0
            val barMaxWidth = contentWidth - 60f
            val rowHeight = 22f

            // Ensure header + all bars fit on the same page
            ensureSpace(lineHeight + 6f + 6f + rowHeight * 7 + 18f)

            drawText("SPENDING BY DAY OF WEEK", paintHeader, 6f)
            y += 6f

            // Horizontal bars — compact and clean
            dayOrder.forEach { day ->
                val amount = report.dayOfWeekSpending[day] ?: 0.0
                val frac = if (maxDaySpend > 0) (amount / maxDaySpend).toFloat().coerceIn(0f, 1f) else 0f

                y += rowHeight

                // Day label (fixed width)
                canvas.drawText(day, leftMargin, y, paintBold)

                // Bar background
                val barLeft = leftMargin + 36f
                val barTop = y - 10f
                val barH = 12f
                canvas.drawRoundRect(
                    barLeft, barTop, barLeft + barMaxWidth, barTop + barH,
                    6f, 6f, paintBarBg
                )

                // Bar fill with gradient-like color
                if (amount > 0) {
                    paintBar.color = if (day == "Sat" || day == "Sun")
                        android.graphics.Color.parseColor("#FF9800")
                    else
                        android.graphics.Color.parseColor("#42A5F5")
                    val filledWidth = (barMaxWidth * frac).coerceAtLeast(4f)
                    canvas.drawRoundRect(
                        barLeft, barTop, barLeft + filledWidth, barTop + barH,
                        6f, 6f, paintBar
                    )
                }

                // Amount at end
                val amtText = CurrencyUtils.formatCompact(amount, currencyCode)
                canvas.drawText(amtText, barLeft + barMaxWidth + 4f, y, paintSubtle)
            }

            y += 8f
            drawDivider()
        }

        // ════════════════════════════════════════════════════════════════
        // TOP MERCHANTS
        // ════════════════════════════════════════════════════════════════
        if (report.topMerchants.isNotEmpty()) {
            drawText("TOP MERCHANTS", paintHeader, 6f)
            val maxMerchantAmt = report.topMerchants.values.maxOrNull() ?: 1.0
            report.topMerchants.entries.take(8).forEachIndexed { index, (merchant, amt) ->
                ensureSpace(lineHeight + 14f)
                y += lineHeight
                canvas.drawText(merchant, leftMargin, y, paintNormal)
                canvas.drawText(CurrencyUtils.format(amt, currencyCode),
                    pageWidth - rightMargin - paintNormal.measureText(CurrencyUtils.format(amt, currencyCode)), y, paintNormal)
                y += 4f
                // Mini bar
                val barW = contentWidth * 0.6f
                val barH = 6f
                canvas.drawRoundRect(leftMargin, y, leftMargin + barW, y + barH, 3f, 3f, paintBarBg)
                val frac = (amt / maxMerchantAmt).toFloat().coerceIn(0f, 1f)
                paintBar.color = chartColors[index % chartColors.size]
                canvas.drawRoundRect(leftMargin, y, leftMargin + barW * frac, y + barH, 3f, 3f, paintBar)
                y += barH + 2f
            }
            drawDivider()
        }

        // ════════════════════════════════════════════════════════════════
        // SOURCE BREAKDOWN
        // ════════════════════════════════════════════════════════════════
        if (report.sourceBreakdown.isNotEmpty()) {
            drawText("TRANSACTION SOURCES", paintHeader, 6f)
            report.sourceBreakdown.entries.sortedByDescending { it.value }.forEach { (source, count) ->
                drawText("  $source: $count transactions", paintNormal)
            }
            drawDivider()
        }

        // ════════════════════════════════════════════════════════════════
        // AI INSIGHT
        // ════════════════════════════════════════════════════════════════
        if (report.aiInsight.isNotEmpty()) {
            drawText("AI INSIGHT & ANALYSIS", paintHeader, 6f)
            y += 4f
            // Draw AI insight with word wrapping
            wrapText(report.aiInsight, paintNormal, contentWidth).forEach { line ->
                drawText(line, paintNormal)
            }
            drawDivider()
        }

        // ════════════════════════════════════════════════════════════════
        // EXPENSE REDUCTION TIPS
        // ════════════════════════════════════════════════════════════════
        if (report.totalExpenses > 0) {
            val tips = AiExpenseEngine().generateExpenseReductionTips(report, currencyCode)
            if (tips.isNotEmpty()) {
                drawText("RECOMMENDATIONS", paintHeader, 6f)
                tips.forEachIndexed { index, tip ->
                    wrapText("${index + 1}. $tip", paintNormal, contentWidth).forEach { line ->
                        drawText(line, paintNormal)
                    }
                }
                drawDivider()
            }
        }

        // ════════════════════════════════════════════════════════════════
        // ALL TRANSACTIONS BY DATE
        // ════════════════════════════════════════════════════════════════
        if (report.transactionsByDate.isNotEmpty()) {
            drawText("ALL TRANSACTIONS", paintHeader, 6f)
            report.transactionsByDate.entries.sortedByDescending { it.key }.forEach { (dateStr, txList) ->
                val dayExpenses = txList.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                val dayIncome = txList.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                ensureSpace(lineHeight + 4f)
                y += lineHeight + 6f
                canvas.drawText("$dateStr", leftMargin, y, paintBold)
                val summaryText = buildString {
                    if (dayExpenses > 0) append("−${CurrencyUtils.format(dayExpenses, currencyCode)}")
                    if (dayIncome > 0) {
                        if (dayExpenses > 0) append("  ")
                        append("+${CurrencyUtils.format(dayIncome, currencyCode)}")
                    }
                }
                canvas.drawText(summaryText, pageWidth - rightMargin - paintSubtle.measureText(summaryText), y, paintSubtle)

                txList.forEach { tx ->
                    ensureSpace(lineHeight + 2f)
                    y += lineHeight
                    val typePrefix = if (tx.type == TransactionType.EXPENSE) "−" else "+"
                    val desc = tx.description.take(35)
                    canvas.drawText(
                        "  $typePrefix${CurrencyUtils.format(tx.amount, currencyCode)}  $desc",
                        leftMargin, y, paintNormal
                    )
                    val catText = "[${tx.category}]"
                    canvas.drawText(catText, pageWidth - rightMargin - paintSubtle.measureText(catText), y, paintSubtle)
                }
            }
        }

        // ════════════════════════════════════════════════════════════════
        // FOOTER
        // ════════════════════════════════════════════════════════════════
        drawText("")
        drawDivider()
        drawText(
            "Generated by FlowSense on ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}",
            paintSubtle
        )

        document.finishPage(page)

        val reportsDir = File(applicationContext.cacheDir, "reports").apply { mkdirs() }
        val fileName = "FlowSense_Report_${periodLabel.replace(" ", "_")}.pdf"
        val file = File(reportsDir, fileName)
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()

        return file
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine)
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) lines.add(currentLine)
        return lines.ifEmpty { listOf("") }
    }
}

