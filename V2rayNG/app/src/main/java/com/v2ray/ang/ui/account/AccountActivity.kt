package com.v2ray.ang.ui.account

import android.content.Context
import android.content.Intent
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.CabinetSyncResult
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.NavigationBarsSpacer
import com.v2ray.ang.util.Utils
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * VPS 007 account screen: login (Telegram bot confirmation or email + password) and, once logged
 * in, the subscription status with refresh / renew / logout. Returns RESULT_OK after a login that
 * loaded a subscription, so the main screen can refresh its server list.
 */
class AccountActivity : BaseComponentActivity() {

    private val viewModel: AccountViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()

        LaunchedEffect(state.openUrl) {
            state.openUrl?.let {
                Utils.openUri(this@AccountActivity, it)
                viewModel.onAction(AccountAction.UrlOpened)
            }
        }
        LaunchedEffect(state.loginCompleted) {
            if (state.loginCompleted) {
                setResult(RESULT_OK)
                finish()
            }
        }

        AccountScreen(
            state = state,
            onAction = viewModel::onAction,
            onOpenBot = { Utils.openUri(this, AppConfig.CABINET_BOT_URL) },
            onOpenCabinet = { Utils.openUri(this, AppConfig.CABINET_WEB_URL) },
            onBackClick = { finish() },
        )
    }

    companion object {
        fun intent(context: Context) = Intent(context, AccountActivity::class.java)
    }
}

@Composable
fun AccountScreen(
    state: AccountUiState,
    onAction: (AccountAction) -> Unit,
    onOpenBot: () -> Unit,
    onOpenCabinet: () -> Unit,
    onBackClick: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.account_title),
                onBackClick = onBackClick,
                isLoading = state.busy,
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.errorRes?.let { res ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(res),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onAction(AccountAction.DismissError) }) {
                            Text(stringResource(android.R.string.ok))
                        }
                    }
                }
            }

            if (state.loggedIn) {
                LoggedInContent(state, onAction, onOpenBot, onOpenCabinet)
            } else {
                LoginContent(state, onAction, onOpenBot, onOpenCabinet)
            }
            NavigationBarsSpacer()
        }
    }
}

@Composable
private fun LoginContent(
    state: AccountUiState,
    onAction: (AccountAction) -> Unit,
    onOpenBot: () -> Unit,
    onOpenCabinet: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    // Not saveable on purpose: the password is not written to the saved-state bundle.
    var password by remember { mutableStateOf("") }
    val controlsEnabled = !state.busy && !state.waitingTelegram

    Text(
        text = stringResource(R.string.account_login_title),
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text = stringResource(R.string.account_login_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    // --- Telegram ---
    if (state.waitingTelegram) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Text(
                    text = stringResource(R.string.account_telegram_waiting),
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = { onAction(AccountAction.LoginTelegram) }) {
                    Text(stringResource(R.string.account_telegram_open_again))
                }
                TextButton(onClick = { onAction(AccountAction.CancelTelegram) }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        }
    } else {
        Button(
            onClick = { onAction(AccountAction.LoginTelegram) },
            enabled = controlsEnabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.account_login_telegram))
        }
    }

    // --- Email ---
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.account_or_email),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text(stringResource(R.string.account_email)) },
        singleLine = true,
        enabled = controlsEnabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text(stringResource(R.string.account_password)) },
        singleLine = true,
        enabled = controlsEnabled,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        onClick = { onAction(AccountAction.LoginEmail(email, password)) },
        enabled = controlsEnabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.account_login_email))
    }

    Spacer(modifier = Modifier.height(8.dp))
    TextButton(onClick = onOpenBot, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_no_account))
    }
    TextButton(onClick = onOpenCabinet, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_no_account_cabinet))
    }
}

@Composable
private fun LoggedInContent(
    state: AccountUiState,
    onAction: (AccountAction) -> Unit,
    onOpenBot: () -> Unit,
    onOpenCabinet: () -> Unit,
) {
    Text(
        text = stringResource(R.string.account_logged_in),
        style = MaterialTheme.typography.headlineSmall,
    )
    state.identity?.let { identity ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            identity.telegram?.let {
                Text(
                    text = stringResource(R.string.account_identity_telegram, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            identity.email?.let {
                Text(
                    text = stringResource(R.string.account_identity_email, it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.account_subscription),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(subscriptionText(state.sync, state.busy))
        }
    }
    state.trial?.let { TrialContent(it, state.busy, onAction) }
    Button(
        onClick = { onAction(AccountAction.RefreshSubscription) },
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.account_refresh))
    }
    // The web cabinet comes first: with an expired subscription Telegram may be unreachable.
    OutlinedButton(onClick = onOpenCabinet, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_renew_in_cabinet))
    }
    OutlinedButton(onClick = onOpenBot, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.account_renew_in_bot))
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    OutlinedButton(
        onClick = { context.startActivity(com.v2ray.ang.ui.devices.DevicesActivity.intent(context)) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.devices_title))
    }
    Text(
        text = stringResource(R.string.account_cabinet_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(
        onClick = { onAction(AccountAction.Logout) },
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.account_logout), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun TrialContent(trial: TrialOffer, busy: Boolean, onAction: (AccountAction) -> Unit) {
    when (trial) {
        is TrialOffer.Available -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.account_trial_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (trial.days > 0) {
                        Text(pluralStringResource(R.plurals.account_trial_days, trial.days, trial.days))
                    }
                }
            }
            Button(
                onClick = { onAction(AccountAction.ActivateTrial) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    trial.priceRubles?.let { stringResource(R.string.account_trial_activate_paid, formatRubles(it)) }
                        ?: stringResource(R.string.account_trial_activate)
                )
            }
        }

        TrialOffer.Used -> TrialNote(R.string.account_trial_used)
        TrialOffer.Unavailable -> TrialNote(R.string.account_trial_unavailable)
    }
}

@Composable
private fun TrialNote(@StringRes res: Int) {
    Text(
        text = stringResource(res),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun subscriptionText(sync: CabinetSyncResult?, busy: Boolean): String = when {
    sync == null -> stringResource(if (busy) R.string.account_subscription_loading else R.string.account_subscription_unknown)
    !sync.hasSubscription -> stringResource(R.string.account_subscription_none)
    sync.isActive -> stringResource(R.string.account_subscription_active_until, formatDate(sync.endDate))
    else -> stringResource(R.string.account_subscription_expired, formatDate(sync.endDate))
}

/** Cabinet dates are ISO-8601 (with or without offset); show them as dd.MM.yyyy. */
internal fun formatDate(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    val out = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    return runCatching { OffsetDateTime.parse(iso).format(out) }
        .recoverCatching { LocalDateTime.parse(iso).format(out) }
        .getOrElse { iso.take(10) }
}
