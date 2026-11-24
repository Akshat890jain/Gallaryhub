package com.example.ui.screens

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraEnhance
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ServerHealthState
import com.example.ui.theme.AccentPreset
import com.example.ui.theme.PlayfairDisplayFontFamily
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.ThemeMode

@Composable
fun AuthScreen(
    serverHealth: ServerHealthState,
    isLoading: Boolean,
    errorMessage: String?,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accentHex: String = AccentPreset.TERRACOTTA.hex,
    onLogin: (email: String, password: String) -> Unit,
    onRegister: (name: String, email: String, password: String) -> Unit,
    onEnterSandbox: (name: String, email: String) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit = {},
    onSelectAccentColor: (String) -> Unit = {},
    onClearError: () -> Unit = {},
    onOpenServerConfig: () -> Unit = {},
    onPingServer: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = LoginScreen, 1 = RegisterScreen
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val bgColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.88f, size.height * 0.10f),
                        radius = size.width * 0.72f
                    ),
                    center = Offset(size.width * 0.88f, size.height * 0.10f),
                    radius = size.width * 0.72f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            secondaryColor.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.12f, size.height * 0.90f),
                        radius = size.width * 0.65f
                    ),
                    center = Offset(size.width * 0.12f, size.height * 0.90f),
                    radius = size.width * 0.65f
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        // Non-scrolling single-screen layout fitted cleanly to the viewport
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 460.dp)
                .padding(horizontal = 24.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Editorial Header + Non-Scrolling Theme & Palette Dots
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.example.ui.components.GalleryHubLogoMark(
                            size = 38.dp,
                            accentColor = primaryColor,
                            testTag = "auth_logo_mark"
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "GALLERYHUB",
                                fontFamily = SpaceGroteskFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                letterSpacing = 2.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Visual Archive & Studio",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Compact Non-Scrolling Palette Swatches + Light/Dark Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AccentPreset.entries.take(5).forEach { preset ->
                            val isSelected = accentHex.equals(preset.hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 20.dp else 15.dp)
                                    .clip(CircleShape)
                                    .background(preset.color)
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        shape = CircleShape
                                    )
                                    .clickable { onSelectAccentColor(preset.hex) }
                                    .testTag("auth_palette_chip_${preset.name.lowercase()}")
                            )
                        }

                        Spacer(Modifier.width(2.dp))

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(30.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clip(CircleShape)
                                .clickable {
                                    val nextMode = if (themeMode == ThemeMode.LIGHT) ThemeMode.DARK else ThemeMode.LIGHT
                                    onSelectThemeMode(nextMode)
                                }
                                .testTag("auth_theme_mode_toggle")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (themeMode == ThemeMode.LIGHT) Icons.Default.DarkMode else Icons.Default.LightMode,
                                    contentDescription = "Switch Theme",
                                    tint = primaryColor,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = if (selectedTab == 0) "Welcome back to your studio."
                    else "Create your private studio.",
                    fontFamily = PlayfairDisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "Store, edit, and share your photos, videos, and documents in one personal cloud.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 2. Main Studio Auth Card (Fits both Sign In & Create Account without scrolling)
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(22.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    // Sliding Pill Segmented Control
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp)
                        ) {
                            val tabs = listOf(
                                Triple(0, "Sign In", "tab_sign_in"),
                                Triple(1, "Create Account", "tab_register")
                            )
                            tabs.forEach { (idx, title, tag) ->
                                val active = selectedTab == idx
                                Surface(
                                    shape = RoundedCornerShape(9.dp),
                                    color = if (active) MaterialTheme.colorScheme.surface else Color.Transparent,
                                    shadowElevation = if (active) 2.dp else 0.dp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(9.dp))
                                        .clickable {
                                            selectedTab = idx
                                            onClearError()
                                        }
                                        .testTag(tag)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                            color = if (active) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Crossfade(
                        targetState = selectedTab,
                        label = "auth_screen_mode"
                    ) { tabIndex ->
                        if (tabIndex == 0) {
                            LoginScreen(
                                email = email,
                                password = password,
                                isLoading = isLoading,
                                errorMessage = errorMessage,
                                onEmailChange = {
                                    email = it
                                    onClearError()
                                },
                                onPasswordChange = {
                                    password = it
                                    onClearError()
                                },
                                onSubmitLogin = { onLogin(email, password) },
                                onSwitchToRegister = {
                                    selectedTab = 1
                                    onClearError()
                                },
                                onExploreGuest = {
                                    onEnterSandbox(
                                        name.ifBlank { "Studio Guest" },
                                        email.ifBlank { "guest@galleryhub.app" }
                                    )
                                }
                            )
                        } else {
                            RegisterScreen(
                                name = name,
                                email = email,
                                password = password,
                                isLoading = isLoading,
                                errorMessage = errorMessage,
                                onNameChange = {
                                    name = it
                                    onClearError()
                                },
                                onEmailChange = {
                                    email = it
                                    onClearError()
                                },
                                onPasswordChange = {
                                    password = it
                                    onClearError()
                                },
                                onSubmitRegister = { onRegister(name, email, password) },
                                onSwitchToLogin = {
                                    selectedTab = 0
                                    onClearError()
                                },
                                onExploreGuest = {
                                    onEnterSandbox(
                                        name.ifBlank { "Studio Guest" },
                                        email.ifBlank { "guest@galleryhub.app" }
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // 3. Clean User-Only Footer (No Server / Database URL button)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(primaryColor)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Private Cloud Archive · 500 MB Free Storage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun LoginScreen(
    email: String,
    password: String,
    isLoading: Boolean,
    errorMessage: String?,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmitLogin: () -> Unit,
    onSwitchToRegister: () -> Unit,
    onExploreGuest: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email Address") },
            placeholder = { Text("you@studio.com") },
            leadingIcon = { Icon(Icons.Default.MailOutline, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(13.dp),
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("auth_email_input")
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            placeholder = { Text("Enter your password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Show or Hide Password"
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            shape = RoundedCornerShape(13.dp),
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmitLogin() }),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("auth_password_input")
        )

        if (!errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = onSubmitLogin,
            enabled = !isLoading,
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("auth_submit_button")
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text("Signing In…", fontWeight = FontWeight.Bold)
            } else {
                Text(
                    text = "Sign In",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onExploreGuest,
            shape = RoundedCornerShape(13.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag("auth_sandbox_button")
        ) {
            Text(
                text = "Continue as Guest",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun RegisterScreen(
    name: String,
    email: String,
    password: String,
    isLoading: Boolean,
    errorMessage: String?,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmitRegister: () -> Unit,
    onSwitchToLogin: () -> Unit,
    onExploreGuest: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Full Name") },
            placeholder = { Text("Enter your name") },
            leadingIcon = { Icon(Icons.Default.PersonOutline, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(13.dp),
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("auth_name_input")
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email Address") },
            placeholder = { Text("you@studio.com") },
            leadingIcon = { Icon(Icons.Default.MailOutline, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(13.dp),
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("auth_email_input")
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("Password") },
            placeholder = { Text("At least 6 characters") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Show or Hide Password"
                    )
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            shape = RoundedCornerShape(13.dp),
            colors = fieldColors,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onSubmitRegister() }),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("auth_password_input")
        )

        if (!errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = onSubmitRegister,
            enabled = !isLoading,
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("auth_submit_button")
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
                Text("Creating Account…", fontWeight = FontWeight.Bold)
            } else {
                Text(
                    text = "Create Account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onExploreGuest,
            shape = RoundedCornerShape(13.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .testTag("auth_sandbox_button")
        ) {
            Text(
                text = "Continue as Guest",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
