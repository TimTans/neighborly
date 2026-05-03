package com.example.android.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.android.viewmodel.login.LoginUiState
import com.example.android.viewmodel.login.LoginViewModel

private val NeighborlyBackground = Color(0xFFF7F3EC)
private val NeighborlyGreen = Color(0xFF0C6A4A)
private val NeighborlyGreenSoft = Color(0xFFE0F1E8)
private val NeighborlyError = Color(0xFFB3261E)
private val NeighborlyMuted = Color(0xFF7C7C7C)

@Composable
fun LoginScreen(viewModel: LoginViewModel) {
    val state by viewModel.uiState.collectAsState()

    LoginContent(
        state = state,
        onModeChange = viewModel::onToggleMode,
        onFirstNameChange = viewModel::onFirstNameChange,
        onLastNameChange = viewModel::onLastNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onConfirmPasswordChange = viewModel::onConfirmPasswordChange,
        onSubmit = viewModel::onSubmit,
        onRequestPasswordReset = viewModel::requestPasswordReset,
        onClearPasswordResetMessages = viewModel::clearPasswordResetMessages
    )
}

@Composable
private fun LoginContent(
    state: LoginUiState,
    onModeChange: (Boolean) -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRequestPasswordReset: (String) -> Unit,
    onClearPasswordResetMessages: () -> Unit
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var resetEmailInput by remember { mutableStateOf(state.email) }

    // When password reset succeeds, close the dialog so the inline banner is visible.
    LaunchedEffect(state.passwordResetSent) {
        if (state.passwordResetSent) {
            showResetDialog = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .background(NeighborlyBackground)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Neighborly",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = NeighborlyGreen
                        )
                    )

                    Text(
                        text = "Smart grocery trips, made easy.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeighborlyMuted
                    )
                }

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val items = listOf("Log In", "Sign Up")
                    val selectedIndex = if (state.isSignUp) 1 else 0

                    items.forEachIndexed { index, label ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = items.size
                            ),
                            onClick = { onModeChange(index == 1) },
                            selected = index == selectedIndex,
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = NeighborlyGreen,
                                activeContentColor = Color.White,
                                inactiveContainerColor = NeighborlyGreenSoft,
                                inactiveContentColor = NeighborlyGreen
                            )
                        ) {
                            Text(text = label)
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (state.isSignUp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = state.firstName,
                                onValueChange = onFirstNameChange,
                                modifier = Modifier.weight(1f),
                                label = { Text("First Name") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = state.lastName,
                                onValueChange = onLastNameChange,
                                modifier = Modifier.weight(1f),
                                label = { Text("Last Name") },
                                singleLine = true
                            )
                        }
                    }

                    OutlinedTextField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )

                    OutlinedTextField(
                        value = state.password,
                        onValueChange = onPasswordChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )

                    if (!state.isSignUp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    resetEmailInput = state.email
                                    onClearPasswordResetMessages()
                                    showResetDialog = true
                                }
                            ) {
                                Text(
                                    text = "Forgot password?",
                                    color = NeighborlyGreen,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }

                    if (state.isSignUp) {
                        OutlinedTextField(
                            value = state.confirmPassword,
                            onValueChange = onConfirmPasswordChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Confirm Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                    }
                }

                state.errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = NeighborlyError,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                state.infoMessage?.let { info ->
                    Text(
                        text = info,
                        color = NeighborlyGreen,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (state.passwordResetSent && state.passwordResetSentToEmail != null) {
                    PasswordResetBanner(
                        message = "Reset link sent to ${state.passwordResetSentToEmail}",
                        background = NeighborlyGreenSoft,
                        textColor = NeighborlyGreen,
                        onDismiss = onClearPasswordResetMessages
                    )
                }

                state.passwordResetError?.let { error ->
                    PasswordResetBanner(
                        message = error,
                        background = Color(0xFFFCE8E6),
                        textColor = NeighborlyError,
                        onDismiss = onClearPasswordResetMessages
                    )
                }

                Button(
                    onClick = onSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeighborlyGreen,
                        contentColor = Color.White
                    )
                ) {
                    Text(text = if (state.isSignUp) "Create Account" else "Log In")
                }

                Text(
                    text = if (state.isSignUp) {
                        "Vendor approval is handled on web. Mobile sign-up creates a shopper account."
                    } else {
                        "Use the same Supabase-backed account across Android, iOS, and web."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = NeighborlyMuted,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = {
                showResetDialog = false
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRequestPasswordReset(resetEmailInput)
                    },
                    enabled = !state.isSendingPasswordReset
                ) {
                    Text(
                        text = if (state.isSendingPasswordReset) "Sending…" else "Send reset link",
                        color = NeighborlyGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(text = "Cancel", color = NeighborlyMuted)
                }
            },
            title = {
                Text(
                    text = "Reset your password",
                    color = NeighborlyGreen,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter the email associated with your account and we'll send you a reset link.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeighborlyMuted
                    )
                    OutlinedTextField(
                        value = resetEmailInput,
                        onValueChange = { resetEmailInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    state.passwordResetError?.let { err ->
                        Text(
                            text = err,
                            color = NeighborlyError,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            containerColor = Color.White
        )
    }
}

@Composable
private fun PasswordResetBanner(
    message: String,
    background: Color,
    textColor: Color,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, shape = RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            color = textColor,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onDismiss) {
            Text(text = "Got it", color = textColor, fontWeight = FontWeight.SemiBold)
        }
    }
}
