from pathlib import Path
import json
p=Path(__file__).parent
s=(p/'preview.template.html').read_text()
s=s.replace('__SPEC__',(p/'design-spec.json').read_text())
s=s.replace('__ICONS__',json.dumps({x:(p/'concepts'/name).read_text() for x,name in [('mark','mark.svg'),('refresh','refresh.svg'),('reset','reset.svg')]},ensure_ascii=False))
(p/'preview.html').write_text(s)
print('Rendered preview.html from design-spec.json')
