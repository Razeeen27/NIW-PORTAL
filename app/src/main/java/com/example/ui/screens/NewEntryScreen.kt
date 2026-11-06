package com.example.ui.screens

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.ui.components.DuplicateWarningDialog
import com.example.ui.components.SuccessSubmissionDialog
import com.example.ui.viewmodel.MimsViewModel
import java.io.File
import java.util.Calendar
import java.util.Locale

@Composable
fun NewEntryScreen(
    viewModel: MimsViewModel,
    onNavigateToHistory: () -> Unit
) {
    val context = LocalContext.current
    val formState by viewModel.formState.collectAsState()
    val scrollState = rememberScrollState()

    // Persistent storage for pending photo file path across activity recreations
    var pendingPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }

    // Dialog state controllers for camera failure & permission handling
    var showNoCameraDialog by rememberSaveable { mutableStateOf(false) }
    var showPermissionDeniedDialog by rememberSaveable { mutableStateOf(false) }
    var showFullscreenPhoto by remember { mutableStateOf(false) }

    // 1. Full-resolution file-based camera capture launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingPhotoPath != null) {
            val file = File(pendingPhotoPath!!)
            if (file.exists() && file.length() > 0) {
                val uri = Uri.fromFile(file)
                viewModel.onPhotoCapturedUri(uri)
            } else {
                file.delete()
            }
        } else {
            // Clean up empty placeholder if user cancelled
            pendingPhotoPath?.let { path ->
                val file = File(path)
                if (file.exists() && file.length() == 0L) file.delete()
            }
        }
    }

    // 2. Direct bitmap camera capture fallback (handles devices where file URI intent fails)
    val takePicturePreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            viewModel.onPhotoCapturedBitmap(bitmap)
        }
    }

    // 3. Photo library/gallery picker
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onPhotoCapturedUri(it) }
    }

    // Camera execution logic wrapped in safety checks
    fun executeCameraLaunch() {
        try {
            val cameraDir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
            val photoFile = File(cameraDir, "vehicle_${System.currentTimeMillis()}.jpg")
            if (!photoFile.exists()) {
                photoFile.createNewFile()
            }
            pendingPhotoPath = photoFile.absolutePath

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )

            // Test if an application can handle this camera capture intent
            val testIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val canResolve = testIntent.resolveActivity(context.packageManager) != null
            if (canResolve) {
                takePictureLauncher.launch(uri)
            } else {
                // If specific output URI intent cannot resolve, attempt thumbnail preview camera
                try {
                    takePicturePreviewLauncher.launch(null)
                } catch (e: ActivityNotFoundException) {
                    showNoCameraDialog = true
                } catch (e: Exception) {
                    showNoCameraDialog = true
                }
            }
        } catch (e: ActivityNotFoundException) {
            showNoCameraDialog = true
        } catch (e: SecurityException) {
            showPermissionDeniedDialog = true
        } catch (e: Exception) {
            // If primary camera launch throws, attempt thumbnail preview before erroring
            try {
                takePicturePreviewLauncher.launch(null)
            } catch (e2: Exception) {
                viewModel.setErrorMessage("Camera could not be launched: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    // 4. Runtime camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            executeCameraLaunch()
        } else {
            showPermissionDeniedDialog = true
        }
    }

    // Entry point for requesting camera
    fun requestCameraCapture() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            executeCameraLaunch()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Date picker dialog
    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val formattedDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
            viewModel.onDateChanged(formattedDate)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    // Time picker dialog
    val timePickerDialog = TimePickerDialog(
        context,
        { _, hourOfDay, minute ->
            val formattedTime = String.format(Locale.US, "%02d:%02d", hourOfDay, minute)
            viewModel.onTimeChanged(formattedTime)
        },
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
        true
    )

    // Camera Unavailable Dialog
    if (showNoCameraDialog) {
        AlertDialog(
            onDismissRequest = { showNoCameraDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.NoPhotography,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Camera App Unavailable",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "No compatible camera app was detected on this device or emulator. You can select an existing vehicle photograph from your gallery or files to continue logging.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNoCameraDialog = false
                        try {
                            pickImageLauncher.launch("image/*")
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open gallery: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("dialog_choose_gallery_button")
                ) {
                    Icon(imageVector = Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Select from Gallery / Files")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoCameraDialog = false }) {
                    Text("Dismiss")
                }
            }
        )
    }

    // Permission Denied Dialog
    if (showPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDeniedDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Camera Permission Required",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "NIW Portal needs camera access to photograph incoming vehicles for waste facility gate verification. Please allow camera access, or choose a photo from your gallery.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionDeniedDialog = false
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.testTag("retry_permission_button")
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showPermissionDeniedDialog = false
                        pickImageLauncher.launch("image/*")
                    }
                ) {
                    Text("Use Gallery Instead")
                }
            }
        )
    }

    // Fullscreen Photo Preview Dialog
    if (showFullscreenPhoto && (formState.photoUri != null || formState.photoPath != null)) {
        Dialog(onDismissRequest = { showFullscreenPhoto = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black, RoundedCornerShape(16.dp))
                    .padding(8.dp)
            ) {
                AsyncImage(
                    model = formState.photoUri ?: formState.photoPath?.let { File(it) },
                    contentDescription = "Fullscreen Vehicle Photograph",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                )
                IconButton(
                    onClick = { showFullscreenPhoto = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }

    // Duplicate Warning Dialog
    if (formState.duplicateWarningEntries != null) {
        DuplicateWarningDialog(
            vehicleNumber = formState.vehicleNumber,
            todayEntries = formState.duplicateWarningEntries!!,
            onConfirmRepeatTrip = {
                viewModel.submitEntry(forceConfirmedDuplicate = true)
            },
            onDismiss = {
                viewModel.dismissDuplicateWarning()
            }
        )
    }

    // Success Submission Dialog
    if (formState.submissionSuccessEntry != null) {
        SuccessSubmissionDialog(
            entry = formState.submissionSuccessEntry!!,
            onRecordAnother = {
                viewModel.dismissSuccessDialog()
            },
            onViewHistory = {
                viewModel.dismissSuccessDialog()
                onNavigateToHistory()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Form Title & Instructions
        Text(
            text = "Record Incoming Vehicle",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Log material inward entry with required facility gate fields & vehicle photo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Error message banner
        if (formState.errorMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formState.errorMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 1. Date & Time Row (exactly required fields: Date, Time of Entry)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Date Field
            OutlinedTextField(
                value = formState.date,
                onValueChange = {},
                readOnly = true,
                label = { Text("Date") },
                trailingIcon = {
                    IconButton(onClick = { datePickerDialog.show() }) {
                        Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = "Pick Date")
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .clickable { datePickerDialog.show() }
                    .testTag("entry_date_field"),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )

            // Time of Entry Field
            OutlinedTextField(
                value = formState.time,
                onValueChange = {},
                readOnly = true,
                label = { Text("Time of Entry") },
                trailingIcon = {
                    IconButton(onClick = { timePickerDialog.show() }) {
                        Icon(imageVector = Icons.Default.Schedule, contentDescription = "Pick Time")
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .clickable { timePickerDialog.show() }
                    .testTag("entry_time_field"),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Vehicle Number Field
        OutlinedTextField(
            value = formState.vehicleNumber,
            onValueChange = { viewModel.onVehicleNumberChanged(it) },
            label = { Text("Vehicle Number *") },
            placeholder = { Text("e.g. MH-12-AB-1234") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.DirectionsCar, contentDescription = null)
            },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("vehicle_number_field"),
            shape = RoundedCornerShape(10.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Customer Name Field
        OutlinedTextField(
            value = formState.customerName,
            onValueChange = { viewModel.onCustomerNameChanged(it) },
            label = { Text("Customer Name *") },
            placeholder = { Text("e.g. BioClean Waste Sol. / GreenRecycle") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Badge, contentDescription = null)
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("customer_name_field"),
            shape = RoundedCornerShape(10.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Driver Name Field
        OutlinedTextField(
            value = formState.driverName,
            onValueChange = { viewModel.onDriverNameChanged(it) },
            label = { Text("Driver Name *") },
            placeholder = { Text("Driver full name") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Person, contentDescription = null)
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("driver_name_field"),
            shape = RoundedCornerShape(10.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 5. Driver Number Field
        OutlinedTextField(
            value = formState.driverNumber,
            onValueChange = { viewModel.onDriverNumberChanged(it) },
            label = { Text("Driver Number (Phone) *") },
            placeholder = { Text("e.g. 9876543210") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Phone, contentDescription = null)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("driver_number_field"),
            shape = RoundedCornerShape(10.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Photograph Section
        Text(
            text = "Vehicle Photograph *",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Capture clear photo of incoming vehicle and license plate for gate verification.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (formState.photoUri != null || formState.photoPath != null) {
            // Preview existing photo with retake option and full inspection
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showFullscreenPhoto = true }
                    .testTag("photo_preview_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = formState.photoUri ?: formState.photoPath?.let { File(it) },
                        contentDescription = "Vehicle Photo Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Top-right controls: Remove & Fullscreen
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = { showFullscreenPhoto = true },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fullscreen,
                                contentDescription = "View Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.onPhotoRetake() },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove Photo",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Bottom Retake & Re-select actions overlay
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Photo Attached",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { requestCameraCapture() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                modifier = Modifier.testTag("retake_photo_button")
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retake", style = MaterialTheme.typography.labelSmall)
                            }

                            OutlinedButton(
                                onClick = { pickImageLauncher.launch("image/*") },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                modifier = Modifier.testTag("change_gallery_button")
                            ) {
                                Icon(imageVector = Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Gallery", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        } else {
            // Take Photo card with protected click action
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .clickable { requestCameraCapture() }
                    .testTag("capture_photo_card"),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoCamera,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Take Vehicle Photograph",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Tap to open camera or choose from gallery",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { requestCameraCapture() },
                            modifier = Modifier.testTag("open_camera_button")
                        ) {
                            Icon(imageVector = Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Camera", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = {
                                try {
                                    pickImageLauncher.launch("image/*")
                                } catch (e: Exception) {
                                    viewModel.setErrorMessage("Could not open gallery: ${e.message}")
                                }
                            },
                            modifier = Modifier.testTag("pick_gallery_button")
                        ) {
                            Icon(imageVector = Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gallery / Files", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Submit Button with double-submission protection
        Button(
            onClick = { viewModel.submitEntry() },
            enabled = !formState.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("submit_inward_entry_button"),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (formState.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Recording Entry & Generating Serial No...")
            } else {
                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Save & Generate Gate Pass",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
