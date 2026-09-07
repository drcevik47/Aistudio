package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.ui.theme.MinimalError
import com.example.ui.theme.MinimalPrimary
import com.example.ui.theme.MinimalPrimaryLight
import com.example.ui.theme.MinimalSecondary
import com.example.ui.theme.MinimalSurface
import com.example.ui.theme.MinimalSurfaceBorder
import com.example.ui.theme.MinimalSurfaceBorderLight
import com.example.ui.theme.MinimalSurfaceElevated
import com.example.ui.theme.MinimalTextMuted
import com.example.ui.theme.MinimalTextPrimary
import com.example.ui.theme.MinimalTextSecondary

@Composable
fun ApiKeySetupDialog(
    initialApiKey: String,
    initialApiSecret: String,
    initialIsTestnet: Boolean,
    isDismissable: Boolean,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSave: (apiKey: String, apiSecret: String, isTestnet: Boolean) -> Unit
) {
    var apiKey by remember(initialApiKey) { mutableStateOf(initialApiKey) }
    var apiSecret by remember(initialApiSecret) { mutableStateOf(initialApiSecret) }
    var isTestnet by remember(initialIsTestnet) { mutableStateOf(initialIsTestnet) }
    var showSecret by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = {
            if (isDismissable) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = isDismissable,
            dismissOnClickOutside = isDismissable,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .testTag("api_key_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MinimalSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorderLight),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MinimalPrimaryLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Bybit API Key",
                            tint = MinimalPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Bybit API Credentials",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinimalTextPrimary,
                            letterSpacing = (-0.2).sp
                        )
                        Text(
                            text = "Encrypted & stored locally via EncryptedSharedPreferences",
                            fontSize = 11.sp,
                            color = MinimalTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = MinimalSurfaceElevated),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorderLight)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isTestnet) "Bybit Testnet Mode" else "Bybit Mainnet (Real Trading)",
                                fontWeight = FontWeight.SemiBold,
                                color = MinimalTextPrimary,
                                fontSize = 13.sp
                            )
                            Text(
                                text = if (isTestnet) "Connected to api-testnet.bybit.com" else "Connected to api.bybit.com",
                                fontSize = 11.sp,
                                color = MinimalTextSecondary
                            )
                        }
                        Switch(
                            checked = isTestnet,
                            onCheckedChange = { isTestnet = it },
                            modifier = Modifier.testTag("testnet_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MinimalPrimary,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = MinimalSurfaceBorder
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        validationError = null
                    },
                    shape = RoundedCornerShape(14.dp),
                    label = { Text("API Key") },
                    placeholder = { Text("e.g. 8gW87d9sF7...") },
                    leadingIcon = {
                        Icon(Icons.Default.Key, contentDescription = null, tint = MinimalPrimary)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MinimalPrimary,
                        unfocusedBorderColor = MinimalSurfaceBorder,
                        focusedLabelColor = MinimalPrimary,
                        unfocusedLabelColor = MinimalTextSecondary,
                        focusedTextColor = MinimalTextPrimary,
                        unfocusedTextColor = MinimalTextPrimary,
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
                    placeholder = { Text("e.g. m1G7xZ90...") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MinimalPrimary)
                    },
                    trailingIcon = {
                        IconButton(onClick = { showSecret = !showSecret }) {
                            Icon(
                                imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Şifre Göster/Gizle",
                                tint = MinimalTextSecondary
                            )
                        }
                    },
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_secret_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MinimalPrimary,
                        unfocusedBorderColor = MinimalSurfaceBorder,
                        focusedLabelColor = MinimalPrimary,
                        unfocusedLabelColor = MinimalTextSecondary,
                        focusedTextColor = MinimalTextPrimary,
                        unfocusedTextColor = MinimalTextPrimary,
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

                Spacer(modifier = Modifier.height(14.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = MinimalSurfaceElevated),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MinimalSurfaceBorderLight)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "💡 Required API Permissions:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MinimalTextPrimary
                        )
                        Text(
                            text = "• Unified Trading Account (Spot Trade Read/Write)\n• Account Balance & Position Read",
                            fontSize = 11.sp,
                            color = MinimalTextSecondary,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isDismissable) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("cancel_api_button")
                        ) {
                            Text("Cancel", color = MinimalTextSecondary, fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Button(
                        onClick = {
                            if (apiKey.isBlank() || apiSecret.isBlank()) {
                                validationError = "Please provide both API Key and API Secret."
                            } else {
                                onSave(apiKey.trim(), apiSecret.trim(), isTestnet)
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.testTag("save_api_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MinimalPrimary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = "Save & Connect",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
