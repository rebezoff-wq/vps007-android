package com.v2ray.ang.ui.devices

import android.content.Context
import android.content.Intent
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.R
import com.v2ray.ang.dto.CabinetDevice
import com.v2ray.ang.dto.CabinetResult
import com.v2ray.ang.handler.CabinetManager
import com.v2ray.ang.ui.account.AccountViewModel
import com.v2ray.ang.ui.base.BaseComponentActivity
import com.v2ray.ang.ui.compose.AppTopBar
import com.v2ray.ang.ui.compose.Vps007Colors
import com.v2ray.ang.ui.support.formatTicketTime
import com.v2ray.ang.util.HttpUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DevicesUiState(
    val loading: Boolean = false,
    val devices: List<CabinetDevice> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val currentHwid: String? = null,
    @StringRes val errorRes: Int? = null,
    /** Set after a successful delete so the activity can tell the main screen to refresh. */
    val changed: Boolean = false,
)

class DevicesViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DevicesUiState(currentHwid = HttpUtil.currentHwid()))
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorRes = null) }
            when (val r = CabinetManager.listDevices()) {
                is CabinetResult.Success -> _uiState.update {
                    it.copy(
                        loading = false,
                        // This phone first, then by last activity.
                        devices = r.value.devices.sortedWith(
                            compareByDescending<CabinetDevice> { d -> d.hwid == it.currentHwid }
                                .thenByDescending { d -> d.lastSeen.orEmpty() }
                        ),
                        total = maxOf(r.value.total, r.value.devices.size),
                        limit = r.value.deviceLimit,
                    )
                }
                is CabinetResult.Failure -> _uiState.update {
                    it.copy(loading = false, errorRes = AccountViewModel.errorText(r.error))
                }
                CabinetResult.Pending -> Unit
            }
        }
    }

    fun delete(hwid: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorRes = null) }
            when (val r = CabinetManager.deleteDevice(hwid)) {
                is CabinetResult.Success -> {
                    _uiState.update { it.copy(changed = true) }
                    load()
                }
                is CabinetResult.Failure -> _uiState.update {
                    it.copy(loading = false, errorRes = AccountViewModel.errorText(r.error))
                }
                CabinetResult.Pending -> Unit
            }
        }
    }
}

/** «Мои устройства»: devices registered on the subscription (Remnawave HWID) with removal. */
class DevicesActivity : BaseComponentActivity() {

    private val viewModel: DevicesViewModel by viewModels()

    @Composable
    override fun ScreenContent() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        LaunchedEffect(state.changed) { if (state.changed) setResult(RESULT_OK) }
        DevicesScreen(
            state = state,
            onDelete = viewModel::delete,
            onRefresh = viewModel::load,
            onBackClick = { finish() },
        )
    }

    companion object {
        fun intent(context: Context) = Intent(context, DevicesActivity::class.java)
    }
}

@Composable
private fun DevicesScreen(
    state: DevicesUiState,
    onDelete: (String) -> Unit,
    onRefresh: () -> Unit,
    onBackClick: () -> Unit,
) {
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }

    confirm?.let { hwid ->
        val device = state.devices.firstOrNull { it.hwid == hwid }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.devices_delete_title)) },
            text = { Text(stringResource(R.string.devices_delete_text, device?.let { deviceName(it) }.orEmpty())) },
            confirmButton = {
                TextButton(onClick = { confirm = null; onDelete(hwid) }) {
                    Text(stringResource(R.string.devices_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AppTopBar(
                title = stringResource(R.string.devices_title),
                onBackClick = onBackClick,
                isLoading = state.loading,
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                val over = state.limit > 0 && state.total > state.limit
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Vps007Colors.Surface, RoundedCornerShape(16.dp))
                        .border(
                            1.dp,
                            if (over) Vps007Colors.Danger.copy(alpha = 0.6f) else Vps007Colors.Line,
                            RoundedCornerShape(16.dp),
                        )
                        .padding(14.dp),
                ) {
                    Text(
                        text = if (state.limit > 0) {
                            stringResource(R.string.devices_count_limit, state.total, state.limit)
                        } else {
                            stringResource(R.string.devices_count, state.total)
                        },
                        color = if (over) Vps007Colors.Danger else Vps007Colors.Text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(if (over) R.string.devices_over_hint else R.string.devices_hint),
                        color = Vps007Colors.Text2,
                        fontSize = 13.sp,
                    )
                }
                state.errorRes?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRefresh) { Text(stringResource(R.string.support_refresh)) }
                }
            }
            if (!state.loading && state.devices.isEmpty() && state.errorRes == null) {
                item { Text(stringResource(R.string.devices_empty), color = Vps007Colors.Muted) }
            }
            items(state.devices, key = { it.hwid }) { device ->
                DeviceRow(
                    device = device,
                    isCurrent = device.hwid == state.currentHwid,
                    enabled = !state.loading,
                    onDelete = { confirm = device.hwid },
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(device: CabinetDevice, isCurrent: Boolean, enabled: Boolean, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCurrent) Vps007Colors.Accent.copy(alpha = 0.12f) else Vps007Colors.Surface,
                RoundedCornerShape(14.dp),
            )
            .border(1.dp, if (isCurrent) Vps007Colors.Accent2.copy(alpha = 0.5f) else Vps007Colors.Line, RoundedCornerShape(14.dp))
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(deviceName(device), color = Vps007Colors.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                if (isCurrent) {
                    Spacer(Modifier.padding(start = 6.dp))
                    Text(
                        text = stringResource(R.string.devices_this),
                        color = Vps007Colors.Accent2,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .background(Vps007Colors.Accent.copy(alpha = 0.18f), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            val platform = device.platform?.takeIf { it.isNotBlank() && it != "Unknown" }
            val seen = formatTicketTime(device.lastSeen)
            val meta = listOfNotNull(platform, seen.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.devices_seen, it) })
            if (meta.isNotEmpty()) {
                Text(meta.joinToString(" · "), color = Vps007Colors.Muted, fontSize = 12.sp)
            }
        }
        if (!isCurrent) {
            TextButton(onClick = onDelete, enabled = enabled) {
                Text(stringResource(R.string.devices_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun deviceName(device: CabinetDevice): String =
    device.localName?.takeIf { it.isNotBlank() }
        ?: device.deviceModel?.takeIf { it.isNotBlank() && it != "Unknown" }
        ?: device.platform?.takeIf { it.isNotBlank() }
        ?: device.hwid.take(8)
