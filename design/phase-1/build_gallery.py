from pathlib import Path
import json,html
p=Path(__file__).parent
s=json.loads((p/'design-spec.json').read_text())
sections=[('logo','启动标记','冷启动的短暂停顿，只显示系统语言的应用名。'),('safety','安全准入','明确同意才进入体验；同一进程会话恢复不重复弹出。'),('main','主体验与计数','初始只显示总投掷数量，避免占用上方视野。'),('shelf-end','物品架末尾','横向浏览到末尾，即可看到刷新与重置。'),('controller','手柄提示','识别激活使用状态，停止消费手柄事件；手势恢复后回原状态。'),('reset','重置确认','防误触提案：确认后清空碎片、在场物体及计数。'),('counts-active','使用后才展开计数','物品首次成功碎裂后显示名称与数量；较多明细可横向滚动。'),('custom-counts','自定义物品计数','自定义物品也在成功碎裂后显示，未使用的模型不占计数面板。')]
body=''
for id_,title,desc in sections:
 body+=f'<section><div class="section-title"><span>界面设计</span><h2>{title}</h2><p>{desc}</p></div><div class="pair">'
 for lang,label in [('zh','中文系统'),('en','英文系统')]:
  body+=f'<figure><figcaption>{label}</figcaption><a href="screens/{id_}-{lang}.png"><img loading="lazy" src="screens/{id_}-{lang}.png" alt="{label}{title}"></a></figure>'
 body+='</div></section>'
rows=''
for cat,label in [('glass','玻璃'),('ceramic','陶瓷'),('plastic','电子塑料')]:
 rows+=f'<h3>{label}</h3><ol start="'+str(next(x['cell'] for x in s['dataCases']['normal']['items'] if x['material']==cat)+1)+'">'
 for it in s['dataCases']['normal']['items']:
  if it['material']==cat: rows+=f'<li><b>{it["zh"]}</b><span>{it["concept"]}</span></li>'
 rows+='</ol>'
page='''<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>碎境 · 第一阶段评审</title><style>
*{box-sizing:border-box}body{margin:0;background:#f0efeb;color:#28362f;font:16px/1.7 -apple-system,BlinkMacSystemFont,'PingFang SC',sans-serif}main{max-width:1400px;margin:auto;padding:64px 40px 80px}a{color:inherit;text-decoration-color:#b0bdb1;text-underline-offset:5px}header{max-width:900px;margin-bottom:80px}small,.section-title>span{font-size:12px;letter-spacing:3px;color:#62756a}h1{font-size:64px;font-weight:500;letter-spacing:-2px;line-height:1.1;margin:24px 0}h2{font-size:32px;font-weight:500;margin:8px 0}h3{font-size:20px;font-weight:500}.intro{max-width:720px;color:#52635a;font-size:20px}nav{display:flex;gap:16px;margin-top:28px;flex-wrap:wrap}nav a{padding:12px 20px;border:1px solid #bdc7bf;border-radius:30px;font-size:14px;text-decoration:none}section{margin-top:64px}.section-title{margin-bottom:22px}.section-title p{color:#62756a;margin:0}.pair{display:grid;grid-template-columns:1fr 1fr;gap:24px}figure{margin:0}figcaption{font-size:13px;color:#62756a;margin-bottom:10px}img{width:100%;height:auto;display:block;border-radius:16px}.wide{background:white;padding:10px;border-radius:22px}.catalog-labels{display:grid;grid-template-columns:repeat(3,1fr);gap:32px}.catalog-labels ol{padding-left:24px}.catalog-labels li{margin-bottom:14px}.catalog-labels li span{display:block;color:#62756a;font-size:13px}.note{font-size:14px;color:#62756a;margin-top:22px}.sound{display:grid;grid-template-columns:repeat(4,1fr);gap:30px}.sound article{padding-top:20px;border-top:1px solid #bdc7bf}.sound p{font-size:14px;color:#62756a}footer{border-top:1px solid #bdc7bf;padding-top:30px;margin-top:70px;color:#62756a}@media(max-width:750px){main{padding:32px 20px}.pair,.catalog-labels,.sound{grid-template-columns:1fr}h1{font-size:44px}}
</style></head><body><main><header><small>第一阶段 / 概念与界面 / 待确认</small><h1>碎境</h1><p class="intro">安静的现实，清晰的碎裂。<br>真实日用品、自定义烟灰面板和可信的材质声音，让中央视野留给你的动作。</p><p class="note">这是评审页面。下方两种语言是独立设计稿，不代表应用会同时显示双语。全部画面为概念设计或网页预览截图，不是实机运行截图。</p><nav><a href="design-doc.md">完整设计方案</a><a href="preview.html?lang=zh">体验中文界面流程</a><a href="preview.html?lang=en">体验英文界面流程</a></nav></header>'''
page+='<section id="launcher"><div class="section-title"><span>安装包图标</span><h2>把裂隙，留在应用入口</h2><p>烟灰底、象牙白方框与浅绿裂隙；不嵌文字，中英文系统共用同一图形。</p></div><a href="concepts/launcher/icon-board.png"><img src="concepts/launcher/icon-board.png" alt="应用图标设计及圆形、圆角裁切和小尺寸预览"></a><p class="note">这是第一阶段图标提案。第二阶段定稿导出，第三阶段写入安装包资源并验证 PICO 系统中的实际显示。</p></section>'
page+=body
page+='''<section><div class="section-title"><span>模型概念</span><h2>十五件熟悉的日用品</h2><p>三行五列，按下面编号由左至右阅读；这是一张概念图，不是三维资产。</p></div><a href="concepts/objects.png"><img class="wide" src="concepts/objects.png" alt="15种日用品，按玻璃、陶瓷、电子塑料分三行"></a><div class="catalog-labels">'''+rows+'''</div></section>
<section><div class="section-title"><span>碎裂语言</span><h2>落下，破碎，留下</h2><p>不使用魔法光效和夸张爆炸；重量、断面、接触阴影和碎片沉积构成反馈。</p></div><a href="concepts/fracture.png"><img src="concepts/fracture.png" alt="玻璃撞击碎裂视觉故事板"></a><p class="note">所有实体碎片须有碰撞体，保留到用户主动重置。图中轨迹和断口是艺术概念，物理结果在第三阶段通过实机验证。</p></section>
<section><div class="section-title"><span>声音方向</span><h2>每一种材质，都有自己的回声</h2><p>第二阶段制作四组材质事件声与界面声；当前没有生成正式音频。</p></div><div class="sound"><article><h3>玻璃</h3><p>轻薄掠过声 → 清脆初撞与厚底碎裂 → 高频细碎触地。</p></article><article><h3>陶瓷</h3><p>沉实空气声 → 瓷胎闷击与干脆断裂 → 有重量的瓷片敲击。</p></article><article><h3>电子塑料</h3><p>低频短掠 → 卡扣与外壳分离 → 键帽、壳片干涩弹跳。</p></article><article><h3>自定义通用</h3><p>中性投掷与硬质断裂，搭配中性碎片触地，不强加玻璃材质。</p></article></div></section>
<footer>请审阅后确认第一阶段，或指出要调整的具体界面、物品与音色。确认后进入第二阶段素材制作，素材再次确认后才进入应用代码开发。</footer></main></body></html>'''
(p/'index.html').write_text(page)
print('Review gallery generated')
