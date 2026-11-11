"""Export approved art and documented emulator captures at the form's exact sizes."""
from pathlib import Path
import base64,json
import resvg_py
from PIL import Image
root=Path(__file__).resolve().parents[2];out=root/'deliverables/submission-1.0.3'
def data(p):return 'data:image/png;base64,'+base64.b64encode(p.read_bytes()).decode()
def render(s,name):
 (out/(name+'.svg')).write_text(s)
 (out/(name+'.png')).write_bytes(resvg_py.svg_to_bytes(svg_string=s))
font='font-family="PingFang SC, Noto Sans CJK SC, sans-serif"'
icon=data(root/'assets/phase-2/ui/launcher-master.png')
hero=root/'assets/phase-2/models/previews/plate-fragments.png';rgb=Image.open(hero).getpixel((0,0))[:3];bg='#'+''.join(f'{x:02x}' for x in rgb)
s=f'''<svg xmlns="http://www.w3.org/2000/svg" width="2560" height="1440" viewBox="0 0 2560 1440">
<rect width="2560" height="1440" fill="#222B26"/>
<path d="M1060 0 1220 420 1120 910 1245 1440" fill="none" stroke="#B9E6C5" stroke-width="2" opacity=".22"/>
<image href="{icon}" x="126" y="110" width="120" height="120"/>
<g {font}><text x="280" y="160" font-size="26" letter-spacing="5" fill="#B9E6C5">PICO SPATIAL</text>
<text x="280" y="208" font-size="24" fill="#ADBDB0">MR 娱乐解压体验</text>
<text x="135" y="490" font-size="196" font-weight="500" letter-spacing="18" fill="#F4F1E8">碎境</text>
<text x="145" y="660" font-size="74" fill="#F4F1E8">抬手，投掷。</text>
<text x="145" y="760" font-size="74" fill="#B9E6C5">让压力碎落。</text>
<path d="M150 870H235" stroke="#B9E6C5" stroke-width="5"/>
<text x="145" y="960" font-size="28" fill="#C8D0C8">手势抓取 · 真实平面碰撞</text>
<text x="145" y="1016" font-size="28" fill="#C8D0C8">15 种物品 · 保留物理碎片</text>
<text x="145" y="1270" font-size="24" fill="#ADBDB0">先完成系统房间扫描，再进入你的碎境。</text>
</g>
<rect x="1250" y="110" width="1180" height="1160" rx="42" fill="{bg}"/>
<image href="{data(hero)}" x="1300" y="170" width="1080" height="1080"/>
<g {font} fill="#26372B"><text x="1320" y="210" font-size="25" letter-spacing="4">陶瓷 / 碎裂之后</text>
<text x="1320" y="1200" font-size="30">每一次释放，都留下真实的物理反馈。</text></g>
<text x="2420" y="1340" text-anchor="end" {font} font-size="22" fill="#ADBDB0">宣传设计 · 模型渲染示意</text>
</svg>'''
render(s,'02-poster-2560x1440')
shots=[('03-screenshot-grab','glass-model-zh.png','手势选择','捏合抓取，挥手释放。'),('04-screenshot-fragments','all15-fragments-zh.png','碎片保留','15 种物品的碰撞回归画面。')]
for name,source,title,caption in shots:
 p=root/'deliverables/screenshots'/source
 width,height=Image.open(p).size
 scaled=1440*width/height;x=(2560-scaled)/2
 s=f'''<svg xmlns="http://www.w3.org/2000/svg" width="2560" height="1440" viewBox="0 0 2560 1440"><rect width="2560" height="1440" fill="#202823"/><image href="{data(p)}" x="{x}" width="{scaled}" height="1440"/>
 <g {font}><text x="72" y="150" font-size="64" fill="#F4F1E8">碎境</text><text x="72" y="265" font-size="35" fill="#B9E6C5">{title}</text><text x="72" y="330" font-size="23" fill="#C5CEC6">{caption}</text><path d="M72 390H170" stroke="#B9E6C5" stroke-width="3"/>
 <text x="72" y="1230" font-size="22" fill="#C5CEC6">模拟器实际运行画面</text><text x="72" y="1270" font-size="22" fill="#C5CEC6">早期版本 1.0</text><text x="72" y="1310" font-size="20" fill="#9DAFA1">未裁切应用画面</text></g></svg>'''
 render(s,name)
metadata=[]
for p in sorted(out.glob('*.png')):
 size=Image.open(p).size;limit=4*1024*1024 if p.name.startswith('01') else 5*1024*1024
 assert size==((1024,1024) if p.name.startswith('01') else (2560,1440)),(p,size)
 assert p.stat().st_size<=limit,(p,p.stat().st_size)
 metadata.append({'file':p.name,'size':size,'bytes':p.stat().st_size})
(out/'image-specs.json').write_text(json.dumps(metadata,indent=2)+'\n')
print(json.dumps(metadata,indent=2))
