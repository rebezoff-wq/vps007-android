package com.v2ray.ang.ui.account

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.dto.CabinetError
import com.v2ray.ang.dto.CabinetResult
import com.v2ray.ang.dto.CabinetSyncResult
import com.v2ray.ang.dto.CabinetTrialInfo
import com.v2ray.ang.handler.CabinetManager
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUiState(
    val loggedIn: Boolean = false,
    val busy: Boolean = false,
    /** Waiting for the user to confirm the login in the Telegram bot. */
    val waitingTelegram: Boolean = false,
    @StringRes val errorRes: Int? = null,
    val sync: CabinetSyncResult? = null,
    /** Email / Telegram of the signed-in account; null until loaded or if the request failed. */
    val identity: AccountIdentity? = null,
    /** Trial block; null hides it. */
    val trial: TrialOffer? = null,
    /** One-off: URL the activity must open (Telegram deep link). Cleared by [AccountAction.UrlOpened]. */
    val openUrl: String? = null,
    /**
     * One-off: login or trial activation finished and the subscription was loaded; the activity
     * returns to the main screen, which reloads the server list.
     */
    val loginCompleted: Boolean = false,
)

sealed interface AccountAction {
    data object LoginTelegram : AccountAction
    data object CancelTelegram : AccountAction
    data class LoginEmail(val email: String, val password: String) : AccountAction
    data object RefreshSubscription : AccountAction
    data object ActivateTrial : AccountAction
    data object Logout : AccountAction
    data object UrlOpened : AccountAction
    data object DismissError : AccountAction
}

class AccountViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState(loggedIn = CabinetManager.isLoggedIn()))
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    private var telegramJob: Job? = null

    init {
        if (_uiState.value.loggedIn) refreshSubscription(afterLogin = false)
    }

    fun onAction(action: AccountAction) {
        when (action) {
            AccountAction.LoginTelegram -> startTelegramLogin()
            AccountAction.CancelTelegram -> cancelTelegramLogin()
            is AccountAction.LoginEmail -> loginWithEmail(action.email, action.password)
            AccountAction.RefreshSubscription -> refreshSubscription(afterLogin = false)
            AccountAction.ActivateTrial -> activateTrial()
            AccountAction.Logout -> logout()
            AccountAction.UrlOpened -> _uiState.update { it.copy(openUrl = null) }
            AccountAction.DismissError -> _uiState.update { it.copy(errorRes = null) }
        }
    }

    private fun startTelegramLogin() {
        telegramJob?.cancel()
        telegramJob = viewModelScope.launch {
            _uiState.update { it.copy(busy = true, errorRes = null) }
            when (val r = CabinetManager.requestTelegramLogin()) {
                is CabinetResult.Success -> {
                    _uiState.update {
                        it.copy(
                            busy = false,
                            waitingTelegram = true,
                            openUrl = CabinetManager.telegramLoginUrl(r.value)
                        )
                    }
                    pollTelegram(r.value.token, r.value.expiresIn)
                }

                is CabinetResult.Failure -> showError(r.error)
                CabinetResult.Pending -> showError(CabinetError.SERVER)
            }
        }
    }

    private suspend fun pollTelegram(token: String, expiresInSeconds: Int) {
        val ttlMs = (if (expiresInSeconds > 0) expiresInSeconds else DEFAULT_LINK_TTL_SECONDS) * 1000L
        val deadline = System.currentTimeMillis() + ttlMs
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            when (val r = CabinetManager.pollTelegramLogin(token)) {
                is CabinetResult.Success -> {
                    _uiState.update { it.copy(waitingTelegram = false) }
                    refreshSubscription(afterLogin = true)
                    return
                }

                CabinetResult.Pending -> Unit
                // Phone switched networks while the user was in Telegram: keep waiting.
                is CabinetResult.Failure -> if (r.error != CabinetError.NETWORK) {
                    _uiState.update { it.copy(waitingTelegram = false) }
                    showError(r.error)
                    return
                }
            }
        }
        _uiState.update { it.copy(waitingTelegram = false) }
        showError(CabinetError.LINK_EXPIRED)
    }

    private fun cancelTelegramLogin() {
        telegramJob?.cancel()
        telegramJob = null
        _uiState.update { it.copy(waitingTelegram = false, busy = false) }
    }

    private fun loginWithEmail(email: String, password: String) {
        if (email.isBlank() || password.isEmpty()) {
            _uiState.update { it.copy(errorRes = R.string.account_error_fill_fields) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, errorRes = null) }
            when (val r = CabinetManager.loginWithEmail(email, password)) {
                is CabinetResult.Success -> refreshSubscription(afterLogin = true)
                is CabinetResult.Failure -> showError(r.error)
                CabinetResult.Pending -> showError(CabinetError.SERVER)
            }
        }
    }

    private fun refreshSubscription(afterLogin: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, errorRes = null) }
            when (val r = CabinetManager.syncSubscription()) {
                is CabinetResult.Success -> {
                    // Without a subscription stay here so the user sees how to get one.
                    val completed = afterLogin && r.value.hasSubscription
                    val identity = if (completed) null else loadIdentity()
                    val trialInfo = if (completed || r.value.isActive) null else loadTrialInfo()
                    _uiState.update {
                        it.copy(
                            busy = false,
                            loggedIn = true,
                            sync = r.value,
                            identity = identity ?: it.identity,
                            trial = trialOffer(trialInfo, r.value),
                            loginCompleted = completed,
                        )
                    }
                }

                is CabinetResult.Failure -> {
                    _uiState.update { it.copy(loggedIn = CabinetManager.isLoggedIn()) }
                    showError(r.error)
                }

                CabinetResult.Pending -> showError(CabinetError.SERVER)
            }
        }
    }

    /** Failures only hide the account line: the subscription status is what matters. */
    private suspend fun loadIdentity(): AccountIdentity? =
        when (val r = CabinetManager.getMe()) {
            is CabinetResult.Success -> accountIdentity(r.value)
            is CabinetResult.Failure -> {
                LogUtil.w(AppConfig.TAG, "Account: profile not loaded: ${r.error}")
                null
            }
            CabinetResult.Pending -> null
        }

    private suspend fun loadTrialInfo(): CabinetTrialInfo? =
        when (val r = CabinetManager.getTrialInfo()) {
            is CabinetResult.Success -> r.value
            is CabinetResult.Failure -> {
                LogUtil.w(AppConfig.TAG, "Account: trial info not loaded: ${r.error}")
                null
            }
            CabinetResult.Pending -> null
        }

    private fun activateTrial() {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, errorRes = null) }
            when (val r = CabinetManager.activateTrial()) {
                // afterLogin = true: with the new subscription the screen closes and the main list reloads.
                is CabinetResult.Success -> refreshSubscription(afterLogin = true)
                is CabinetResult.Failure -> {
                    LogUtil.w(AppConfig.TAG, "Account: trial activation failed: ${r.error}")
                    showError(r.error)
                    // The offer may be stale (e.g. used from the bot meanwhile): reload it.
                    if (r.error == CabinetError.TRIAL_USED || r.error == CabinetError.TRIAL_UNAVAILABLE) {
                        val info = loadTrialInfo()
                        _uiState.update { it.copy(trial = trialOffer(info, it.sync)) }
                    }
                }
                CabinetResult.Pending -> showError(CabinetError.SERVER)
            }
        }
    }

    private fun logout() {
        cancelTelegramLogin()
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            CabinetManager.logout()
            _uiState.value = AccountUiState(loggedIn = false)
        }
    }

    private fun showError(error: CabinetError) {
        _uiState.update { it.copy(busy = false, errorRes = errorText(error)) }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2000L
        private const val DEFAULT_LINK_TTL_SECONDS = 300

        @StringRes
        fun errorText(error: CabinetError): Int = when (error) {
            CabinetError.INVALID_CREDENTIALS -> R.string.account_error_invalid_credentials
            CabinetError.EMAIL_NOT_VERIFIED -> R.string.account_error_email_not_verified
            CabinetError.ACCOUNT_DISABLED -> R.string.account_error_disabled
            CabinetError.LINK_EXPIRED -> R.string.account_error_link_expired
            CabinetError.TOO_MANY_REQUESTS -> R.string.account_error_too_many
            CabinetError.NETWORK -> R.string.account_error_network
            CabinetError.SERVER -> R.string.account_error_server
            CabinetError.TICKET_ALREADY_OPEN -> R.string.support_error_already_open
            CabinetError.TICKET_CLOSED -> R.string.support_error_closed
            CabinetError.TICKETS_UNAVAILABLE -> R.string.support_error_unavailable
            CabinetError.NOT_LOGGED_IN -> R.string.support_error_login
            CabinetError.TRIAL_USED -> R.string.account_trial_used
            CabinetError.TRIAL_UNAVAILABLE -> R.string.account_trial_unavailable
            CabinetError.INSUFFICIENT_BALANCE -> R.string.account_error_trial_balance
        }
    }
}
