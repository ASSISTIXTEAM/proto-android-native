package org.assistix.proto.nativeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.assistix.proto.nativeapp.ProtoLegal
import org.assistix.proto.nativeapp.data.AuthResult
import org.assistix.proto.nativeapp.data.ProtoApi
import org.assistix.proto.nativeapp.data.ProtoAppPreferences
import org.assistix.proto.nativeapp.data.ProtoSessionStore
import org.assistix.proto.nativeapp.data.resolveDisplayName

private enum class RegisterStep {
    Email,
    VerifyCode,
    Password,
    PasswordConfirm,
    Nick,
    DisplayName,
}

private val nickRegex = Regex("^[a-zA-Z0-9_]{3,32}$")

private val registerSteps =
    listOf(
        RegisterStep.Email,
        RegisterStep.VerifyCode,
        RegisterStep.Password,
        RegisterStep.PasswordConfirm,
        RegisterStep.DisplayName,
        RegisterStep.Nick,
    )

@Composable
fun AuthAccountHubScreen(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    onShowOnboarding: (() -> Unit)? = null,
) {
    Box(Modifier.fillMaxSize()) {
        ProtoBrandBackdrop()
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProtoLogo(height = 72.dp)
            Spacer(Modifier.height(12.dp))
            ProtoBrandWordmark()
            Spacer(Modifier.height(20.dp))
            Text(
                UiStrings.authHubTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                UiStrings.authHubSubtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))
            ProtoPrimaryButton(UiStrings.authNewAccount, onCreateAccount, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            ProtoGhostButton(UiStrings.authHaveAccount, onSignIn, Modifier.fillMaxWidth())
            if (onShowOnboarding != null) {
                Spacer(Modifier.height(20.dp))
                TextButton(onClick = onShowOnboarding) {
                    Text(UiStrings.showOnboardingAgain, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterAccountWizard(
    api: ProtoApi,
    session: ProtoSessionStore,
    prefs: ProtoAppPreferences,
    onBack: () -> Unit,
    onRegisteredAndVerified: () -> Unit,
    onPendingEmailVerification: (userId: Int, emailHint: String, serverMessage: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(RegisterStep.Email) }
    var email by remember { mutableStateOf("") }
    var registrationProof by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var passConfirm by remember { mutableStateOf("") }
    var nick by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var suggestedNick by remember { mutableStateOf("") }
    var nickStatus by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var verifyInfo by remember { mutableStateOf("") }
    var policyAccepted by remember { mutableStateOf(false) }

    LaunchedEffect(step) {
        if (step == RegisterStep.Nick) {
            policyAccepted = prefs.hasPolicyAccepted(ProtoLegal.POLICY_VERSION)
            if (suggestedNick.isBlank() && displayName.isNotBlank()) {
                val sug = withContext(Dispatchers.IO) { api.suggestNick(displayName.trim()) }
                if (sug != null && sug.nick.isNotBlank()) {
                    suggestedNick = sug.nick
                    if (nick.isBlank()) nick = sug.nick
                }
            }
        }
    }

    LaunchedEffect(nick, step) {
        if (step != RegisterStep.Nick) return@LaunchedEffect
        val n = nick.trim().removePrefix("@")
        if (n.length < 3) {
            nickStatus = null
            return@LaunchedEffect
        }
        if (!nickRegex.matches(n)) {
            nickStatus = null
            return@LaunchedEffect
        }
        delay(400)
        val free = withContext(Dispatchers.IO) { api.checkNickAvailable(n) }
        nickStatus = if (free) UiStrings.regNickAvailable else UiStrings.regNickTaken
    }

    val stepIndex = registerSteps.indexOf(step).coerceAtLeast(0)
    val stepTotal = registerSteps.size
    val canGoBack = registerSteps.indexOf(step) > 0

    fun goBack() {
        if (busy) return
        err = null
        val idx = registerSteps.indexOf(step)
        if (idx <= 0) {
            onBack()
            return
        }
        if (step == RegisterStep.VerifyCode) {
            registrationProof = ""
        }
        step = registerSteps[idx - 1]
    }

    fun goNext() {
        if (busy) return
        err = null
        when (step) {
            RegisterStep.Email -> {
                val em = email.trim().lowercase()
                if (em.isBlank() || !em.contains("@")) {
                    err = UiStrings.emailRequired
                    return
                }
                email = em
                scope.launch {
                    busy = true
                    when (val r = withContext(Dispatchers.IO) { api.sendRegistrationCode(email) }) {
                        is AuthResult.MessageOk -> {
                            verifyInfo = r.message
                            step = RegisterStep.VerifyCode
                        }
                        is AuthResult.Fail -> {
                            err = r.message.ifBlank { UiStrings.genericError }
                            if (r.retryAfterSec > 0) {
                                err = "$err (${r.retryAfterSec} с)"
                            }
                        }
                        else -> err = UiStrings.genericError
                    }
                    busy = false
                }
            }
            RegisterStep.VerifyCode -> Unit
            RegisterStep.Password -> {
                if (pass.length < 6) {
                    err = UiStrings.passwordTooShort
                    return
                }
                step = RegisterStep.PasswordConfirm
            }
            RegisterStep.PasswordConfirm -> {
                if (pass != passConfirm) {
                    err = UiStrings.passwordMismatch
                    return
                }
                step = RegisterStep.DisplayName
            }
            RegisterStep.DisplayName -> {
                if (displayName.trim().isEmpty()) {
                    err = UiStrings.displayNameRequired
                    return
                }
                scope.launch {
                    busy = true
                    val sug =
                        withContext(Dispatchers.IO) {
                            api.suggestNick(displayName.trim())
                        }
                    if (sug != null && sug.nick.isNotBlank()) {
                        suggestedNick = sug.nick
                        if (nick.isBlank()) nick = sug.nick
                    }
                    busy = false
                    step = RegisterStep.Nick
                }
            }
            RegisterStep.Nick -> {
                val n = nick.trim().removePrefix("@").ifBlank { suggestedNick.trim().removePrefix("@") }
                if (n.isBlank()) {
                    err = UiStrings.nickInvalid
                    return
                }
                if (!nickRegex.matches(n)) {
                    err = UiStrings.nickInvalid
                    return
                }
                if (registrationProof.isBlank()) {
                    err = UiStrings.verifyEmailRequiredBanner
                    step = RegisterStep.VerifyCode
                    return
                }
                if (!policyAccepted) {
                    err = UiStrings.policyMustAccept
                    return
                }
                scope.launch {
                    busy = true
                    val free = withContext(Dispatchers.IO) { api.checkNickAvailable(n) }
                    if (!free) {
                        err = UiStrings.regNickTaken
                        busy = false
                        return@launch
                    }
                    nick = n
                    prefs.setPolicyAccepted(ProtoLegal.POLICY_VERSION)
                    val name = resolveDisplayName(displayName, nick)
                    when (
                        val r =
                            withContext(Dispatchers.IO) {
                                api.register(nick, pass, email, name, true, registrationProof)
                            }
                    ) {
                        is AuthResult.Ok -> {
                            session.save(r.token, r.userId, r.nick)
                            onRegisteredAndVerified()
                        }
                        is AuthResult.PendingEmailVerification -> {
                            onPendingEmailVerification(r.userId, r.emailHint.ifBlank { email }, r.message)
                        }
                        is AuthResult.Fail -> err = r.message
                        else -> err = UiStrings.genericError
                    }
                    busy = false
                }
            }
        }
    }

    val (title, body) =
        when (step) {
            RegisterStep.Email -> UiStrings.regEmailStepTitle to UiStrings.regEmailStepBody
            RegisterStep.VerifyCode -> UiStrings.verifyEmailTitle to UiStrings.regVerifyLaterHint
            RegisterStep.Password -> UiStrings.regPasswordStepTitle to UiStrings.regPasswordStepBody
            RegisterStep.PasswordConfirm -> UiStrings.regPasswordConfirmTitle to UiStrings.regPasswordConfirmBody
            RegisterStep.DisplayName -> UiStrings.regDisplayStepTitle to UiStrings.regDisplayStepBody
            RegisterStep.Nick -> UiStrings.regNickStepTitle to UiStrings.regNickStepBody
        }

    val primaryLabel =
        when {
            busy -> "…"
            step == RegisterStep.Email -> UiStrings.regSendCode
            step == RegisterStep.Nick -> UiStrings.createAccount
            else -> UiStrings.next
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(UiStrings.createAccountWizardTitle) },
                navigationIcon = {
                    IconButton(onClick = { goBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = UiStrings.back)
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding(),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    "${stepIndex + 1} / $stepTotal",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (step == RegisterStep.VerifyCode && email.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        email,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (verifyInfo.isNotBlank() && step == RegisterStep.VerifyCode) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        verifyInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(20.dp))

                when (step) {
                    RegisterStep.Email ->
                        OutlinedTextField(
                            email,
                            { email = it },
                            label = { Text(UiStrings.email) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape = ProtoShapes.field,
                            enabled = !busy,
                        )
                    RegisterStep.VerifyCode ->
                        RegistrationVerifyStep(
                            email = email,
                            api = api,
                            onVerified = { proof ->
                                registrationProof = proof
                                verifyInfo = ""
                                step = RegisterStep.Password
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    RegisterStep.Password ->
                        OutlinedTextField(
                            pass,
                            { pass = it },
                            label = { Text(UiStrings.password) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = ProtoShapes.field,
                            enabled = !busy,
                        )
                    RegisterStep.PasswordConfirm ->
                        OutlinedTextField(
                            passConfirm,
                            { passConfirm = it },
                            label = { Text(UiStrings.regPasswordConfirmField) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = ProtoShapes.field,
                            enabled = !busy,
                        )
                    RegisterStep.DisplayName ->
                        OutlinedTextField(
                            displayName,
                            { displayName = it },
                            label = { Text(UiStrings.displayName) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = ProtoShapes.field,
                            enabled = !busy,
                        )
                    RegisterStep.Nick -> {
                        if (suggestedNick.isNotBlank()) {
                            Surface(
                                shape = ProtoShapes.field,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(
                                        UiStrings.regNickSuggested,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "@$suggestedNick",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    TextButton(
                                        onClick = { nick = suggestedNick },
                                        enabled = !busy,
                                    ) {
                                        Text(UiStrings.regNickUseSuggestedFmt(suggestedNick))
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        OutlinedTextField(
                            nick,
                            { nick = it },
                            label = { Text(UiStrings.nick) },
                            placeholder = {
                                if (suggestedNick.isNotBlank()) {
                                    Text("@$suggestedNick")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = ProtoShapes.field,
                            enabled = !busy,
                        )
                        nickStatus?.let { status ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                status,
                                style = MaterialTheme.typography.bodySmall,
                                color =
                                    if (status == UiStrings.regNickTaken) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        ProtoPolicyConsentCard(
                            checked = policyAccepted,
                            onCheckedChange = { policyAccepted = it },
                            enabled = !busy,
                        )
                    }
                }

                err?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(24.dp))
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (canGoBack) {
                    ProtoGhostButton(
                        UiStrings.back,
                        { goBack() },
                        Modifier.weight(1f),
                    )
                }
                if (step != RegisterStep.VerifyCode) {
                    val canProceed = step != RegisterStep.Nick || policyAccepted
                    ProtoPrimaryButton(
                        primaryLabel,
                        {
                            if (canProceed) {
                                goNext()
                            } else {
                                err = UiStrings.policyMustAccept
                            }
                        },
                        Modifier
                            .weight(if (canGoBack) 1.5f else 1f)
                            .alpha(if (canProceed || busy) 1f else 0.45f),
                    )
                }
            }
        }
    }
}
