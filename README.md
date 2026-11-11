# 碎境 · Shatter Realm

A hand-tracked mixed reality throwing and shattering experience for PICO Spatial.

中文系统显示「碎境」，英文系统显示「Shatter Realm」。当前版本 **1.0.3 (4)**，包名 `com.example.shatterrealm`。

## 功能

- 官方手势追踪实现选择、捏合抓取和挥手释放，使用实际跟踪速度，无投掷瞄准辅助。
- 15种玻璃、陶瓷、电子塑料物品；431个碎片定义、2206个组合凸碰撞体。
- 使用系统空间平面碰撞、9.8m/s²重力、保留碎片碰撞，主动重置后清理。
- 上方统计、右侧物品面板固定在空间中；初始仅显示总数，成功碎裂后显示物品分项。
- 支持GLB、FBX、OBJ闭合静态模型导入和自动碎裂；自定义导入不保留贴图和动画。
- 简体中文／英文随系统语言切换，音量跟随系统。

## 运行前提

需要支持Spatial的PICO OS 6头显、手势追踪与可用的系统空间平面。请先在头显系统中完成并保存房间扫描，然后进入应用。平面数据为空时暂不生成物品。应用不制作虚构地面，也没有独立扫描向导。

模型目录：`/sdcard/Android/data/com.example.shatterrealm/files/models/`。单文件≤64MiB、≤150000三角面、最长边≤2m，要求闭合可定向静态实体。

## 构建

Kotlin / SpatialUI Compose / PICO Spatial SDK BOM6.1.9；JDK21、Android SDK35、NDK28.2.13676358、CMake3.22.1，ARM64。

使用官方PICO CLI配置Spatial SDK依赖仓库后，设置本机 `local.properties` 中的 `sdk.dir`，运行：

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

原生库使用随仓库提供的Assimp6.0.2和Manifold3.5.4源码；关闭未使用的上游测试和示例。各依赖许可保留在原目录及 `app/src/main/assets/licenses`。

## 验证状态

- 最新版本构建通过，6项JVM测试通过，已安装运行于PICO B3110。
- 五块面板的实机数值验证为world-up=(0,1,0)、水平边rightY=0；最终佩戴观感仍需复测。
- 早期1.0版本在PICO Emulator6.1中完成15物品逐一碎裂、碎片保留与重置回归。这不是1.0.3的完整实机验收。
- 当前测试房间尚未完成系统扫描，系统服务运行但返回0个平面；真实房间碰撞、长时间碎片性能仍待验证。
- 投稿目录中的截图明确标注早期模拟器版本；海报为宣传设计，不是实机截图。

## 素材与许可

定制图标、界面与模型制作源文件保留在 `design`、`assets` 和 `tools`。玻璃／陶瓷部分碎裂和落地音采用Joseph Sardin发布的CC0录音剪辑，详见 [音频来源](assets/phase-2/audio/SOURCES.md)。不将第三方录音声称为本项目原创现场录音。

本仓库未额外授予项目自有代码／素材的开源许可证；第三方内容分别适用其附带许可证。

本应用仅供娱乐解压，请勿在现实中模仿投掷易碎物品，不提供治疗功能。
