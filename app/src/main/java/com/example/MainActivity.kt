package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.auth.AuthState
import com.example.data.model.UserRole
import com.example.ui.components.CloudStatusDialog
import com.example.ui.components.MimsTopBar
import com.example.ui.components.RoleSelectionDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.NewEntryScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MimsViewModel

enum class NavScreen(val title: String) {
    DASHBOARD("Dashboard"),
    NEW_ENTRY("New Entry"),
    HISTORY("History"),
    REPORTS("Reports")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: MimsViewModel = viewModel()
                val authState by viewModel.authState.collectAsState()
                val isAuthLoading by viewModel.isAuthLoading.collectAsState()
                val authErrorMessage by viewModel.authErrorMessage.collectAsState()

                Crossfade(targetState = authState, label = "auth_screen_transition") { state ->
                    when (state) {
                        is AuthState.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Verifying session...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        is AuthState.Unauthenticated -> {
                            LoginScreen(
                                isLoading = isAuthLoading,
                                errorMessage = authErrorMessage,
                                onSignIn = { email, password ->
                                    viewModel.signInWithEmail(email, password)
                                },
                                onClearError = {
                                    viewModel.clearAuthError()
                                }
                            )
                        }
                        is AuthState.Authenticated -> {
                            MimsApp(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MimsApp(viewModel: MimsViewModel) {
    var currentScreen by remember { mutableStateOf(NavScreen.DASHBOARD) }
    var showRoleDialog by remember { mutableStateOf(false) }
    var showCloudDialog by remember { mutableStateOf(false) }

    val currentRole by viewModel.currentUserRole.collectAsState()
    val cloudStatus by viewModel.cloudStatus.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    // Handle back button on sub-screens to return to Dashboard
    BackHandler(enabled = currentScreen != NavScreen.DASHBOARD) {
        currentScreen = NavScreen.DASHBOARD
    }

    // Role switcher and operator account dialog
    if (showRoleDialog) {
        RoleSelectionDialog(
            currentRole = currentRole,
            userEmail = currentUser?.email,
            onRoleSelected = { role ->
                viewModel.setUserRole(role)
                if (role == UserRole.VENDOR && currentScreen == NavScreen.NEW_ENTRY) {
                    currentScreen = NavScreen.DASHBOARD
                }
            },
            onSignOut = {
                viewModel.signOut()
            },
            onDismiss = { showRoleDialog = false }
        )
    }

    // Cloud status modal
    if (showCloudDialog) {
        CloudStatusDialog(
            status = cloudStatus,
            onDismiss = { showCloudDialog = false }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MimsTopBar(
                currentRole = currentRole,
                cloudStatus = cloudStatus,
                currentUserEmail = currentUser?.email,
                onRoleClick = { showRoleDialog = true },
                onCloudClick = {
                    viewModel.refreshCloudStatus()
                    showCloudDialog = true
                },
                onSignOutClick = {
                    viewModel.signOut()
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                // 1. Dashboard
                NavigationBarItem(
                    selected = currentScreen == NavScreen.DASHBOARD,
                    onClick = { currentScreen = NavScreen.DASHBOARD },
                    icon = {
                        Icon(
                            imageVector = if (currentScreen == NavScreen.DASHBOARD) Icons.Filled.Dashboard else Icons.Outlined.Dashboard,
                            contentDescription = "Dashboard"
                        )
                    },
                    label = { Text("Dashboard") },
                    modifier = Modifier.testTag("nav_tab_dashboard")
                )

                // 2. New Entry (Hidden or disabled if Vendor)
                if (currentRole != UserRole.VENDOR) {
                    NavigationBarItem(
                        selected = currentScreen == NavScreen.NEW_ENTRY,
                        onClick = { currentScreen = NavScreen.NEW_ENTRY },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == NavScreen.NEW_ENTRY) Icons.Filled.AddCircle else Icons.Outlined.AddCircleOutline,
                                contentDescription = "New Entry"
                            )
                        },
                        label = { Text("New Entry", fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("nav_tab_new_entry"),
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }

                // 3. History
                NavigationBarItem(
                    selected = currentScreen == NavScreen.HISTORY,
                    onClick = { currentScreen = NavScreen.HISTORY },
                    icon = {
                        Icon(
                            imageVector = if (currentScreen == NavScreen.HISTORY) Icons.Filled.History else Icons.Outlined.History,
                            contentDescription = "History"
                        )
                    },
                    label = { Text("History") },
                    modifier = Modifier.testTag("nav_tab_history")
                )

                // 4. Reports
                NavigationBarItem(
                    selected = currentScreen == NavScreen.REPORTS,
                    onClick = { currentScreen = NavScreen.REPORTS },
                    icon = {
                        Icon(
                            imageVector = if (currentScreen == NavScreen.REPORTS) Icons.Filled.Assessment else Icons.Outlined.Assessment,
                            contentDescription = "Reports"
                        )
                    },
                    label = { Text("Reports") },
                    modifier = Modifier.testTag("nav_tab_reports")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(targetState = currentScreen, label = "screen_crossfade") { screen ->
                when (screen) {
                    NavScreen.DASHBOARD -> DashboardScreen(
                        viewModel = viewModel,
                        onNavigateToNewEntry = { currentScreen = NavScreen.NEW_ENTRY },
                        onNavigateToHistory = { currentScreen = NavScreen.HISTORY },
                        onNavigateToReports = { currentScreen = NavScreen.REPORTS },
                        onOpenCloudStatus = { showCloudDialog = true }
                    )
                    NavScreen.NEW_ENTRY -> NewEntryScreen(
                        viewModel = viewModel,
                        onNavigateToHistory = { currentScreen = NavScreen.HISTORY }
                    )
                    NavScreen.HISTORY -> HistoryScreen(
                        viewModel = viewModel
                    )
                    NavScreen.REPORTS -> ReportsScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
