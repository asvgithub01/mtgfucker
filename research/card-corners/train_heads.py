"""Controlled synthetic head ablation. Original implementation, not Han's trainer."""
import argparse, hashlib, html, json, random, time
from pathlib import Path
import cv2
import numpy as np
from PIL import Image, ImageDraw
import torch
from torch import nn
from train_pilot import CornerNet
from validate import validate

BINS = 256

class HeadNet(nn.Module):
    def __init__(self, head, pool=4):
        super().__init__()
        # Exactly the pilot feature extractor, including its shared spatial representation.
        baseline = CornerNet()
        self.features = baseline.net[:-1]
        if pool != 4:
            # Retain a denser spatial grid, without changing convolutional backbone.
            self.features[8] = nn.AdaptiveAvgPool2d((pool, pool))
            self.features[10] = nn.Linear(96 * pool * pool, 128)
        self.presence = nn.Linear(128, 1)
        self.coordinates = nn.Linear(128, 8 if head == 'regression' else 8 * BINS)
        self.head = head

    def forward(self, x):
        z = self.features(x)
        p = self.presence(z).flatten()
        q = self.coordinates(z)
        return p, q.sigmoid() if self.head == 'regression' else q.reshape(-1, 8, BINS)


def decode(q):
    return q if q.ndim == 2 else q.argmax(-1).float() / (BINS - 1)


def coordinate_loss(q, target):
    if q.ndim == 2:
        return 5 * nn.functional.smooth_l1_loss(q, target)
    axis = torch.arange(BINS, device=q.device)
    # Experimental Gaussian targets: sigma=2 bins, not a claim about Han's recipe.
    soft = (-0.5 * ((axis - target[..., None] * (BINS - 1)) / 2) ** 2).softmax(-1)
    return -(soft * q.log_softmax(-1)).sum(-1).mean()


def geometry(q):
    q = np.asarray(q, np.float32).reshape(4, 2)
    edges = np.roll(q, -1, axis=0) - q
    following = np.roll(edges, -1, axis=0)
    cross = edges[:, 0] * following[:, 1] - edges[:, 1] * following[:, 0]
    return bool(np.all(cross > 1e-6) and cv2.contourArea(q) > 1e-4)


def quad_iou(pred, gt):
    if not geometry(pred):
        return 0.0
    p, g = np.asarray(pred, np.float32).reshape(4, 2), np.asarray(gt, np.float32).reshape(4, 2)
    inter = cv2.intersectConvexConvex(p, g)[0]
    return float(inter / max(1e-9, cv2.contourArea(p) + cv2.contourArea(g) - inter))


def selftest():
    box = [0, 0, 1, 0, 1, 1, 0, 1]
    assert geometry(box) and abs(quad_iou(box, box) - 1) < 1e-6
    assert not geometry([0.5] * 8)
    assert not geometry([0, 0, 1, 1, 1, 0, 0, 1])
    q = torch.zeros(1, 8, BINS); q[..., 30] = 10; q[..., 220] = 9
    assert torch.allclose(decode(q), torch.full((1, 8), 30 / 255))
    target = torch.full((1, 8), .4)
    logits = torch.randn(1, 8, BINS, requires_grad=True)
    coordinate_loss(logits, target).backward()
    assert torch.isfinite(logits.grad).all()
    for pool in (4,8):
        for head in ('regression','simcc'):
            model = HeadNet(head, pool)
            p, q = model(torch.zeros(2,3,224,160))
            assert p.shape == (2,) and decode(q).shape == (2,8)


def evaluate(model, indices, xs, ps, cs, rows):
    model.eval(); records = []
    with torch.no_grad():
        for start in range(0, len(indices), 32):
            ids = indices[start:start + 32]
            score, raw = model(xs[ids]); pred = decode(raw).cpu().numpy()
            scores = score.sigmoid().cpu().tolist()
            for i, q, s in zip(ids, pred, scores):
                r = rows[i]; rec = {'image': r['image'], 'present': r['present'], 'score': s, 'corners': q.tolist()}
                if r['present']:
                    gt = cs[i].cpu().numpy()
                    rec.update(error=np.linalg.norm(q.reshape(4, 2) - gt.reshape(4, 2), axis=1).tolist(),
                               visible=r['cornerVisible'], iou=quad_iou(q, gt), invalid=not geometry(q))
                records.append(rec)
    positive = [r for r in records if r['present']]; negative = [r for r in records if not r['present']]
    mean = lambda v: float(np.mean(v)) if v else None
    ious = [r['iou'] for r in positive]
    metrics = {'frames': len(records), 'positiveFrames': len(positive), 'meanCornerError': mean([np.mean(r['error']) for r in positive]),
               'meanIoU': mean(ious), 'p10IoU': float(np.percentile(ious, 10)),
               'badIoUBelow05': sum(x < .5 for x in ious), 'invalidQuads': sum(r['invalid'] for r in positive),
               'recallAt05': mean([r['score'] >= .5 for r in positive]),
               'falsePositiveRateAt05': mean([r['score'] >= .5 for r in negative])}
    for label, flag in [('visible', True), ('hidden', False)]:
        metrics[label + 'Error'] = mean([e for r in positive for e, v in zip(r['error'], r['visible']) if v == flag])
    return metrics, records


def main():
    p = argparse.ArgumentParser(); p.add_argument('--dataset'); p.add_argument('--out'); p.add_argument('--selftest', action='store_true')
    p.add_argument('--steps', type=int, default=1200); p.add_argument('--pool', type=int, choices=(4,8), default=4)
    p.add_argument('--seed', type=int, default=20261029); a = p.parse_args(); selftest()
    if a.selftest:
        print('Self-tests PASS'); return
    assert 1 <= a.steps <= 3000 and torch.cuda.is_available()
    root = Path(a.dataset); rows = validate(root); out = Path(a.out); out.mkdir(parents=True, exist_ok=False)
    torch.set_num_threads(4)
    config = vars(a) | {'seed': a.seed, 'input': [3, 224, 160], 'bins': BINS, 'sigmaBins': 2,
                       'batch': 16, 'lr': 3e-4, 'overfitSteps': 600, 'maxSecondsPerRun': 600,
                       'device': torch.cuda.get_device_name(), 'torch': torch.__version__,
                       'sourceSha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                       'importHashes': {name: hashlib.sha256(Path(__file__).with_name(name).read_bytes()).hexdigest() for name in ('train_pilot.py', 'validate.py')},
                       'teachersUsed': False, 'promoted': False, 'purpose': 'Synthetic ablation, not exact reproduction of Han architecture'}
    (out/'config.json').write_text(json.dumps(config, indent=2))
    (out/'dataset-hashes.json').write_text(json.dumps({r['image']: r['imageSha256'] for r in rows}, indent=2))
    arrays = []
    for r in rows:
        with Image.open(root/r['image']) as im:
            arrays.append(np.asarray(im.convert('RGB').resize((160, 224), Image.Resampling.BILINEAR)).transpose(2, 0, 1).copy())
    xs = torch.from_numpy(np.stack(arrays)).cuda().float() / 255
    ps = torch.tensor([float(r['present']) for r in rows], device='cuda')
    cs = torch.tensor([[v for pt in r['corners'] for v in (pt['x'], pt['y'])] if r['present'] else [0.]*8 for r in rows], device='cuda')
    train = [i for i, r in enumerate(rows) if r['split'] == 'train']
    val = [i for i, r in enumerate(rows) if r['split'] == 'validation']
    small = [i for i in train if rows[i]['present']][:16]
    (out/'split-indices.json').write_text(json.dumps({'train':train,'validation':val,'overfit':small}))
    results = {}; all_records = {}
    for stage in ('overfit', 'generalization'):
        for head in ('regression', 'simcc'):
            name = stage + '-' + head; folder = out/name; folder.mkdir()
            random.seed(config['seed']); torch.manual_seed(config['seed']); torch.cuda.manual_seed_all(config['seed'])
            model = HeadNet(head, pool=a.pool).cuda(); opt = torch.optim.AdamW(model.parameters(), lr=config['lr'])
            ids = small if stage == 'overfit' else train
            steps = config['overfitSteps'] if stage == 'overfit' else a.steps
            torch.cuda.synchronize(); start = time.perf_counter(); model.train()
            completed = 0
            with (folder/'metrics.jsonl').open('x') as log:
                for step in range(steps):
                    if time.perf_counter() - start > config['maxSecondsPerRun']: break
                    ix = random.choices(ids, k=16); score, q = model(xs[ix]); mask = ps[ix] > 0
                    loss = nn.functional.binary_cross_entropy_with_logits(score, ps[ix])
                    if mask.any(): loss = loss + coordinate_loss(q[mask], cs[ix][mask])
                    assert torch.isfinite(loss); opt.zero_grad(); loss.backward(); opt.step(); completed = step + 1
                    log.write(json.dumps({'step':step+1, 'loss':loss.item()})+'\n'); log.flush()
                    if (step+1) % 300 == 0: torch.save(model.state_dict(), folder/f'step-{step+1:04}.pt')
            torch.cuda.synchronize(); seconds = time.perf_counter() - start
            torch.save(model.state_dict(), folder/'final.pt')
            metrics, records = evaluate(model, small if stage == 'overfit' else val, xs, ps, cs, rows)
            metrics.update(steps=completed, trainingSeconds=seconds, parameters=sum(v.numel() for v in model.parameters()))
            results[name] = metrics; all_records[name] = records
            (folder/'predictions.json').write_text(json.dumps(records)); (folder/'result.json').write_text(json.dumps(metrics, indent=2))
            print(name, json.dumps(metrics), flush=True)
    (out/'result.json').write_text(json.dumps(results, indent=2))
    chosen = []; seen = set()
    for i in val:
        r = rows[i]
        if r['present'] and r['group'] not in seen:
            chosen.append(i); seen.add(r['group'])
    figures = []
    for i in chosen:
        r = rows[i]
        with Image.open(root/r['image']) as source: im = source.convert('RGB')
        draw = ImageDraw.Draw(im)
        lines = [('lime', [v for pt in r['corners'] for v in (pt['x'],pt['y'])])]
        for head, color in [('regression', 'red'), ('simcc', 'cyan')]:
            rec = next(v for v in all_records['generalization-'+head] if v['image']==r['image'])
            lines.append((color, rec['corners']))
        for color, values in lines:
            pts = [(x*im.width, y*im.height) for x,y in np.asarray(values).reshape(4,2)]
            draw.line(pts+[pts[0]], fill=color, width=4)
        im.thumbnail((320,448)); im.save(out/r['image'])
        figures.append(f'<figure><img src="{r["image"]}"><figcaption>{r["image"]}</figcaption></figure>')
    (out/'report.html').write_text('<meta charset="utf-8"><title>Esquinitas SimCC</title><style>body{font:16px system-ui;margin:30px}section{display:flex;flex-wrap:wrap}figure{margin:8px}</style><h1>Esquinitas: regresión vs SimCC</h1><p>Solo sintético. Verde GT, rojo regresión, cian SimCC. Misma CNN, split y pasos; distinta cabeza y función de pérdida. No es réplica de Cornelius/FastWeb. Sin promoción ni prueba Sony. La prueba de memorización NO mide generalización.</p><pre>'+html.escape(json.dumps(results,indent=2))+'</pre><section>'+''.join(figures)+'</section>', encoding='utf8')

if __name__ == '__main__': main()
