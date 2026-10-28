"""Prepare reproducible expanded identity splits. Preview while downloading; final requires completeness."""
import argparse,collections,hashlib,json,sqlite3,time
from pathlib import Path
R=Path(__file__).resolve().parent

def main():
 p=argparse.ArgumentParser();p.add_argument('--final',action='store_true');a=p.parse_args()
 db=sqlite3.connect('file:'+str(R/'manifest.sqlite')+'?mode=ro',uri=True)
 rows=[]
 for raw,h,w,height in db.execute('select metadata,sha256,width,height from images order by id'):
  r=json.loads(raw);r['sha256']=h;r['width']=w;r['height']=height;rows.append(r)
 db.close();complete=all(r['sha256'] for r in rows)
 if a.final and not complete:raise RuntimeError('Download incomplete: refusing final split')
 old=json.loads((R/'pilot-01/split.json').read_text(encoding='utf8'))
 dev=json.loads((R/'expanded-01/manifest.json').read_text(encoding='utf8'))
 reserved={r['name'].casefold() for r in dev['queries']}
 reserved.update(json.loads((R/'pilot-01/audit.json').read_text(encoding='utf8'))['excluded_real_query_names'])
 reserved.update({'archmage emeritus','pick your poison','sleight of hand','giant growth','bartolomé del presidio','deduce'})
 # Connected components enforce transitive oracle/art/exact-file exclusion.
 parent=list(range(len(rows)))
 def root(i):
  while parent[i]!=i:parent[i]=parent[parent[i]];i=parent[i]
  return i
 seen={}
 for i,r in enumerate(rows):
  for field in ('oracle_id','illustration_id','sha256'):
   v=r.get(field)
   if not v:continue
   key=(field,v)
   if key in seen:parent[root(i)]=root(seen[key])
   else:seen[key]=i
 groups=collections.defaultdict(list)
 for i,r in enumerate(rows):groups[root(i)].append(r)
 oldtrain={r['id'] for r in old['train']};oldval={r['id'] for r in old['validation']}
 split={'train':[],'validation':[]};report=collections.Counter();all_members=[]
 for members in groups.values():
  ids={r['id'] for r in members};g=min(ids)
  wastrain=bool(ids&oldtrain);wasval=bool(ids&oldval)
  if wastrain and wasval:raise RuntimeError('New metadata links previous train and validation: review before proceeding '+g)
  if any(r['name'].casefold() in reserved for r in members):dest='reserved_real_evaluation'
  elif wasval:dest='validation'
  elif wastrain:dest='train'
  else:dest='validation' if int(hashlib.sha256(('expanded-v1:'+g).encode()).hexdigest()[:8],16)%20==0 else 'train'
  report[dest+'_groups']+=1
  all_members.append({'group':g,'split':dest,'ids':sorted(ids)})
  if dest not in split:continue
  arts=set()
  for r in sorted(members,key=lambda r:r['id']):
   if not r['sha256'] or r['image_status']!='highres_scan' or not r.get('illustration_id'):continue
   art=r['illustration_id']
   if not r.get('width') or not r.get('height') or not .65 < r['width']/r['height'] < .78:continue
   if art in arts:continue
   arts.add(art);split[dest].append(dict(r,group=g))
 for f in ('group','oracle_id','illustration_id','sha256'):
  x={r[f] for r in split['train'] if r.get(f)};y={r[f] for r in split['validation'] if r.get(f)};assert not x&y,f
 for k,v in split.items():report[k+'_images']=len(v);report[k+'_oracle']=len({r['oracle_id'] for r in v if r['oracle_id']})
 out=R/('expanded-training-final' if a.final else 'expanded-training-preview')
 out.mkdir(exist_ok=True)
 if a.final and (out/'split.json').exists():raise RuntimeError('Frozen final already exists')
 metadata={'time':time.strftime('%Y-%m-%d %H:%M:%S'),'complete_download':complete,'downloaded':sum(bool(r['sha256']) for r in rows),'total':len(rows),'mode':'final_pending_integrity_audit' if a.final else 'preview_not_trainable','counts':dict(report),'notes':'Groups based on complete metadata plus available exact hashes. Preview MUST be regenerated after download to catch late duplicate hashes. One highres English image per illustration; old train/val groups preserved, real-evaluation names excluded. No photographs read. Final split still requires full hash/decode validation before training.'}
 for name,data in [('split.json',split),('groups.json',all_members),('status.json',metadata)]:
  tmp=out/(name+'.tmp');tmp.write_text(json.dumps(data,indent=2,ensure_ascii=False),encoding='utf8');tmp.replace(out/name)
 print(json.dumps(metadata,indent=2))
if __name__=='__main__':main()

