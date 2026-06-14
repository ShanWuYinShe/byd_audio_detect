package com.byd.audiochanneldetector.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.byd.audiochanneldetector.domain.model.AudioChannel
import com.byd.audiochanneldetector.domain.model.AudioChannelType
import com.byd.audiochanneldetector.domain.model.ChannelConfig
import com.byd.audiochanneldetector.domain.model.ChannelSource
import com.byd.audiochanneldetector.ui.theme.*

@Composable
fun AudioChannelCard(
    channel: AudioChannel,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val typeColor = when (channel.type) {
        AudioChannelType.MEDIA -> MediaColor
        AudioChannelType.NAVIGATION -> NavigationColor
        AudioChannelType.VOICE_CALL -> VoiceCallColor
        AudioChannelType.SYSTEM -> SystemColor
        AudioChannelType.ALARM -> AlarmColor
        AudioChannelType.UNKNOWN -> UnknownColor
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 类型指示器
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(typeColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = channel.type.name.first().toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = typeColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 通道信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChannelTypeChip(type = channel.type)
                    Spacer(modifier = Modifier.width(8.dp))
                    ChannelConfigChip(config = channel.channelConfig)
                    Spacer(modifier = Modifier.width(8.dp))
                    SourceChip(source = channel.source)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 详细信息
                Text(
                    text = buildString {
                        append("ID: ${channel.id}")
                        channel.devicePath?.let { append(" | 路径: $it") }
                        channel.sampleRate?.let { append(" | 采样率: ${it}Hz") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            // 状态指示
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (channel.isValid) Color(0xFF4CAF50) else Color(0xFFF44336)
                    )
            )
        }
    }
}

@Composable
fun ChannelTypeChip(type: AudioChannelType) {
    val color = when (type) {
        AudioChannelType.MEDIA -> MediaColor
        AudioChannelType.NAVIGATION -> NavigationColor
        AudioChannelType.VOICE_CALL -> VoiceCallColor
        AudioChannelType.SYSTEM -> SystemColor
        AudioChannelType.ALARM -> AlarmColor
        AudioChannelType.UNKNOWN -> UnknownColor
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = when (type) {
                AudioChannelType.MEDIA -> "媒体"
                AudioChannelType.NAVIGATION -> "导航"
                AudioChannelType.VOICE_CALL -> "语音"
                AudioChannelType.SYSTEM -> "系统"
                AudioChannelType.ALARM -> "警报"
                AudioChannelType.UNKNOWN -> "未知"
            },
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color
        )
    }
}

@Composable
fun ChannelConfigChip(config: ChannelConfig) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    ) {
        Text(
            text = when (config) {
                ChannelConfig.MONO -> "单声道"
                ChannelConfig.STEREO -> "立体声"
                ChannelConfig.SURROUND_5_1 -> "5.1环绕"
                ChannelConfig.SURROUND_7_1 -> "7.1环绕"
                ChannelConfig.UNKNOWN -> "未知"
            },
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun SourceChip(source: ChannelSource) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
    ) {
        Text(
            text = when (source) {
                ChannelSource.ANDROID -> "Android"
                ChannelSource.ALSA -> "ALSA"
            },
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}
