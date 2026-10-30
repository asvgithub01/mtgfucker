"""Conservative session quarantine before real corner training. No labels inferred."""
import argparse, json
from collections import Counter
from pathlib import Path

def audit(queue, development):
    records=queue['records']
    used={r['sha256'] for r in development}
    reserved_sessions={r['session'] for r in records if r['sha256'] in used}
    counts=Counter(r['session'] for r in records)
    rows=[]
    for r in records:
        reasons=[]
        if r['sha256'] in used: reasons.append('previously consulted development frame')
        if r['session'] in reserved_sessions: reasons.append('same session as development evidence')
        reasons.extend(['independent corner review pending','reserved identity/art overlap audit pending'])
        rows.append(dict(index=r['index'],sha256=r['sha256'],session=r['session'],
            trainingEligible=False,sessionQuarantined=r['session'] in reserved_sessions,reasons=reasons))
    return dict(policy='Conservative session-disjoint policy. This does not identify cards from predictions. All original records remain unchanged.',
        total=len(rows),trainingEligible=0,sessionCounts=dict(counts),reservedSessions=sorted(reserved_sessions),
        sessionQuarantined=sum(r['sessionQuarantined'] for r in rows),records=rows,
        next='Use these sessions for reviewed development/evaluation. New training sessions must avoid reserved card/art groups; review identities and corners before any eligibility. Do not silently relabel existing development data as training.')

def main():
    p=argparse.ArgumentParser();p.add_argument('--queue',required=True);p.add_argument('--development',required=True);p.add_argument('--out',required=True);a=p.parse_args()
    out=Path(a.out);assert not out.exists()
    result=audit(json.loads(Path(a.queue).read_text()),json.loads(Path(a.development).read_text()))
    out.write_text(json.dumps(result,indent=2));print(json.dumps({k:v for k,v in result.items() if k!='records'}))

if __name__=='__main__':main()
