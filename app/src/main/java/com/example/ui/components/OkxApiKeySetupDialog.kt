package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*

@Composable
fun OkxApiKeySetupDialog(
    initialApiKey: String = "",
    hasExistingCredentials: Boolean = false,
    isLoading: Boolean = false,
    isDismissable: Boolean = false,
    onDismiss: () -> Unit = {},
    onSave: (key: String, secret: String, passphrase: String) -> Unit
) {
    var apiKey by remember(initialApiKey) { mutableStateOf(initialApiKey) }
    var apiSecret by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var showSecret by remember { mutableStateOf(false) }
    var showPassphrase by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = { if (isDismissable) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = isDismissable,
            dismissOnClickOutside = isDismissable,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MinimalSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MinimalPrimaryLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MinimalPrimary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "OKX TR API Bağlantısı",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinimalTextPrimary
                        )
                        Text(
                            text = "API Key, Secret ve Passphrase girin.",
                            fontSize = 13.sp,
                            color = MinimalTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        validationError = null
                    },
                    shape = RoundedCornerShape(14.dp),
                    label = { Text("API Key") },
                    leadingIcon = {
                        Icon(Icons.Default.Key, contentDescription = null, tint = MinimalPrimary)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MinimalPrimary,
                        unfocusedBorderColor = MinimalSurfaceBorder,
                        focusedContainerColor = MinimalSurface,
                        unfocusedContainerColor = MinimalSurfaceElevated.copy(alpha = 0.5f)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = apiSecret,
                    onValueChange = {
                        apiSecret = it
                        validationError = null
                    },
                    shape = RoundedCornerShape(14.dp),
                    label = { Text("API Secret") },
                    placeholder = { Text(if (hasExistingCredentials) "•••••••• (Kayıtlı - değiştirmek için yazın)" else "API Secret") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MinimalPrimary)
                    },
                    trailingIcon = {
                        IconButton(onClick = { showSecret = !showSecret }) {
                            Icon(
                                imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Şifre Göster/Gizle"
                            )
                        }
                    },
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MinimalPrimary,
                        unfocusedBorderColor = MinimalSurfaceBorder,
                        focusedContainerColor = MinimalSurface,
                        unfocusedContainerColor = MinimalSurfaceElevated.copy(alpha = 0.5f)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = passphrase,
                    onValueChange = {
                        passphrase = it
                        validationError = null
                    },
                    shape = RoundedCornerShape(14.dp),
                    label = { Text("API Passphrase (Şifre)") },
                    placeholder = { Text(if (hasExistingCredentials) "•••••••• (Kayıtlı - değiştirmek için yazın)" else "API Passphrase") },
                    leadingIcon = {
                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = MinimalPrimary)
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassphrase = !showPassphrase }) {
                            Icon(
                                imageVector = if (showPassphrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Şifre Göster/Gizle"
                            )
                        }
                    },
                    visualTransformation = if (showPassphrase) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MinimalPrimary,
                        unfocusedBorderColor = MinimalSurfaceBorder,
                        focusedContainerColor = MinimalSurface,
                        unfocusedContainerColor = MinimalSurfaceElevated.copy(alpha = 0.5f)
                    )
                )

                if (validationError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = validationError ?: "",
                        color = MinimalError,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isDismissable) {
                        TextButton(onClick = onDismiss) {
                            Text("Vazgeç", color = MinimalTextSecondary, fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Button(
                        onClick = {
                            if (apiKey.isBlank()) {
                                validationError = "API Key zorunludur."
                            } else if ((apiSecret.isBlank() || passphrase.isBlank()) && !hasExistingCredentials) {
                                validationError = "Tüm alanların doldurulması zorunludur."
                            } else {
                                onSave(apiKey.trim(), apiSecret.trim(), passphrase.trim())
                            }
                        },
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = MinimalPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(text = "Kaydet", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
