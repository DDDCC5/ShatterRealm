import subprocess,time,sys
from datetime import datetime
stem=sys.argv[1] if len(sys.argv)>1 else "latest"
from pathlib import Path
cli='/Users/chanzungsan/.local/lib/nodejs/node-v22.23.2-darwin-arm64/bin/pico-cli'
seen=set();end=time.monotonic()+1200
cutoff=datetime.now().strftime("%m-%d %H:%M:%S")
while time.monotonic()<end:
 p=subprocess.run([cli,'app','logcat','--device','emulator-5554','--package','com.example.shatterrealm','--tag','ShatterRealm','--lines','25'],capture_output=True,text=True)
 text="\n".join(line for line in p.stdout.splitlines() if line[:14]>=cutoff)
 if 'INTEGRATION_PREVIEW_READY' in text and 'preview' not in seen:
  seen.add('preview');time.sleep(2);subprocess.run([cli,'capture','screenshot','--device','emulator-5554','--out',f'docs/implementation/runtime-{stem}-preview.png'],check=False)
 if 'Fractured glass-cup' in text and 'first' not in seen:
  seen.add('first');subprocess.run([cli,'capture','screenshot','--device','emulator-5554','--out',f'docs/implementation/runtime-{stem}-first-fracture.png'],check=False)
 if 'INTEGRATION_SCENE_READY' in text and 'full' not in seen:
  seen.add('full');time.sleep(2);subprocess.run([cli,'capture','screenshot','--device','emulator-5554','--out',f'docs/implementation/runtime-{stem}-verified-scene.png'],check=False)
  Path('docs/implementation/integration-ready-log.txt').write_text(text);break
 time.sleep(2)
print('Captured states:',sorted(seen))
