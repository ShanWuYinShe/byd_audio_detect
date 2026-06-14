# CLAUDE.md

Android 原生应用，检测比亚迪车机音频通道（识别有效通道、区分媒体/导航、声道组合）。

## 构建 & 测试

```bash
./gradlew assembleDebug                    # 编译调试 APK
./gradlew test                             # 运行单元测试（ALSAScanner 纯函数）
./gradlew lint                             # Lint（Android 默认规则）
./gradlew clean                            # 清理构建产物
```

> **测试**：`app/src/test/` 含 `ALSAScannerTest`，覆盖类型识别/声道配置/采样率/位深等纯函数（13 例），`./gradlew test` 运行。依赖 Android Framework（`AudioDeviceInfo`）的 Repository 映射逻辑难做 JVM 单测，留作插桩测试。

## 约束

- **架构**：MVVM + Clean Architecture（data/domain/ui 三层）。
- **UI**：Jetpack Compose + Material 3。
- **DI**：Hilt（`@HiltAndroidApp`、`@AndroidEntryPoint`、`@Module`）。
- **状态管理**：ViewModel + StateFlow，ui 层通过 `collectAsStateWithLifecycle()` 收集。
- **音频检测路径**（优先级由高到低）：`/proc/asound/cards` → `/proc/asound/pcm` → `/dev/snd/` → `AudioManager.getDevices()` → `AudioMixer.getActiveTracks()`。
- **通道类型识别**：关键词匹配（media=MEDIA、nav=NAVIGATION、voice=VOICE_CALL 等）。
- **声道配置判定**：Android 源用 `AudioDeviceInfo.channelMasks`，对每个掩码 `Integer.bitCount(mask)` 求声道数（掩码置位位数即声道数）取最大值映射；ALSA 源解析 `/proc/asound/pcm` 的 `N channels` 长格式，回退 `Nch` 短格式。
- **数据来源标识**：`AudioChannel.source`（`ChannelSource.ANDROID`=公开 API 主源 / `ALSA`=/proc 节点辅助源），区分结果可靠性。
- **活跃判定**：基于 `AudioManager.getActivePlaybackConfigurations()`（API 31+），低于 31 返回空集、isActive 诚实回落 false。
- **UI 功能**：列表 + 类型过滤；点击卡片弹出 `ModalBottomSheet` 展示完整详情；顶栏分享按钮经 `Intent.ACTION_SEND` 导出纯文本报告（无新权限）。
- **权限**：当前仅 `READ_EXTERNAL_STORAGE`，无 INTERNET 或 RECORD_AUDIO。
- **Git 忽略**：`.gitignore` 至少忽略 `.gradle/`、`.idea/`、`build/`、`local.properties`、`.claude/worktrees/`；本仓库已据此创建 `.gitignore`。
- **构建 JDK**：本机 shell 无独立 JDK，Gradle 用 Android Studio 自带 JBR（`/Applications/Android Studio.app/Contents/jbr/Contents/Home`，JDK 21，符合 `gradle-daemon-jvm.properties`）。
- **设计参考**：`docs/` 目录和 `SPEC.md` 含详细设计文档，修改前先查阅。
