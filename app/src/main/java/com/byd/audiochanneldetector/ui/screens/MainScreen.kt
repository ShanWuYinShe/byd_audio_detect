package com.byd.audiochanneldetector.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.byd.audiochanneldetector.domain.model.AudioChannel
import com.byd.audiochanneldetector.domain.model.AudioChannelType
import com.byd.audiochanneldetector.ui.DisplayLabels
import com.byd.audiochanneldetector.ui.components.AudioChannelCard
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
                    // 分享检测结果：经系统分享面板 ACTION_SEND 导出纯文本报告，无需任何权限
                    IconButton(
                        onClick = {
                            val shareText = viewModel.buildShareText()
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(
                                Intent.createChooser(sendIntent, "分享检测结果")
                            )
                        },
                        enabled = uiState.channels.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "分享检测结果"
                        )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 过滤芯片
            FilterChips(
                selectedFilter = uiState.selectedFilter,
                onFilterSelected = { viewModel.setFilter(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 内容区域
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "错误: ${uiState.error}",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                uiState.filteredChannels.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "未检测到音频通道",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "请确保应用有足够的权限",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                else -> {
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
                                onClick = { viewModel.selectChannel(channel) }
                            )
                        }
                    }
                }
            }
        }
    }

    // 通道详情：点击卡片后弹出 ModalBottomSheet，展示完整信息
    val selected = uiState.selectedChannel
    if (selected != null) {
        ChannelDetailSheet(
            channel = selected,
            onDismiss = { viewModel.selectChannel(null) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelDetailSheet(
    channel: AudioChannel,
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
                label = {
                    Text(
                        when (type) {
                            AudioChannelType.MEDIA -> "媒体"
                            AudioChannelType.NAVIGATION -> "导航"
                            AudioChannelType.VOICE_CALL -> "语音"
                            AudioChannelType.SYSTEM -> "系统"
                            AudioChannelType.ALARM -> "警报"
                            AudioChannelType.UNKNOWN -> "未知"
                        }
                    )
                }
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


