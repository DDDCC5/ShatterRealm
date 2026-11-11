from pathlib import Path
import json,hashlib,wave,struct
import numpy as np,trimesh as tm
from PIL import Image
R=Path(__file__).resolve().parents[2]/'assets/phase-2';m=json.loads((R/'models/manifest.json').read_text());assert m['totalObjects']==15
checks=[];files=[]
for a in m['assets']:
 p=R/'models'/a['id'];meta=json.loads((p/'metadata.json').read_text());assert abs(meta['longestDimensionM']-a['longestDimensionM'])<1e-8
 for name in ['intact.glb','fragments.glb','fracture-solids.glb','collision-hulls.glb']:
  f=p/name;b=f.read_bytes();assert b[:4]==b'glTF';assert struct.unpack_from('<I',b,8)[0]==len(b);n=struct.unpack_from('<I',b,12)[0];tree=json.loads(b[20:20+n]);assert all('uri' not in buf for buf in tree.get('buffers',[]));assert all('bufferView' in img for img in tree.get('images',[]));files.append({'path':str(f.relative_to(R)),'sha256':hashlib.sha256(b).hexdigest(),'bytes':len(b)})
 solids=tm.load_scene(p/'fracture-solids.glb',process=False);assert len(solids.geometry)==a['fragmentCount'],a['id'];vol=0
 for mesh in solids.geometry.values():assert mesh.is_watertight and mesh.volume>0,a['id'];vol+=mesh.volume
 assert abs(vol-a['intactVolumeM3'])/a['intactVolumeM3']<1e-5,a['id']
 rendered=tm.load_scene(p/'fragments.glb',process=False);area=sum(x.area for x in rendered.geometry.values());solid_area=sum(x.area for x in solids.geometry.values());assert abs(area-solid_area)/solid_area<1e-5,(a['id'],'material splitting changed physical surface')
 if a['id'] in ['keyboard','phone','remote','monitor']:
  assert a.get('fractureStrategy')=='structural-components',a['id']
  roles=set(a['sourceComponentRoles']);assert 'case' in roles and 'circuit-board' in roles,a['id']
  if a['id'] in ['phone','monitor']:assert 'screen' in roles and 'chip' in roles,a['id']
  if a['id']=='keyboard':assert sum(x['sourceRole']=='keycap' for x in a['fragmentRecords'])>=60
  for f in a['fragmentRecords']:assert f['sourceRole']==a['sourceComponentRoles'][f['sourceComponent']]
 assert abs(sum(x['massKg'] for x in a['fragmentRecords'])-a['massKg'])<1e-8
 if a['fractureStrategy']!='structural-components':assert abs(sum(f['exteriorAreaM2'] for f in a['fragmentRecords'])-a['intactSurfaceAreaM2'])/a['intactSurfaceAreaM2']<1e-4,(a['id'],'lost exterior material provenance')
 hulls=tm.load_scene(p/'collision-hulls.glb',process=False);assert len(hulls.geometry)==sum(x['colliderHullCount'] for x in a['fragmentRecords'])
 for hull in hulls.geometry.values():assert hull.is_watertight and hull.volume>0 and abs(hull.convex_hull.volume-hull.volume)/hull.volume<1e-5,a['id']
 checks.append({'id':a['id'],'closedFragments':a['fragmentCount'],'compoundHulls':len(hulls.geometry),'massConservation':True,'volumeConservation':True,'maxHullVolumeRatio':max(x['convexHullVolumeRatio'] for x in a['fragmentRecords'])})
au=json.loads((R/'audio/manifest.json').read_text());assert len(au['files'])==43
for x in au['files']:
 with wave.open(str(R/'audio'/x['path']),'rb') as w:
  assert w.getframerate()==48000 and w.getnchannels()==1 and w.getsampwidth()==2
  data=np.frombuffer(w.readframes(w.getnframes()),dtype='<i2');assert len(data)>1000 and np.max(np.abs(data.astype(np.int32)))<32767 and np.std(data)>30
ui=json.loads((R/'ui/manifest.json').read_text())
for x in ui['files']:
 for key in ['png','svg']:
  if key in x:assert (R/'ui'/x[key]).is_file(),x
 for key in ['png']:
  if key in x:
   im=Image.open(R/'ui'/x[key]);assert im.width>0 and im.height>0
report={'status':'passed','objects':15,'closedFragments':sum(x['closedFragments'] for x in checks),'collisionHulls':sum(x['compoundHulls'] for x in checks),'audioFiles':43,'uiEntries':len(ui['files']),'checks':checks,'scope':'asset structure only; PICO runtime physics/comfort/performance and human sound approval not performed','collisionAccuracy':'conservative compound convex approximation; per-fragment volume ratios recorded, no exact-surface collision claim'}
(R/'review/asset-validation.json').write_text(json.dumps(report,indent=2));(R/'review/model-checksums.json').write_text(json.dumps(files,indent=2));print(json.dumps({k:v for k,v in report.items() if k!='checks'},indent=2))
