"""Isolated, resumable Scryfall reference acquisition. No application access."""
import argparse, gzip, hashlib, json, msvcrt, re, shutil, sqlite3, time
from pathlib import Path
from urllib.parse import urlparse
import requests
from PIL import Image

ROOT = Path(__file__).resolve().parent
SESSION = requests.Session()
SESSION.headers.update({'User-Agent': 'MtgIdentityResearch/0.1 (personal research dataset)', 'Accept': 'application/json;q=0.9,*/*;q=0.8'})

def fetch(url, target):
    host = urlparse(url).hostname or ''
    if urlparse(url).scheme != 'https' or not (host == 'api.scryfall.com' or host.endswith('.scryfall.io')):
        raise ValueError('Unexpected source host')
    for attempt in range(4):
        time.sleep(.25)
        try:
            with SESSION.get(url, timeout=(20, 90), stream=True) as r:
                # Stop rather than power through rate limiting or access denial.
                if r.status_code in (403, 429):
                    raise RuntimeError(f'Server requests stop: {r.status_code}')
                r.raise_for_status()
                part = target.with_suffix(target.suffix + '.part')
                with part.open('wb') as f:
                    for chunk in r.iter_content(262144):
                        f.write(chunk)
                part.replace(target)
                return
        except requests.RequestException:
            if attempt == 3:
                raise
            time.sleep(2 ** (attempt + 1))

def faces(card):
    if card.get('lang') != 'en' or 'paper' not in card.get('games', []) or card.get('oversized'):
        return
    if card.get('layout') in ('token', 'double_faced_token', 'emblem', 'art_series'):
        return
    if card.get('image_status') not in ('highres_scan', 'lowres'):
        return
    code, uid = card['set'], card['id']
    if not re.fullmatch(r'[a-z0-9]+', code) or not re.fullmatch(r'[a-f0-9-]{36}', uid):
        raise ValueError('Unsafe identifier')
    items = [card] if card.get('image_uris') else card.get('card_faces', [])
    for i, face in enumerate(items):
        url = face.get('image_uris', {}).get('large')
        if not url:
            continue
        yield {'id': f'{uid}_f{i}', 'scryfall_id': uid, 'face': i,
               'oracle_id': card.get('oracle_id'), 'illustration_id': face.get('illustration_id'),
               'name': face.get('name', card['name']), 'set': code, 'set_name': card['set_name'],
               'collector_number': card['collector_number'], 'lang': card['lang'],
               'layout': card['layout'], 'full_art': card.get('full_art', False),
               'image_status': card['image_status'], 'url': url,
               'path': f'images/{code}/{uid}_f{i}.jpg'}

def connect():
    db = sqlite3.connect(ROOT / 'manifest.sqlite')
    db.execute('CREATE TABLE IF NOT EXISTS images (id TEXT PRIMARY KEY, set_code TEXT, metadata TEXT, sha256 TEXT, bytes INTEGER, width INTEGER, height INTEGER)')
    return db

def prepare(db):
    meta = ROOT / 'bulk-metadata.json'
    if not meta.exists():
        listing = ROOT / 'bulk-list.json'
        fetch('https://api.scryfall.com/bulk-data', listing)
        entry = next(x for x in json.loads(listing.read_text())['data'] if x['type'] == 'default_cards')
        meta.write_text(json.dumps(entry, indent=2), encoding='utf-8')
    entry = json.loads(meta.read_text())
    bulk = ROOT / 'default-cards.jsonl.gz'
    if not bulk.exists():
        fetch(entry['jsonl_download_uri'], bulk)
    handles = {}
    try:
        with gzip.open(bulk, 'rt', encoding='utf-8') as f:
            for line in f:
                card = json.loads(line)
                for item in faces(card):
                    code = item['set']
                    if code not in handles:
                        folder = ROOT / 'images' / code
                        folder.mkdir(parents=True, exist_ok=True)
                        handles[code] = (folder / 'manifest.jsonl').open('w', encoding='utf-8')
                    encoded = json.dumps(item, ensure_ascii=False)
                    handles[code].write(encoded + '\n')
                    db.execute('INSERT OR IGNORE INTO images(id,set_code,metadata) VALUES (?,?,?)', (item['id'], code, encoded))
        db.commit()
    finally:
        for f in handles.values():
            f.close()
    (ROOT / 'prepared.json').write_text(json.dumps({'bulk_sha256': hashlib.sha256(bulk.read_bytes()).hexdigest(), 'updated_at': entry['updated_at']}))

def status(db):
    total, done, size = db.execute('SELECT count(*),count(sha256),coalesce(sum(bytes),0) FROM images').fetchone()
    value = {'total_faces': total, 'downloaded': done, 'bytes': size, 'time': time.strftime('%Y-%m-%d %H:%M:%S')}
    print(json.dumps(value), flush=True)
    (ROOT / 'status.tmp').write_text(json.dumps(value, indent=2))
    (ROOT / 'status.tmp').replace(ROOT / 'status.json')

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('command', choices=['run', 'status'])
    parser.add_argument('--max-images', type=int, default=10000)
    args = parser.parse_args()
    db = connect()
    if args.command == 'status':
        status(db)
        return
    with (ROOT / 'download.lock').open('a+b') as lock:
        lock.seek(0); lock.write(b'0'); lock.flush(); lock.seek(0)
        msvcrt.locking(lock.fileno(), msvcrt.LK_NBLCK, 1)
        if not (ROOT / 'prepared.json').exists():
            prepare(db)
        status(db)
        rows = db.execute("SELECT id,metadata FROM images WHERE sha256 IS NULL ORDER BY CASE set_code WHEN 'spg' THEN 0 WHEN 'sld' THEN 1 WHEN 'fin' THEN 2 WHEN 'lea' THEN 3 WHEN 'm21' THEN 4 ELSE 5 END, set_code,id").fetchall()
        downloaded = 0
        for uid, encoded in rows:
            if (ROOT / 'STOP').exists() or downloaded >= args.max_images:
                break
            used = db.execute('SELECT coalesce(sum(bytes),0) FROM images').fetchone()[0]
            if shutil.disk_usage(ROOT).free < 100 * 1024**3 or used > 80 * 1024**3:
                print('Paused: disk reserve/quota reached', flush=True); break
            item = json.loads(encoded)
            target = ROOT / item['path']
            if not target.exists():
                fetch(item['url'], target)
            try:
                with Image.open(target) as image:
                    image.verify()
                with Image.open(target) as image:
                    w, h = image.size
                    if min(w, h) < 100:
                        raise ValueError('Image too small')
            except Exception:
                # Quarantine corrupt responses so resume can fetch a fresh file.
                target.replace(target.with_suffix('.invalid'))
                raise
            data = target.read_bytes()
            db.execute('UPDATE images SET sha256=?,bytes=?,width=?,height=? WHERE id=?', (hashlib.sha256(data).hexdigest(), len(data), w, h, uid))
            db.commit()
            downloaded += 1
            if downloaded % 20 == 0:
                status(db)
        status(db)

if __name__ == '__main__':
    main()
