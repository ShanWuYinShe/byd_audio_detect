# byd_audio_channel_detect

Android 原生应用，检测比亚迪车机音频通道（识别有效通道、区分媒体/导航、声道组合）。

通用约束（Git 规范、Commit 格式、验证流程、工具链偏好、JDK 配置等）以全局 `~/AGENTS.md` 为准，本文件只做项目特有补充，不得放宽或违背全局约束。


## 构建 & 测试

以下命令用于 GitHub workflow；**本地不做常规构建**，单测/Lint 仅限排障需要；发布产物一律由 CI 构建，日常验证分工见下一节「CI 门禁与本地验证分工」。

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew assembleDebug --no-daemon        # 编译调试 APK（常规由 CI 构建；本地排障可临时执行，不作为发布产物）
./gradlew test --no-daemon                 # 运行单元测试（当前 8 个测试类 78 例，debug 变体；以实跑结果为准）
./gradlew lint --no-daemon                 # Lint（Android 默认规则）
./gradlew clean                            # 清理构建产物
```

Gradle 命令加 `--no-daemon` 的原因见全局 `toolchain-setup` skill（防 Daemon 累积撑爆内存）。

测试：`app/src/test/` 含 `ALSAScannerTest`（类型识别/声道配置/hw_params 解析/设备节点去重）、`ChannelTypeRulesTest`（关键词优先级/usage→类型映射）、`ChannelMergeLogicTest`（跨源去重/设备 key/活跃判定）、`AudioEventRepositoryTest`（事件记录/环形缓冲容量淘汰/清空）、`GetAudioChannelsUseCaseTest`（用例层编排）、`ShareReportFormatterTest`（分享报告文本、标准 JSON 格式化与事件日志输出）、`MainUiLogicTest`（过滤/详情弹层跟随/大屏自适应阈值）、`ToneGeneratorTest`（正弦波 PCM 采样/左右声道发声生成），共 78 例纯函数单测，`./gradlew test` 运行。
**测试数量是易腐烂断言**（当前 8 类 78 例；增删测试后同步本处，取数口径 `grep -rh '@Test' app/src/test --include="*.kt" | wc -l`）。插桩测试：`app/src/androidTest/` 含 `AudioChannelRepositoryImplInstrumentedTest`（真行为测试——`AudioDeviceInfo`/`AudioPlaybackConfiguration` 为 final 类无法 mock，故用 `AudioTrack` 播放指定用途静音音频驱动实测判定/累积/优先级路径），`./gradlew connectedDebugAndroidTest` 需连接设备或模拟器运行。

## CI 门禁与本地验证分工

重活（全量测试、构建、打包、E2E）一律交给 GitHub Actions，本地只跑秒级检查。
完整流程见全局 skill `github-ci-flow`：开发分支推送 → `gh pr create` → `gh pr checks --watch`
全绿 → `gh pr merge --rebase --delete-branch` → 本地 `git pull --ff-only` 并清理 worktree。

- 本地（提交前，秒级）：**不跑 gradle**——本项目所有校验都是 `./gradlew` 任务，单次即分钟级，属重活。
- **APK 构建默认走 GitHub workflow**：Debug 构建由 ci.yml 的 build job 承担，发布产物由 release.yml 在 tag 推送时构建并附到 GitHub Release；本地排障可临时 `./gradlew assembleDebug --no-daemon`（用后清理、不作为发布产物），发版产物仍必须由 tag 触发 release.yml。
  项目未配置可独立运行的格式化工具（无 ktlint/detekt 任务），本地只做文件级检查：
  - `git diff --check`：改动文件无行尾空白、无冲突标记；
  - 文档类改动人工核对其中路径、命令、job 名与项目文件一致；
  - 需要编译、Lint 或单元测试证据时一律看 CI，不在本机执行。
- CI 覆盖（`.github/workflows/ci.yml` 的 `build` job，`push` 限 main + `pull_request` 触发，concurrency 同分支取消旧 run）：开发分支的检查经 PR 的 `pull_request` 触发；连续推送自动取消旧 run，只保留最新结论。
  wrapper-validation 校验 `gradle-wrapper.jar`；`./gradlew --no-daemon lint test assembleDebug assembleRelease`
  覆盖 Android Lint、debug 变体单元测试、Debug APK 构建、Release 构建（R8/minify + 资源收缩；
  无签名凭据时产出 unsigned APK 且不失败）。
- CI 覆盖不到、必须本地/真机验证的：`app/src/androidTest/` 的插桩测试
  （`./gradlew connectedDebugAndroidTest`）需要真机/模拟器，CI 的 ubuntu runner 无设备；
  仅当改动涉及音频检测/设备相关行为时，先在真机或模拟器执行再合并。

## 约束

- 检测领域规则（检测路径/类型识别/设备地址/声道判定/来源标识/活跃判定）：详见 skill byd-audio-detection。
- 架构：MVVM + Clean Architecture（data/domain/ui 三层）。
- UI：Jetpack Compose + Material 3。
- DI：Hilt（`@HiltAndroidApp`、`@AndroidEntryPoint`、`@Module`）。
- 状态管理：ViewModel + StateFlow，ui 层通过 `collectAsStateWithLifecycle()` 收集。
- UI 功能：列表 + 类型过滤（`FlowRow` 自适应换行）；大屏/横屏自适应分栏（宽度 >= 720dp 自动切换为 Master-Detail 左右双栏，右栏常驻展示详情与试音面板）；小屏/竖屏点击卡片弹出 `ModalBottomSheet` 展示详情；通道发声测试（双声道/左声道/右声道正弦波发声测试，基于 AudioTrack setPreferredDevice 定向路由）；顶栏导出菜单支持纯文本报告与标准 JSON 配置导出（`Intent.ACTION_SEND`，无新权限）；第三方盒子集成：内置只读 `AudioChannelProvider`（`content://com.byd.audiochanneldetector.provider/channels`）支持跨进程免交互查询通道路由；通道事件日志与稳定性监控（实时监听热插拔、播放流变更与试音操作，内存环形缓冲最多 50 条）；刷新机制：播放开始/停止经 `AudioPlaybackCallback` 即时触发（400ms 防抖）+ 每 30s 轮询兜底 + 手动刷新；Android 12+ 启用 Material You 动态取色。
- 展示文案统一：类型/声道/来源文案集中在 `ui/DisplayLabels`，详情页与分享报告共用，避免不一致。
- 权限：无任何运行时权限（不申明存储/录音/网络权限）；音频信息经 AudioManager 公开 API 与 `/proc`、`/dev/snd` 节点读取。`/proc`、`/dev/snd` 在非 root 设备上可能不可读，结果以 Android API 为主源。
- Git 忽略：`.gitignore` 至少忽略 `.gradle/`、`.idea/`、`build/`、`local.properties`、`.agents/worktrees/`。
- 设计参考：设计文档见 `docs/superpowers/specs/2026-05-31-byd-audio-channel-design.md` 与 `SPEC.md`，修改前先查阅。
