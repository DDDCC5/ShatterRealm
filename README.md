# 碎境 · Shatter Realm

A hand-tracked mixed reality throwing and shattering experience for PICO Spatial.

中文系统显示「碎境」，英文系统显示「Shatter Realm」。当前版本 **1.0.10 (11)**，包名 `com.example.shatterrealm`。

## 功能

- 官方手势追踪实现选择、捏合抓取和挥手释放，使用实际跟踪速度，无投掷瞄准辅助。
- 15种玻璃、陶瓷、电子塑料物品；431个碎片定义，每个碎片使用独立凸碰撞体。
- 使用系统空间平面碰撞、9.8m/s²重力、保留碎片碰撞，主动重置后清理。
- 上方统计、右侧物品面板固定在空间中；初始仅显示总数，成功碎裂后显示物品分项。
- 选中物品固定生成在用户正前方中央，左右手均可捏合抓取；持物阶段仅更新外观，松手时启用物理。
- 支持GLB、FBX、OBJ闭合静态模型导入和自动碎裂；自定义导入不保留贴图和动画。
- 简体中文／英文随系统语言切换，音量跟随系统。

## 运行前提

需要支持Spatial的PICO OS 6头显、手势追踪与可用的系统空间平面。请先在头显系统中完成并保存房间扫描，然后进入应用。平面数据为空时暂不生成物品。应用没有独立扫描向导。碎片专用支撑以系统已测地面的高度和朝向为依据外扩；完整物体仍须碰到系统实际平面才会碎裂。

模型目录：`/sdcard/Android/data/com.example.shatterrealm/files/models/`。单文件≤64MiB、≤150000三角面、最长边≤2m，要求闭合可定向静态实体。

## 构建

Kotlin / SpatialUI Compose / PICO Spatial SDK BOM6.1.9；JDK21、Android SDK35、NDK28.2.13676358、CMake3.22.1，ARM64。

使用官方PICO CLI配置Spatial SDK依赖仓库后，设置本机 `local.properties` 中的 `sdk.dir`，运行：

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

原生库使用随仓库提供的Assimp6.0.2和Manifold3.5.4源码；关闭未使用的上游测试和示例。各依赖许可保留在原目录及 `app/src/main/assets/licenses`。

## 最新版本与验证

- [1.0.10 APK 与版本说明](https://github.com/DDDCC5/ShatterRealm/releases/tag/v1.0.10)
- [更新记录](CHANGELOG.md)
- 构建与32项JVM单元测试通过；最终APK已安装并在PICO实机运行。
- 当前版本使用合成双手追踪输入，在实机验证前方生成、左手抓取、8秒持物移动、键盘77块碎片停留、休眠恢复及重置；模拟器验证右手抓取、玻璃杯24块碎片停留、恢复及重置。这不代替真实手势舒适度验收。
- 1.0.8曾在模拟器逐一验证15种物品、累计431块碎片，并完成99块碎片同时保留专项；不能将其表述为当前版本的全15种实机验收。
- 已减少持物阶段的物理与重复姿态更新开销，但实测仍有偶发慢帧，尚未完全消除掉帧；不宣称固定FPS或提升百分比。
- 旧版投稿目录中的截图明确标注早期模拟器版本；海报为宣传设计，不是本版实机截图。
- 音效按整件物品分组播放，避免每块碎片依次配音导致过长尾音。

## 素材与许可

定制图标、界面与模型制作源文件保留在 `design`、`assets` 和 `tools`。玻璃／陶瓷部分碎裂和落地音采用Joseph Sardin发布的CC0录音剪辑，详见 [音频来源](assets/phase-2/audio/SOURCES.md)。不将第三方录音声称为本项目原创现场录音。

本仓库未额外授予项目自有代码／素材的开源许可证；第三方内容分别适用其附带许可证。

本应用仅供娱乐解压，请勿在现实中模仿投掷易碎物品，不提供治疗功能。
