package com.example.triqx.ui.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.triqx.data.model.Country
import com.example.triqx.ui.auth.components.CountryPickerBottomSheet
import com.example.triqx.ui.auth.components.OtpInputField
import com.example.triqx.ui.components.M3ExpressiveButton
import com.example.triqx.ui.components.M3ExpressiveKeypad
import com.example.triqx.ui.components.M3ExpressiveTextField
import com.example.triqx.ui.components.M3StepTopBar
import com.example.triqx.ui.components.TriqxCard
import com.example.triqx.ui.theme.Dimens

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: (isFirstLogin: Boolean) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is LoginNavigationEvent.NavigateNext -> onLoginSuccess(event.isFirstLogin)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .padding(top = Dimens.ScreenTopPadding)
                .navigationBarsPadding()
        ) {
            // Top Bar with Capsule Step Indicators
            M3StepTopBar(
                totalSteps = 2,
                currentStep = if (uiState.step == LoginStep.PHONE_INPUT) 0 else 1,
                onBack = if (uiState.step == LoginStep.OTP_VERIFICATION) {
                    { viewModel.onChangePhoneNumber() }
                } else null,
                skipText = if (uiState.step == LoginStep.PHONE_INPUT) "Help" else null,
                onSkip = {}
            )

            // Step Content Animated Transition
            AnimatedContent(
                targetState = uiState.step,
                transitionSpec = {
                    if (targetState == LoginStep.OTP_VERIFICATION) {
                        (slideInHorizontally(animationSpec = tween(durationMillis = Dimens.AnimDurationMedium, easing = FastOutSlowInEasing)) { width -> width } +
                                fadeIn(animationSpec = tween(durationMillis = Dimens.AnimDurationStandard))).togetherWith(
                            slideOutHorizontally(animationSpec = tween(durationMillis = Dimens.AnimDurationMedium, easing = FastOutSlowInEasing)) { width -> -width } +
                                    fadeOut(animationSpec = tween(durationMillis = Dimens.AnimDurationFast))
                        )
                    } else {
                        (slideInHorizontally(animationSpec = tween(durationMillis = Dimens.AnimDurationMedium, easing = FastOutSlowInEasing)) { width -> -width } +
                                fadeIn(animationSpec = tween(durationMillis = Dimens.AnimDurationStandard))).togetherWith(
                            slideOutHorizontally(animationSpec = tween(durationMillis = Dimens.AnimDurationMedium, easing = FastOutSlowInEasing)) { width -> width } +
                                    fadeOut(animationSpec = tween(durationMillis = Dimens.AnimDurationFast))
                        )
                    }
                },
                modifier = Modifier.weight(1f),
                label = "LoginStepAnimation"
            ) { step ->
                when (step) {
                    LoginStep.PHONE_INPUT -> {
                        PhoneInputExpressive(
                            uiState = uiState,
                            onPhoneChange = viewModel::onPhoneNumberChange,
                            onCountrySelected = viewModel::onCountrySelected,
                            onSendOtp = viewModel::onSendOtp
                        )
                    }
                    LoginStep.OTP_VERIFICATION -> {
                        OtpVerificationExpressive(
                            uiState = uiState,
                            onOtpChange = viewModel::onOtpChange,
                            onVerifyOtp = viewModel::onVerifyOtp,
                            onResendOtp = viewModel::onResendOtp,
                            onChangeNumber = viewModel::onChangePhoneNumber
                        )
                    }
                }
            }
        }
    }
}

/**
 * Step 1: Phone Input with consistent typography, padding, and spacing.
 */
@Composable
private fun PhoneInputExpressive(
    uiState: LoginUiState,
    onPhoneChange: (String) -> Unit,
    onCountrySelected: (Country) -> Unit,
    onSendOtp: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var showCountryPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.SpacingStandard)
            .padding(bottom = Dimens.SpacingStandard)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingStandard)
        ) {
            Spacer(modifier = Modifier.height(Dimens.SpacingMicro))

            // Clean, concise headline & subtitle
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMicro)) {
                Text(
                    text = "Enter Mobile Number",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "We'll send a 6-digit verification code to this number.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Card Container using standardized TriqxCard
            TriqxCard(
                contentPadding = PaddingValues(Dimens.SpacingStandard)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(Dimens.SquircleShape)
                            .clickable { showCountryPicker = true }
                            .padding(vertical = Dimens.SpacingMicro),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium)
                    ) {
                        // Flag Emoji Badge
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Text(
                                text = uiState.countryFlag,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Country / Region",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${uiState.countryName} (${uiState.countryIso})",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingMicro)
                        ) {
                            Text(
                                text = uiState.countryCode,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select country",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // Pill-Shaped Phone Text Field
                    M3ExpressiveTextField(
                        value = uiState.phoneNumber,
                        onValueChange = onPhoneChange,
                        placeholder = "Phone number",
                        isError = uiState.errorMessage != null
                    )
                }
            }

            // Error Banner
            if (uiState.errorMessage != null) {
                Surface(
                    shape = Dimens.CardShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = Dimens.SpacingStandard, vertical = Dimens.SpacingMedium),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Fixed Bottom Pill CTA Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.SpacingStandard)
        ) {
            M3ExpressiveButton(
                text = "Continue",
                onClick = {
                    focusManager.clearFocus()
                    onSendOtp()
                },
                enabled = uiState.isPhoneValid,
                isLoading = uiState.isLoading
            )
        }

        if (showCountryPicker) {
            CountryPickerBottomSheet(
                selectedCountryCode = uiState.countryCode,
                onCountrySelected = { country ->
                    onCountrySelected(country)
                    showCountryPicker = false
                },
                onDismiss = { showCountryPicker = false }
            )
        }
    }
}

/**
 * Step 2: OTP Verification with clean header and balanced keypad.
 */
@Composable
private fun OtpVerificationExpressive(
    uiState: LoginUiState,
    onOtpChange: (String) -> Unit,
    onVerifyOtp: () -> Unit,
    onResendOtp: () -> Unit,
    onChangeNumber: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Dimens.SpacingStandard)
            .padding(bottom = Dimens.SpacingStandard),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMedium)
        ) {
            Spacer(modifier = Modifier.height(Dimens.SpacingMicro))

            // Headline & Subtitle
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpacingMicro)) {
                Text(
                    text = "Verify Phone Number",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpacingSmall)
                ) {
                    Text(
                        text = "Sent to ${uiState.fullPhoneNumber}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = onChangeNumber,
                        contentPadding = PaddingValues(horizontal = Dimens.SpacingMicro, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = "Edit",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // OTP 6-box input
            OtpInputField(
                otpValue = uiState.otpCode,
                onOtpChange = onOtpChange,
                isError = uiState.errorMessage != null,
                enabled = !uiState.isLoading,
                onDone = onVerifyOtp,
                modifier = Modifier.padding(vertical = Dimens.SpacingMicro)
            )

            // Resend Countdown Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.countdownSeconds > 0) {
                    val formattedTime = String.format("00:%02d", uiState.countdownSeconds)
                    Text(
                        text = "Resend code in $formattedTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Didn't receive it? ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = onResendOtp,
                        enabled = uiState.canResendOtp,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "Resend code",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Error Banner
            if (uiState.errorMessage != null) {
                Surface(
                    shape = Dimens.CardShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = Dimens.SpacingStandard, vertical = Dimens.SpacingMedium),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Dedicated Keypad supporting Light & Dark themes
            M3ExpressiveKeypad(
                onDigitClick = { digit ->
                    if (uiState.otpCode.length < 6) {
                        onOtpChange(uiState.otpCode + digit)
                    }
                },
                onBackspaceClick = {
                    if (uiState.otpCode.isNotEmpty()) {
                        onOtpChange(uiState.otpCode.dropLast(1))
                    }
                }
            )
        }

        // Fixed Bottom CTA
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.SpacingStandard)
        ) {
            M3ExpressiveButton(
                text = "Continue",
                onClick = onVerifyOtp,
                enabled = uiState.canSubmitOtp,
                isLoading = uiState.isLoading
            )
        }
    }
}
