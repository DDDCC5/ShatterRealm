"""Authored geometry and exact-volume plane fracture; no generated-model claims."""
from pathlib import Path
import json,hashlib,math,time
import numpy as np
from scipy.spatial import cKDTree, ConvexHull
from PIL import Image
import trimesh as tm
import manifold3d as md
ROOT=Path(__file__).resolve().parents[2]; OUT=ROOT/'assets/phase-2/models'; OUT.mkdir(exist_ok=True,parents=True)
SPEC=json.loads((ROOT/'design/phase-1/design-spec.json').read_text())['dataCases']['normal']['items']
MATS={
 'clear':('#DEE9E6',.08,0,.92),'green':('#21462B',.14,0,.72),'frosted':('#DAE3DE',.52,0,.56),'amber':('#99501C',.14,0,.65),
 'ivory':('#E8E3D6',.22,0,0),'blue':('#526A70',.25,0,0),'terracotta':('#A75F3D',.28,0,0),'clay':('#AFA998',.85,0,0),
 'circuit':('#2D5940',.65,0,0),'copper':('#B08B42',.4,.6,0),
 'black':('#242825',.62,0,0),'keys':('#383C38',.58,0,0),'screen':('#101719',.16,.15,0),'metal':('#8B918B',.30,.8,0),
 'plastic':('#78817C',.40,0,.35),'rubber':('#111A16',.85,0,0),'marking':('#B1B6A9',.75,0,0)}
def rgb(c):return np.array([int(c[i:i+2],16) for i in [1,3,5]],dtype=np.uint8)
# Subtle authored microvariation, not a photo texture or third-party image.
TEX={}
for n,(c,r,m,t) in MATS.items():
 rng=np.random.default_rng(int(hashlib.sha256(n.encode()).hexdigest()[:8],16));noise=rng.normal(0,1,(128,128,1));a=np.clip(np.array([245,245,245])[None,None,:]+noise*(1.7 if n not in ['screen','metal'] else .4),0,255).astype(np.uint8);TEX[n]=Image.fromarray(a)
PARTS=[]
def part(mesh,mat,role="body"):
 if len(mesh.faces)==0:return
 mesh.remove_unreferenced_vertices();tm.repair.fix_normals(mesh);mesh.metadata["semanticRole"]=role;PARTS.append((mesh,mat))
def box(e,pos,mat,bevel=0,role="body"):
 e=np.array(e,float)
 if bevel:
  # Convex rounded edge profile, with flat centers and physical bevel width.
  s=tm.creation.icosphere(subdivisions=1,radius=bevel).vertices
  corners=np.array([[x,y,z] for x in [-1,1] for y in [-1,1] for z in [-1,1]])*(e/2-bevel)
  m=tm.convex.convex_hull(np.concatenate([s+c for c in corners]))
 else:m=tm.creation.box(e)
 m.apply_translation(pos);part(m,mat,role);return m

def cyl(radius,height,pos,mat,axis='Y',sections=64,role='body'):
 m=tm.creation.cylinder(radius,height,sections=sections)
 if axis=='Y':m.apply_transform(tm.transformations.rotation_matrix(-np.pi/2,[1,0,0]))
 m.apply_translation(pos);part(m,mat,role);return m

def lathe(profile,mat,sections=96):
 m=tm.creation.revolve(np.array(profile),sections=sections)
 m.apply_transform(tm.transformations.rotation_matrix(-np.pi/2,[1,0,0]));part(m,mat);return m

def shell(outer,thick,mat,bottom=True):
 inner=[(max(r-thick,thick*.5),y) for r,y in reversed(outer)]
 if bottom:inner[-1]=(max(outer[0][0]-thick,thick*.5),outer[0][1]+thick);p=[(0,outer[0][1])]+outer+inner+[(0,outer[0][1]+thick)]
 else:p=outer+inner+[outer[0]]
 return lathe(p,mat)

def torus(major,minor,pos,mat,scale=(1,1,1),horizontal=False):
 m=tm.creation.torus(major,minor,major_sections=64,minor_sections=16);m.apply_scale(scale)
 if horizontal:m.apply_transform(tm.transformations.rotation_matrix(np.pi/2,[1,0,0]))
 m.apply_translation(pos);part(m,mat)

def manifold(mesh,face_ids=None):
 mesh=mesh.copy();mesh.merge_vertices();tm.repair.fix_normals(mesh)
 a=md.Manifold(md.Mesh(np.array(mesh.vertices,dtype=np.float32),np.array(mesh.faces,dtype=np.uint32),face_id=face_ids))
 if a.is_empty():raise ValueError('Invalid manifold input')
 return a

def asmesh(m):
 x=m.to_mesh();return tm.Trimesh(np.array(x.vert_properties)[:,:3],np.array(x.tri_verts),process=False)


def case_box(extents,pos,wall,axis,role='case'):
 outer=box(extents,pos,'black',min(wall*.8,.004));PARTS.pop()
 inner_size=np.array(extents)-2*wall;inner_size[axis]=extents[axis]+wall
 inner_pos=np.array(pos,float);inner_pos[axis]+=wall
 inner=tm.creation.box(inner_size);inner.apply_translation(inner_pos)
 part(asmesh(manifold(outer)-manifold(inner)),'black',role)

def build(id):
 global PARTS;PARTS=[]
 if id=='glass-cup':shell([(.032,0),(.034,.005),(.038,.09),(.04,.11)],.0025,'clear')
 elif id=='wine-bottle':
  shell([(.034,0),(.036,.009),(.036,.204),(.033,.22),(.017,.25),(.014,.26),(.014,.287),(.016,.29),(.016,.298),(.015,.30)],.0023,'green')
 elif id=='glass-jar':
  shell([(.047,0),(.052,.008),(.052,.13),(.046,.145),(.046,.158)],.0028,'clear');cyl(.048,.011,[0,.164,0],'metal')
 elif id=='lamp-shade':shell([(.108,0),(.11,.009),(.098,.026),(.061,.122),(.038,.145),(.038,.16)],.003,'frosted',False)
 elif id=='perfume-bottle':
  outer=tm.creation.box([.058,.071,.036]);outer.apply_translation([0,.0355,0]);inner=tm.creation.box([.049,.06,.027]);inner.apply_translation([0,.037,0]);glass=asmesh(manifold(outer)-manifold(inner));part(glass,'amber');cyl(.008,.016,[0,.078,0],'amber');cyl(.011,.022,[0,.096,0],'metal')
 elif id=='plate':
  lathe([(0,0),(.054,0),(.058,.004),(.095,.006),(.12,.017),(.125,.022),(.124,.026),(.115,.025),(.093,.013),(.052,.007),(0,.007)],'ivory')
 elif id=='bowl':shell([(.032,0),(.038,.004),(.045,.017),(.06,.039),(.073,.067),(.075,.071)],.003,'blue')
 elif id=='vase':shell([(.031,0),(.043,.012),(.052,.065),(.046,.13),(.025,.19),(.021,.23),(.024,.26)],.003,'ivory')
 elif id=='mug':
  shell([(.037,0),(.041,.005),(.043,.095)],.0032,'terracotta');torus(.026,.006,[.045,.051,0],'terracotta',scale=(.8,1.1,1))
 elif id=='ornament':
  t=np.linspace(0,2*np.pi,145)[:-1];pos=np.column_stack(((2+np.cos(3*t))*np.cos(2*t),np.sin(3*t),(2+np.cos(3*t))*np.sin(2*t)))*.023
  tang=np.roll(pos,-1,axis=0)-np.roll(pos,1,axis=0);tang/=np.linalg.norm(tang,axis=1)[:,None];ref=np.tile([0,1.,0],(len(t),1));n=np.cross(tang,ref);n/=np.linalg.norm(n,axis=1)[:,None];b=np.cross(tang,n);ang=np.linspace(0,2*np.pi,16,endpoint=False);v=(pos[:,None,:]+.012*(np.cos(ang)[None,:,None]*n[:,None,:]+np.sin(ang)[None,:,None]*b[:,None,:])).reshape(-1,3);f=[]
  for i in range(len(t)):
   for j in range(16):
    a=i*16+j;bb=i*16+(j+1)%16;c=((i+1)%len(t))*16+j;d=((i+1)%len(t))*16+(j+1)%16;f.extend([[a,c,bb],[bb,c,d]])
  m=tm.Trimesh(v,f);tm.repair.fix_normals(m);part(m,'ivory')
 elif id=='keyboard':
  case_box([.44,.016,.145],[0,.008,0],.002,1)
  box([.429,.001,.134],[0,.014,0],'black',role='front-cover')
  box([.41,.0007,.12],[0,.004,0],'circuit',role='circuit-board')
  for row in range(5):
   for col in range(15):
    if row==4 and 3<=col<=9:continue
    x=(col-7)*.027;z=(row-2)*.025
    box([.023,.009,.021],[x,.018,z],'keys',.0014,role='keycap')
  box([.181,.009,.021],[-.027,.018,.05],'keys',.002,role='keycap')
 elif id=='phone':
  case_box([.073,.15,.009],[0,.075,0],.0018,2)
  box([.064,.113,.0012],[0,.078,.0047],'screen',.0005,role='screen')
  box([.062,.127,.0007],[0,.075,-.0018],'circuit',role='circuit-board')
  for y in [.045,.075,.105]:box([.018,.016,.0012],[0,y,-.0009],'black',.0004,role='chip')
  box([.016,.0018,.001],[0,.141,.005],'metal',.0004,role='speaker')
  cyl(.0058,.001,[0,.011,.005],'black',axis='Z',sections=32,role='button')
 elif id=='remote':
  case_box([.045,.18,.017],[0,.09,0],.002,2)
  box([.0405,.174,.0015],[0,.09,.0075],'black',.0005,role='front-cover')
  box([.035,.16,.0007],[0,.09,-.0035],'circuit',role='circuit-board')
  for row in range(8):
   for col in range(3):
    cyl(.0035,.003,[(col-1)*.012,.026+row*.016,.009],'rubber',axis='Z',sections=16,role='button')
  cyl(.009,.003,[0,.159,.009],'keys',axis='Z',sections=32,role='button')
 elif id=='monitor':
  case_box([.46,.29,.034],[0,.222,0],.004,2)
  box([.413,.237,.0015],[0,.229,.0176],'screen',.0006,role='screen')
  box([.39,.19,.001],[0,.222,-.008],'circuit',role='circuit-board')
  for x in [-.09,0,.09]:box([.04,.03,.006],[x,.222,-.004],'black',.001,role='chip')
  box([.034,.099,.034],[0,.056,-.008],'black',.003,role='support')
  box([.18,.014,.12],[0,.007,0],'black',.006,role='base')
  for x in [-.19,-.17,-.15]:cyl(.002,.0015,[x,.089,.018],'metal',axis='Z',sections=12,role='button')
 elif id=='storage-box':
  outer=tm.creation.box([.30,.17,.21]);outer.apply_translation([0,.085,0]);inner=tm.creation.box([.292,.17,.202]);inner.apply_translation([0,.09,0]);part(asmesh(manifold(outer)-manifold(inner)),'plastic');box([.308,.012,.218],[0,.176,0],'plastic',.004)
 else:raise ValueError(id)
 return PARTS


def material(name,inner=False):
 c,r,m,trans=MATS[name];mat=tm.visual.material.PBRMaterial(name=name,baseColorFactor=[*np.where((rgb(c)/255.)<=.04045,(rgb(c)/255.)/12.92,((rgb(c)/255.+.055)/1.055)**2.4),1.0],baseColorTexture=TEX[name],metallicFactor=m,roughnessFactor=r,doubleSided=False,alphaMode='OPAQUE')
 return mat

def render_mesh(m,name,flat=False):
 m=m.copy()
 if flat:m.unmerge_vertices()
 v=m.vertices;bounds=m.bounds;d=np.maximum(m.extents,1e-8);uv=np.column_stack((np.arctan2(v[:,2],v[:,0])/(2*np.pi)+.5,(v[:,1]-bounds[0,1])/d[1]));m.visual=tm.visual.TextureVisuals(uv=uv,material=material(name));return m

def ext(tree):
 used=tree.setdefault('extensionsUsed',[])
 for mat in tree.get('materials',[]):
  name=mat.get('name','');trans=MATS.get(name,(None,None,None,0))[3]
  if trans:
   mat.setdefault('extensions',{})['KHR_materials_transmission']={'transmissionFactor':trans};mat['extensions']['KHR_materials_ior']={'ior':1.46 if name=='plastic' else 1.5};
   for x in ['KHR_materials_transmission','KHR_materials_ior']:
    if x not in used:used.append(x)
 tree.setdefault('asset',{})['extras']={'generator':'Shatter Realm original procedural asset pipeline','units':'meters','up':'Y','phase':'2; runtime not validated'}

def export(scene,path):path.write_bytes(tm.exchange.gltf.export_glb(scene,include_normals=True,tree_postprocessor=ext))


def partition(m,target,rng):
 def pieces(x):
  ds=list(x.decompose())
  # A sealed cavity is a negative boundary shell, not an independent physical shard.
  return [x] if any(z.volume()<=0 for z in ds) else ds
 chunks=pieces(m)
 while len(chunks)<target:
  sizes=[x.volume() for x in chunks];idx=int(np.argmax(sizes));big=chunks.pop(idx);mesh=asmesh(big);center=mesh.center_mass
  # Randomized plane cuts create irregular true volumetric pieces, not detached sprites.
  direction=rng.normal(size=3);direction/=np.linalg.norm(direction);center+=rng.uniform(-.12,.12,3)*mesh.extents
  a,b=big.split_by_plane(direction,float(np.dot(direction,center)))
  aa=pieces(a) if not a.is_empty() else [];bb=pieces(b) if not b.is_empty() else []
  if not aa or not bb:chunks.append(big);continue
  parts=aa+bb
  if any(x.volume()<=1e-14 for x in parts):chunks.append(big);continue
  chunks.extend(parts)
 return chunks



def robust_hull(mesh):
 # Quantize below a micron, then triangulate the convex boundary without dropping tiny faces.
 points=np.unique(np.round(mesh.vertices*1e7)/1e7,axis=0).astype(np.float32).astype(float)
 ch=ConvexHull(points);faces=ch.simplices.copy();normals=np.cross(points[faces[:,1]]-points[faces[:,0]],points[faces[:,2]]-points[faces[:,0]]);flip=np.einsum("ij,ij->i",normals,ch.equations[:,:3])<0;faces[flip]=faces[flip][:,[0,2,1]];out=tm.Trimesh(points,faces,process=False);out.remove_unreferenced_vertices()
 assert out.is_watertight and out.volume>0
 return out

def convex_compound(fragment,limit=24,tolerance=1.35):
 pieces=[fragment]
 def info(x):
  mesh=asmesh(x);h=robust_hull(mesh);return mesh,h,float(h.volume/max(mesh.volume,1e-16))
 records=[info(fragment)]
 while len(pieces)<limit:
  errors=[max(0,h.volume-tolerance*m.volume) for m,h,r in records]
  idx=int(np.argmax(errors))
  if errors[idx]<=1e-14:break
  current=pieces[idx];mesh,hull,ratio=records[idx];best=None
  axes=np.eye(3)
  for normal in axes:
   at=float(np.dot(normal,mesh.center_mass));a,b=current.split_by_plane(normal,at)
   if a.is_empty() or b.is_empty() or min(a.volume(),b.volume())<=1e-14:continue
   aa,bb=info(a),info(b);cost=aa[1].volume+bb[1].volume
   if best is None or cost<best[0]:best=(cost,a,b,aa,bb)
  if best is None:break
  _,a,b,aa,bb=best;pieces[idx:idx+1]=[a,b];records[idx:idx+1]=[aa,bb]
 return [h for _,h,_ in records]


def face_materials(mesh,mat,item_id,component):
 labels=np.full(len(mesh.faces),mat,dtype='<U16')
 if item_id in ['mug','bowl'] and component==0:
  c=mesh.triangles_center;n=mesh.face_normals;radial=c.copy();radial[:,1]=0;radial/=np.maximum(np.linalg.norm(radial,axis=1)[:,None],1e-12)
  inner=(np.einsum('ij,ij->i',n,radial)<-.15)|((n[:,1]>.7)&(c[:,1]<mesh.bounds[0,1]+.015))
  labels[inner]='ivory'
 return labels

def make_asset(item):
 tick=time.time();id=item['id'];dest=OUT/id;dest.mkdir(exist_ok=True)
 parts=build(id)
 # Calibrate approved longest dimension and place lowest point at Y=0.
 bounds=np.array([np.min([m.bounds[0] for m,_ in parts],axis=0),np.max([m.bounds[1] for m,_ in parts],axis=0)]);scale=item['longestDimensionM']/max(bounds[1]-bounds[0]);offset=np.array([-(bounds[0,0]+bounds[1,0])/2,-bounds[0,1],-(bounds[0,2]+bounds[1,2])/2])*scale
 for m,_ in parts:m.apply_scale(scale);m.apply_translation(offset)
 src=tm.Scene()
 for j,(m,mat) in enumerate(parts):
  labels=face_materials(m,mat,id,j)
  for color in np.unique(labels):
   sub=m.submesh([np.flatnonzero(labels==color)],append=True,repair=False);src.add_geometry(render_mesh(sub,str(color)),node_name=f'{id}-component-{j:03}-{color}',geom_name=f'{id}-{j:03}-{color}')
 export(src,dest/'intact.glb')
 solids=[];face_offset=0
 for m,_ in parts:
  solids.append(manifold(m,np.arange(face_offset,face_offset+len(m.faces),dtype=np.uint32)));face_offset+=len(m.faces)
 surface_original_ids={rid for x in solids for rid in x.to_mesh().run_original_id};solid=md.Manifold.batch_boolean(solids,md.OpType.Add);full=asmesh(solid)
 assert full.is_watertight and full.volume>0,(id,'intact solid invalid')
 source_triangles=np.vstack([m.triangles for m,_ in parts]);source_centers=np.vstack([m.triangles_center for m,_ in parts]);source_normals=np.vstack([m.face_normals for m,_ in parts]);source_mats=np.concatenate([face_materials(m,mat,id,j) for j,(m,mat) in enumerate(parts)]);tree=cKDTree(source_centers)
 rng=np.random.default_rng(int(hashlib.sha256(id.encode()).hexdigest()[:8],16));structural=id in ['keyboard','phone','remote','monitor','storage-box'];chunk_sources=[]
 if structural:
  chunks=[];occupied=md.Manifold()
  for component,((mesh,mat),piece) in enumerate(zip(parts,solids)):
   clean=piece-occupied if not occupied.is_empty() else piece;occupied=occupied+piece
   if clean.is_empty() or clean.volume()<=1e-14:continue
   role=mesh.metadata.get('semanticRole','body')
   target={'keyboard':6,'phone':6,'remote':8,'monitor':12,'storage-box':15}[id] if component==0 else 1
   derived=partition(clean,target,rng);chunks.extend(derived);chunk_sources.extend([{'component':component,'role':role} for _ in derived])
 else:
  chunks=partition(solid,item['targetFragments'],rng);chunk_sources=[{'component':None,'role':'body'} for _ in chunks]
 frag_scene=tm.Scene();solid_scene=tm.Scene();hull_scene=tm.Scene();records=[]
 total_vol=sum(x.volume() for x in chunks)
 for j,chunk in enumerate(chunks):
  mesh=asmesh(chunk);assert mesh.is_watertight and mesh.volume>0,(id,j,'invalid shard');center=mesh.center_mass.copy();node=f'fragment-{j:03}';transform=np.eye(4);transform[:3,3]=center
  raw=chunk.to_mesh();face_ids=np.array(raw.face_id,dtype=int);source_ids=np.full(len(mesh.faces),-1,dtype=np.int64)
  for run,original in enumerate(raw.run_original_id):source_ids[int(raw.run_index[run])//3:int(raw.run_index[run+1])//3]=original
  outside=np.isin(source_ids,list(surface_original_ids))&(face_ids<len(source_mats))
  interior='clay' if item['material']=='ceramic' else ('black' if item['material']=='plastic' else ('clear' if id not in ['wine-bottle','perfume-bottle','lamp-shade'] else {'wine-bottle':'green','perfume-bottle':'amber','lamp-shade':'frosted'}[id]))
  labels=np.full(len(mesh.faces),interior,dtype='<U16');labels[outside]=source_mats[face_ids[outside]]

  frag_scene.graph.update(frame_to=node,matrix=transform)
  for k,mat in enumerate(np.unique(labels)):
   sub=mesh.submesh([np.flatnonzero(labels==mat)],append=True,repair=False);sub.apply_translation(-center);frag_scene.add_geometry(render_mesh(sub,str(mat),flat=(str(mat)=="clay")),node_name=node+'-'+str(mat),parent_node_name=node,geom_name=node+'-'+str(mat))
  solid_local=mesh.copy();solid_local.apply_translation(-center);solid_scene.add_geometry(render_mesh(solid_local,'clay' if item['material']=='ceramic' else ('clear' if item['material']=='glass' else 'black')),node_name=node,transform=transform)
  hulls=convex_compound(chunk);hull_volume=0
  for k,hull in enumerate(hulls):
   hull_volume+=hull.volume;hull.apply_translation(-center);hull.visual=tm.visual.ColorVisuals(hull,face_colors=[120,170,140,255]);hull_scene.add_geometry(hull,node_name=node+f'-hull-{k:02}',transform=transform)

  records.append(dict(id=node,exteriorAreaM2=float(mesh.area_faces[outside].sum()),cutAreaM2=float(mesh.area_faces[~outside].sum()),sourceComponent=chunk_sources[j]['component'],sourceRole=chunk_sources[j]['role'],positionM=center.tolist(),volumeM3=float(mesh.volume),massKg=float(item['massKg']*mesh.volume/total_vol),centerOfMassLocalM=[0,0,0],inertiaKgM2=(mesh.moment_inertia*(item['massKg']/total_vol)).tolist(),triangles=len(mesh.faces),colliderTriangles=sum(len(h.faces) for h in hulls),colliderHullCount=len(hulls),collider='compound convex hulls; volume-adaptive conservative approximation',watertight=True,convexHullVolumeRatio=float(hull_volume/mesh.volume)))
 export(frag_scene,dest/'fragments.glb');export(solid_scene,dest/'fracture-solids.glb');export(hull_scene,dest/'collision-hulls.glb')
 data=dict(id=id,fractureStrategy='structural-components' if structural else 'volumetric-plane-partition',sourceComponentRoles=[m.metadata.get('semanticRole','body') for m,_ in parts],zh=item['zh'],en=item['en'],material=item['material'],massKg=item['massKg'],boundsM=full.bounds.tolist(),longestDimensionM=float(max(full.extents)),materialProvenance='Manifold original face IDs; cut faces get separate interior material',intactSurfaceAreaM2=float(full.area),intactVolumeM3=float(full.volume),fragmentVolumeSumM3=float(total_vol),relativeVolumeError=float(abs(total_vol-full.volume)/full.volume),fragmentCount=len(chunks),targetFragmentBudget=item['targetFragments'],intact='intact.glb',fragments='fragments.glb',colliders='collision-hulls.glb',closedSolids='fracture-solids.glb',fragmentRecords=records,provenance='Original project-authored parametric meshes; exact Manifold plane partitions; no third-party models',runtimeValidation='not_performed')
 (dest/'metadata.json').write_text(json.dumps(data,ensure_ascii=False,indent=2));print(f'{id}: {len(chunks)} closed fragments, volume error {data["relativeVolumeError"]:.8f}, {time.time()-tick:.1f}s',flush=True);return data

if __name__=='__main__':
 import sys
 selected=set(sys.argv[1:]);results=[]
 for i in SPEC:
  if selected and i['id'] not in selected:continue
  results.append(make_asset(i))
 manifests=[]
 for i in SPEC:
  f=OUT/i['id']/'metadata.json'
  if f.exists():manifests.append(json.loads(f.read_text()))
 (OUT/'manifest.json').write_text(json.dumps(dict(assets=manifests,totalObjects=len(manifests),totalFragments=sum(x['fragmentCount'] for x in manifests),source='Original parametric asset production; fallback after official Editor workflow failed',status='requires visual and user approval'),ensure_ascii=False,indent=2))
