# byd_audio_channel_detect

Android 原生应用，检测比亚迪车机音频通道（识别有效通道、区分媒体/导航、声道组合）。

通用约束（Git 规范、Commit 格式、验证流程、工具链偏好、JDK 配置等）以全局 `~/AGENTS.md` 为准，本文件只做项目特有补充，不得放宽或违背全局约束。


## 构建 & 测试

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew assembleDebug --no-daemon        # 编译调试 APK
./gradlew test --no-daemon                 # 运行单元测试（4 个测试类 48 例，debug 变体）
./gradlew lint --no-daemon                 # Lint（Android 默认规则）
./gradlew clean                            # 清理构建产物
```

Gradle 命令加 `--no-daemon` 的原因见全局 `toolchain-setup` skill（防 Daemon 累积撑爆内存）。

测试：`app/src/test/` 含 `ALSAScannerTest`（类型识别/声道配置/hw_params 解析）、`ChannelTypeRulesTest`（关键词优先级/usage→类型映射）、`ChannelMergeLogicTest`（跨源去重/设备 key/活跃判定）、`ShareReportFormatterTest`（分享报告格式化），共 48 例纯函数单测，`./gradlew test` 运行。插桩测试：`app/src/androidTest/` 含 `AudioChannelRepositoryImplInstrumentedTest`（真行为测试——`AudioDeviceInfo`/`AudioPlaybackConfiguration` 为 final 类无法 mock，故用 `AudioTrack` 播放指定用途静音音频驱动实测判定/累积/优先级路径），`./gradlew connectedDebugAndroidTest` 需连接设备或模拟器运行。

## 约束

- 检测领域规则（检测路径/类型识别/设备地址/声道判定/来源标识/活跃判定）：详见 skill byd-audio-detection。
- 架构：MVVM + Clean Architecture（data/domain/ui 三层）。
- UI：Jetpack Compose + Material 3。
- DI：Hilt（`@HiltAndroidApp`、`@AndroidEntryPoint`、`@Module`）。
- 状态管理：ViewModel + StateFlow，ui 层通过 `collectAsStateWithLifecycle()` 收集。
- UI 功能：列表 + 类型过滤（`FlowRow` 自适应换行）；点击卡片弹出 `ModalBottomSheet` 展示完整详情；顶栏分享按钮经 `Intent.ACTION_SEND` 导出纯文本报告（无新权限）；每 30s 自动刷新并支持手动刷新；Android 12+ 启用 Material You 动态取色。
- 展示文案统一：类型/声道/来源文案集中在 `ui/DisplayLabels`，详情页与分享报告共用，避免不一致。
- 权限：无任何运行时权限（不申明存储/录音/网络权限）；音频信息经 AudioManager 公开 API 与 `/proc`、`/dev/snd` 节点读取。`/proc`、`/dev/snd` 在非 root 设备上可能不可读，结果以 Android API 为主源。
- Git 忽略：`.gitignore` 至少忽略 `.gradle/`、`.idea/`、`build/`、`local.properties`、`.worktrees/`。
- 设计参考：设计文档见 `docs/superpowers/specs/2026-05-31-byd-audio-channel-design.md` 与 `SPEC.md`，修改前先查阅。
