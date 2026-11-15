# Custom BGM Studio

作者：AL-is999（千代不是Chiyo）

一个给 Minecraft 整合包作者用的背景音乐模组：用配置文件管播放列表，自带调音台，配置能整包导入导出。

Minecraft Java 1.21.1 · NeoForge · 纯客户端 · 不需要装在服务端

---

## 它做什么

原版音乐写死在 `sounds.json` 里，换曲子要改资源包、重新打包、处理 events 格式。这个模组绕开资源包，直接读 `config/custombgm/music/` 下的音频文件，自己开一路 OpenAL 播放：

- 支持 `.ogg` / `.wav` / `.aiff` / `.mp3`，不用转格式
- 六个情境分开配：主菜单、生存、创造、水下、下界、末地
- 播放顺序、曲间间隔、单曲音量、淡入淡出全部写在 JSON 里，可以跟着整合包做版本管理
- 内置均衡、压缩、混响、立体声宽度、变速，逐首或批量调
- 配置包导出成 zip，含音频，换机器导入即完全一致

如果某个情境没配曲目，原版音乐照常播。可以只换下界音乐、其它保持原版。

---

## 安装

1. 装 NeoForge 1.21.1。
2. 把 `custombgm-x.y.z.jar` 放进 `.minecraft/mods/`。
3. 启动游戏，生成 `config/custombgm/`。

只装客户端就行，服务器上不需要这个模组。

---

## 快速开始

```
.minecraft/config/custombgm/
├── config.json          播放列表 + 全局调音参数
├── music/               音频文件放这里
└── profiles/            导出的配置包
```

1. 把音频文件扔进 `music/`，重启游戏，它们会自动进「生存」和「主菜单」两个列表。
2. 打开 `config.json`，把 `"debugMode": false` 改成 `true`。
3. 进游戏按 **F9**（或在模组列表点 Config），打开工作台。
4. 切到各个情境，增删曲目、调顺序和间隔。
5. 点「调音」，逐首或批量调整。
6. 点「配置包」，填个名字导出。
7. 发布前把 `debugMode` 改回 `false`，玩家就只能听、不能改。

调试模式关着的时候，增删、调音、导入导出的按钮全部是灰的，玩家只能关掉整个音乐引擎。

---

## 配置说明

### 全局

| 字段 | 说明 |
| --- | --- |
| `enabled` | 音乐引擎总开关 |
| `debugMode` | 打开后 GUI 才允许编辑 |
| `replaceVanillaMusic` | 是否压掉原版背景音乐 |
| `masterVolume` | 主音量，0 ~ 2 |
| `fadeInSeconds` / `fadeOutSeconds` / `crossfadeSeconds` | 淡入、淡出、切曲交叉淡化时长 |
| `autoPopulate` | 把 `music/` 里没进列表的文件自动收进 `autoSections` |
| `autoSections` | 参与自动收录的情境，默认 `["game", "menu"]` |
| `autoExclude` | 手动移除过的文件，不再被自动收录加回来 |
| `effects` | 全局默认调音参数 |
| `sections` | 各情境的播放列表 |

### 情境（`sections.<id>`）

`id` 取 `menu` / `game` / `creative` / `underwater` / `nether` / `end`。

| 字段 | 说明 |
| --- | --- |
| `enabled` | 这个情境用不用自定义音乐 |
| `mode` | `SEQUENTIAL` 顺序循环 / `SHUFFLE` 每轮洗牌 / `WEIGHTED_RANDOM` 按权重抽 / `ONCE` 播一遍就停 |
| `gapSeconds` | 一首播完后静默多少秒，0 表示立刻接 |
| `tracks` | 曲目数组 |

### 曲目（`tracks[]`）

| 字段 | 说明 |
| --- | --- |
| `file` | `music/` 下的文件名 |
| `volume` | 单曲音量倍率，用来拉平响度 |
| `weight` | 权重随机模式的权重，默认 1 |
| `effects` | 可选。非 null 时覆盖全局调音参数 |

---

## 调音参数

开关：`eqEnabled`、`compressorEnabled`、`reverbEnabled`。

| 参数 | 范围 | 说明 |
| --- | --- | --- |
| `gainDb` | -24 ~ +24 | 输出增益 |
| `lowShelfGainDb` | -24 ~ +24 | 低频搁架，默认 200 Hz |
| `peak1GainDb` | -24 ~ +24 | 中低频峰值，默认 800 Hz |
| `peak2GainDb` | -24 ~ +24 | 中高频峰值，默认 3 kHz |
| `highShelfGainDb` | -24 ~ +24 | 高频搁架，默认 6 kHz |
| `stereoWidth` | 0 ~ 2 | 0 单声道，1 原样，2 加倍展宽 |
| `speed` | 0.5 ~ 2.0 | 磁带式变速，同时改变音高 |
| `compThresholdDb` | -60 ~ 0 | 压缩阈值 |
| `compRatio` | 1 ~ 20 | 压缩比 |
| `compMakeupDb` | 0 ~ 24 | 补偿增益 |
| `reverbMix` | 0 ~ 1 | 混响湿声比例 |
| `reverbRoom` | 0 ~ 1 | 混响空间大小 |
| `reverbDamp` | 0 ~ 1 | 混响阻尼 |

最终音量：

```
游戏"音乐"滑块 × masterVolume × 单曲 volume × 淡入淡出包络
```

玩家在游戏设置里拉低音乐，这个模组也跟着变小。

---

## 导入导出

导出的 zip 里既有 `profile.json` 也有它引用的所有音频文件。换一台机器导入，播放列表、顺序、每首音量、每首调音、全局参数全部一致，不用再调一次。

导入会覆盖当前 `config.json`，并把 zip 里的音频写进 `music/`（同名覆盖），已有的其它音频不动。配置包放在 `profiles/`，可以直接当文件分发。

---

## 从源码构建

需要 JDK 21 和 Gradle 8.10+。

```bash
gradle build
```

产物在 `build/libs/`。

没装 Gradle 也行——IDEA 打开项目目录会自动用它内置的 Gradle 构建。装好 Gradle 之后建议跑一次 `gradle wrapper` 把 wrapper 提交进仓库，之后就能用 `./gradlew build`。

仓库带了 GitHub Actions（`.github/workflows/build.yml`），推上去之后每次提交都会自动构建。在 Actions 页面点进对应运行，底部 Artifacts 里能下载 jar，不需要本地装任何东西。国内网络连不上 `maven.neoforged.net` 的话，这是最省事的一条路。

### 版本

- Minecraft 1.21.1
- NeoForge 21.1.209（改 `gradle.properties` 的 `neo_version` 即可换版本）
- Java 21

### 构建故障排查

**连不上 `maven.neoforged.net`**：国内网络常见。NeoForge 的构件只发布在官方 Maven，BMCLAPI 之类的镜像并没有完整覆盖（`neoform-runtime` 就找不到）。挂代理，或者直接用 GitHub Actions 构建。

**`Could not find method jarJar()`**：ModDevGradle 版本差异。把 `build.gradle` 里那段 `jarJar(implementation('javazoom:jlayer:1.0.1'))` 换成 `implementation 'javazoom:jlayer:1.0.1'`，其余不动。这样能构建，但 MP3 支持需要额外把 jlayer 放进 mods 目录。

**`Unsupported class file major version`**：Gradle 跑在了太新的 JDK 上。用 JDK 21 启动 Gradle。

---

## 实现要点

- `MusicManagerMixin` 只注入 `MusicManager#tick` 的 HEAD，判断当前情境有没有自定义曲目，有就 `cancel`。原版调度逻辑一行没改。
- 播放不走 `SoundEngine`，直接 `alGenSources` 开一路自己的 source，三缓冲流式，每块一秒，在客户端 tick 里补数据。所以不依赖资源包，也不需要重载资源。
- DSP 在 float 交错 PCM 上原地处理：EQ → 压缩 → 立体声宽度 → 混响 → 增益 → 软限幅。
- 变速用线性插值重采样，放在解码和 DSP 之间，不改变标称采样率。
- 所有 OpenAL 调用都在客户端主线程，不另起线程抢 context。

---

## 已知限制

- 只管背景音乐。唱片机、洞穴音效、Boss 战音乐不在范围内——Boss 战走 `BossEvent` 那条路径，不经过 `MusicManager`。
- 变速是磁带式，不能独立变调。
- 混响是简化的 Schroeder 结构，够用但不如专业插件。
- 音频版权自负。往整合包里塞商业音乐的风险在你这边。

---

## 许可证

MIT
