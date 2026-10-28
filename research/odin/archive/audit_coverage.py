import json,sqlite3,time,requests,collections,re
from pathlib import Path
R=Path(r'C:/asv/datasets/mtg-identity-v1'); O=R/'coverage-audit-01';O.mkdir(exist_ok=True)
db=sqlite3.connect('file:'+str(R/'manifest.sqlite')+'?mode=ro',uri=True)
rows=[(json.loads(m),s) for m,s in db.execute('select metadata,sha256 from images')];db.close()
def counts(items):
 return {'faces':len(items),'oracle_identities':len({x['oracle_id'] for x in items if x['oracle_id']}),'printings':len({x['scryfall_id'] for x in items}),'artworks':len({x['illustration_id'] for x in items if x['illustration_id']})}
a={'timestamp':time.strftime('%Y-%m-%d %H:%M:%S'),'eligible_snapshot':counts([r for r,s in rows]),'downloaded':counts([r for r,s in rows if s]),'train':counts(json.loads((R/'pilot-02/split.json').read_text(encoding='utf-8'))['train'])}
s=requests.Session();s.headers.update({'User-Agent':'MtgIdentityResearch/0.1 (personal research dataset)','Accept':'application/json'})
a['scryfall_live']=[]
for q in ['game:paper','game:paper date<=2026-10-26','game:paper or game:arena or game:mtgo']:
 time.sleep(.6)
 try:
  r=s.get('https://api.scryfall.com/cards/search',params={'q':q,'unique':'cards'},timeout=35);r.raise_for_status();a['scryfall_live'].append({'query':q,'url':r.url,'total_cards':r.json()['total_cards']})
 except Exception as e:a['scryfall_live'].append({'query':q,'error':str(e)})
b=Path(r'C:/Users/nubor/.codex/backups/mtgfucker/sony-before-triple-probe-20261025-202612/source-complete.sha256')
p=[]
for line in b.read_text(encoding='utf-8').splitlines():
 m=re.match(r'^([0-9a-f]{64})\s+(.+)$',line)
 if m and m[2].lower().endswith(('.jpg','.jpeg','.png','.webp')) and m[2].startswith('./files/'):
  p.append({'sha256':m[1],'path':m[2]})
a['historical_photos']={'source_hash_manifest':str(b),'files':len(p),'unique_sha256':len({x['sha256'] for x in p}),'by_directory':dict(collections.Counter(str(Path(x['path']).parent).replace('\\','/') for x in p)),'scope':'Inventory from verified backup hashes, not a new decode or labeling audit; do not use historical recognition as ground truth.'}
old=json.loads(Path(r'C:/Users/nubor/.codex/worktrees/dinov2-benchmark/mtgfucker/build/dinov2-expanded/corpus/manifest.json').read_text(encoding='utf-8'))['queries']
a['historical_photos']['already_reviewed_development_queries']=len(old)
a['historical_photos']['matching_reviewed_hashes']=len({x['sha256'] for x in p}&{x['sha256'] for x in old})
(O/'photo-inventory.json').write_text(json.dumps(p,indent=2));(O/'counts.json').write_text(json.dumps(a,indent=2));print(json.dumps(a,indent=2))

