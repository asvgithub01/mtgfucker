"""Read-only intake audit. Creates a review queue, NEVER training labels or splits."""
import argparse, hashlib, html, io, json, tarfile
from pathlib import Path
from PIL import Image

def main():
    p=argparse.ArgumentParser();p.add_argument('--intake',required=True);p.add_argument('--unity',required=True);p.add_argument('--out',required=True)
    a=p.parse_args();src=Path(a.intake);u=Path(a.unity);out=Path(a.out);out.mkdir(parents=True,exist_ok=False)
    reviewed=json.loads((u/'reports/real-review-expanded-01/manifest.json').read_text())
    reserved={r['sha256'] for r in reviewed}
    recent=json.loads((u/'reports/backbone-real-01/result.json').read_text())
    reserved.update(r['imageSha'] for r in recent['records'])
    inventory=json.loads((src/'verified-inventory.json').read_text()); records=[]; duplicates={}
    with tarfile.open(src/'evidence.tar') as tar:
        for i,r in enumerate(inventory):
            member=r['path']+'/frame.png';data=tar.extractfile(tar.getmember(member)).read();sha=hashlib.sha256(data).hexdigest()
            with Image.open(io.BytesIO(data)) as im:
                im.load();size=list(im.size);thumb=im.convert('RGB');thumb.thumbnail((180,240));thumb.save(out/f'{i:04}.jpg',quality=85)
            duplicates.setdefault(sha,[]).append(i)
            records.append(dict(index=i,sourceTar=str(src/'evidence.tar'),member=member,sha256=sha,
                session=r['session'],created=r['created'],size=size,thumbnail=f'{i:04}.jpg',
                excludedFromTraining=sha in reserved,exclusionReason='already consulted development frame' if sha in reserved else None,
                trainingEligible=False,reviewStatus='unreviewed',physicalCardGroup=None,corners=None,cornerVisible=None,
                split=None,needsReservedIdentityAudit=True))
    groups=[v for v in duplicates.values() if len(v)>1]
    summary=dict(frames=len(records),sessions=len({r['session'] for r in records}),exactDuplicateGroups=groups,
        excludedDevelopmentFrames=sum(r['excludedFromTraining'] for r in records),trainingEligible=0,
        policy='All records locked. Group same physical card/art across sessions before split. Exact hashes do not establish identity. Review all corners/visibility independently; never convert detector or identifier predictions into GT. Audit against 23 heldout and historical reserved groups before eligibility. No automatic training importer.')
    (out/'queue.json').write_text(json.dumps(dict(summary=summary,records=records),indent=2))
    cards=[]
    for r in records:
        caption=f'{r["index"]:04} · session {r["session"][:8]} · '+('DEVELOPMENT EXCLUDED' if r['excludedFromTraining'] else 'UNREVIEWED / LOCKED')
        cards.append('<figure><img loading="lazy" src="'+r['thumbnail']+'"><figcaption>'+html.escape(caption)+'</figcaption></figure>')
    (out/'review.html').write_text('<meta charset="utf-8"><style>body{font:16px system-ui;margin:25px}section{display:flex;flex-wrap:wrap}figure{width:190px;margin:8px}figcaption{font-size:12px}</style><h1>Revisión real de Esquinitas — sin entrenamiento</h1><p>'+html.escape(summary['policy'])+'</p><p>No son etiquetas ni una división train/test. Los fotogramas consultados y sus grupos deben quedar fuera del entrenamiento.</p><section>'+''.join(cards)+'</section>',encoding='utf8')
    print(json.dumps(summary))

if __name__=='__main__':main()
