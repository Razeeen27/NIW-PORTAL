package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.MaterialInwardEntry
import com.example.util.ExcelReportGenerator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testAppNameString() {
        val appName = context.getString(R.string.app_name)
        assertEquals("NIW Portal", appName)
    }

    @Test
    fun testSequentialSerialNumberGeneration() = runBlocking {
        val seqDao = database.sequenceDao()
        val seq1 = seqDao.getNextSequenceNumber(2026)
        val seq2 = seqDao.getNextSequenceNumber(2026)
        val seq3 = seqDao.getNextSequenceNumber(2026)

        assertEquals(1, seq1)
        assertEquals(2, seq2)
        assertEquals(3, seq3)

        val formattedSerial = String.format("NIW-2026-%04d", seq1)
        assertEquals("NIW-2026-0001", formattedSerial)
    }

    @Test
    fun testPreservesExistingRecordsAndAcceptsNewPrefix() = runBlocking {
        val entryDao = database.entryDao()
        // Existing legacy record
        val legacyEntry = MaterialInwardEntry(
            serialNumber = "MIMS-2026-0001",
            entryDate = "2026-10-22",
            entryTime = "09:30",
            vehicleNumber = "MH-12-AB-1234",
            customerName = "EcoWaste Ltd",
            driverName = "John Doe",
            driverNumber = "9876543210",
            photoPath = null,
            recordedBy = "Gate Operator",
            recordedAtTimestamp = 1000L
        )
        // New record with NIW- prefix
        val newEntry = MaterialInwardEntry(
            serialNumber = "NIW-2026-0002",
            entryDate = "2026-10-22",
            entryTime = "11:45",
            vehicleNumber = "MH-12-AB-1234",
            customerName = "EcoWaste Ltd",
            driverName = "John Doe",
            driverNumber = "9876543210",
            photoPath = null,
            recordedBy = "Gate Operator",
            recordedAtTimestamp = 2000L
        )
        entryDao.insertEntry(legacyEntry)
        entryDao.insertEntry(newEntry)

        val resultsToday = entryDao.getEntriesForVehicleOnDate("MH-12-AB-1234", "2026-10-22")
        assertEquals(2, resultsToday.size)
        assertEquals("NIW-2026-0002", resultsToday[0].serialNumber)
        assertEquals("MIMS-2026-0001", resultsToday[1].serialNumber)
    }

    @Test
    fun testExcelCsvReportGeneration() {
        val entries = listOf(
            MaterialInwardEntry(
                serialNumber = "NIW-2026-0001",
                entryDate = "2026-10-22",
                entryTime = "10:15",
                vehicleNumber = "KA-01-E-9999",
                customerName = "CleanGreen Industries",
                driverName = "Alex Smith",
                driverNumber = "9123456780",
                photoPath = null,
                recordedBy = "Gate Operator"
            )
        )

        val file: File? = ExcelReportGenerator.generateReport(context, "Daily_Test", entries)
        assertNotNull(file)
        assertTrue(file!!.exists())
        val content = file.readText()
        assertTrue(content.contains("NIW Portal"))
        assertTrue(content.contains("NIW-2026-0001"))
        assertTrue(content.contains("KA-01-E-9999"))
        assertTrue(content.contains("CleanGreen Industries"))
    }
}
