from pathlib import Path
import json
p=Path(__file__).parent
folder=p/'concepts/launcher';folder.mkdir(exist_ok=True)
background='''<defs><linearGradient id="smoke" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#3D4B43"/><stop offset=".55" stop-color="#252E29"/><stop offset="1" stop-color="#1C2420"/></linearGradient><radialGradient id="sheen" cx=".22" cy=".08" r=".9"><stop stop-color="#B9E6C5" stop-opacity=".13"/><stop offset="1" stop-color="#B9E6C5" stop-opacity="0"/></radialGradient></defs><path fill="url(#smoke)" d="M0 0h1024v1024H0z"/><path fill="url(#sheen)" d="M0 0h1024v1024H0z"/>'''
mark='''<g transform="translate(232 232) scale(3.5)" fill="none" stroke-width="8.5" stroke-linecap="square"><path stroke="#F4F1E8" d="M64 20H24v116h45M95 20h41v116H96"/><path stroke="#B9E6C5" d="M89 15 68 69l27 20-22 58"/></g>'''
def svg(content):return '<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">'+content+'</svg>'
(folder/'launcher-master.svg').write_text(svg(background+mark))
(folder/'launcher-foreground.svg').write_text(svg(mark))
(folder/'launcher-background.svg').write_text(svg(background))
(folder/'launcher-monochrome.svg').write_text(svg(mark.replace('#F4F1E8','#FFFFFF').replace('#B9E6C5','#FFFFFF')))
f=p/'build_design.py';s=f.read_text().replace("spec['metadata']['revision']=3","spec['metadata']['revision']=4")
needle="(ROOT/'design-spec.json').write_text"
insert="""spec['dataCases']['normal']['launcherIcon']={'conceptOnly':True,'textFree':True,'masterSizePx':1024,'foreground':'concepts/launcher/launcher-foreground.svg','background':'concepts/launcher/launcher-background.svg','monochrome':'concepts/launcher/launcher-monochrome.svg','androidReference':'https://developer.android.com/develop/ui/compose/system/icon_design_adaptive','packaging':'materialize Android resources and verify actual PICO launcher in phase 3'}
spec['assets'].append(dict(id='launcher-icon',kind='image',src='concepts/launcher/launcher-master.png',purpose='安装包与系统应用列表图标的第一阶段设计稿，无文字、单一品牌标记'))
"""
if "normal']['launcherIcon']" not in s:s=s.replace(needle,insert+needle)
f.write_text(s)
f=p/'build_gallery.py';s=f.read_text()
insert='''page+='''+repr('''<section id="launcher"><div class="section-title"><span>安装包图标</span><h2>把裂隙，留在应用入口</h2><p>烟灰底、象牙白方框与浅绿裂隙；不嵌文字，中英文系统共用同一图形。</p></div><a href="concepts/launcher/icon-board.png"><img src="concepts/launcher/icon-board.png" alt="应用图标设计及圆形、圆角裁切和小尺寸预览"></a><p class="note">这是第一阶段图标提案。第二阶段定稿导出，第三阶段写入安装包资源并验证 PICO 系统中的实际显示。</p></section>''')+"\n"
if 'id="launcher"' not in s:s=s.replace('page+=body',insert+'page+=body')
f.write_text(s)
(p/'icon-preview.html').write_text('''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>碎境 · 安装包图标设计</title><style>*{box-sizing:border-box}body{margin:0;background:#F0EFEB;color:#28362F;font:16px/1.6 -apple-system,'PingFang SC',sans-serif}main{padding:54px 64px;width:1400px}h1{font-size:38px;font-weight:500;margin:4px 0 10px}p{margin:0;color:#62756a}small{letter-spacing:3px;color:#62756a}.grid{display:grid;grid-template-columns:512px 1fr;gap:90px;margin-top:38px}img{display:block}aside{padding-top:10px}.shapes{display:flex;gap:32px;margin:22px 0 40px}.shape img{width:164px;height:164px}.round img{border-radius:50%}.squircle img{border-radius:38px}.caption{font-size:14px;margin-top:10px;color:#62756a}.sizes{display:flex;align-items:end;gap:35px;margin-top:22px}.sizes div{min-width:64px}h2{font-weight:500;font-size:20px}.master{width:512px;height:512px;border-radius:100px}.foot{margin-top:24px;font-size:14px}</style><main><small>第一阶段 / 图标设计提案</small><h1>应用入口，只有一个裂隙标记</h1><p>烟灰底色 · 象牙白框线 · 浅绿断面 · 无中英文文字</p><div class="grid"><div><img class="master" src="concepts/launcher/launcher-master.png"><p class="caption">主图示意 · 自定义矢量绘制</p></div><aside><h2>不同遮罩下的辨识度</h2><div class="shapes"><div class="shape round"><img src="concepts/launcher/launcher-master.png"><p class="caption">圆形遮罩示意</p></div><div class="shape squircle"><img src="concepts/launcher/launcher-master.png"><p class="caption">圆角遮罩示意</p></div></div><h2>缩小后保留轮廓与裂隙</h2><div class="sizes">'''+''.join(f'<div><img width="{n}" height="{n}" style="border-radius:{n*.22}px" src="concepts/launcher/launcher-master.png"><p class="caption">{n}px</p></div>' for n in [96,64,48,32])+'''</div><p class="foot">前景 / 背景分层；核心标记留在中央安全区域。<br>遮罩仅为设计示意，尚未在 PICO 系统验证。</p></aside></div></main></html>''')
print('Launcher icon vector concept and review board source written')
