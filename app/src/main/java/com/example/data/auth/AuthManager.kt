package com.example.data.auth

import com.example.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

sealed class AuthState {
    object Loading : AuthState()
    data class Authenticated(val user: FirebaseUser, val role: UserRole) : AuthState()
    object Unauthenticated : AuthState()
}

class AuthManager {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    init {
        // Observe persistent auth state across app restarts
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _currentUser.value = user
            if (user != null) {
                val role = deriveRoleFromUser(user)
                _authState.value = AuthState.Authenticated(user, role)
            } else {
                _authState.value = AuthState.Unauthenticated
            }
        }
    }

    fun isUserSignedIn(): Boolean = auth.currentUser != null

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    /**
     * Authenticates an operator using Firebase Authentication Email & Password.
     */
    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = authResult.user ?: throw Exception("Authentication returned no user profile")
            val role = deriveRoleFromUser(user)
            _currentUser.value = user
            _authState.value = AuthState.Authenticated(user, role)
            Result.success(user)
        } catch (e: FirebaseAuthInvalidUserException) {
            Result.failure(Exception("No account found with this email. Please contact your facility administrator."))
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Result.failure(Exception("Invalid email address or password. Please verify your credentials."))
        } catch (e: FirebaseAuthException) {
            Result.failure(Exception(e.localizedMessage ?: "Authentication failed (${e.errorCode})"))
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Unable to sign in. Please check your connection."))
        }
    }

    /**
     * Signs out the user and clears the persisted session.
     */
    fun signOut() {
        try {
            auth.signOut()
        } catch (_: Exception) {}
        _currentUser.value = null
        _authState.value = AuthState.Unauthenticated
    }

    /**
     * Derives role safely from authenticated user profile.
     * Default privilege is always EMPLOYEE (Gate Operator).
     * Never assigns MANAGER_ADMIN by default.
     */
    fun deriveRoleFromUser(user: FirebaseUser): UserRole {
        val email = user.email?.lowercase() ?: return UserRole.EMPLOYEE

        return when {
            email.startsWith("admin") || email.contains("+admin@") || email.contains(".admin@") -> UserRole.MANAGER_ADMIN
            email.startsWith("manager") || email.contains("+manager@") || email.contains(".manager@") -> UserRole.MANAGER_ADMIN
            email.startsWith("vendor") || email.contains("+vendor@") || email.contains(".vendor@") -> UserRole.VENDOR
            else -> UserRole.EMPLOYEE
        }
    }
}
