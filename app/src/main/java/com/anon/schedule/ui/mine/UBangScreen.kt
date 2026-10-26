package com.anon.schedule.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anon.schedule.data.UBangApi
import com.anon.schedule.data.UBangPrefs
import com.anon.schedule.ui.common.GlassCard
import com.anon.schedule.ui.common.SectionTitle
import com.anon.schedule.ui.common.TileGreen
import com.anon.schedule.ui.common.TileOrange
import com.anon.schedule.ui.common.TilePurple
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 大白U帮：账户余额 + 澡堂空位。
 *
 * 数据来自 lz.hydream.cn 的卓驰接口（见 [UBangApi]），只用本机保存的账号登录，
 * 只读余额与空位，不做支付、开阀等写操作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UBangScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loggedIn by remember { mutableStateOf(UBangPrefs.hasSession(context)) }
    var mobile by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var snapshot by remember { mutableStateOf<UBangApi.Snapshot?>(null) }
    var loading by remember { mutableStateOf(false) }
    var loggingIn by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var updatedAt by remember { mutableStateOf<String?>(null) }

    var expandedId by remember { mutableStateOf<Int?>(null) }
    var slots by remember { mutableStateOf<Map<Int, List<UBangApi.Slot>>>(emptyMap()) }
    var slotLoading by remember { mutableStateOf<Int?>(null) }
    var slotError by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            runCatching { UBangApi.load(context) }
                .onSuccess {
                    snapshot = it
                    // 坑位是上一次展开时抓的，主数据刷新后一并作废，展开时重新拉
                    slots = emptyMap()
                    expandedId = null
                    slotError = null
                    updatedAt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                }
                .onFailure { error = it.message ?: "读取失败，请稍后再试" }
            loading = false
        }
    }

    fun doLogin() {
        if (loggingIn) return
        loggingIn = true
        error = null
        scope.launch {
            runCatching { UBangApi.login(context, mobile.trim(), password) }
                .onSuccess {
                    loggedIn = true
                    password = ""
                    refresh()
                }
                .onFailure { error = it.message ?: "登录失败，请检查账号密码" }
            loggingIn = false
        }
    }

    fun toggleArea(area: UBangApi.Area) {
        if (expandedId == area.id) {
            expandedId = null
            return
        }
        expandedId = area.id
        slotError = null
        if (slots.containsKey(area.id)) return
        slotLoading = area.id
        scope.launch {
            runCatching { UBangApi.slots(context, area.id) }
                .onSuccess { slots = slots + (area.id to it) }
                .onFailure { slotError = it.message ?: "读取坑位失败" }
            slotLoading = null
        }
    }

    LaunchedEffect(Unit) {
        if (loggedIn) refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("澡堂 · 余额") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (loggedIn) {
                        IconButton(onClick = { refresh() }, enabled = !loading) {
                            if (loading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "刷新")
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (!loggedIn) {
                LoginSection(
                    mobile = mobile,
                    password = password,
                    loading = loggingIn,
                    error = error,
                    onMobileChange = { mobile = it },
                    onPasswordChange = { password = it },
                    onLogin = { doLogin() }
                )
            } else {
                val snap = snapshot
                SectionTitle("账户余额")
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    if (snap == null) {
                        Text(
                            when {
                                loading -> "正在读取…"
                                else -> "还没有数据，点右上角刷新"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            "总余额",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "¥${formatMoney(snap.balance.total)}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "本金 ¥${formatMoney(snap.balance.available)} · 赠送 ¥${formatMoney(snap.balance.given)}" +
                                    (snap.balance.deposit?.let { " · 押金 ¥$it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val where = listOfNotNull(
                            snap.orgName,
                            snap.areaName
                        ).joinToString(" · ")
                        if (where.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                where,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                SectionTitle("澡堂空位")
                when {
                    snap == null && loading -> GlassCard(Modifier.fillMaxWidth()) {
                        Text("正在读取澡堂空位…", style = MaterialTheme.typography.bodyMedium)
                    }

                    snap == null -> GlassCard(Modifier.fillMaxWidth()) {
                        Text(
                            error ?: "还没有读到数据",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    snap.areas.isEmpty() -> GlassCard(Modifier.fillMaxWidth()) {
                        Text(
                            "没有查到澡堂。可能账号还没有绑定校区，先在「大白U帮」App 里设置常用澡堂后再试。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    else -> {
                        snap.areas.forEach { area ->
                            AreaCard(
                                area = area,
                                expanded = expandedId == area.id,
                                loadingSlots = slotLoading == area.id,
                                slots = slots[area.id],
                                slotError = if (expandedId == area.id) slotError else null,
                                onClick = { toggleArea(area) }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                if (error != null && snap != null) {
                    Text(
                        error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Text(
                        updatedAt?.let { "更新于 $it" } ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        UBangPrefs.clear(context)
                        snapshot = null
                        slots = emptyMap()
                        expandedId = null
                        error = null
                        updatedAt = null
                        loggedIn = false
                    }) { Text("退出登录") }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LoginSection(
    mobile: String,
    password: String,
    loading: Boolean,
    error: String?,
    onMobileChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit
) {
    SectionTitle("登录大白U帮")
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            "用「大白U帮」App 的登录手机号和密码。凭据只保存在本机，只用于读取你自己的余额和澡堂空位。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = mobile,
            onValueChange = onMobileChange,
            label = { Text("手机号") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onLogin,
            enabled = mobile.isNotBlank() && password.isNotBlank() && !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("登录")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AreaCard(
    area: UBangApi.Area,
    expanded: Boolean,
    loadingSlots: Boolean,
    slots: List<UBangApi.Slot>?,
    slotError: String?,
    onClick: () -> Unit
) {
    val idleColor = if (area.idle > 0) TileGreen else MaterialTheme.colorScheme.onSurfaceVariant
    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    area.name.ifBlank { "澡堂" },
                    style = MaterialTheme.typography.bodyLarge
                )
                val sub = area.gender
                if (sub.isNotBlank()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (area.tip.isNotBlank()) {
                    Text(
                        area.tip,
                        style = MaterialTheme.typography.bodySmall,
                        color = TileOrange,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                "${area.idle}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = idleColor
            )
            Text(
                "/${area.total}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 2.dp)
            )
        }

        if (expanded) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                modifier = Modifier.padding(vertical = 10.dp)
            )
            when {
                loadingSlots -> Text(
                    "正在读取坑位…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                slotError != null -> Text(
                    slotError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )

                slots.isNullOrEmpty() -> Text(
                    "没有坑位数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                else -> {
                    val ready = slots.count { it.status == "ready" }
                    val running = slots.count { it.status == "running" }
                    val reserved = slots.count { it.status == "reserved" }
                    Text(
                        "空闲 $ready · 使用中 $running · 已预约 $reserved",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        slots.forEach { slot ->
                            SlotChip(slot)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotChip(slot: UBangApi.Slot) {
    val color = when (slot.status) {
        "ready" -> TileGreen
        "running" -> TileOrange
        "reserved" -> TilePurple
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = slot.number.ifBlank { "?" },
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 9.dp, vertical = 6.dp)
    )
}

private fun formatMoney(v: Double): String = String.format(Locale.US, "%.2f", v)
