import json,tarfile,hashlib,io,collections,time
from pathlib import Path
from PIL import Image
R=Path(r'C:/asv/datasets/mtg-identity-v1');O=R/'postprocessed-audit-01';O.mkdir(exist_ok=True);(O/'images').mkdir(exist_ok=True)
B=Path(r'C:/Users/nubor/.codex/backups/mtgfucker/sony-before-triple-probe-20261025-202612')
inv=json.loads((R/'coverage-audit-01/photo-inventory.json').read_text());wanted={x['path'].removeprefix('./'):x for x in inv if x['path'].startswith('./files/edscan/')}
rows=[];seen=set();start=time.time()
with tarfile.open(B/'private-verified.tar','r|*') as tf:
 for m in tf:
  key=m.name.removeprefix('./')
  if key not in wanted or not m.isfile():continue
  data=tf.extractfile(m).read();h=hashlib.sha256(data).hexdigest();assert h==wanted[key]['sha256']
  row={'source_archive':str(B/'private-verified.tar'),'source_member':m.name,'sha256':h,'duplicate':h in seen,'label_status':'unreviewed','split':'quarantine_not_training'}
  try:
   with Image.open(io.BytesIO(data)) as im:
    im.load();row.update(width=im.width,height=im.height)
    thumb=im.convert('L').resize((9,8),Image.Resampling.BILINEAR);p=list(thumb.getdata());row['dhash']=format(sum((p[y*9+x]>p[y*9+x+1])<<(y*8+x) for y in range(8) for x in range(8)),'016x')
   dst=O/'images'/(h+Path(key).suffix.lower())
   if not dst.exists():dst.write_bytes(data)
   assert hashlib.sha256(dst.read_bytes()).hexdigest()==h
   row['copy']=str(dst);row['decode_ok']=True
  except Exception as e:row['decode_ok']=False;row['error']=str(e)
  seen.add(h);rows.append(row)
assert len(rows)==len(wanted),(len(rows),len(wanted))
summary={'files':len(rows),'unique_exact':len(seen),'decoded':sum(r['decode_ok'] for r in rows),'elapsed_seconds':time.time()-start,'warning':'No identities inferred or training performed. Perceptual hashes only flag similar images, not proof of duplicate or same card.'}
(O/'manifest.json').write_text(json.dumps({'summary':summary,'rows':rows},indent=2),encoding='utf8');print(json.dumps(summary))
