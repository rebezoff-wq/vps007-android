package com.v2ray.ang.ui.support

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.CabinetTicket
import com.v2ray.ang.dto.CabinetTicketMessage
import com.v2ray.ang.ui.account.AccountActivity
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.Vps007Colors
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** In-app support: tickets answered by the VPS 007 operator (no Telegram needed). */
class SupportActivity : BaseComponentActivity() {

    private val viewModel: SupportViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        BackHandler(enabled = viewModel.canGoBack()) { viewModel.onAction(SupportAction.Back) }

        SupportScreen(
            state = state,
            onAction = viewModel::onAction,
            onLogin = { startActivity(AccountActivity.intent(this)) },
            onBackClick = {
                if (viewModel.canGoBack()) viewModel.onAction(SupportAction.Back) else finish()
            },
        )
    }

    companion object {
        fun intent(context: Context) = Intent(context, SupportActivity::class.java)
    }
}

@Composable
private fun SupportScreen(
    state: SupportUiState,
    onAction: (SupportAction) -> Unit,
    onLogin: () -> Unit,
    onBackClick: () -> Unit,
) {
    val title = when (state.page) {
        SupportPage.List -> stringResource(R.string.support_title)
        SupportPage.New -> stringResource(R.string.support_new_title)
        is SupportPage.Ticket -> state.ticket?.title ?: stringResource(R.string.support_title)
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = title,
                onBackClick = onBackClick,
                isLoading = state.loading || state.sending,
                actions = {
                    if (state.loggedIn && state.page != SupportPage.New) {
                        IconButton(onClick = { onAction(SupportAction.Refresh) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_refresh_24dp),
                                contentDescription = stringResource(R.string.support_refresh),
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) {
            state.errorRes?.let { res ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(res),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onAction(SupportAction.DismissError) }) {
                        Text(stringResource(android.R.string.ok))
                    }
                }
            }

            when {
                !state.loggedIn -> NotLoggedIn(onLogin)
                state.page == SupportPage.List -> TicketList(state, onAction)
                state.page == SupportPage.New -> NewTicket(state, onAction)
                state.page is SupportPage.Ticket -> TicketChat(state, onAction)
            }
        }
    }
}

@Composable
private fun NotLoggedIn(onLogin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.support_login_needed), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onLogin) { Text(stringResource(R.string.account_login_title)) }
    }
}

@Composable
private fun TicketList(state: SupportUiState, onAction: (SupportAction) -> Unit) {
    // Only one unclosed ticket at a time (server rule): offer «continue» instead of «new».
    val active = state.tickets.firstOrNull { it.status != STATUS_CLOSED }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.support_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (active == null) {
                Button(onClick = { onAction(SupportAction.OpenNew) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.support_new_button))
                }
            } else {
                Button(onClick = { onAction(SupportAction.OpenTicket(active.id)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.support_continue_button))
                }
            }
        }
        if (state.tickets.isEmpty() && !state.loading) {
            item {
                Text(
                    text = stringResource(R.string.support_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
        items(state.tickets, key = { it.id }) { ticket ->
            TicketRow(ticket) { onAction(SupportAction.OpenTicket(ticket.id)) }
        }
    }
}

@Composable
private fun TicketRow(ticket: CabinetTicket, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Vps007Colors.Surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ticket.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(ticket.status)
            }
            ticket.lastMessage?.let { last ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = last.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatTicketTime(ticket.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (res, color) = when (status) {
        STATUS_ANSWERED -> R.string.support_status_answered to Vps007Colors.Ok
        STATUS_CLOSED -> R.string.support_status_closed to Vps007Colors.Muted
        else -> R.string.support_status_open to Vps007Colors.Accent2
    }
    Text(
        text = stringResource(res),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun NewTicket(state: SupportUiState, onAction: (SupportAction) -> Unit) {
    var subject by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = subject,
            onValueChange = { subject = it.take(255) },
            label = { Text(stringResource(R.string.support_subject)) },
            singleLine = true,
            enabled = !state.sending,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = message,
            onValueChange = { message = it.take(4000) },
            label = { Text(stringResource(R.string.support_message)) },
            minLines = 5,
            enabled = !state.sending,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onAction(SupportAction.Create(subject, message)) },
            enabled = !state.sending,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.support_send))
        }
    }
}

@Composable
private fun TicketChat(state: SupportUiState, onAction: (SupportAction) -> Unit) {
    val ticket = state.ticket
    var reply by rememberSaveable(state.sentCounter) { mutableStateOf("") }
    val listState = rememberLazyListState()
    val messages = ticket?.messages.orEmpty()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages, key = { it.id }) { MessageBubble(it) }
        }
        val closed = ticket?.status == STATUS_CLOSED || ticket?.isReplyBlocked == true
        if (ticket != null && closed) {
            Text(
                text = stringResource(R.string.support_closed_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(16.dp)
                    .navigationBarsPadding(),
            )
        } else if (ticket != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = reply,
                    onValueChange = { reply = it.take(4000) },
                    placeholder = { Text(stringResource(R.string.support_reply_hint)) },
                    maxLines = 5,
                    enabled = !state.sending,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.padding(4.dp))
                Button(
                    onClick = { onAction(SupportAction.Reply(reply)) },
                    enabled = !state.sending && reply.isNotBlank(),
                ) {
                    Text(stringResource(R.string.support_send))
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: CabinetTicketMessage) {
    val fromOperator = message.isFromAdmin
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (fromOperator) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(
                    if (fromOperator) Vps007Colors.Surface2 else Vps007Colors.AccentDeep,
                    RoundedCornerShape(14.dp),
                )
                .border(1.dp, if (fromOperator) Vps007Colors.Line else Color.Transparent, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (fromOperator) {
                Text(
                    text = stringResource(R.string.support_operator),
                    style = MaterialTheme.typography.labelSmall,
                    color = Vps007Colors.Gold,
                )
            }
            Text(
                text = message.text.ifBlank { if (message.hasMedia) stringResource(R.string.support_media) else "" },
                color = Vps007Colors.Text,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = formatTicketTime(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = if (fromOperator) Vps007Colors.Muted else Color(0xFFBFDBFE),
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

private const val STATUS_ANSWERED = "answered"
private const val STATUS_CLOSED = "closed"

/** Cabinet timestamps are UTC ISO-8601, with or without offset; show local «dd.MM HH:mm». */
internal fun formatTicketTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    val out = DateTimeFormatter.ofPattern("dd.MM HH:mm")
    val zone = ZoneId.systemDefault()
    return runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(zone).format(out) }
        .recoverCatching { LocalDateTime.parse(iso).atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).format(out) }
        .getOrElse { iso.take(16).replace('T', ' ') }
}
