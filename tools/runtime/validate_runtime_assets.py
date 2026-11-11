from pathlib import Path
import json,hashlib,struct
root=Path('app/src/main/assets');approved=Path('assets/phase-2')
items=json.loads((root/'catalog.json').read_text());assert len(items)==15
hulls=fragments=0
for item in items:
 path=root/'models'/item['id'];data=json.loads((path/'colliders.json').read_text());meta=json.loads((path/'metadata.json').read_text())
 for hull in data:
  assert all(len(v)==3 for v in hull['positions'])
  assert len(hull['indices'])%3==0 and max(hull['indices'])<len(hull['positions'])
 for piece in meta['fragmentRecords']:
  actual=sum(h['name'].startswith(piece['id']+'-hull-') for h in data)
  assert actual==piece['colliderHullCount'],(item['id'],piece['id'],actual,piece['colliderHullCount'])
 for filename in ['intact.glb','fragments.glb','collision-hulls.glb']:
  actual=(path/filename).read_bytes();original=(approved/'models'/item['id']/filename).read_bytes()
  assert actual==original # final delivery retains approved PBR materials byte for byte
  a=struct.unpack_from('<I',actual,12)[0];b=struct.unpack_from('<I',original,12)[0]
  assert actual[20+a:]==original[20+b:] # all binary geometry and texture bytes identical
  one=json.loads(actual[20:20+a]);two=json.loads(original[20:20+b])
  for key in ['nodes','meshes','accessors','bufferViews']: assert one.get(key)==two.get(key)
 hulls+=len(data);fragments+=meta['fragmentCount']
audio=list((root/'audio').rglob('*.wav'));assert len(audio)==43
for p in audio:assert p.read_bytes()==(approved/'audio'/p.relative_to(root/'audio')).read_bytes()
result={'status':'passed','items':len(items),'fragments':fragments,'convexHulls':hulls,'audioFilesByteIdenticalToApprovedRevision2':len(audio),'metricGLBGeometryUnchanged':True}
Path('docs/implementation/runtime-asset-validation.json').write_text(json.dumps(result,indent=2));print(json.dumps(result,indent=2))
