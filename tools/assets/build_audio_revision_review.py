from pathlib import Path
import json,html
R=Path(__file__).resolve().parents[2]/'assets/phase-2'
sections=''
for group,title in [('glass','玻璃'),('ceramic','陶瓷')]:
 for event,label,desc in [('shatter','碰撞碎裂','保留实物撞击与第一轮破裂，去除原版固定音高的长鸣。'),('fragment-land','碎片落地','取自破裂后的真实碎片尾声，保留不规则触地与逐渐收尾。')]:
  sections+=f'<section><h2>{title} · {label}</h2><p>{desc}</p><div class="variants">'
  for v in range(1,4):
   url=f'audio/materials/{group}/{event}-{v:02}.wav';old=f'audio/revisions/v1/materials/{group}/{event}-{v:02}.wav'
   sections+=f'<article><h3>新版 · 变体 {v}</h3><audio controls preload="metadata" src="{url}?rev=2"></audio><a href="{url}?rev=2" download>下载 WAV</a><details><summary>试听旧版对比</summary><audio controls preload="none" src="{old}"></audio></details></article>'
  sections+='</div></section>'
page='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>碎境 · 玻璃与陶瓷音效修订</title><style>*{box-sizing:border-box}body{margin:0;background:#efefea;color:#283b2d;font:16px/1.65 -apple-system,'PingFang SC',sans-serif}main{max-width:1200px;margin:auto;padding:55px 35px}h1{font-size:38px;font-weight:500;margin:10px 0 22px}h2{font-size:25px;font-weight:500}h3{font-size:17px;font-weight:500}p{color:#5b6d60}small{letter-spacing:2px;color:#6a7c6b}section{margin-top:45px}.variants{display:grid;grid-template-columns:repeat(3,1fr);gap:20px}article{padding:20px;background:#fff9;border-radius:20px}audio{width:100%;height:38px}a{color:inherit;text-underline-offset:4px;font-size:14px}details{margin-top:18px;font-size:13px}details audio{margin-top:15px}summary{cursor:pointer}.note{padding:15px 20px;background:#dfe8de;border-radius:15px}nav{display:flex;gap:25px;flex-wrap:wrap}@media(max-width:750px){.variants{grid-template-columns:1fr}main{padding:30px 20px}h1{font-size:30px}}</style><main><small>第二阶段 / 音效修订第2版 / 待试听确认</small><h1>重新听见破裂，而不是敲击长鸣。</h1><p class="note">仅更换玻璃和陶瓷的碰撞碎裂、碎片落地，共12个样本。所有投掷音、电子塑料、自定义通用及UI音效保持原文件字节不变。</p><p>本版剪辑自已核验CC0标注的实物录音：玻璃落地破裂、瓷盘摔落及其碎片尾声。未添加振荡器音调、人工长混响或音高变换。作者、原文件与剪辑区间已记录；并非本项目现场录音。</p><nav><a href="index.html#audio">完整素材页</a><a href="audio/SOURCES.md">来源与授权记录</a><a href="review/audio-revision-2.json">文件保持不变检查</a></nav>'''+sections+'''<section><p>播放器均由你手动播放，不自动发声。请重点判断玻璃是否有碎片颗粒感、陶瓷是否摆脱金属空罐听感；音色仍以你的试听为准。</p></section></main><script>document.addEventListener('play',event=>{if(event.target.tagName==='AUDIO')document.querySelectorAll('audio').forEach(a=>{if(a!==event.target)a.pause()})},true)</script></html>'''
(R/'audio-review.html').write_text(page)
print('Audio revision listening page written: 12 new samples + optional old comparisons')
