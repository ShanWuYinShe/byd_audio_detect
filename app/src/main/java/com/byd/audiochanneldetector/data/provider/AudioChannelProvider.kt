package com.byd.audiochanneldetector.data.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.byd.audiochanneldetector.domain.model.AudioChannel
import com.byd.audiochanneldetector.domain.usecase.GetAudioChannelsUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * 只读 ContentProvider：供车机上的第三方盒子守护程序、CarPlay/HiCar 转接应用
 * 或跨进程脚本直接免交互查询当前检测识别出的音频通道与最佳媒体/导航路由。
 *
 * 访问 URI: content://com.byd.audiochanneldetector.provider/channels
 */
class AudioChannelProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ProviderEntryPoint {
        fun getAudioChannelsUseCase(): GetAudioChannelsUseCase
    }

    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, "channels", CODE_CHANNELS)
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return null
        return when (uriMatcher.match(uri)) {
            CODE_CHANNELS -> {
                val entryPoint = EntryPointAccessors.fromApplication(
                    ctx.applicationContext,
                    ProviderEntryPoint::class.java
                )
                val channels: List<AudioChannel> = runCatching {
                    runBlocking<List<AudioChannel>> {
                        entryPoint.getAudioChannelsUseCase().first()
                    }
                }.getOrDefault(emptyList())

                val cursor = MatrixCursor(COLUMNS)
                channels.forEach { ch ->
                    cursor.addRow(
                        arrayOf(
                            ch.id,
                            ch.name,
                            ch.type.name,
                            ch.channelConfig.name,
                            if (ch.isActive) 1 else 0,
                            if (ch.isValid) 1 else 0,
                            ch.address,
                            ch.devicePath,
                            ch.cardName,
                            ch.sampleRate,
                            ch.bitDepth,
                            ch.source.name,
                            ch.typeEvidence.name
                        )
                    )
                }
                cursor
            }
            else -> null
        }
    }

    override fun getType(uri: Uri): String? = when (uriMatcher.match(uri)) {
        CODE_CHANNELS -> "vnd.android.cursor.dir/vnd.$AUTHORITY.channels"
        else -> null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "com.byd.audiochanneldetector.provider"
        private const val CODE_CHANNELS = 1

        val COLUMNS = arrayOf(
            "id",
            "name",
            "type",
            "channel_config",
            "is_active",
            "is_valid",
            "address",
            "device_path",
            "card_name",
            "sample_rate",
            "bit_depth",
            "source",
            "type_evidence"
        )
    }
}
