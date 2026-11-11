from pathlib import Path
import json
ROOT=Path(__file__).parent
roles='fillPrimary fillSecondary fillTertiary fillLight labelPrimaryLight labelPrimary labelSecondary labelTertiary labelQuaternary lightenHover lightenPressed error alert passable interaction dividerLine'.split()
rows=[
('glass-cup','玻璃杯','Tumbler','glass',.11,.23,'透明钠钙玻璃，轻微水痕；保留杯底厚度',24),
('wine-bottle','红酒瓶','Wine bottle','glass',.30,.48,'无商标深绿色玻璃，空瓶；瓶口与底部加厚',32),
('glass-jar','玻璃罐','Glass jar','glass',.17,.38,'透明宽口罐，烟灰盖；盖体为独立刚体',28),
('lamp-shade','吊灯罩','Lamp shade','glass',.22,.40,'乳白磨砂钟形玻璃罩，无电线；薄壳断口',32),
('perfume-bottle','香水瓶','Perfume bottle','glass',.10,.22,'琥珀色厚底玻璃，金属盖；不模拟液体',20),
('plate','陶瓷餐盘','Dinner plate','ceramic',.25,.52,'象牙白釉面，细微刀痕；断面露浅灰陶胎',24),
('bowl','瓷碗','Bowl','ceramic',.15,.30,'青灰釉，米白内壁；环形足圈可辨识',22),
('vase','花瓶','Vase','ceramic',.26,.65,'暖白釉细长瓶，轻微窑变；保持空心',30),
('mug','马克杯','Mug','ceramic',.12,.34,'砖红釉面与白色内壁；把手独立断裂',24),
('ornament','装饰摆件','Ornament','ceramic',.16,.46,'白色陶瓷抽象结形摆件；曲面可抓取',28),
('keyboard','旧键盘','Old keyboard','plastic',.44,.68,'磨旧黑色键盘，无品牌；外壳与键帽分离',28),
('phone','旧手机','Old phone','plastic',.15,.18,'黑色旧手机，熄屏；前盖后壳电路板分离',18),
('remote','遥控器','Remote','plastic',.18,.12,'炭黑塑料，磨损按键；壳体扣件和按键分离',18),
('monitor','显示器模型','Monitor','plastic',.46,2.4,'厚边框旧显示器，熄屏；底座壳体屏片分离',36),
('storage-box','塑料收纳盒','Storage box','plastic',.30,.42,'烟灰半透聚丙烯，带盖；厚片撕裂状断口',22)]
items=[dict(id=r[0],zh=r[1],en=r[2],material=r[3],longestDimensionM=r[4],massKg=r[5],concept=r[6],targetFragments=r[7],cell=i) for i,r in enumerate(rows)]
i18n={
'zh':dict(name='碎境',safetyTitle='先照顾好现实中的自己',safety='本应用仅供娱乐解压，请勿在真实世界模仿投掷易碎物品行为，请注意周边环境安全',agree='同意',leave='退出',total='总投掷数量',countNote='成功碎裂后计数',shelf='挑一件，放下压力',instruction='手指触碰后左右滑动 · 捏取物品后挥手松开',glass='玻璃',ceramic='陶瓷',plastic='电子塑料',custom='自定义模型',refresh='刷新',reset='重置',controllerTitle='放下手柄，用双手释放',controller='已检测到手柄正在使用。应用已切换为纯手势交互，请放下手柄，让双手进入追踪范围。',hands='等待双手进入追踪范围',continueLabel='继续',resetTitle='让空间重新归零',resetBody='清空场景内所有碎片，并将全部投掷计数归零。',confirmReset='确认重置',cancel='取消',ready='已准备好',refreshing='正在刷新自定义模型…',refreshed='已刷新自定义模型',empty='暂无自定义模型',loadError='部分模型无法加载，其他物品仍可使用',noPlane='暂未读取到环境平面，请在系统中确认空间信息',noHands='未检测到双手，请将双手移入追踪范围',exitTitle='已退出预览',back='重新开始',picked='已拿取',previewNote='概念预览：不执行手势追踪或物理模拟'),
'en':dict(name='Shatter Realm',safetyTitle='Take care of your real surroundings',safety='This app is for entertainment and stress relief only. Do not imitate throwing fragile objects in the real world. Stay aware of your surroundings.',agree='Agree',leave='Exit',total='Total throws',countNote='Counted when an object shatters',shelf='Pick an object. Let it go.',instruction='Touch and swipe to browse · Pinch to grab, swing and release',glass='Glass',ceramic='Ceramic',plastic='Electronics & plastic',custom='Custom models',refresh='Refresh',reset='Reset',controllerTitle='Set down your controllers',controller='Active controller use detected. The app is now using hand interaction only. Set down your controllers and bring your hands into view.',hands='Waiting for hand tracking',continueLabel='Continue',resetTitle='Start with a clear space',resetBody='Remove all fragments and reset every throw count to zero.',confirmReset='Reset everything',cancel='Cancel',ready='Ready',refreshing='Refreshing custom models…',refreshed='Custom models refreshed',empty='No custom models found',loadError='Some models could not be loaded. Other objects are still available.',noPlane='No environment planes available. Check spatial data in system settings.',noHands='Hands not detected. Bring your hands into view.',exitTitle='Preview ended',back='Start again',picked='Picked up',previewNote='Concept preview: no hand tracking or physics simulation')}
geometry={
'logo':{'widthPx':500,'heightPx':300},
'counter':{'initialWidthPx':220,'maxWidthPx':720,'heightPx':96,'topPx':38,'totalWidthPx':170,'cellWidthPx':130},
'shelf':{'widthPx':660,'heightPx':238,'rightPx':42,'bottomPx':40,'cardWidthPx':112,'cardHeightPx':128},
'modal':{'widthPx':620,'paddingPx':44,'radiusPx':28},
'controls':{'heightPx':60,'minTargetPx':56},
'view':{'widthPx':1600,'heightPx':1000},
'motion':{'logoMs':1600,'transitionMs':220,'feedbackMs':140,'recenterPolicy':'head-relative-with-damping; freeze panel while touching; never smooth throw velocity'},
'customStyleException':{'authority':'User explicitly requires fully custom photorealistic UI and forbids native control styling. This overrides generic SpatialUI Web control and root-surface styling guidance for this concept preview. Preserve PicoTheme and public SDK accessibility/input semantics in later runtime implementation; API feasibility is not yet verified.'}}
assets=[dict(id='catalog',kind='image',src='concepts/objects.png',purpose='15种物品概念图；不是可导入的三维模型'),dict(id='fracture',kind='image',src='concepts/fracture.png',purpose='撞击与碎片保留的视觉故事板；不是运行截图'),dict(id='mark',kind='icon',src='concepts/mark.svg',purpose='裂隙方框启动标记概念'),dict(id='refresh-icon',kind='icon',src='concepts/refresh.svg',purpose='自定义刷新图标'),dict(id='reset-icon',kind='icon',src='concepts/reset.svg',purpose='自定义重置图标')]
states=[('cold-logo','logo'),('safety-gate','safety'),('experience','main'),('controller-gate','controller'),('reset-gate','reset'),('exited','exit')]
spec=dict(schemaVersion='1.0',metadata=dict(projectName='Shatter Realm',sourceMode='product_doc',revision=1),experience=dict(subject='MR发泄解压',user='使用手势的单人体验者',context='已在系统获得空间信息的安全真实房间',singleJob='抓取虚拟日用品并亲手投掷，体验真实碎裂',space='full',form='stage_mixed',signature='裂隙标记与真实断面，中央视野让给投掷'),theme=dict(scheme='vibrant',colorScheme={x:dict(source='vibrant',role=x) for x in roles},brandColors=dict(smoke='#222725ED',porcelain='#F4F1E8',muted='#B9C1BB',accent='#B9E6C5',warning='#E6C394',pressed='#485A4D',room='#DAD6D3'),typography=dict(display=dict(family='sans-serif',sizeDp=52,lineHeightDp=64,weight=500),body=dict(family='sans-serif',sizeDp=20,lineHeightDp=32,weight=500),label=dict(family='sans-serif',sizeDp=15,lineHeightDp=22,weight=500)),spacing=dict(small=8,regular=16,medium=24,large=32)),surfaces=[dict(id='experience-stage',type='stage_mixed',defaultSize=dict(width=3,height=2.5,depth=3,unit='m'),minSize=dict(width=2,height=2,depth=2,unit='m'),maxSize=dict(width=5,height=3,depth=5,unit='m'),rootNodeId='main',placement={'policy':'room scale; extent values are design envelope, never create fake environment planes'}),dict(id='counter-panel',type='augment',defaultSize=dict(width=.92,height=.23,unit='m'),minSize=dict(width=.82,height=.23,unit='m'),maxSize=dict(width=1.02,height=.27,unit='m'),rootNodeId='counter',placement=dict(reference='head',distanceM=1.35,elevationDegrees=13,interactive=False)),dict(id='selection-panel',type='augment',defaultSize=dict(width=.48,height=.20,unit='m'),minSize=dict(width=.42,height=.20,unit='m'),maxSize=dict(width=.56,height=.23,unit='m'),rootNodeId='shelf',placement=dict(reference='head',distanceM=.65,azimuthDegrees=23,elevationDegrees=-18,freezeWhileTouching=True)),dict(id='gate-panel',type='augment',defaultSize=dict(width=.62,height=.40,unit='m'),minSize=dict(width=.56,height=.40,unit='m'),maxSize=dict(width=.72,height=.46,unit='m'),rootNodeId='safety',placement=dict(reference='head',distanceM=1.1,azimuthDegrees=0,elevationDegrees=0))],initialStateId='cold-logo',states=[dict(id=x,surfaceRoots={'experience-stage':y}) for x,y in states],transitions=[],actions=[],dataCases=dict(normal=dict(locale='zh',i18n=i18n,items=items,geometry=geometry,counters={x['id']:0 for x in items},customCounters={},modelsFolder='getExternalFilesDir(null)/models',units='meters kilograms seconds',gravityMps2=9.8,customModelFormats=['glb','fbx','obj'],physicsPolicies=dict(fragmentLifetime='until explicit reset',fragmentCollision='all fragments retain colliders; sleeping retains reactivation',releaseVelocity='tracked hand linear and angular velocity, no aim correction',breakTrigger='first collision with recognized real plane; idempotent per item',counterMeaning='successfully shattered objects, not release attempts',customAudio='one universal material set',importFailure='isolate per file; preserve builtins',refresh='append custom items before refresh and reset; keep held object and existing shards')),fallback=dict(customModels=[],planes=[],handsTracked=False),error=dict(customImport='skip-invalid-file',planePermission='block-spawn-with-inline-message',controllerActive='controller-gate',memoryPressure='no silent fragment deletion; surface limit and seek user decision if measured')),nodes=[],responsiveRules=[],assets=assets)
for _,view in states:
 spec['nodes'].append(dict(id=view,kind='domain_visual',rendererKey='shatter-'+view,purpose='自定义'+view+'设计预览；遵循用户禁止原生控件样式的要求',props=dict(view=view,data='dataCases.normal',controlStyle='custom-physical',languageSource='system-only; query param used only by design reviewer')))
for n in ['counter','shelf']:
 spec['nodes'].append(dict(id=n,kind='domain_visual',rendererKey='shatter-'+n,purpose='常驻'+n,props=dict(data='dataCases.normal')))
for id_,fr,to,node,event in [('logo-finished','cold-logo','safety-gate','logo','timer'),('consent','safety-gate','experience','safety','agree'),('decline','safety-gate','exited','safety','exit'),('detect-controller','experience','controller-gate','main','active-controller'),('resume-hands','controller-gate','experience','controller','hands-tracked'),('ask-reset','experience','reset-gate','shelf','reset'),('confirm-reset','reset-gate','experience','reset','confirm'),('cancel-reset','reset-gate','experience','reset','cancel'),('restart','exited','cold-logo','exit','restart')]:
 spec['transitions'].append({'id':id_,'from':fr,'to':to,'trigger':dict(nodeId=node,event=event)})
 spec['actions'].append(dict(id=id_,type='transition',purpose=id_,transitionId=id_))
for id_,purpose in [('refresh-models','重新读取自定义模型目录'),('pick-item','捏取物品，面板保持显示'),('swipe-items','横向触摸滚动，末尾为刷新重置'),('record-shatter','单物品首次成功碎裂后更新该物品及总数'),('hot-resume','同一进程生命周期恢复时保留安全同意状态和场景'),('tracking-lost','停止生成新物品并显示追踪状态，不创建虚假速度')]:
 spec['actions'].append(dict(id=id_,type='custom',purpose=purpose))
spec['metadata']['revision']=4
spec['dataCases']['normal']['runtimePolicies']={
 'controllerResumeEvents':{'cold-logo':'restore-logo','safety-gate':'restore-safety','experience':'hands-tracked','reset-gate':'restore-reset'},
 'counterExpansion':{'initialTotalOnly':True,'revealOn':'first successful shatter','hideZeroCounts':True,'order':'first successful shatter','resetHidesItemCounts':True,'overflow':'horizontal hand-gesture scroll; keep total pinned','customPerModel':True,'stableKey':'model content identity, not list index'},
 'resetPolicy':{'confirmationRequired':True,'clearAllFragments':True,'reclaimHeldAndThrownObjects':True,'resetAllCounters':True},
 'customModelRoot':'getExternalFilesDir(null)/models',
 'handTrackingLoss':'freeze held item; never fabricate release velocity',
 'materialFractureRules':{'glass':'curved thin shards and thick base','ceramic':'glazed shell and matte interior','plastic':'structural casing separation'},
 'audio':{'groups':['glass','ceramic','plastic','custom'],'events':['throw','shatter','fragment-land'],'variantsPerEvent':3,'uiEvents':['confirm','cancel','pick','refresh-start','refresh-complete','failure','reset-complete'],'sampleRateHz':48000}}
spec['dataCases']['normal']['customExampleModels']=[{'id':'custom-a','zh':'自定义花器','en':'Custom vessel'},{'id':'custom-b','zh':'自定义雕塑','en':'Custom sculpture'},{'id':'custom-c','zh':'自定义摆件','en':'Custom ornament'}]
spec['surfaces'][1]['placement']['interactive']=True
spec['surfaces'][1]['placement']['interactionScope']='scroll revealed item counts only; total remains pinned'
for suffix,from_state,restore_event in [('logo','cold-logo','restore-logo'),('safety','safety-gate','restore-safety'),('reset','reset-gate','restore-reset')]:
 for id_,fr,to,event in [('controller-from-'+suffix,from_state,'controller-gate','active-controller'),('controller-to-'+suffix,'controller-gate',from_state,restore_event)]:
  spec['transitions'].append({'id':id_,'from':fr,'to':to,'trigger':{'nodeId':'controller' if fr=='controller-gate' else dict(states)[fr],'event':event}})
  spec['actions'].append(dict(id=id_,type='transition',purpose=id_,transitionId=id_))
spec['surfaces'][1]['defaultSize']={'width':.22,'height':.096,'unit':'m'}
spec['surfaces'][1]['minSize']={'width':.22,'height':.096,'unit':'m'}
spec['surfaces'][1]['maxSize']={'width':.72,'height':.096,'unit':'m'}
spec['actions'].append(dict(id='swipe-revealed-counters',type='custom',purpose='Scroll only successfully shattered item types in first-use order; keep total pinned'))
spec['dataCases']['normal']['launcherIcon']={'conceptOnly':True,'textFree':True,'masterSizePx':1024,'foreground':'concepts/launcher/launcher-foreground.svg','background':'concepts/launcher/launcher-background.svg','monochrome':'concepts/launcher/launcher-monochrome.svg','androidReference':'https://developer.android.com/develop/ui/compose/system/icon_design_adaptive','packaging':'materialize Android resources and verify actual PICO launcher in phase 3'}
spec['assets'].append(dict(id='launcher-icon',kind='image',src='concepts/launcher/launcher-master.png',purpose='安装包与系统应用列表图标的第一阶段设计稿，无文字、单一品牌标记'))
(ROOT/'design-spec.json').write_text(json.dumps(spec,ensure_ascii=False,indent=2))
(ROOT/'concepts/mark.svg').write_text('''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 160 160"><g fill="none" stroke="#F4F1E8" stroke-width="5" stroke-linecap="square"><path d="M64 20H24v116h45M95 20h41v116H96"/><path d="M89 15 68 69l27 20-22 58"/></g></svg>''')
(ROOT/'concepts/refresh.svg').write_text('''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M20 10a8 8 0 1 0-1 7M20 4v6h-6"/></svg>''')
(ROOT/'concepts/reset.svg').write_text('''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M4 7h16M9 4h6M6 7l1 14h10l1-14M10 10v7m4-7v7"/></svg>''')
print('Design specification written:',len(items),'items;',len(spec['transitions']),'transitions')
