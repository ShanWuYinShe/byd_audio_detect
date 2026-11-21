package com.byd.audiochanneldetector.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.audiochanneldetector.domain.model.AudioChannel
import com.byd.audiochanneldetector.domain.model.AudioChannelType
import com.byd.audiochanneldetector.domain.model.ChannelSource
import com.byd.audiochanneldetector.domain.model.ToneMode
import com.byd.audiochanneldetector.ui.DisplayLabels
import com.byd.audiochanneldetector.ui.components.AudioChannelCard
import com.byd.audiochanneldetector.ui.viewmodel.MainUiLogic
import com.byd.audiochanneldetector.ui.viewmodel.MainUiState
import com.byd.audiochanneldetector.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "比亚迪音频通道检测",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "BYD Audio Channel Detector",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                actions = {
                    // 分享与导出检测结果：经系统分享面板导出文本或标准 JSON，无需任何权限
                    var showShareMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { showShareMenu = true },
                            enabled = uiState.channels.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "分享或导出检测结果"
                            )
                        }
                        DropdownMenu(
                            expanded = showShareMenu,
                            onDismissRequest = { showShareMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("分享纯文本报告") },
                                onClick = {
                                    showShareMenu = false
                                    val shareText = viewModel.buildShareText()
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(sendIntent, "分享检测结果")
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("导出标准 JSON 配置") },
                                onClick = {
                                    showShareMenu = false
                                    val shareJson = viewModel.buildShareJson()
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "application/json"
                                        putExtra(Intent.EXTRA_TEXT, shareJson)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(sendIntent, "导出 JSON 配置")
                                    )
                                }
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.loadChannels() },
                        enabled = !uiState.isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val isWide = MainUiLogic.isWideScreen(maxWidth.value.toInt())

            if (isWide) {
                // 车机大屏/横屏：左右双栏 Master-Detail 布局
                Row(modifier = Modifier.fillMaxSize()) {
                    // 左侧：列表与过滤
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                    ) {
                        FilterChips(
                            selectedFilter = uiState.selectedFilter,
                            onFilterSelected = { viewModel.setFilter(it) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        MainContentList(
                            uiState = uiState,
                            selectedChannelId = uiState.selectedChannel?.id,
                            onChannelClick = { viewModel.selectChannel(it) },
                            onRetry = { viewModel.loadChannels() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // 右侧：常驻详情与发声测试面板
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .padding(16.dp)
                    ) {
                        val selected = uiState.selectedChannel
                        if (selected != null) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(20.dp)
                            ) {
                                val isPlayingThisChannel =
                                    uiState.isPlayingTone && uiState.playingToneChannelId == selected.id
                                ChannelDetailContent(
                                    channel = selected,
                                    isPlayingThisChannel = isPlayingThisChannel,
                                    onPlayTone = { mode -> viewModel.playTone(selected, mode) },
                                    onStopTone = { viewModel.stopTone() }
                                )
                            }
                        } else {
                            // 未选择时的车载交互引导
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "请从左侧选择通道",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "选择任一音频通道后，在此查看详细硬件参数与进行定向发声测试",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // 普通小屏/竖屏：单列列表 + 弹层
                Column(modifier = Modifier.fillMaxSize()) {
                    FilterChips(
                        selectedFilter = uiState.selectedFilter,
                        onFilterSelected = { viewModel.setFilter(it) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    MainContentList(
                        uiState = uiState,
                        selectedChannelId = null,
                        onChannelClick = { viewModel.selectChannel(it) },
                        onRetry = { viewModel.loadChannels() },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                val selected = uiState.selectedChannel
                if (selected != null) {
                    val isPlayingThisChannel =
                        uiState.isPlayingTone && uiState.playingToneChannelId == selected.id
                    ChannelDetailSheet(
                        channel = selected,
                        isPlayingThisChannel = isPlayingThisChannel,
                        onPlayTone = { mode -> viewModel.playTone(selected, mode) },
                        onStopTone = { viewModel.stopTone() },
                        onDismiss = { viewModel.selectChannel(null) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MainContentList(
    uiState: MainUiState,
    selectedChannelId: String?,
    onChannelClick: (AudioChannel) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        uiState.isLoading -> {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        uiState.error != null -> {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = "检测失败",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRetry) {
                        Text("重试")
                    }
                }
            }
        }
        uiState.filteredChannels.isEmpty() -> {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (uiState.channels.isEmpty()) {
                        Text(
                            text = "未检测到音频通道",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "当前设备未枚举到音频输出，或系统音频节点不可读",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Text(
                            text = "当前筛选下无匹配通道",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "切换上方类型筛选查看其他通道",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        else -> {
            Column(modifier = modifier) {
                // 统计信息
                StatsBar(
                    totalChannels = uiState.channels.size,
                    mediaChannels = uiState.channels.count { it.type == AudioChannelType.MEDIA },
                    navChannels = uiState.channels.count { it.type == AudioChannelType.NAVIGATION },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // 通道列表
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = uiState.filteredChannels,
                        key = { it.id }
                    ) { channel ->
                        AudioChannelCard(
                            channel = channel,
                            isSelected = channel.id == selectedChannelId,
                            onClick = { onChannelClick(channel) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelDetailSheet(
    channel: AudioChannel,
    isPlayingThisChannel: Boolean,
    onPlayTone: (ToneMode) -> Unit,
    onStopTone: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            ChannelDetailContent(
                channel = channel,
                isPlayingThisChannel = isPlayingThisChannel,
                onPlayTone = onPlayTone,
                onStopTone = onStopTone
            )
        }
    }
}

@Composable
private fun ChannelDetailContent(
    channel: AudioChannel,
    isPlayingThisChannel: Boolean,
    onPlayTone: (ToneMode) -> Unit,
    onStopTone: () -> Unit
) {
    Text(
        text = channel.name,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = channel.id,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
    Spacer(modifier = Modifier.height(16.dp))

    DetailRow(
        label = "通道类型",
        value = "${DisplayLabels.typeText(channel.type)}（${DisplayLabels.evidenceText(channel.typeEvidence)}）"
    )
    DetailRow(label = "声道配置", value = DisplayLabels.configText(channel.channelConfig))
    DetailRow(label = "数据来源", value = DisplayLabels.sourceText(channel.source))
    DetailRow(label = "是否有效", value = if (channel.isValid) "是" else "否")
    DetailRow(label = "是否活跃", value = if (channel.isActive) "是（正在使用）" else "否")
    channel.address?.let { DetailRow(label = "地址", value = it) }
    channel.devicePath?.let { DetailRow(label = "设备路径", value = it) }
    channel.cardName?.let { DetailRow(label = "声卡名称", value = it) }
    channel.sampleRate?.let { DetailRow(label = "采样率", value = "${it}Hz") }
    channel.bitDepth?.let { DetailRow(label = "位深", value = "${it}bit") }

    Spacer(modifier = Modifier.height(20.dp))

    // 通道试音区域
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "通道发声测试",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (isPlayingThisChannel) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "发声中...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (channel.source == ChannelSource.ANDROID) {
                Text(
                    text = "向该物理通道定向发送 1 秒正弦波音频，用于验证车机喇叭连通性与声道对应关系。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (isPlayingThisChannel) {
                    Button(
                        onClick = onStopTone,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("停止发声")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onPlayTone(ToneMode.STEREO) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("双声道")
                        }
                        OutlinedButton(
                            onClick = { onPlayTone(ToneMode.LEFT_ONLY) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("仅左声")
                        }
                        OutlinedButton(
                            onClick = { onPlayTone(ToneMode.RIGHT_ONLY) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("仅右声")
                        }
                    }
                }
            } else {
                Text(
                    text = "此通道来自 ALSA 底层设备节点，普通应用在非 root 权限下无法直接定向写入，请选择上方对应的 Android 映射通道进行试音。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun FilterChips(
    selectedFilter: AudioChannelType?,
    onFilterSelected: (AudioChannelType?) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedFilter == null,
            onClick = { onFilterSelected(null) },
            label = { Text("全部") }
        )
        AudioChannelType.entries.filter { it != AudioChannelType.UNKNOWN }.forEach { type ->
            FilterChip(
                selected = selectedFilter == type,
                onClick = { onFilterSelected(type) },
                label = { Text(DisplayLabels.typeText(type)) }
            )
        }
    }
}

@Composable
fun StatsBar(
    totalChannels: Int,
    mediaChannels: Int,
    navChannels: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(label = "总通道", value = totalChannels.toString())
            StatItem(label = "媒体", value = mediaChannels.toString())
            StatItem(label = "导航", value = navChannels.toString())
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}
