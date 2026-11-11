package com.v2ray.ang.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.handler.CabinetManager
import com.v2ray.ang.ui.compose.Vps007Colors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * VPS 007 home screen: subscription status, one big connect button, the chosen server with live
 * speed / traffic, and the main actions. The full v2rayNG server list stays available from the
 * drawer («Все серверы»).
 */
@Composable
fun MainHomeScreen(
    mainViewModel: MainViewModel,
    onAction: (MainAction) -> Unit,
    onNavigate: (MainDestination) -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Group with the account subscription; falls back to the currently selected group.
    val loggedIn = remember(uiState.groups) { CabinetManager.isLoggedIn() }
    val linkedId = remember(uiState.groups) { CabinetManager.getLinkedSubscriptionId() }
    val homeGroup = uiState.groups.firstOrNull { it.id == linkedId }
        ?: uiState.groups.firstOrNull { it.id == uiState.selectedGroupId }
        ?: uiState.groups.firstOrNull()
    val homeGroupId = homeGroup?.id.orEmpty()

    LaunchedEffect(homeGroupId) {
        if (homeGroupId.isNotEmpty() && homeGroupId != uiState.selectedGroupId) {
            onAction(MainAction.SelectGroup(homeGroupId))
        }
    }

    val groupState by mainViewModel.serverGroupState(homeGroupId).collectAsStateWithLifecycle()
    val rows = groupState.rows
    val selectedRow = rows.firstOrNull { it.guid == uiState.selectedGuid } ?: rows.firstOrNull()
    val showWelcome = !loggedIn && rows.isEmpty()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainDrawerContent(
                drawerState = drawerState,
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    onNavigate(route)
                }
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF13254A), Vps007Colors.Bg),
                        center = Offset(540f, 900f),
                        radius = 1400f,
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
            ) {
                HomeTopBar(
                    showRefresh = loggedIn,
                    onMenu = { scope.launch { drawerState.open() } },
                    onRefresh = { onAction(MainAction.SyncAccount) },
                )

                if (showWelcome) {
                    WelcomeCard(
                        onTrial = { onNavigate(MainDestination.Renew) },
                        onLogin = { onNavigate(MainDestination.Account) },
                    )
                    FeatureGrid()
                } else {
                    SubscriptionCard(
                        usage = homeGroup?.usageInfo,
                        onRenew = { onNavigate(MainDestination.Renew) },
                    )
                    PowerSection(
                        isRunning = uiState.isRunning,
                        downSpeed = uiState.traffic?.downSpeed,
                        upSpeed = uiState.traffic?.upSpeed,
                        onToggle = {
                            // Make sure a server of the home group is selected before connecting.
                            val row = selectedRow
                            if (row != null && row.guid != uiState.selectedGuid) {
                                onAction(MainAction.SelectServer(row.guid))
                            }
                            onAction(MainAction.ToggleService)
                        },
                    )
                    ServerPicker(
                        rows = rows,
                        selected = selectedRow,
                        isRunning = uiState.isRunning,
                        sessionTraffic = uiState.traffic?.let { it.sessionUp + it.sessionDown },
                        isTesting = uiState.isTesting,
                        onSelect = { guid -> onAction(MainAction.SelectServer(guid)) },
                        onTestPing = { onAction(MainAction.TestRealAllServers) },
                    )
                    QuickActions(onNavigate = onNavigate)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------------------------

@Composable
private fun HomeTopBar(showRefresh: Boolean, onMenu: () -> Unit, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu) {
            Icon(
                painter = painterResource(R.drawable.ic_menu_24dp),
                contentDescription = stringResource(R.string.home_menu),
                tint = Vps007Colors.Text2,
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Emblem007(size = 30)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.app_name),
                color = Vps007Colors.Text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (showRefresh) {
            IconButton(onClick = onRefresh) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh_24dp),
                    contentDescription = stringResource(R.string.account_refresh),
                    tint = Vps007Colors.Text2,
                )
            }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

@Composable
private fun Emblem007(size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .border(2.dp, Vps007Colors.Gold, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("007", color = Vps007Colors.Gold, fontSize = (size * 0.33f).sp, fontWeight = FontWeight.ExtraBold)
    }
}

// ---------------------------------------------------------------------------------------------
// Welcome (not signed in, no servers)
// ---------------------------------------------------------------------------------------------

@Composable
private fun WelcomeCard(onTrial: () -> Unit, onLogin: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(listOf(Vps007Colors.Gold.copy(alpha = 0.10f), Vps007Colors.Surface.copy(alpha = 0.92f)))
            )
            .border(1.dp, Vps007Colors.Gold.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(18.dp),
    ) {
        Text(
            text = stringResource(R.string.home_welcome_title),
            color = Vps007Colors.Text,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 26.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.home_welcome_text),
            color = Vps007Colors.Text2,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(14.dp))
        PrimaryPill(text = stringResource(R.string.home_trial), onClick = onTrial)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .border(1.dp, Vps007Colors.Line2, RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = onLogin)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.home_have_account), color = Vps007Colors.Text2)
        }
    }
}

@Composable
private fun PrimaryPill(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(Vps007Colors.Accent, Color(0xFF2563EB))))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
private fun FeatureGrid() {
    val items = listOf(
        R.string.home_feature_servers_title to R.string.home_feature_servers_text,
        R.string.home_feature_devices_title to R.string.home_feature_devices_text,
        R.string.home_feature_support_title to R.string.home_feature_support_text,
        R.string.home_feature_cabinet_title to R.string.home_feature_cabinet_text,
    )
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (title, text) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .homeCard()
                            .padding(12.dp),
                    ) {
                        Text(stringResource(title), color = Vps007Colors.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(stringResource(text), color = Vps007Colors.Text2, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Subscription
// ---------------------------------------------------------------------------------------------

@Composable
private fun SubscriptionCard(usage: SubscriptionUsageInfo?, onRenew: () -> Unit) {
    val nowSec = System.currentTimeMillis() / 1000
    val expire = usage?.expire ?: 0L
    val expired = expire in 1 until nowSec
    val daysLeft = if (expire > nowSec) ((expire - nowSec) / 86_400L).toInt() else 0
    val used = (usage?.upload ?: 0L) + (usage?.download ?: 0L)
    val total = usage?.total ?: 0L

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .homeCard(borderColor = if (expired) Vps007Colors.Danger.copy(alpha = 0.6f) else Vps007Colors.Line)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.account_subscription),
                color = Vps007Colors.Text,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            when {
                usage == null -> Unit
                expired -> Chip(stringResource(R.string.home_sub_expired), Vps007Colors.Danger)
                expire > 0 -> Chip(stringResource(R.string.home_sub_active_days, daysLeft), Vps007Colors.Ok)
                else -> Chip(stringResource(R.string.home_sub_active), Vps007Colors.Ok)
            }
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(6.dp)),
            color = Vps007Colors.Accent,
            trackColor = Vps007Colors.Line,
            drawStopIndicator = {},
        )
        Spacer(Modifier.height(6.dp))
        Row {
            Text(
                text = stringResource(
                    R.string.home_sub_used,
                    used.toTrafficString(),
                    if (total > 0) total.toTrafficString() else "∞",
                ),
                color = Vps007Colors.Muted,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            if (expire > 0) {
                Text(
                    text = stringResource(R.string.home_sub_until, formatEpochDate(expire)),
                    color = Vps007Colors.Muted,
                    fontSize = 12.sp,
                )
            }
        }
        if (expired || (expire > 0 && daysLeft <= RENEW_HINT_DAYS)) {
            Spacer(Modifier.height(10.dp))
            PrimaryPill(text = stringResource(R.string.account_renew_in_cabinet), onClick = onRenew)
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

// ---------------------------------------------------------------------------------------------
// Power button
// ---------------------------------------------------------------------------------------------

@Composable
private fun PowerSection(isRunning: Boolean, downSpeed: Long?, upSpeed: Long?, onToggle: () -> Unit) {
    var connectedAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isRunning) {
        connectedAt = if (isRunning) System.currentTimeMillis() else 0L
        while (isRunning) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PowerButton(isRunning = isRunning, onClick = onToggle)
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(if (isRunning) R.string.home_connected else R.string.home_disconnected),
            color = if (isRunning) Color(0xFF93C5FD) else Vps007Colors.Text,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        if (isRunning) {
            Text(
                text = formatDuration(((now - connectedAt).coerceAtLeast(0)) / 1000),
                color = Vps007Colors.Text2,
                fontSize = 14.sp,
            )
            if (downSpeed != null && upSpeed != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "↓ ${downSpeed.toSpeedString()}    ↑ ${upSpeed.toSpeedString()}",
                    color = Vps007Colors.Accent2,
                    fontSize = 13.sp,
                )
            }
        } else {
            Text(stringResource(R.string.home_tap_to_connect), color = Vps007Colors.Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PowerButton(isRunning: Boolean, onClick: () -> Unit) {
    val label = stringResource(if (isRunning) R.string.home_disconnect_action else R.string.home_connect_action)
    val ringColor by animateColorAsState(if (isRunning) Vps007Colors.Accent2 else Vps007Colors.Line2, label = "ring")
    val iconColor by animateColorAsState(if (isRunning) Color.White else Vps007Colors.Accent2, label = "icon")

    Box(modifier = Modifier.size(210.dp), contentAlignment = Alignment.Center) {
        if (isRunning) Waves()
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(
                    if (isRunning) {
                        Brush.radialGradient(listOf(Vps007Colors.Accent, Vps007Colors.AccentDeep))
                    } else {
                        Brush.radialGradient(listOf(Color(0xFF1E3A8A), Vps007Colors.Surface))
                    }
                )
                .border(2.dp, ringColor, CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(58.dp)) {
                val stroke = size.minDimension * 0.1f
                val inset = stroke / 2 + size.minDimension * 0.05f
                drawArc(
                    color = iconColor,
                    startAngle = -60f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawLine(
                    color = iconColor,
                    start = Offset(size.width / 2, size.height * 0.02f),
                    end = Offset(size.width / 2, size.height * 0.45f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** Three soft rings spreading from the button (smaller than on the website). */
@Composable
private fun Waves() {
    val transition = rememberInfiniteTransition(label = "waves")
    repeat(3) { i ->
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
                initialStartOffset = StartOffset(i * 800),
            ),
            label = "wave$i",
        )
        Box(
            modifier = Modifier
                .size(150.dp)
                .scale(1f + 0.32f * progress)
                .border(2.dp, Vps007Colors.Accent2.copy(alpha = 0.6f * (1f - progress)), CircleShape)
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Server
// ---------------------------------------------------------------------------------------------

@Composable
private fun ServerPicker(
    rows: List<ServerRowUiModel>,
    selected: ServerRowUiModel?,
    isRunning: Boolean,
    sessionTraffic: Long?,
    isTesting: Boolean,
    onSelect: (String) -> Unit,
    onTestPing: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .homeCard(),
    ) {
        if (selected == null) {
            Text(
                text = stringResource(R.string.home_no_servers),
                color = Vps007Colors.Muted,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            val subtitle = if (isRunning && sessionTraffic != null) {
                stringResource(R.string.home_server_session, sessionTraffic.toTrafficString())
            } else {
                stringResource(R.string.home_server_choose)
            }
            ServerRow(
                row = selected,
                subtitle = subtitle,
                trailing = if (expanded) "⌃" else "›",
                onClick = { expanded = !expanded },
            )
            AnimatedVisibility(visible = expanded) {
                Column {
                    rows.filter { it.guid != selected.guid }.forEach { row ->
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Vps007Colors.Line))
                        ServerRow(
                            row = row,
                            subtitle = null,
                            trailing = null,
                            onClick = {
                                onSelect(row.guid)
                                expanded = false
                            },
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Vps007Colors.Line))
                    Text(
                        text = stringResource(if (isTesting) R.string.home_ping_testing else R.string.home_ping_test),
                        color = Vps007Colors.Accent2,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isTesting, role = Role.Button, onClick = onTestPing)
                            .padding(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerRow(row: ServerRowUiModel, subtitle: String?, trailing: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Vps007Colors.Surface2, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(flagFor(row.remarks), fontSize = 22.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = row.remarks,
                color = Vps007Colors.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, color = Vps007Colors.Muted, fontSize = 12.sp, maxLines = 1)
            }
        }
        PingText(row.testDelayMillis)
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, color = Vps007Colors.Muted, fontSize = 18.sp)
        }
    }
}

@Composable
private fun PingText(delayMs: Long) {
    when {
        delayMs > 0 -> Text(
            text = stringResource(R.string.home_ping_ms, delayMs),
            color = when {
                delayMs < 300 -> Vps007Colors.Ok
                delayMs < 1000 -> Vps007Colors.Gold
                else -> Vps007Colors.Danger
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        delayMs < 0 -> Text(stringResource(R.string.home_ping_fail), color = Vps007Colors.Danger, fontSize = 12.sp)
        else -> Unit
    }
}

// ---------------------------------------------------------------------------------------------
// Quick actions
// ---------------------------------------------------------------------------------------------

@Composable
private fun QuickActions(onNavigate: (MainDestination) -> Unit) {
    val actions = listOf(
        MainDestination.Renew,
        MainDestination.PerAppProxy,
        MainDestination.Support,
        MainDestination.Account,
    )
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actions.forEach { dest ->
            val gold = dest == MainDestination.Renew
            Column(
                modifier = Modifier
                    .weight(1f)
                    .homeCard()
                    .clickable(role = Role.Button) { onNavigate(dest) }
                    .padding(vertical = 10.dp, horizontal = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            (if (gold) Vps007Colors.Gold else Vps007Colors.Accent).copy(alpha = 0.15f),
                            RoundedCornerShape(10.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(dest.iconRes),
                        contentDescription = null,
                        tint = if (gold) Vps007Colors.Gold else Vps007Colors.Accent2,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(quickLabel(dest)),
                    color = Vps007Colors.Text2,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun quickLabel(dest: MainDestination): Int = when (dest) {
    MainDestination.Renew -> R.string.home_action_renew
    MainDestination.PerAppProxy -> R.string.home_action_apps
    MainDestination.Support -> R.string.home_action_support
    MainDestination.Account -> R.string.title_account
    else -> dest.labelRes
}

// ---------------------------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------------------------

private const val RENEW_HINT_DAYS = 3

private fun Modifier.homeCard(borderColor: Color = Vps007Colors.Line): Modifier = this
    .clip(RoundedCornerShape(16.dp))
    .background(Vps007Colors.Surface.copy(alpha = 0.9f))
    .border(1.dp, borderColor, RoundedCornerShape(16.dp))

/** «SE-Stockholm» → 🇸🇪. Server names start with an ISO country code; otherwise a globe. */
internal fun flagFor(remarks: String): String {
    val code = remarks.trim().take(2).uppercase()
    val sep = remarks.trim().getOrNull(2)
    if (code.length != 2 || !code.all { it in 'A'..'Z' } || (sep != null && sep.isLetter())) return "🌐"
    val base = 0x1F1E6 - 'A'.code
    return String(Character.toChars(base + code[0].code)) + String(Character.toChars(base + code[1].code))
}

internal fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

private fun formatEpochDate(epochSeconds: Long): String =
    java.time.Instant.ofEpochSecond(epochSeconds)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
