from pathlib import Path
import json,re
p=Path(__file__).resolve().parents[1]
s=json.loads((p/'design-spec.json').read_text())
assert len(s['dataCases']['normal']['items'])==15
for k in ['glass','ceramic','plastic']:assert sum(x['material']==k for x in s['dataCases']['normal']['items'])==5
for group in ['nodes','states','transitions','actions','assets','surfaces']:
 ids=[x['id'] for x in s[group]];assert len(ids)==len(set(ids)),group
nodes={x['id'] for x in s['nodes']};states={x['id'] for x in s['states']}
for t in s['transitions']:assert t['from'] in states and t['to'] in states and t['trigger']['nodeId'] in nodes
for a in s['assets']:assert (p/a['src']).is_file(),a['src']
for lang in ['zh','en']:
 for view in ['logo','safety','main','shelf-end','controller','reset','counts-active','custom-counts']:assert (p/'screens'/f'{view}-{lang}.png').is_file()
for f in ['design-doc.md','preview.html','index.html','concepts/prompts.json']:assert (p/f).is_file(),f
schema_path=p.parents[1]/'.agents/skills/pico-spatial-app-designer/assets/design-spec.schema.json'
import jsonschema
jsonschema.validate(s,json.loads(schema_path.read_text()))
report=dict(status='passed',schema='1.0',revision=s['metadata']['revision'],builtinObjects=15,groups=3,uiScreens=16,referenceIntegrity='passed',allDeclaredAssetsExist=True,deviceValidation='not_performed',scope='phase-1 only')
(p/'review/spec-checks.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
