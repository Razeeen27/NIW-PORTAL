package com.example.util

import android.content.Context
import com.example.data.model.MaterialInwardEntry
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object ExcelReportGenerator {

    /**
     * Generates a CSV file formatted for Excel and spreadsheet software,
     * including UTF-8 Byte Order Mark (BOM) so Excel opens it with proper character encoding.
     */
    fun generateReport(
        context: Context,
        reportTitle: String,
        entries: List<MaterialInwardEntry>
    ): File? {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val filename = "NIW_Portal_${reportTitle.replace(" ", "_")}_${System.currentTimeMillis()}.csv"
        val outputFile = File(reportsDir, filename)

        return try {
            val fos = FileOutputStream(outputFile)
            val writer = OutputStreamWriter(fos, StandardCharsets.UTF_8)

            // UTF-8 BOM for Microsoft Excel compatibility
            writer.write("\uFEFF")

            // Title block
            writer.write("NIW Portal - Material Inward Management System (Waste Facility)\n")
            writer.write("Report:,$reportTitle\n")
            writer.write("Total Records:,${entries.size}\n\n")

            // Headers
            writer.write("Serial Number,Date,Time,Vehicle Number,Customer Name,Driver Name,Driver Phone,Recorded By,Cloud Sync Status,Timestamp\n")

            // Rows
            for (entry in entries) {
                val line = buildString {
                    append(escapeCsv(entry.serialNumber)).append(",")
                    append(escapeCsv(entry.entryDate)).append(",")
                    append(escapeCsv(entry.entryTime)).append(",")
                    append(escapeCsv(entry.vehicleNumber)).append(",")
                    append(escapeCsv(entry.customerName)).append(",")
                    append(escapeCsv(entry.driverName)).append(",")
                    append(escapeCsv(entry.driverNumber)).append(",")
                    append(escapeCsv(entry.recordedBy)).append(",")
                    append(if (entry.isSyncedToCloud) "Synced" else "Local Database").append(",")
                    append(entry.recordedAtTimestamp)
                    append("\n")
                }
                writer.write(line)
            }

            writer.flush()
            writer.close()
            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
