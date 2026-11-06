package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.model.MaterialInwardEntry
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportGenerator {

    fun generateReport(
        context: Context,
        reportTitle: String,
        periodSubtitle: String,
        entries: List<MaterialInwardEntry>,
        generatedByRole: String
    ): File? {
        val pdfDocument = PdfDocument()
        val pageNumber = 1
        val pageWidth = 595 // Standard A4 width in PostScript points at 72 dpi
        val pageHeight = 842 // Standard A4 height
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Background
        canvas.drawColor(Color.WHITE)

        // Header Background Banner
        paint.color = Color.parseColor("#0F3A2E") // Forest green
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 95f, paint)

        // Header Title
        paint.color = Color.WHITE
        paint.textSize = 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("NIW Portal - Material Inward Management System", 24f, 40f, paint)

        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.parseColor("#A7F3D0") // Emerald tint
        canvas.drawText("Waste Management Facility Log • $reportTitle", 24f, 62f, paint)

        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        paint.textSize = 10f
        paint.color = Color.parseColor("#D1D5DB")
        canvas.drawText("Generated: $timeStr | Operator: $generatedByRole", 24f, 82f, paint)

        // Summary Card
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        cardPaint.color = Color.parseColor("#F1F5F9")
        canvas.drawRoundRect(24f, 108f, pageWidth - 24f, 160f, 8f, 8f, cardPaint)

        paint.color = Color.parseColor("#1E293B")
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("SUMMARY STATISTICS", 36f, 128f, paint)

        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.parseColor("#475569")
        val uniqueVehicles = entries.map { it.vehicleNumber.uppercase() }.distinct().size
        val uniqueCustomers = entries.map { it.customerName }.distinct().size
        canvas.drawText("Total Incoming Vehicles: ${entries.size}", 36f, 146f, paint)
        canvas.drawText("Unique Vehicles: $uniqueVehicles", 220f, 146f, paint)
        canvas.drawText("Active Customers: $uniqueCustomers", 380f, 146f, paint)

        // Table Header
        var currentY = 185f
        paint.color = Color.parseColor("#0F3A2E")
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        val colSerial = 24f
        val colDateTime = 110f
        val colVehicle = 200f
        val colCustomer = 295f
        val colDriver = 430f

        canvas.drawText("Serial No", colSerial, currentY, paint)
        canvas.drawText("Date & Time", colDateTime, currentY, paint)
        canvas.drawText("Vehicle No", colVehicle, currentY, paint)
        canvas.drawText("Customer", colCustomer, currentY, paint)
        canvas.drawText("Driver & Contact", colDriver, currentY, paint)

        // Table divider
        paint.color = Color.parseColor("#CBD5E1")
        paint.strokeWidth = 1.2f
        canvas.drawLine(24f, currentY + 6f, pageWidth - 24f, currentY + 6f, paint)

        currentY += 24f
        val maxEntriesPerPage = 22
        val displayEntries = entries.take(maxEntriesPerPage)

        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        val rowBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        rowBgPaint.color = Color.parseColor("#F8FAFC")

        displayEntries.forEachIndexed { index, entry ->
            if (index % 2 == 1) {
                canvas.drawRect(24f, currentY - 14f, pageWidth - 24f, currentY + 6f, rowBgPaint)
            }

            paint.color = Color.parseColor("#0F172A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(entry.serialNumber, colSerial, currentY, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = Color.parseColor("#334155")
            canvas.drawText("${entry.entryDate} ${entry.entryTime}", colDateTime, currentY, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.color = Color.parseColor("#0F3A2E")
            canvas.drawText(entry.vehicleNumber, colVehicle, currentY, paint)

            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paint.color = Color.parseColor("#1E293B")
            val truncatedCustomer = if (entry.customerName.length > 20) entry.customerName.take(18) + ".." else entry.customerName
            canvas.drawText(truncatedCustomer, colCustomer, currentY, paint)

            val driverInfo = "${entry.driverName} (${entry.driverNumber})"
            val truncatedDriver = if (driverInfo.length > 24) driverInfo.take(22) + ".." else driverInfo
            canvas.drawText(truncatedDriver, colDriver, currentY, paint)

            currentY += 24f
        }

        if (entries.size > maxEntriesPerPage) {
            paint.color = Color.parseColor("#64748B")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            canvas.drawText("... and ${entries.size - maxEntriesPerPage} more inward entries (see full Excel report for complete log)", 24f, currentY + 10f, paint)
        }

        // Footer
        paint.color = Color.parseColor("#94A3B8")
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Page 1 of 1 • NIW Portal Waste Management Facility Internal Record", 24f, pageHeight - 30f, paint)

        pdfDocument.finishPage(page)

        // Write to file
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val filename = "NIW_Portal_Report_${System.currentTimeMillis()}.pdf"
        val outputFile = File(reportsDir, filename)

        return try {
            val fos = FileOutputStream(outputFile)
            pdfDocument.writeTo(fos)
            fos.close()
            pdfDocument.close()
            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }
}
