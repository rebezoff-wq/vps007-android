package com.v2ray.ang.ui.support

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.R
import com.v2ray.ang.dto.CabinetError
import com.v2ray.ang.dto.CabinetResult
import com.v2ray.ang.dto.CabinetTicket
import com.v2ray.ang.dto.CabinetTicketDetail
import com.v2ray.ang.handler.CabinetManager
import com.v2ray.ang.ui.account.AccountViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Which support page is visible. */
sealed interface SupportPage {
    data object List : SupportPage
    data object New : SupportPage
    data class Ticket(val id: Long) : SupportPage
}

data class SupportUiState(
    val loggedIn: Boolean = false,
    val page: SupportPage = SupportPage.List,
    val loading: Boolean = false,
    val sending: Boolean = false,
    val tickets: List<CabinetTicket> = emptyList(),
    val ticket: CabinetTicketDetail? = null,
    @StringRes val errorRes: Int? = null,
    /** One-off: clears the reply/new-ticket input after a successful send. */
    val sentCounter: Int = 0,
)

sealed interface SupportAction {
    data object Refresh : SupportAction
    data object OpenNew : SupportAction
    data class OpenTicket(val id: Long) : SupportAction
    data object Back : SupportAction
    data class Create(val title: String, val message: String) : SupportAction
    data class Reply(val message: String) : SupportAction
    data object DismissError : SupportAction
}

/**
 * Support tickets through the Bedolaga cabinet API: the same tickets the admin answers in the
 * bot/cabinet admin panel, so nothing depends on Telegram being reachable.
 */
class SupportViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SupportUiState(loggedIn = CabinetManager.isLoggedIn()))
    val uiState: StateFlow<SupportUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        if (_uiState.value.loggedIn) loadList()
    }

    fun onAction(action: SupportAction) {
        when (action) {
            SupportAction.Refresh -> refreshCurrent()
            SupportAction.OpenNew -> {
                stopPolling()
                _uiState.update { it.copy(page = SupportPage.New, errorRes = null) }
            }
            is SupportAction.OpenTicket -> openTicket(action.id)
            SupportAction.Back -> back()
            is SupportAction.Create -> create(action.title, action.message)
            is SupportAction.Reply -> reply(action.message)
            SupportAction.DismissError -> _uiState.update { it.copy(errorRes = null) }
        }
    }

    /** Returns false when the activity should close (already on the list). */
    fun canGoBack(): Boolean = _uiState.value.page != SupportPage.List

    private fun back() {
        stopPolling()
        _uiState.update { it.copy(page = SupportPage.List, ticket = null, errorRes = null) }
        loadList()
    }

    private fun refreshCurrent() {
        when (val page = _uiState.value.page) {
            SupportPage.List -> loadList()
            is SupportPage.Ticket -> loadTicket(page.id, showLoading = true)
            SupportPage.New -> Unit
        }
    }

    private fun loadList() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            when (val r = CabinetManager.listTickets()) {
                is CabinetResult.Success -> _uiState.update {
                    it.copy(loading = false, tickets = r.value.sortedByDescending { t -> t.updatedAt.orEmpty() })
                }
                is CabinetResult.Failure -> fail(r.error)
                CabinetResult.Pending -> fail(CabinetError.SERVER)
            }
        }
    }

    private fun openTicket(id: Long) {
        _uiState.update { it.copy(page = SupportPage.Ticket(id), ticket = null, errorRes = null) }
        loadTicket(id, showLoading = true)
        startPolling(id)
    }

    private suspend fun fetchTicket(id: Long, showLoading: Boolean) {
        if (showLoading) _uiState.update { it.copy(loading = true) }
        when (val r = CabinetManager.getTicket(id)) {
            is CabinetResult.Success -> _uiState.update {
                if (it.page == SupportPage.Ticket(id)) it.copy(loading = false, ticket = r.value) else it
            }
            is CabinetResult.Failure -> if (showLoading) fail(r.error) else Unit
            CabinetResult.Pending -> Unit
        }
    }

    private fun loadTicket(id: Long, showLoading: Boolean) {
        viewModelScope.launch { fetchTicket(id, showLoading) }
    }

    /** While a ticket is open, check for the operator's answer every 20 seconds. */
    private fun startPolling(id: Long) {
        stopPolling()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                fetchTicket(id, showLoading = false)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun create(title: String, message: String) {
        if (title.trim().length < 3 || message.isBlank()) {
            _uiState.update { it.copy(errorRes = R.string.support_error_fill) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(sending = true, errorRes = null) }
            when (val r = CabinetManager.createTicket(title, message)) {
                is CabinetResult.Success -> {
                    _uiState.update {
                        it.copy(sending = false, sentCounter = it.sentCounter + 1, page = SupportPage.Ticket(r.value.id), ticket = r.value)
                    }
                    startPolling(r.value.id)
                }
                is CabinetResult.Failure -> fail(r.error)
                CabinetResult.Pending -> fail(CabinetError.SERVER)
            }
        }
    }

    private fun reply(message: String) {
        val page = _uiState.value.page as? SupportPage.Ticket ?: return
        if (message.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(sending = true, errorRes = null) }
            when (val r = CabinetManager.replyTicket(page.id, message)) {
                is CabinetResult.Success -> {
                    _uiState.update { it.copy(sending = false, sentCounter = it.sentCounter + 1) }
                    fetchTicket(page.id, showLoading = false)
                }
                is CabinetResult.Failure -> fail(r.error)
                CabinetResult.Pending -> fail(CabinetError.SERVER)
            }
        }
    }

    private fun fail(error: CabinetError) {
        _uiState.update {
            it.copy(
                loading = false,
                sending = false,
                loggedIn = CabinetManager.isLoggedIn(),
                errorRes = AccountViewModel.errorText(error),
            )
        }
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    private companion object {
        const val POLL_INTERVAL_MS = 20_000L
    }
}
