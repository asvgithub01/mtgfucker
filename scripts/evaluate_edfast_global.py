"""Read-only real-photo evaluation against complete Fast indexes, never a collection writer.

Global reference competitors do not make the selected stress-photo corpus representative.
Outputs per-photo evidence, not mobile timings or two-frame acceptance claims.
"""
import argparse
import hashlib
import json
from pathlib import Path
import numpy as np
from PIL import Image
from evaluate_edfast_regions import MODEL_SHA, REGIONS, region, rectify, lane_summary, shadow_choice


def evaluate(args):
    import onnxruntime as ort
    assert hashlib.sha256(args.model.read_bytes()).hexdigest() == MODEL_SHA
    manifest = json.loads((args.index / 'manifest.json').read_text(encoding='utf8'))
    assert manifest['complete'] and manifest['modelSha256'] == MODEL_SHA
    for name, spec in manifest['files'].items():
        data = (args.index / name).read_bytes()
        assert len(data) == spec['bytes'] and hashlib.sha256(data).hexdigest() == spec['sha256']
    rows = json.loads((args.index / 'rows.json').read_text(encoding='utf8'))
    refs = [dict(id=r[0], identity=r[1]) for r in rows]
    full_mask = np.array([r[4] for r in rows], bool)
    full = np.concatenate([np.fromfile(p, '<f2').reshape(-1, 128) for p in args.full]).astype(np.float32)
    assert full.shape == (len(rows), 128)
    vectors = {'full': full}
    for profile in ('upper', 'art'):
        vectors[profile] = np.fromfile(args.index / (profile + '.f16.bin'), '<f2').reshape(-1, 128).astype(np.float32)
        assert not vectors[profile][full_mask].any(), 'Full-layout placeholders must never be searched'
    corpus = json.loads(args.corpus.read_text(encoding='utf8'))
    opts = ort.SessionOptions(); opts.intra_op_num_threads = 2; opts.inter_op_num_threads = 1
    session = ort.InferenceSession(str(args.model), sess_options=opts, providers=['CPUExecutionProvider'])
    means = np.array([.485, .456, .406], np.float32); std = np.array([.229, .224, .225], np.float32)
    def embed(image, profile):
        pixels = np.asarray(region(image, profile).resize((448,448), Image.Resampling.BILINEAR), np.float32)/255
        tensor = ((pixels-means)/std).transpose(2,0,1)[None].copy()
        v = session.run(['embedding'], {'image': tensor})[0].reshape(-1)
        return v / np.linalg.norm(v)
    result = []
    for query in corpus['queries']:
        image = rectify(Image.open(args.corpus.parent/query['path']).convert('RGB'), query['corners'])
        scores = {}
        for profile in REGIONS:
            scores[profile] = np.maximum(vectors[profile] @ embed(image,profile), vectors[profile] @ embed(image.rotate(180),profile))
        summaries = {'full': lane_summary(refs,scores['full'])}
        indices = np.flatnonzero(full_mask)
        full_lane = lane_summary([refs[i] for i in indices],scores['full'][indices])
        for profile in ('upper','art'):
            indices = np.flatnonzero(~full_mask)
            cropped = lane_summary([refs[i] for i in indices],scores[profile][indices])
            lanes = {'full':full_lane,profile:cropped}
            summaries[profile] = dict(lanes=lanes,**shadow_choice(lanes))
        old_indices = np.argsort(-scores['full'])[:8]
        summaries['oldTop8'] = lane_summary([refs[i] for i in old_indices],scores['full'][old_indices])
        result.append(dict(query=query,profiles=summaries))
        args.output.write_text(json.dumps(dict(rows=len(rows), results=result),ensure_ascii=False,indent=2),encoding='utf8')
        print(query['path'], summaries['full']['identity'], summaries['upper']['candidate'],summaries['art']['candidate'],flush=True)


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--model',type=Path,required=True);p.add_argument('--index',type=Path,required=True)
    p.add_argument('--full',type=Path,nargs='+',required=True,help='Base, FRA, collector matrices in exact row order')
    p.add_argument('--corpus',type=Path,required=True);p.add_argument('--output',type=Path,required=True)
    evaluate(p.parse_args())
