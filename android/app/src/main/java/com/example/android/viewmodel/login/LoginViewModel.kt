package com.example.android.viewmodel.login

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.android.data.supabase
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.user.UserInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class LoginUiState(
    val isSignUp: Boolean = false,
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val displayName: String = "User",
    val initials: String = "U",
    /**
     * Supabase user UUID for the active session, or `null` when no session
     * has been hydrated yet. Consumers use it as the `user_id` row key for
     * cross-platform Postgrest reads/writes (see
     * [com.example.android.data.repository.preferences.PreferenceRemoteRepository]).
     */
    val userId: String? = null,
    val passwordResetSent: Boolean = false,
    val passwordResetError: String? = null,
    val passwordResetSentToEmail: String? = null,
    val isSendingPasswordReset: Boolean = false
)

/** Lightweight email shape check used by [LoginViewModel.requestPasswordReset]. */
fun isLikelyEmail(email: String): Boolean {
    val trimmed = email.trim()
    if (trimmed.isBlank()) return false
    if (!trimmed.contains("@")) return false
    val parts = trimmed.split("@")
    if (parts.size != 2) return false
    return parts[0].isNotBlank() && parts[1].isNotBlank()
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val authPrefs = application.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val session = supabase.auth.currentSessionOrNull()
            val storedDisplayName = authPrefs.getString(KEY_DISPLAY_NAME, null)
            val storedInitials = authPrefs.getString(KEY_INITIALS, null)
            val hasStoredLogin = authPrefs.getBoolean(KEY_IS_LOGGED_IN, false)

            _uiState.update {
                it.copy(
                    isLoggedIn = session != null || hasStoredLogin,
                    displayName = session?.user?.toDisplayName()
                        ?: storedDisplayName
                        ?: "User",
                    initials = session?.user?.toInitials()
                        ?: storedInitials
                        ?: "U",
                    userId = session?.user?.id
                )
            }
        }
    }

    fun onToggleMode(isSignUp: Boolean) {
        _uiState.update {
            it.copy(
                isSignUp = isSignUp,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun onFirstNameChange(value: String) {
        _uiState.update { it.copy(firstName = value, errorMessage = null, infoMessage = null) }
    }

    fun onLastNameChange(value: String) {
        _uiState.update { it.copy(lastName = value, errorMessage = null, infoMessage = null) }
    }

    fun onEmailChange(value: String) {
        _uiState.update { it.copy(email = value, errorMessage = null, infoMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null, infoMessage = null) }
    }

    fun onConfirmPasswordChange(value: String) {
        _uiState.update { it.copy(confirmPassword = value, errorMessage = null, infoMessage = null) }
    }

    fun onSubmit() {
        val state = _uiState.value
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Email and password are required") }
            return
        }
        if (state.isSignUp) {
            if (state.firstName.isBlank() || state.lastName.isBlank()) {
                _uiState.update { it.copy(errorMessage = "First and last name are required") }
                return
            }
            if (state.password.length < 6) {
                _uiState.update { it.copy(errorMessage = "Password must be at least 6 characters") }
                return
            }
            if (state.password != state.confirmPassword) {
                _uiState.update { it.copy(errorMessage = "Passwords do not match") }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            runCatching {
                if (state.isSignUp) {
                    supabase.auth.signUpWith(Email) {
                        email = state.email
                        password = state.password
                        data = buildJsonObject {
                            put("first_name", state.firstName)
                            put("last_name", state.lastName)
                        }
                    }
                    val session = supabase.auth.currentSessionOrNull()
                    val user = session?.user
                    if (session != null && user != null) {
                        persistLogin(user.toDisplayName(), user.toInitials())
                        _uiState.update {
                            it.copy(
                                isLoggedIn = true,
                                isLoading = false,
                                displayName = user.toDisplayName(),
                                initials = user.toInitials(),
                                userId = user.id
                            )
                        }
                    } else {
                        val confirmEmail = state.email
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                infoMessage = "Check your email — we sent a confirmation link to $confirmEmail. After confirming, sign in.",
                                isSignUp = false,
                                password = "",
                                confirmPassword = ""
                            )
                        }
                    }
                } else {
                    supabase.auth.signInWith(Email) {
                        email = state.email
                        password = state.password
                    }
                    syncCurrentUser()
                }
            }.onSuccess {
                _uiState.update {
                    if (it.isLoggedIn || state.isSignUp) {
                        it.copy(errorMessage = null)
                    } else {
                        it.copy(isLoggedIn = true, isLoading = false, errorMessage = null)
                    }
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Authentication failed"
                    )
                }
            }
        }
    }

    fun requestPasswordReset(email: String) {
        val trimmed = email.trim()
        if (!isLikelyEmail(trimmed)) {
            _uiState.update {
                it.copy(
                    passwordResetError = "Enter a valid email address",
                    passwordResetSent = false,
                    passwordResetSentToEmail = null
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSendingPasswordReset = true,
                    passwordResetError = null,
                    passwordResetSent = false
                )
            }
            runCatching {
                supabase.auth.resetPasswordForEmail(trimmed)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSendingPasswordReset = false,
                        passwordResetSent = true,
                        passwordResetSentToEmail = trimmed,
                        passwordResetError = null
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isSendingPasswordReset = false,
                        passwordResetSent = false,
                        passwordResetSentToEmail = null,
                        passwordResetError = e.message ?: "Could not send reset link"
                    )
                }
            }
        }
    }

    fun clearPasswordResetMessages() {
        _uiState.update {
            it.copy(
                passwordResetSent = false,
                passwordResetError = null,
                passwordResetSentToEmail = null
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { supabase.auth.signOut() }
            clearPersistedLogin()
            _uiState.update {
                it.copy(
                    isLoggedIn = false,
                    password = "",
                    confirmPassword = "",
                    infoMessage = null,
                    userId = null
                )
            }
        }
    }

    private suspend fun syncCurrentUser() {
        val session = supabase.auth.currentSessionOrNull()
        val user = session?.user
        val displayName = user?.toDisplayName()
            ?: authPrefs.getString(KEY_DISPLAY_NAME, null)
            ?: "User"
        val initials = user?.toInitials()
            ?: authPrefs.getString(KEY_INITIALS, null)
            ?: "U"

        if (session != null && user != null) {
            persistLogin(displayName, initials)
        }

        _uiState.update {
            it.copy(
                isLoggedIn = session != null || authPrefs.getBoolean(KEY_IS_LOGGED_IN, false),
                isLoading = false,
                displayName = displayName,
                initials = initials,
                userId = user?.id ?: it.userId
            )
        }
    }

    private fun persistLogin(displayName: String, initials: String) {
        authPrefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, true)
            .putString(KEY_DISPLAY_NAME, displayName)
            .putString(KEY_INITIALS, initials)
            .apply()
    }

    private fun clearPersistedLogin() {
        authPrefs.edit()
            .remove(KEY_IS_LOGGED_IN)
            .remove(KEY_DISPLAY_NAME)
            .remove(KEY_INITIALS)
            .apply()
    }
}

private const val AUTH_PREFS = "neighborly_auth"
private const val KEY_IS_LOGGED_IN = "is_logged_in"
private const val KEY_DISPLAY_NAME = "display_name"
private const val KEY_INITIALS = "initials"

private fun UserInfo.toDisplayName(): String {
    val first = userMetadata?.get("first_name")?.jsonPrimitive?.contentOrNull.orEmpty()
    val last = userMetadata?.get("last_name")?.jsonPrimitive?.contentOrNull.orEmpty()
    return listOf(first, last).filter { it.isNotBlank() }.joinToString(" ").ifBlank {
        email ?: "User"
    }
}

private fun UserInfo.toInitials(): String {
    val first = userMetadata?.get("first_name")?.jsonPrimitive?.contentOrNull.orEmpty()
    val last = userMetadata?.get("last_name")?.jsonPrimitive?.contentOrNull.orEmpty()
    val initials = buildString {
        first.firstOrNull()?.let(::append)
        last.firstOrNull()?.let(::append)
    }.uppercase()
    return initials.ifBlank { email?.firstOrNull()?.uppercase() ?: "U" }
}
