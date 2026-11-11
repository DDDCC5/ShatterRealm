"""Original procedural material sound design. Not recordings of real objects."""
from pathlib import Path
import numpy as np, wave, json, hashlib
ROOT=Path(__file__).resolve().parents[2]/'assets/phase-2/audio'
SR=48000
PROFILES={'glass':([2317,3571,4903,6737,8311],.17,.18),'ceramic':([743,1193,1907,2801,4211],.095,.30),'plastic':([139,277,463,887,1427],.055,.66),'custom':([389,827,1531,2447,3779],.09,.42)}
def band_noise(n,rng,low,high):
 z=rng.normal(0,1,n);freq=np.fft.rfftfreq(n,1/SR);h=(1-np.exp(-(freq/max(low,1))**4))*np.exp(-(freq/high)**4);x=np.fft.irfft(np.fft.rfft(z)*h,n);return x/(np.std(x)+1e-9)
def hit(group,rng,amp=1):
 modes,decay,noisiness=PROFILES[group];duration=.55 if group=='glass' else .42;n=int(duration*SR);t=np.arange(n)/SR;x=np.zeros(n)
 for j,f in enumerate(modes):
  f*=rng.uniform(.94,1.06);tau=decay*rng.uniform(.7,1.25)/(1+j*.17);x+=np.sin(2*np.pi*f*t+rng.uniform(0,np.pi))*np.exp(-t/tau)/(1+j*.7)
 excitation=band_noise(n,rng,220 if group!='plastic' else 70,13500 if group=='glass' else 7800)*np.exp(-t/(.011 if group=='glass' else .024))
 x=(1-noisiness)*x+noisiness*excitation;x*=np.minimum(t/.0006,1);return x*amp

def event(group,event,variant):
 seed=int(hashlib.sha256(f'{group}-{event}-{variant}'.encode()).hexdigest()[:8],16);rng=np.random.default_rng(seed)
 if event=='throw':
  dur=rng.uniform(.20,.30);n=int(dur*SR);t=np.linspace(0,1,n);x=band_noise(n,rng,180,6400 if group=='glass' else 3200)*np.sin(np.pi*t)**2.7
  return x*.18
 dur=1.45 if event=='shatter' else 2.05;x=np.zeros(int(dur*SR))
 if event=='shatter':
  h=hit(group,rng,1);x[:len(h)]+=h
  onset=np.sort(rng.uniform(.008,.38,17 if group=='glass' else 11));amps=np.exp(-onset*4)*rng.uniform(.08,.26,len(onset))
 else:
  onset=np.cumsum(rng.uniform(.055,.18,12));amps=np.exp(-onset*1.5)*rng.uniform(.12,.42,len(onset))
 for start,amp in zip(onset,amps):
  h=hit(group,rng,amp);at=int(start*SR);count=min(len(h),len(x)-at)
  if count>0:x[at:at+count]+=h[:count]
 x[-int(SR*.03):]*=np.linspace(1,0,int(SR*.03));return x

def ui(name):
 index=['confirm','cancel','pick','refresh-start','refresh-complete','failure','reset-complete'].index(name);rng=np.random.default_rng(700+index);dur=[.16,.14,.09,.18,.22,.22,.28][index];n=int(dur*SR);t=np.arange(n)/SR
 tones={'confirm':[620,930],'cancel':[490,370],'pick':[720],'refresh-start':[430,580],'refresh-complete':[580,870,1160],'failure':[320,270],'reset-complete':[440,660,880]}[name];x=np.zeros(n)
 for j,f in enumerate(tones):
  at=int(j*.035*SR);tt=t[:n-at];x[at:]+=.25*np.sin(2*np.pi*f*tt)*np.exp(-tt/.045)*np.minimum(tt/.002,1)
 x+=band_noise(n,rng,700,5000)*np.exp(-t/.008)*.012;x[-480:]*=np.linspace(1,0,480);return x

def write(path,x,meta):
 x=x-np.mean(x);x*=min(1,.78/(np.max(np.abs(x))+1e-12));x[:144]*=np.linspace(0,1,144);x[-144:]*=np.linspace(1,0,144)
 pcm=np.round(np.clip(x,-1,1)*32767).astype('<i2');path.parent.mkdir(parents=True,exist_ok=True)
 with wave.open(str(path),'wb') as w:w.setnchannels(1);w.setsampwidth(2);w.setframerate(SR);w.writeframes(pcm.tobytes())
 return dict(path=str(path.relative_to(ROOT)),sampleRateHz=SR,channels=1,bits=16,durationSeconds=len(x)/SR,peakDbFS=round(float(20*np.log10(np.max(np.abs(pcm))/32768)),2),rmsDbFS=round(float(20*np.log10(np.sqrt(np.mean((pcm.astype(float)/32768)**2))+1e-12)),2),sha256=hashlib.sha256(path.read_bytes()).hexdigest(),**meta)
manifest=[]
for g in PROFILES:
 for e in ['throw','shatter','fragment-land']:
  for v in range(1,4):manifest.append(write(ROOT/'materials'/g/f'{e}-{v:02}.wav',event(g,e,v),dict(group=g,event=e,variant=v,source='original procedural modal/noise synthesis')))
for name in ['confirm','cancel','pick','refresh-start','refresh-complete','failure','reset-complete']:manifest.append(write(ROOT/'ui'/f'{name}.wav',ui(name)*.5,dict(group='ui',event=name,source='original procedural synthesis')))
(ROOT/'manifest.json').write_text(json.dumps(dict(license='Original project-authored sounds; no third-party recordings',authenticity='Procedural material sound design, not field recordings; requires listening approval',files=manifest),indent=2))
print(f'Wrote {len(manifest)} WAV files, all 48kHz mono PCM16; maximum peak {max(x["peakDbFS"] for x in manifest)} dBFS')
