# byd_audio_channel_detect

Android 原生应用，检测比亚迪车机音频通道（识别有效通道、区分媒体/导航、声道组合）。

通用约束（Git 规范、Commit 格式、验证流程、工具链偏好、JDK 配置等）以全局 `~/AGENTS.md` 为准，本文件只做项目特有补充，不得放宽或违背全局约束。

## 构建 & 测试

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"  # 全局「本机环境」权威定义；以 ls 验证，变动时用 mdfind 定位
./gradlew assembleDebug                    # 编译调试 APK
./gradlew test                             # 运行单元测试（4 个测试类 48 例，debug 变体）
./gradlew lint                             # Lint（Android 默认规则）
./gradlew clean                            # 清理构建产物
```

测试：`app/src/test/` 含 `ALSAScannerTest`（类型识别/声道配置/hw_params 解析）、`ChannelTypeRulesTest`（关键词优先级/usage→类型映射）、`ChannelMergeLogicTest`（跨源去重/设备 key/活跃判定）、`ShareReportFormatterTest`（分享报告格式化），共 48 例纯函数单测，`./gradlew test` 运行。插桩测试：`app/src/androidTest/` 含 `AudioChannelRepositoryImplInstrumentedTest`（真行为测试——`AudioDeviceInfo`/`AudioPlaybackConfiguration` 为 final 类无法 mock，故用 `AudioTrack` 播放指定用途静音音频驱动实测判定/累积/优先级路径），`./gradlew connectedDebugAndroidTest` 需连接设备或模拟器运行。

## 约束

- 架构：MVVM + Clean Architecture（data/domain/ui 三层）。
- UI：Jetpack Compose + Material 3。
- DI：Hilt（`@HiltAndroidApp`、`@AndroidEntryPoint`、`@Module`）。
- 状态管理：ViewModel + StateFlow，ui 层通过 `collectAsStateWithLifecycle()` 收集。
- 音频检测路径：`AudioManager.getDevices()`（仅输出设备，主源）→ `/proc/asound/cards` → `/proc/asound/pcm` → `/dev/snd/`（ALSA 辅助源）。
- 通道类型识别（优先级由高到低）：① 实测——`AudioPlaybackConfiguration.getAudioAttributes()` 的用途映射（`USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`=导航、`USAGE_MEDIA`=媒体等），结果跨刷新累积并标注"实测"；设备关联 API 33+ 经 `AudioManager.getAudioDevicesForAttributes()` 路由查询（公开免权限，返回与 `getDevices()` 枚举同源），31-32 依赖 `audioDeviceInfo`（系统侧从未填充，普通 App 恒 null，尽力而为）；② 名称/地址关键词（顺序：导航 → 语音 → 警报 → 系统 → 媒体，媒体词最泛化故最后，规则集中在 `data/ChannelTypeRules`）；③ `AudioDeviceInfo.type` 推断（`TYPE_BUS` 不做臆断映射，诚实回落 UNKNOWN）。
- 设备地址：Android 源采集 `AudioDeviceInfo.address`（API 28+，即 audio_policy 端口 tag），是区分同名 BUS 端口的关键，参与去重 key、活跃匹配与关键词判定。
- 声道配置判定：Android 源用 `AudioDeviceInfo.channelMasks`，对每个掩码 `Integer.bitCount(mask)` 求声道数（掩码置位位数即声道数）取最大值映射；ALSA 源尽力读取 `/proc/asound/cardX/pcmY{p,c}/sub0/hw_params`（仅流打开时有数据），回退设备名 `Nch` 短格式。`/proc/asound/pcm` 本身不含声道/采样率/位深（内核 `snd_pcm_proc_read` 格式：`NN-NN: id : name[ : playback N]`），解析格式以内核源码为准。
- 数据来源标识：`AudioChannel.source`（`ChannelSource.ANDROID`=公开 API 主源 / `ALSA`=/proc 节点辅助源），`AudioChannel.typeEvidence`（`MEASURED`=usage 实测 / `INFERRED`=关键词或设备类型推断）区分判定可靠性。
- 活跃判定：基于 `AudioManager.getActivePlaybackConfigurations()`（API 31+），低于 31 返回空集、isActive 诚实回落 false；播放变化经 `registerAudioPlaybackCallback`（API 26+）事件驱动刷新（ViewModel 防抖 400ms），另有每 30s 轮询兜底。
- UI 功能：列表 + 类型过滤（`FlowRow` 自适应换行）；点击卡片弹出 `ModalBottomSheet` 展示完整详情；顶栏分享按钮经 `Intent.ACTION_SEND` 导出纯文本报告（无新权限）；每 30s 自动刷新并支持手动刷新；Android 12+ 启用 Material You 动态取色。
- 展示文案统一：类型/声道/来源文案集中在 `ui/DisplayLabels`，详情页与分享报告共用，避免不一致。
- 权限：无任何运行时权限（不申明存储/录音/网络权限）；音频信息经 AudioManager 公开 API 与 `/proc`、`/dev/snd` 节点读取。`/proc`、`/dev/snd` 在非 root 设备上可能不可读，结果以 Android API 为主源。
- Git 忽略：`.gitignore` 至少忽略 `.gradle/`、`.idea/`、`build/`、`local.properties`、`.worktrees/`。
- 设计参考：设计文档见 `docs/superpowers/specs/2026-05-31-byd-audio-channel-design.md` 与 `SPEC.md`，修改前先查阅。
