"""Manual read-only Scryfall set check; no scheduling, downloads or publication."""
import argparse, json, urllib.request
from pathlib import Path

def compare(previous, current):
    fields=('name','released_at','card_count','set_type')
    old={x['code']:x for x in previous};new={x['code']:x for x in current}
    return {'added':[new[k] for k in sorted(new.keys()-old.keys())],
            'changed':[{'before':old[k],'after':new[k]} for k in sorted(new.keys()&old.keys()) if any(old[k].get(f)!=new[k].get(f) for f in fields)],
            'removedCodes':sorted(old.keys()-new.keys()),
            'warning':'Set metadata is not a complete card/image delta; compare bulk cards separately. No automatic promotion.'}

def main():
    p=argparse.ArgumentParser();p.add_argument('--previous',required=True);p.add_argument('--current',help='Offline /sets JSON snapshot; otherwise fetch one request');p.add_argument('--out',required=True);a=p.parse_args()
    out=Path(a.out)
    if out.exists():raise FileExistsError(out)
    previous=json.loads(Path(a.previous).read_text(encoding='utf-8-sig'))
    if a.current:current=json.loads(Path(a.current).read_text(encoding='utf-8-sig'))
    else:
        req=urllib.request.Request('https://api.scryfall.com/sets',headers={'User-Agent':'MtgCatalogResearch/0.1','Accept':'application/json'})
        with urllib.request.urlopen(req,timeout=30) as r:current=json.load(r)
    def rows(x):return x['data'] if isinstance(x,dict) else x
    report=compare(rows(previous),rows(current));out.mkdir(parents=True)
    (out/'snapshot.json').write_text(json.dumps(current,indent=2),encoding='utf8')
    (out/'diff.json').write_text(json.dumps(report,indent=2),encoding='utf8')
    print(json.dumps({k:len(v) for k,v in report.items() if isinstance(v,list)}))

if __name__=='__main__':main()
