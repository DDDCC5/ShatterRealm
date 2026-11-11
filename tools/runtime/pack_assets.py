from pathlib import Path
import json,shutil
import numpy as np
from PIL import Image
root=Path('assets/phase-2'); out=Path('app/src/main/assets');out.mkdir(parents=True,exist_ok=True)
assets=json.loads((root/'models/manifest.json').read_text())['assets']
for a in assets:
 dest=out/'models'/a['id'];dest.mkdir(parents=True,exist_ok=True)
 for n in ['intact.glb','fragments.glb','collision-hulls.glb']:shutil.copyfile(root/'models'/a['id']/n,dest/n)
 for f in a['fragmentRecords']:
  vals,vecs=np.linalg.eigh(np.array(f['inertiaKgM2']))
  if np.linalg.det(vecs)<0:vecs[:,0]*=-1
  # scipy handles rotation principal axes robustly.
  from scipy.spatial.transform import Rotation
  f['principalInertia']=vals.tolist();f['principalRotation']=Rotation.from_matrix(vecs).as_quat().tolist()
 import trimesh
 scene=trimesh.load(root/'models'/a['id']/'collision-hulls.glb',force='scene')
 hulls=[]
 for node in scene.graph.nodes_geometry:
  transform,geometry=scene.graph[node];mesh=scene.geometry[geometry]
  hulls.append(dict(name=node,positions=mesh.vertices.tolist(),indices=mesh.faces.reshape(-1).tolist()))
 (dest/'colliders.json').write_text(json.dumps(hulls,separators=(',',':')))
 (dest/'metadata.json').write_text(json.dumps(a,ensure_ascii=False))
(out/'catalog.json').write_text(json.dumps([dict(id=a['id'],zh=a['zh'],en=a['en'],material=a['material']) for a in assets],ensure_ascii=False))
for group in ['materials','ui']:
 shutil.copytree(root/'audio'/group,out/'audio'/group,dirs_exist_ok=True)
shutil.copyfile(root/'audio/SOURCES.md',out/'AUDIO-SOURCES.md')
res=Path('app/src/main/res/drawable-nodpi');res.mkdir(parents=True,exist_ok=True)
for name in ['mark','refresh','reset','launcher-master']:
 shutil.copyfile(root/'ui'/f'{name}.png',res/(name.replace('-','_')+'.png'))
for a in assets:
 src=root/'models'/a['id']/'preview.png'
 if not src.exists():src=root/'models/previews'/f"{a['id']}-intact.png"
 if src.exists():
  im=Image.open(src);im.thumbnail((192,192));im.save(res/('item_'+a['id'].replace('-','_')+'.png'))
for den,size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
 d=Path('app/src/main/res')/('mipmap-'+den);d.mkdir(exist_ok=True)
 shutil.copyfile(root/'ui'/f'launcher-{size}.png',d/'ic_spatial_launcher.png')
print('Packed',len(assets),'models, revised material audio, brand and icons')

