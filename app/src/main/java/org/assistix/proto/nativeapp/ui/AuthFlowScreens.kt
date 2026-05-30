package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.assistix.proto.nativeapp.data.AuthResult
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.ProtoSessionStore

@Composable
fun RegistrationVerifyStep(
    email: String,
    api: ProtoApi,
    onVerified: (proof: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var code by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var resendSec by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(resendSec) {
        if (resendSec > 0) {
            kotlinx.coroutines.delay(1000)
            resendSec -= 1
        }
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        OutlinedTextField(
            value = code,
            onValueChange = { c -> code = c.filter { it.isDigit() }.take(6) },
            label = { Text(UiStrings.verificationCode) },
            placeholder = { Text("000000") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            shape = ProtoShapes.field,
            enabled = !busy,
        )
        err?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        info?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = {
                if (busy || resendSec > 0) return@TextButton
                scope.launch {
                    busy = true
                    err = null
                    when (
                        val r =
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                api.sendRegistrationCode(email)
                            }
                    ) {
                        is AuthResult.MessageOk -> {
                            info = r.message
                            resendSec = 180
                        }
                        is AuthResult.Fail -> {
                            err = r.message
                            if (r.retryAfterSec > 0) resendSec = r.retryAfterSec
                        }
                        else -> err = UiStrings.genericError
                    }
                    busy = false
                }
            },
            enabled = !busy && resendSec <= 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (resendSec > 0) {
                    "${UiStrings.resendCode} (${resendSec / 60}:${(resendSec % 60).toString().padStart(2, '0')})"
                } else {
                    UiStrings.resendCode
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        ProtoPrimaryButton(
            if (busy) "…" else UiStrings.confirm,
            {
                if (busy || code.length < 6) {
                    if (code.length < 6) err = UiStrings.verificationCodeTooShort
                    return@ProtoPrimaryButton
                }
                scope.launch {
                    busy = true
                    err = null
                    when (
                        val r =
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                api.verifyRegistrationCode(email, code)
                            }
                    ) {
                        is AuthResult.RegistrationProof -> onVerified(r.proof)
                        is AuthResult.Fail -> err = r.message
                        else -> err = UiStrings.genericError
                    }
                    busy = false
                }
            },
            Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun EmailVerificationCodeForm(
    userId: Int,
    emailHint: String,
    serverMessage: String,
    api: ProtoApi,
    session: ProtoSessionStore,
    onVerified: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var code by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            UiStrings.verifyEmailTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            UiStrings.verifyEmailHint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        val banner = serverMessage.ifBlank { UiStrings.verifyEmailRequiredBanner }
        Spacer(Modifier.height(12.dp))
        Text(
            banner,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        if (emailHint.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                emailHint,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { c -> code = c.filter { it.isDigit() }.take(6) },
            label = { Text(UiStrings.verificationCode) },
            placeholder = { Text("000000") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            shape = ProtoShapes.field,
            enabled = !busy,
        )
        err?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        info?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(20.dp))
        ProtoPrimaryButton(
            if (busy) "…" else UiStrings.confirm,
            {
                if (busy || code.length < 6) {
                    if (code.length < 6) err = UiStrings.verificationCodeTooShort
                    return@ProtoPrimaryButton
                }
                scope.launch {
                    busy = true
                    err = null
                    when (val r = api.verifyEmail(userId, code)) {
                        is AuthResult.Ok -> {
                            session.save(r.token, r.userId, r.nick)
                            onVerified()
                        }
                        is AuthResult.Fail -> err = r.message
                        else -> err = UiStrings.genericError
                    }
                    busy = false
                }
            },
            Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = {
                if (busy) return@TextButton
                scope.launch {
                    busy = true
                    err = null
                    when (val r = api.resendVerification(userId)) {
                        is AuthResult.MessageOk -> info = r.message
                        is AuthResult.Fail -> err = r.message
                        else -> err = UiStrings.genericError
                    }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        ) {
            Text(UiStrings.resendCode)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifyEmailScreen(
    userId: Int,
    emailHint: String,
    serverMessage: String = "",
    api: ProtoApi,
    session: ProtoSessionStore,
    onVerified: () -> Unit,
    onBack: () -> Unit,
) {
    val effectiveUserId = userId.coerceAtLeast(0)

    if (effectiveUserId < 1) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text(
                    UiStrings.verifyEmailMissingUserId,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                ProtoPrimaryButton(UiStrings.back, onBack, Modifier.fillMaxWidth())
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(UiStrings.verifyEmailTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = UiStrings.back)
                    }
                },
            )
        },
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            ProtoBrandBackdrop()
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ProtoLogo(height = 64.dp)
                Spacer(Modifier.height(12.dp))
                EmailVerificationCodeForm(
                    userId = effectiveUserId,
                    emailHint = emailHint,
                    serverMessage = serverMessage,
                    api = api,
                    session = session,
                    onVerified = onVerified,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    api: ProtoApi,
    initialLogin: String,
    onBack: () -> Unit,
) {
    var login by remember { mutableStateOf(initialLogin) }
    var err by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(UiStrings.forgotPasswordTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = UiStrings.back)
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(20.dp)) {
            Text(UiStrings.forgotPasswordHint, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                login,
                { login = it },
                label = { Text(UiStrings.nickOrEmail) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = ProtoShapes.field,
            )
            err?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            done?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))
            ProtoPrimaryButton(
                if (busy) "…" else UiStrings.forgotPassword,
                {
                    if (busy || login.isBlank()) return@ProtoPrimaryButton
                    scope.launch {
                        busy = true
                        err = null
                        done = null
                        when (val r = api.forgotPassword(login.trim())) {
                            is AuthResult.MessageOk -> done = r.message
                            is AuthResult.Fail -> err = r.message
                            else -> err = UiStrings.genericError
                        }
                        busy = false
                    }
                },
                Modifier.fillMaxWidth(),
            )
        }
    }
}
