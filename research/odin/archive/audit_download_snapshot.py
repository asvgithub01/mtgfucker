"""Read-only image audit. Writes evidence only; never repairs/deletes source images."""
import concurrent.futures,hashlib,json,sqlite3,time,io,argparse
from pathlib import Path
from PIL import Image
R=Path(__file__).resolve().parent

def check(row):
 raw,h,w,ht,n=row;r=json.loads(raw);p=(R/r['path']).resolve()
 try:
  if not p.is_relative_to((R/'images').resolve()):raise ValueError('Outside image root')
  data=p.read_bytes();assert len(data)==n,'size mismatch';assert hashlib.sha256(data).hexdigest()==h,'SHA mismatch'
  with Image.open(io.BytesIO(data)) as im:im.load();assert im.size==(w,ht),'dimensions mismatch'
  return {'id':r['id'],'sha256':h,'ok':True}
 except Exception as e:return {'id':r['id'],'ok':False,'error':str(e)}

def main():
 ap=argparse.ArgumentParser();ap.add_argument('--output',required=True);a=ap.parse_args();out=R/a.output
 if out.exists():raise RuntimeError('Use new audit directory; evidence immutable')
 out.mkdir();db=sqlite3.connect('file:'+str(R/'manifest.sqlite')+'?mode=ro',uri=True)
 total=db.execute('select count(*) from images').fetchone()[0]
 rows=db.execute('select metadata,sha256,width,height,bytes from images where sha256 is not null order by id').fetchall();db.close()
 snapshot_sha=hashlib.sha256(json.dumps(rows,ensure_ascii=False,separators=(',',':')).encode()).hexdigest()
 start=time.time();bad=[];done=0
 def progress(phase):
  v={'phase':phase,'audited':done,'snapshot_downloaded':len(rows),'total_eligible':total,'download_complete_at_snapshot':len(rows)==total,'snapshot_sha256':snapshot_sha,'errors':bad,'elapsed_seconds':time.time()-start,'eligible_for_training':phase=='complete' and len(rows)==total and not bad}
  tmp=out/'status.tmp';tmp.write_text(json.dumps(v,indent=2),encoding='utf8');tmp.replace(out/'status.json');return v
 progress('auditing')
 with (out/'verified.jsonl').open('w',encoding='utf8') as log,concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
  for r in pool.map(check,rows):
   done+=1;log.write(json.dumps(r)+'\n')
   if not r['ok']:bad.append(r)
   if done%1000==0:log.flush();progress('auditing');print(done,flush=True)
 print(json.dumps(progress('complete')),flush=True)
if __name__=='__main__':main()
