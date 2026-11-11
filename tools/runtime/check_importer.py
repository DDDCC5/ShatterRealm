from pathlib import Path
import json,subprocess
import trimesh
root=Path('.scratch/import-tests');root.mkdir(exist_ok=True,parents=True)
cube=trimesh.creation.box([.1,.15,.08]);(root/'cube.obj').write_text(cube.export(file_type='obj'))
fixtures={'glb':Path('assets/phase-2/models/glass-cup/intact.glb'),'fbx':Path('app/src/main/cpp/vendor/assimp-6.0.2/test/models/FBX/box.fbx'),'obj':root/'cube.obj'}
results=[]
for ext,src in fixtures.items():
 target=root/ext
 subprocess.run(['.scratch/native-host/shatter_import',str(src),str(target)],check=True)
 meta=json.loads((target/'metadata.json').read_text());scene=trimesh.load(target/'fragments.glb',force='scene')
 pieces=list(scene.geometry.values())
 for mesh in pieces: mesh.merge_vertices(merge_tex=True,merge_norm=True)
 assert len(pieces)>=20
 assert all(m.is_watertight and m.volume>0 for m in pieces)
 assert abs(meta['sourceVolumeM3']-meta['fragmentVolumeM3'])/meta['sourceVolumeM3']<1e-5
 results.append({'format':ext,'fragments':len(pieces),'closed':True,'volumeM3':meta['sourceVolumeM3'],'volumeError':abs(meta['sourceVolumeM3']-meta['fragmentVolumeM3'])/meta['sourceVolumeM3']})
# Open surface must report a file-level error rather than invent a cube.
(root/'open.obj').write_text('v 0 0 0\nv 0.1 0 0\nv 0 0.1 0\nf 1 2 3\n')
p=subprocess.run(['.scratch/native-host/shatter_import',str(root/'open.obj'),str(root/'open')],capture_output=True,text=True)
assert p.returncode!=0
report={'status':'passed','fixtures':results,'invalidOpenSurfaceRejected':True}
Path('docs/implementation/native-import-validation.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
