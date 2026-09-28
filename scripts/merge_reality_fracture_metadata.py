#!/usr/bin/env python3
"""Merge FRA into the previous snapshot without touching the original downloaded source."""
import gzip,hashlib,json,sys
from pathlib import Path
import ijson
ROOT=Path(__file__).resolve().parents[1];WORK=ROOT/'build/reality-fracture-indices'
OLD=ROOT.parent/'mtgfucker-art-hash-data'
source=WORK/'AllPrintings-with-FRA.json.gz'
if not source.exists():
    with gzip.open(source.with_suffix('.part'),'wt',encoding='utf8',compresslevel=1) as out:
        out.write('{"data":{');first=True
        with gzip.open(OLD/'AllPrintings.json.gz','rb') as stream:
            for code,data in ijson.kvitems(stream,'data',use_float=True):
                if code=='FRA':continue
                if not first:out.write(',')
                first=False;out.write(json.dumps(code)+':'+json.dumps(data,separators=(',',':')))
        out.write(',"FRA":'+json.dumps(json.loads((WORK/'FRA.json').read_text(encoding='utf8'))['data'],separators=(',',':'))+'}}')
    source.with_suffix('.part').replace(source)
    print('Merged metadata snapshot',flush=True)
rows=[json.loads(line) for line in (OLD/'hashes.jsonl').read_text(encoding='utf8').splitlines()]
known={r['illustration_id'] for r in rows}
for line in (WORK/'fra-hashes.jsonl').read_text(encoding='utf8').splitlines():
    row=json.loads(line)
    if row['illustration_id'] not in known:rows.append(row);known.add(row['illustration_id'])
hashes=WORK/'all-hashes-with-FRA.jsonl'
hashes.write_text(''.join(json.dumps(r,ensure_ascii=False)+'\n' for r in rows),encoding='utf8')
assets=WORK/'hash-assets';assets.mkdir(exist_ok=True)
from build_art_hash_asset import build as build_hash
from build_art_printing_asset import build as build_printings
from build_printing_artist_asset import build as build_artists
from build_title_language_asset import build as build_languages
build_hash(hashes,assets/'art_hash_index.bin')
build_printings(source,hashes,assets/'art_printing_index.bin')
build_artists(source,assets/'art_printing_index.bin',assets/'printing_artist_index.bin')
build_languages(source,assets/'title_language_index.bin')
report={'scope':'Previous snapshot plus FRA only','mtgjsonFraDate':json.loads((WORK/'FRA.json').read_text(encoding='utf8'))['meta']['date'],'originalSourceSha256':hashlib.file_digest((OLD/'AllPrintings.json.gz').open('rb'),'sha256').hexdigest(),'files':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in assets.glob('*.bin')}}
(assets/'fra-index-update.provenance.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report),flush=True)
