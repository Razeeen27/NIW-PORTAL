package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthManager
import com.example.data.auth.AuthState
import com.example.data.cloud.CloudStatus
import com.example.data.model.MaterialInwardEntry
import com.example.data.model.UserRole
import com.example.data.repository.MimsRepository
import com.google.firebase.auth.FirebaseUser
import com.example.util.ExcelReportGenerator
import com.example.util.FileSharingUtil
import com.example.util.PdfReportGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class NewEntryFormState(
    val date: String = "",
    val time: String = "",
    val vehicleNumber: String = "",
    val customerName: String = "",
    val driverName: String = "",
    val driverNumber: String = "",
    val photoUri: Uri? = null,
    val photoPath: String? = null,
    val isSubmitting: Boolean = false,
    val duplicateWarningEntries: List<MaterialInwardEntry>? = null,
    val submissionSuccessEntry: MaterialInwardEntry? = null,
    val errorMessage: String? = null
)

class MimsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MimsRepository(application)
    private val authManager = AuthManager()

    val authState: StateFlow<AuthState> = authManager.authState
    val currentUser: StateFlow<FirebaseUser?> = authManager.currentUser

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _authErrorMessage = MutableStateFlow<String?>(null)
    val authErrorMessage: StateFlow<String?> = _authErrorMessage.asStateFlow()

    // Current User Role (defaults to Gate Operator / Employee)
    private val _currentUserRole = MutableStateFlow(UserRole.EMPLOYEE)
    val currentUserRole: StateFlow<UserRole> = _currentUserRole.asStateFlow()

    init {
        viewModelScope.launch {
            authManager.authState.collect { state ->
                if (state is AuthState.Authenticated) {
                    _currentUserRole.value = state.role
                } else if (state is AuthState.Unauthenticated) {
                    _currentUserRole.value = UserRole.EMPLOYEE
                }
            }
        }
    }

    fun clearAuthError() {
        _authErrorMessage.value = null
    }

    fun signInWithEmail(email: String, pass: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authErrorMessage.value = null
            val result = authManager.signInWithEmail(email, pass)
            _isAuthLoading.value = false
            result.onSuccess { user ->
                _currentUserRole.value = authManager.deriveRoleFromUser(user)
            }.onFailure { error ->
                _authErrorMessage.value = error.message ?: "Authentication failed"
            }
        }
    }

    fun signOut() {
        authManager.signOut()
        _currentUserRole.value = UserRole.EMPLOYEE
    }

    fun setUserRole(role: UserRole) {
        _currentUserRole.value = role
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    private fun getTodayDateString(): String = dateFormat.format(Date())
    private fun getCurrentTimeString(): String = timeFormat.format(Date())

    // All entries flow
    val allEntries: StateFlow<List<MaterialInwardEntry>> = repository.getAllEntriesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Today entries flow
    val todayEntries: StateFlow<List<MaterialInwardEntry>> = repository.getTodayEntriesFlow(getTodayDateString())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Counters
    val todayCount: StateFlow<Int> = repository.getTodayCountFlow(getTodayDateString())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalCount: StateFlow<Int> = repository.getTotalCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Cloud status
    private val _cloudStatus = MutableStateFlow(repository.getCloudStatus())
    val cloudStatus: StateFlow<CloudStatus> = _cloudStatus.asStateFlow()

    fun refreshCloudStatus() {
        _cloudStatus.value = repository.getCloudStatus()
    }

    // Search and filter in History
    val searchQuery = MutableStateFlow("")
    val filterDateRange = MutableStateFlow("All") // "All", "Today", "This Week", "This Month"
    val filterCustomer = MutableStateFlow("")

    val filteredEntries: StateFlow<List<MaterialInwardEntry>> = combine(
        allEntries,
        searchQuery,
        filterDateRange,
        filterCustomer
    ) { entries, query, dateFilter, customerFilter ->
        val q = query.trim().lowercase(Locale.ROOT)
        val todayStr = getTodayDateString()

        entries.filter { entry ->
            // Search match
            val matchesQuery = q.isEmpty() ||
                entry.serialNumber.lowercase(Locale.ROOT).contains(q) ||
                entry.vehicleNumber.lowercase(Locale.ROOT).contains(q) ||
                entry.customerName.lowercase(Locale.ROOT).contains(q) ||
                entry.driverName.lowercase(Locale.ROOT).contains(q) ||
                entry.driverNumber.contains(q)

            // Date match
            val matchesDate = when (dateFilter) {
                "Today" -> entry.entryDate == todayStr
                "This Month" -> entry.entryDate.startsWith(todayStr.substring(0, 7))
                else -> true
            }

            // Customer match
            val matchesCustomer = customerFilter.isEmpty() || entry.customerName.equals(customerFilter, ignoreCase = true)

            matchesQuery && matchesDate && matchesCustomer
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // New Entry Form State
    private val _formState = MutableStateFlow(
        NewEntryFormState(
            date = getTodayDateString(),
            time = getCurrentTimeString()
        )
    )
    val formState: StateFlow<NewEntryFormState> = _formState.asStateFlow()

    fun resetForm() {
        _formState.value = NewEntryFormState(
            date = getTodayDateString(),
            time = getCurrentTimeString()
        )
    }

    fun onDateChanged(date: String) {
        _formState.value = _formState.value.copy(date = date, errorMessage = null)
    }

    fun onTimeChanged(time: String) {
        _formState.value = _formState.value.copy(time = time, errorMessage = null)
    }

    fun onVehicleNumberChanged(vehicleNo: String) {
        _formState.value = _formState.value.copy(
            vehicleNumber = vehicleNo.uppercase(Locale.ROOT),
            errorMessage = null,
            duplicateWarningEntries = null
        )
    }

    fun onCustomerNameChanged(customer: String) {
        _formState.value = _formState.value.copy(customerName = customer, errorMessage = null)
    }

    fun onDriverNameChanged(driver: String) {
        _formState.value = _formState.value.copy(driverName = driver, errorMessage = null)
    }

    fun onDriverNumberChanged(phone: String) {
        _formState.value = _formState.value.copy(driverNumber = phone, errorMessage = null)
    }

    fun onPhotoCaptured(uri: Uri) {
        onPhotoCapturedUri(uri)
    }

    fun onPhotoCapturedUri(uri: Uri) {
        _formState.value = _formState.value.copy(photoUri = uri, errorMessage = null)
        viewModelScope.launch {
            val localPath = repository.copyPhotoToInternalStorage(uri)
            _formState.value = _formState.value.copy(photoPath = localPath)
        }
    }

    fun onPhotoCapturedBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            val localPath = repository.saveBitmapToInternalStorage(bitmap)
            if (localPath != null) {
                val file = File(localPath)
                val uri = Uri.fromFile(file)
                _formState.value = _formState.value.copy(
                    photoUri = uri,
                    photoPath = localPath,
                    errorMessage = null
                )
            }
        }
    }

    fun setErrorMessage(message: String) {
        _formState.value = _formState.value.copy(errorMessage = message)
    }

    fun onPhotoRetake() {
        _formState.value = _formState.value.copy(photoUri = null, photoPath = null)
    }

    fun dismissDuplicateWarning() {
        _formState.value = _formState.value.copy(duplicateWarningEntries = null)
    }

    fun dismissSuccessDialog() {
        _formState.value = _formState.value.copy(submissionSuccessEntry = null)
        resetForm()
    }

    /**
     * Submits the entry with validations and duplicate vehicle check for the same day.
     */
    fun submitEntry(forceConfirmedDuplicate: Boolean = false) {
        val current = _formState.value
        if (current.isSubmitting) return

        // Validations
        val vehicleTrimmed = current.vehicleNumber.trim()
        val customerTrimmed = current.customerName.trim()
        val driverTrimmed = current.driverName.trim()
        val phoneTrimmed = current.driverNumber.trim()

        if (current.date.isBlank()) {
            _formState.value = current.copy(errorMessage = "Please specify the Entry Date")
            return
        }
        if (current.time.isBlank()) {
            _formState.value = current.copy(errorMessage = "Please specify the Time of Entry")
            return
        }
        if (vehicleTrimmed.length < 3) {
            _formState.value = current.copy(errorMessage = "Please enter a valid Vehicle Number (e.g. MH-12-AB-1234)")
            return
        }
        if (customerTrimmed.isBlank()) {
            _formState.value = current.copy(errorMessage = "Customer Name is required")
            return
        }
        if (driverTrimmed.isBlank()) {
            _formState.value = current.copy(errorMessage = "Driver Name is required")
            return
        }
        if (phoneTrimmed.length < 5) {
            _formState.value = current.copy(errorMessage = "Please enter a valid Driver Contact Number")
            return
        }
        if (current.photoPath == null && current.photoUri == null) {
            _formState.value = current.copy(errorMessage = "Vehicle photograph is required for facility gate record")
            return
        }

        viewModelScope.launch {
            _formState.value = current.copy(isSubmitting = true, errorMessage = null)

            // Duplicate vehicle check for today
            if (!forceConfirmedDuplicate) {
                val previousEntriesToday = repository.checkDuplicateVehicleToday(vehicleTrimmed, current.date)
                if (previousEntriesToday.isNotEmpty()) {
                    // Show warning without blocking legitimate repeat trips
                    _formState.value = current.copy(
                        isSubmitting = false,
                        duplicateWarningEntries = previousEntriesToday
                    )
                    return@launch
                }
            }

            val operatorIdentity = currentUser.value?.email ?: _currentUserRole.value.displayName
            val result = repository.createAndSaveEntry(
                entryDate = current.date,
                entryTime = current.time,
                vehicleNumber = vehicleTrimmed,
                customerName = customerTrimmed,
                driverName = driverTrimmed,
                driverNumber = phoneTrimmed,
                photoPath = current.photoPath,
                recordedBy = operatorIdentity
            )

            result.onSuccess { savedEntry ->
                _formState.value = current.copy(
                    isSubmitting = false,
                    submissionSuccessEntry = savedEntry,
                    duplicateWarningEntries = null,
                    errorMessage = null
                )
            }.onFailure { err ->
                _formState.value = current.copy(
                    isSubmitting = false,
                    errorMessage = "Failed to record entry: ${err.localizedMessage ?: "Database error"}"
                )
            }
        }
    }

    // Reports export
    fun exportDailyPdf(dateString: String) {
        viewModelScope.launch {
            val entries = allEntries.value.filter { it.entryDate == dateString }
            val file = PdfReportGenerator.generateReport(
                context = getApplication(),
                reportTitle = "Daily Inward Log ($dateString)",
                periodSubtitle = dateString,
                entries = entries,
                generatedByRole = currentUserRole.value.displayName
            )
            file?.let {
                FileSharingUtil.shareOrOpenFile(
                    context = getApplication(),
                    file = it,
                    mimeType = "application/pdf",
                    chooserTitle = "Export NIW Portal Daily PDF Report"
                )
            }
        }
    }

    fun exportMonthlyPdf(yearMonth: String) {
        viewModelScope.launch {
            val entries = allEntries.value.filter { it.entryDate.startsWith(yearMonth) }
            val file = PdfReportGenerator.generateReport(
                context = getApplication(),
                reportTitle = "Monthly Inward Summary ($yearMonth)",
                periodSubtitle = yearMonth,
                entries = entries,
                generatedByRole = currentUserRole.value.displayName
            )
            file?.let {
                FileSharingUtil.shareOrOpenFile(
                    context = getApplication(),
                    file = it,
                    mimeType = "application/pdf",
                    chooserTitle = "Export NIW Portal Monthly PDF Report"
                )
            }
        }
    }

    fun exportDailyExcel(dateString: String) {
        viewModelScope.launch {
            val entries = allEntries.value.filter { it.entryDate == dateString }
            val file = ExcelReportGenerator.generateReport(
                context = getApplication(),
                reportTitle = "Daily_Inward_$dateString",
                entries = entries
            )
            file?.let {
                FileSharingUtil.shareOrOpenFile(
                    context = getApplication(),
                    file = it,
                    mimeType = "text/csv",
                    chooserTitle = "Export NIW Portal Daily Excel/CSV Report"
                )
            }
        }
    }

    fun exportMonthlyExcel(yearMonth: String) {
        viewModelScope.launch {
            val entries = allEntries.value.filter { it.entryDate.startsWith(yearMonth) }
            val file = ExcelReportGenerator.generateReport(
                context = getApplication(),
                reportTitle = "Monthly_Inward_$yearMonth",
                entries = entries
            )
            file?.let {
                FileSharingUtil.shareOrOpenFile(
                    context = getApplication(),
                    file = it,
                    mimeType = "text/csv",
                    chooserTitle = "Export NIW Portal Monthly Excel/CSV Report"
                )
            }
        }
    }
}
