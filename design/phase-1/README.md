# 第一阶段交付

先打开 `index.html` 查看评审总览，再阅读 `design-doc.md`。

- `preview.html`：离线布局与流程预览；默认中文。用 `?lang=en` 预览英文；语言参数仅供评审，不是应用设置。
- `screens/`：每种语言独立导出的 Logo、安全、主体验、物品架末尾、手柄提示、重置、使用后计数和自定义计数设计图。
- `concepts/objects.png`：三行五列，玻璃/陶瓷/电子塑料各五种。
- `concepts/fracture.png`：落下、碎裂、留存的视觉故事板。
- `concepts/prompts.json`：内置 imagegen 生成提示词；其余 UI 由自定义矢量及 JSON 驱动网页渲染生成。
- `design-spec.json`：版本4，设计规格和可触发状态机。
- `review/preview-checks.json`：网页布局、单语言、流程及规格一致性检查。
- `review/builder-review.md`：独立评审及修订结果。
- `../../docs/environment/`：CLI 环境健康报告。

本目录没有正式 3D 模型、音频、应用代码或 APK。图像为概念参考和网页设计稿，不是头显运行截图。需要用户确认第一阶段后再进入素材制作。

- `icon-preview.html` / `concepts/launcher/icon-board.png`：安装包与系统应用图标提案及多尺寸裁切评审。
- `concepts/launcher/`：图标的矢量前景、背景、单色层与评审母图；尚未配置到 APK。
