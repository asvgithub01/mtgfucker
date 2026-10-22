#!/usr/bin/env python3
"""Build a reproducible FRA supplement. Cached source images stay outside the APK."""
import json, time, hashlib, sys, urllib.request
from pathlib import Path
import numpy as np
from PIL import Image
import onnxruntime as ort
sys.path.insert(0,str(Path(__file__).resolve().parent))
from art_hash_poc import hashes, load_gray

ROOT=Path(__file__).resolve().parents[1]
WORK=ROOT/'build/reality-fracture-indices'
WORK.mkdir(parents=True,exist_ok=True)
HEADERS={'User-Agent':'MtgScannerIndexUpdater/1.0','Accept':'application/json'}
def download(url,path):
    if path.exists():return path.read_bytes()
    for attempt in range(4):
        try:
            data=urllib.request.urlopen(urllib.request.Request(url,headers=HEADERS),timeout=60).read()
            tmp=path.with_suffix(path.suffix+'.part');tmp.write_bytes(data);tmp.replace(path)
            time.sleep(.12)
            return data
        except Exception:
            if attempt==3:raise
            time.sleep(2**attempt)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
model=ROOT/'build/collectorvision-native-assets/milo.onnx'
assert sha(model)=='bd13d8d60383c69da04dce261f32e93fdaeaa8fd618fbc991e7385f71b3d45df'
base_ids=set(json.loads((ROOT/'build/collectorvision-native-assets/scryfall-mtg-card-ids.json').read_text()))
setinfo=json.loads(download('https://api.scryfall.com/sets/fra',WORK/'set.json'))
mtgjson=json.loads(download('https://mtgjson.com/api/v5/FRA.json',WORK/'FRA.json'))
url=setinfo['search_uri'];cards=[];page=0
while url:
    page+=1;data=json.loads(download(url,WORK/f'page-{page}.json'))
    cards.extend(data['data']);url=data.get('next_page')
assert len(cards)==setinfo['card_count'],(len(cards),setinfo['card_count'])
options=ort.SessionOptions();options.intra_op_num_threads=4
session=ort.InferenceSession(str(model),sess_options=options,providers=['CPUExecutionProvider'])
mean=np.array([.485,.456,.406],dtype=np.float32);std=np.array([.229,.224,.225],dtype=np.float32)
ids=[];vectors=[];artrows=[];seen_art=set();skipped=[]
for index,card in enumerate(cards):
    if 'paper' not in card.get('games',[]):continue
    # Existing engine maps one vector to a physical front printing; do not add unsupported back identities.
    faces=card.get('card_faces') if not card.get('image_uris') else [card]
    face=faces[0]
    images=face.get('image_uris',{})
    if not images.get('normal') or not images.get('art_crop'):
        skipped.append(card['id']);continue
    photo=WORK/(card['id']+'.jpg');download(images['normal'],photo)
    if card['id'] not in base_ids:
        cache=WORK/(card['id']+'.npy')
        if cache.exists():v=np.load(cache)
        else:
            x=np.asarray(Image.open(photo).convert('RGB').resize((448,448),Image.Resampling.BILINEAR),dtype=np.float32)/255
            x=((x-mean)/std).transpose(2,0,1)[None].copy()
            v=session.run(['embedding'],{session.get_inputs()[0].name:x})[0].reshape(-1)
            v=v/np.linalg.norm(v);np.save(cache,v)
        assert v.shape==(128,) and np.isfinite(v).all()
        ids.append(card['id']);vectors.append(v)
    for fi,face in enumerate(faces):
        illustration=face.get('illustration_id',card.get('illustration_id',''))
        if not illustration or illustration in seen_art:continue
        art=face.get('image_uris',{}).get('art_crop')
        if not art:continue
        crop=WORK/(illustration+'-art.jpg');download(art,crop)
        ph,dh=hashes(load_gray(crop));seen_art.add(illustration)
        artrows.append(dict(key=card['id']+(f'-{fi}' if len(faces)>1 else ''),name=card['name'],set='fra',collector_number=card['collector_number'],illustration_id=illustration,phash=f'{ph:016x}',dhash=f'{dh:016x}'))
    if index%25==0:print(f'{index+1}/{len(cards)} cards',flush=True)
assert not skipped,skipped
out=ROOT/'cameraVision/src/main/assets/edscan-fra';out.mkdir(parents=True,exist_ok=True)
binfile=out/'fra-20260928-embeddings.f16.bin';idfile=out/'fra-20260928-card-ids.json'
np.asarray(vectors,dtype='<f2').tofile(binfile)
idfile.write_text(json.dumps(ids,separators=(',',':')),encoding='utf8')
assert len(ids)==len(set(ids)) and not(base_ids & set(ids))
(WORK/'fra-hashes.jsonl').write_text(''.join(json.dumps(r,ensure_ascii=False)+'\n' for r in artrows),encoding='utf8')
report=dict(set='FRA',source='Scryfall + MTGJSON',mtgjsonDate=mtgjson['meta']['date'],sourceCards=len(cards),addedRows=len(ids),uniqueArtworks=len(artrows),modelSha256=sha(model),files={p.name:dict(bytes=p.stat().st_size,sha256=sha(p)) for p in [binfile,idfile]},excluded=skipped)
(out/'provenance.json').write_text(json.dumps(report,indent=2),encoding='utf8');print(json.dumps(report),flush=True)
