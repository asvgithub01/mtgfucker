import collections, hashlib, json, random, sqlite3
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parent
OUT=ROOT/'pilot-01'; OUT.mkdir(exist_ok=True)
if (OUT/'split.json').exists():
    raise SystemExit('Frozen pilot split exists; use a new output version for another audit.')
db=sqlite3.connect(ROOT/'manifest.sqlite')
rows=db.execute('select metadata,sha256,width,height from images where sha256 is not null order by id').fetchall()
bad=[]; valid=[]; hashes=collections.Counter(); sets=collections.Counter()
for encoded,sha,w,h in rows:
    r=json.loads(encoded); p=ROOT/r['path']
    try:
        assert hashlib.sha256(p.read_bytes()).hexdigest()==sha, 'hash mismatch'
        with Image.open(p) as im:
            im.load(); assert im.size==(w,h)
        r['sha256']=sha; r['width']=w; r['height']=h
        valid.append(r); hashes[sha]+=1; sets[r['set']]+=1
    except Exception as e: bad.append({'path':str(p),'error':str(e)})
    if len(valid)%1000==0: print('audited',len(valid),flush=True)
# Do not train on oracle identities represented in previously inspected real queries.
old=Path('C:/Users/nubor/.codex/worktrees/dinov2-benchmark/mtgfucker/build/dinov2-expanded/corpus/manifest.json')
known=json.loads(old.read_text(encoding='utf8'))
excluded_names={q['name'].casefold() for q in known['queries']}
excluded_names.update(x.casefold() for x in ['Archmage Emeritus','Ghalta the Immovable',"Abu Ja’far", "Abu Ja'far",'Order of Leitbur','Duty Beyond Death','Sleight of Hand'])
excluded_oracles={r['oracle_id'] for r in valid if r['name'].casefold() in excluded_names}
eligible=[r for r in valid if r['image_status']=='highres_scan' and r.get('oracle_id') and r.get('illustration_id') and r['layout']=='normal' and r['oracle_id'] not in excluded_oracles and r['name'].casefold() not in excluded_names and .65<r['width']/r['height']<.78]
# Group connected oracle/art/hash identities before splitting; no art shared across splits.
parent={}
def find(x):
    parent.setdefault(x,x)
    if parent[x]!=x: parent[x]=find(parent[x])
    return parent[x]
def union(a,b): parent[find(a)]=find(b)
for r in eligible:
    union('o'+r['oracle_id'],'a'+r['illustration_id']); union('o'+r['oracle_id'],'h'+r['sha256'])
unique={}
for r in eligible: unique.setdefault(r['illustration_id'],r)
train=[]; val=[]
for r in unique.values():
    group=find('o'+r['oracle_id']); r['group']=group
    (val if int(hashlib.sha256(group.encode()).hexdigest()[:8],16)%5==0 else train).append(r)
def select(rows,n):
    rng=random.Random(20261026); rows=sorted(rows,key=lambda r:r['id']); rng.shuffle(rows)
    full=[r for r in rows if r['full_art']]; normal=[r for r in rows if not r['full_art']]
    picked=full[:n//2]+normal[:n-len(full[:n//2])]
    return picked
train=select(train,2048); val=select(val,256)
assert not ({r['group'] for r in train}&{r['group'] for r in val})
summary={'audited':len(rows),'valid':len(valid),'bad':bad,'sets':dict(sets),'full_art_flag':sum(r['full_art'] for r in valid),'lowres':sum(r['image_status']=='lowres' for r in valid),'duplicate_exact_files':sum(n-1 for n in hashes.values()),'eligible_unique_arts':len(unique),'train':len(train),'validation':len(val),'train_full_art':sum(r['full_art'] for r in train),'validation_full_art':sum(r['full_art'] for r in val),'excluded_real_query_names':sorted(excluded_names),'scope':'Frozen subset; full_art is Scryfall flag, not an exhaustive borderless label. Synthetic validation is not real-camera precision.'}
(OUT/'audit.json').write_text(json.dumps(summary,indent=2),encoding='utf8')
(OUT/'split.json').write_text(json.dumps({'train':train,'validation':val},indent=2),encoding='utf8')
print(json.dumps({k:v for k,v in summary.items() if k not in ('sets','excluded_real_query_names')},indent=2),flush=True)
