# CLAUDE.md

Android 原生应用，检测比亚迪车机音频通道（识别有效通道、区分媒体/导航、声道组合）。

## 构建 & 测试

```bash
./gradlew assembleDebug                    # 编译调试 APK
./gradlew lint                             # Lint（Android 默认规则）
./gradlew clean                            # 清理构建产物
```

> **测试**：当前无 `app/src/test/` 目录，`./gradlew test` 无用例可运行。待项目稳定后补充。

## 约束

- **架构**：MVVM + Clean Architecture（data/domain/ui 三层）。
- **UI**：Jetpack Compose + Material 3。
- **DI**：Hilt（`@HiltAndroidApp`、`@AndroidEntryPoint`、`@Module`）。
- **状态管理**：ViewModel + StateFlow，ui 层通过 `collectAsStateWithLifecycle()` 收集。
- **音频检测路径**（优先级由高到低）：`/proc/asound/cards` → `/proc/asound/pcm` → `/dev/snd/` → `AudioManager.getDevices()` → `AudioMixer.getActiveTracks()`。
- **通道类型识别**：关键词匹配（media=MEDIA、nav=NAVIGATION、voice=VOICE_CALL 等）。
- **权限**：当前仅 `READ_EXTERNAL_STORAGE`，无 INTERNET 或 RECORD_AUDIO。
- **Git 忽略**：`.gitignore` 至少忽略 `.gradle/`、`.idea/`、`build/`、`local.properties`、`.claude/worktrees/`；本仓库已据此创建 `.gitignore`。
- **设计参考**：`docs/` 目录和 `SPEC.md` 含详细设计文档，修改前先查阅。
