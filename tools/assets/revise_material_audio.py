"""Revision 2: deterministic editing of licensed real recordings; preserve throw WAV bytes."""
from pathlib import Path
import json,re,hashlib,urllib.request,concurrent.futures,wave,shutil
import numpy as np
from scipy.io import wavfile
from scipy.signal import butter,sosfiltfilt,resample_poly
R=Path(__file__).resolve().parents[2]/'assets/phase-2/audio';SR=48000
SOURCES=[('glass',1591,'broken-glass-of-champagne-s1591.html'),('glass',387,'mirror-explodes-s0387.html'),('glass',1121,'broken-christmas-ball-1-s1121.html'),('ceramic',1643,'broken-plate-1-s1643.html'),('ceramic',1644,'broken-plate-2-s1644.html'),('ceramic',1645,'broken-plate-3-s1645.html')]
backup=R/'revisions/v1';backup.mkdir(parents=True,exist_ok=True)
old=json.loads((R/'manifest.json').read_text());before={x['path']:hashlib.sha256((R/x['path']).read_bytes()).hexdigest() for x in old['files']}
if not (backup/'manifest.json').exists():shutil.copy2(R/'manifest.json',backup/'manifest.json')
before={x['path']:x['sha256'] for x in json.loads((backup/'manifest.json').read_text())['files']}
known={x['id']:x for x in json.loads((R/'sources/manifest.json').read_text())['sources']} if (R/'sources/manifest.json').exists() else {}
for group in ['glass','ceramic']:
 for event in ['shatter','fragment-land']:
  for v in range(1,4):
   rel=Path('materials')/group/f'{event}-{v:02}.wav';dst=backup/rel;dst.parent.mkdir(parents=True,exist_ok=True)
   if not dst.exists():shutil.copy2(R/rel,dst)

def fetch(source):
 group,id,slug=source;url='https://bigsoundbank.com/'+slug
 if id in known:
  cached=R/known[id]['file']
  if cached.exists() and hashlib.sha256(cached.read_bytes()).hexdigest()==known[id]['sha256']:return known[id]
 html=urllib.request.urlopen(url,timeout=30).read().decode('utf-8')
 assert 'CC0 (public domain)' in html,(id,'CC0 license statement missing')
 blocks=re.findall(r'<script[^>]*type=["\']application/ld\+json["\'][^>]*>(.*?)</script>',html,re.S)
 objects=[json.loads(s) for s in blocks];obj=next(x for x in objects if x.get('@type')=='AudioObject')
 media=next(x['contentUrl'] for x in obj['encoding'] if x.get('encodingFormat')=='audio/wav')
 dest=R/'sources/cc0'/f'{id}.wav'
 if not dest.exists():dest.write_bytes(urllib.request.urlopen(media,timeout=30).read())
 assert dest.read_bytes()[:4]==b'RIFF'
 return dict(group=group,id=id,title=obj['name'].split(' — ')[0],page=url,download=media,author='Joseph Sardin',publisher='BigSoundBank / LaSonothèque',license='CC0 1.0 / public-domain equivalent as stated by source',licensePage='https://bigsoundbank.com/licenses.html',file=str(dest.relative_to(R)),sha256=hashlib.sha256(dest.read_bytes()).hexdigest())
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:sources=list(pool.map(fetch,SOURCES))
(R/'sources/manifest.json').write_text(json.dumps({'sources':sources,'use':'deterministic trim, split, gentle high-pass and level matching; no ML training'},ensure_ascii=False,indent=2))

def load(s):
 rate,x=wavfile.read(R/s['file']);x=x.astype(float)/(2**(np.iinfo(x.dtype).bits-1)) if x.dtype.kind in 'iu' else x.astype(float)
 if x.ndim==2:x=x.mean(axis=1)
 if rate!=SR:
  import math
  g=math.gcd(rate,SR);x=resample_poly(x,SR//g,rate//g)
 x-=x.mean();return x

def bounds(x):
 win=240;n=len(x)//win;energy=np.sqrt(np.mean(x[:n*win].reshape(n,win)**2,axis=1));active=np.flatnonzero(energy>max(energy)*.065);on=max(0,int(active[0])*win-int(.004*SR));tail=np.flatnonzero(energy>max(energy)*.012);end=min(len(x),int(tail[-1]+1)*win+int(.04*SR));return on,end

def encode(rel,x,meta):
 x=x.copy();x-=x.mean();n=min(60,len(x)//4);x[:n]*=np.linspace(0,1,n);fade=min(1440,len(x)//8);x[-fade:]*=np.linspace(1,0,fade)
 # Consistent peaks without hard limiting or adding resonant tones.
 target=.73 if meta['event']=='shatter' else .48;x*=target/max(np.max(np.abs(x)),1e-12);pcm=np.round(x*32767).astype('<i2');p=R/rel
 with wave.open(str(p),'wb') as w:w.setnchannels(1);w.setsampwidth(2);w.setframerate(SR);w.writeframes(pcm.tobytes())
 return dict(path=rel,sampleRateHz=SR,channels=1,bits=16,durationSeconds=len(pcm)/SR,peakDbFS=round(float(20*np.log10(np.max(np.abs(pcm.astype(float)))/32768)),2),rmsDbFS=round(float(20*np.log10(np.sqrt(np.mean((pcm.astype(float)/32768)**2))+1e-12)),2),sha256=hashlib.sha256(p.read_bytes()).hexdigest(),revision=2,source='edited CC0 real-world recording; no resonant oscillator synthesis',**meta)

replacements={};edits=[]
for group in ['glass','ceramic']:
 for v,s in enumerate([s for s in sources if s['group']==group],1):
  x=load(s);on,end=bounds(x);x=sosfiltfilt(butter(2,120 if group=='glass' else 70,fs=SR,btype='highpass',output='sos'),x)
  for event in ['shatter','fragment-land']:
   if event=='shatter':start=on;stop=min(end,on+int((.46 if group=='glass' else .40)*SR))
   else:
    # Separate the real shard-bounce tail from the initial vessel impact/crack.
    start=on+int((.105 if group=='glass' else .12)*SR);stop=end
   assert stop-start>SR*.12,(group,s['id'],event,'insufficient recorded tail')
   clip=x[start:stop];rel=f'materials/{group}/{event}-{v:02}.wav';meta=dict(group=group,event=event,variant=v,sourceId=s['id'],sourcePage=s['page'],license='CC0',processing={'startSeconds':start/SR,'endSeconds':stop/SR,'highpassHz':120 if group=='glass' else 70,'addedTones':False,'addedReverb':False,'pitchShift':False});replacements[rel]=encode(rel,clip,meta);edits.append(dict(path=rel,sourceId=s['id'],**meta['processing']))

newfiles=[replacements.get(x['path'],x) for x in old['files']];after={x['path']:hashlib.sha256((R/x['path']).read_bytes()).hexdigest() for x in newfiles};changed=sorted(p for p in before if before[p]!=after[p]);assert changed==sorted(replacements),'Unrequested audio modification'
assert all(before[p]==after[p] for p in before if '/throw-' in p),'Throw files changed'
old.update(license='Original procedural sounds plus edited CC0 recordings; see sources/manifest.json and SOURCES.md',authenticity='Glass/ceramic shatter and fragment-land revision 2 use real recordings; throws and other groups retain original synthesis',revision=2,files=newfiles);(R/'manifest.json').write_text(json.dumps(old,ensure_ascii=False,indent=2))
# Update only the two affected listening reels, keeping the throw section byte-for-byte sourced from the same files.
for group in ['glass','ceramic']:
 chunks=[]
 for event in ['throw','shatter','fragment-land']:
  with wave.open(str(R/f'materials/{group}/{event}-01.wav'),'rb') as w:chunks.append(w.readframes(w.getnframes()))
  chunks.append(bytes(SR//3*2))
 with wave.open(str(R/f'{group}-preview.wav'),'wb') as w:w.setnchannels(1);w.setsampwidth(2);w.setframerate(SR);w.writeframes(b''.join(chunks))
report={'status':'passed','revision':2,'changedIndependentWavFiles':changed,'changedCount':len(changed),'preservedIndependentWavCount':43-len(changed),'allThrowFilesByteIdentical':True,'allOtherGroupsByteIdentical':True,'beforeSha256':before,'afterSha256':after,'edits':edits,'listeningApproval':'pending user review'};(R.parent/'review/audio-revision-2.json').write_text(json.dumps(report,indent=2))
text='# Audio recording sources · revision 2\n\n玻璃和陶瓷的碰撞碎裂/碎片落地改为实物录音剪辑；投掷音和其余31个独立音频保留原字节。未添加纯音、人工长混响或音高变换。原始录音及SHA-256保留在sources/，旧12个声音在revisions/v1/供对比。\n\n'
for s in sources:text+=f'- {s["title"]} — {s["author"]}, [{s["publisher"]}]({s["page"]}), CC0。\n'
text+='\n[发布者授权说明](https://bigsoundbank.com/licenses.html)。这是获授权实录的项目剪辑，不标作本项目现场录制；最终听感需用户确认。重建顺序：先执行原始generate_audio.py，再执行revise_material_audio.py。\n';(R/'SOURCES.md').write_text(text)
print(json.dumps({'revision':2,'changed':len(changed),'preserved':43-len(changed),'throwsIdentical':True,'sources':len(sources)},indent=2))
