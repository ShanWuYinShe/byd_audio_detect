# CLAUDE.md

Android 原生应用，检测比亚迪车机音频通道（识别有效通道、区分媒体/导航、声道组合）。

## 构建 & 测试

```bash
./gradlew assembleDebug                    # 编译调试 APK
./gradlew test                             # 运行单元测试（ALSAScanner 纯函数）
./gradlew lint                             # Lint（Android 默认规则）
./gradlew clean                            # 清理构建产物
```

> **测试**：`app/src/test/` 含 `ALSAScannerTest`（类型识别/声道配置/采样率/位深）、`ChannelMergeLogicTest`（跨源去重/活跃判定）、`ShareReportFormatterTest`（分享报告格式化），共 30 例纯函数单测，`./gradlew test` 运行。`AudioChannelRepositoryImpl` 中依赖 `AudioDeviceInfo`/`AudioManager` 的设备枚举与类型推断逻辑需插桩测试。

## 约束

- **架构**：MVVM + Clean Architecture（data/domain/ui 三层）。
- **UI**：Jetpack Compose + Material 3。
- **DI**：Hilt（`@HiltAndroidApp`、`@AndroidEntryPoint`、`@Module`）。
- **状态管理**：ViewModel + StateFlow，ui 层通过 `collectAsStateWithLifecycle()` 收集。
- **音频检测路径**（优先级由高到低）：`/proc/asound/cards` → `/proc/asound/pcm` → `/dev/snd/` → `AudioManager.getDevices()`。活跃状态基于 `AudioManager.getActivePlaybackConfigurations()`（API 31+）。
- **通道类型识别**：关键词匹配（media=MEDIA、nav=NAVIGATION、voice=VOICE_CALL 等）。
- **声道配置判定**：Android 源用 `AudioDeviceInfo.channelMasks`，对每个掩码 `Integer.bitCount(mask)` 求声道数（掩码置位位数即声道数）取最大值映射；ALSA 源解析 `/proc/asound/pcm` 的 `N channels` 长格式，回退 `Nch` 短格式。
- **数据来源标识**：`AudioChannel.source`（`ChannelSource.ANDROID`=公开 API 主源 / `ALSA`=/proc 节点辅助源），区分结果可靠性。
- **活跃判定**：基于 `AudioManager.getActivePlaybackConfigurations()`（API 31+），低于 31 返回空集、isActive 诚实回落 false。
- **UI 功能**：列表 + 类型过滤（`FlowRow` 自适应换行）；点击卡片弹出 `ModalBottomSheet` 展示完整详情；顶栏分享按钮经 `Intent.ACTION_SEND` 导出纯文本报告（无新权限）；每 30s 自动刷新并支持手动刷新；Android 12+ 启用 Material You 动态取色。
- **展示文案统一**：类型/声道/来源文案集中在 `ui/DisplayLabels`，详情页与分享报告共用，避免不一致。
- **权限**：无任何运行时权限（不申明存储/录音/网络权限）；音频信息经 AudioManager 公开 API 与 `/proc`、`/dev/snd` 节点读取。`/proc`、`/dev/snd` 在非 root 设备上可能不可读，结果以 Android API 为主源。
- **Git 忽略**：`.gitignore` 至少忽略 `.gradle/`、`.idea/`、`build/`、`local.properties`、`.claude/worktrees/`；本仓库已据此创建 `.gitignore`。
- **构建 JDK**：本机 shell 无独立 JDK，Gradle 用 Android Studio 自带 JBR（`/Applications/Android Studio.app/Contents/jbr/Contents/Home`，JDK 21，符合 `gradle-daemon-jvm.properties`）。
- **设计参考**：`docs/` 目录和 `SPEC.md` 含详细设计文档，修改前先查阅。
