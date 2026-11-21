package com.byd.audiochanneldetector.domain.model

/**
 * 试音发声声道模式
 */
enum class ToneMode {
    STEREO,     // 双声道发声 (440Hz)
    LEFT_ONLY,  // 仅左声道发声 (440Hz，右声道静音)
    RIGHT_ONLY  // 仅右声道发声 (880Hz，左声道静音)
}
