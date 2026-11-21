package com.byd.audiochanneldetector.ui.viewmodel

import com.byd.audiochanneldetector.domain.model.AudioChannel
import com.byd.audiochanneldetector.domain.model.AudioChannelType

/**
 * MainViewModel 的状态推导纯函数，独立成对象以便不依赖 Android 框架地单测
 * （ViewModel 主体与协程流程留给插桩/真机验证）。
 */
internal object MainUiLogic {

    /** 按类型过滤通道；filter 为 null 返回全部。 */
    internal fun filterChannels(
        channels: List<AudioChannel>,
        filter: AudioChannelType?
    ): List<AudioChannel> =
        if (filter == null) channels else channels.filter { it.type == filter }

    /**
     * 详情弹层跟随刷新：选中通道按 id 在最新列表中重查——找到则替换为最新数据
     * （弹窗内容随刷新保持准确），通道消失返回 null（关闭弹层），避免持续展示过期设备。
     */
    internal fun resolveSelectedChannel(
        selected: AudioChannel?,
        channels: List<AudioChannel>
    ): AudioChannel? =
        selected?.let { sel -> channels.firstOrNull { it.id == sel.id } }

    /** 大屏/车机横屏双栏自适应门槛宽度 (dp) */
    const val WIDE_SCREEN_THRESHOLD_DP = 720

    /** 判断当前可用宽度是否达到大屏/横屏双栏展示门槛 */
    internal fun isWideScreen(widthDp: Int): Boolean = widthDp >= WIDE_SCREEN_THRESHOLD_DP
}
