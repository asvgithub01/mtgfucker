"""Offline stage timings from private Triple traces; no OCR text, photos or inventory required."""
import argparse
from collections import Counter, defaultdict
import json
from pathlib import Path
import re


STAGES = {
    "triple.confirmation": ("frameMs", "detectMs", "featureMs", "searchMs", "preprocessAndQueueMs"),
    "triple.ocrComplete": ("elapsedMs",),
    "triple.ocrLane": ("totalMs", "captureAgeMs", "modelsMs", "namesMs", "languageMs"),
    "triple.titleResolved": ("ms", "captureAgeMs"),
    "triple.save": ("ms",),
    "triple.lookup": ("ms",),
    "triple.sessionAppend": ("ms",),
    "triple.ocrQuick": ("ms",),
    "triple.lane": ("queueAndDecodeMs",),
}


def stats(values):
    values = sorted(values)
    return {"n": len(values), "p50": values[round((len(values)-1)*.5)] if values else None,
            "p95": values[round((len(values)-1)*.95)] if values else None,
            "max": max(values, default=None)}


def summarize(lines, start_ms=None, end_ms=None):
    stages, models = defaultdict(list), defaultdict(list)
    reasons, lanes, thermal, sources = Counter(), Counter(), Counter(), Counter()
    methods, languages = [], []
    late_titles = title_count = expired = completed_lanes = 0
    drop_max = 0
    heap = []
    first = last = None
    for line in lines:
        match = re.match(r"(\d+)\s+(\S+)", line)
        if not match:
            continue
        at, tag = int(match[1]), match[2]
        if (start_ms is not None and at < start_ms) or (end_ms is not None and at > end_ms):
            continue
        first = min(at, first) if first is not None else at
        last = max(at, last) if last is not None else at
        fields = dict(re.findall(r"(\w+)=([^\s]+)", line))

        def number(key):
            try:
                value = float(fields[key])
                return value if 0 <= value < float("inf") else None
            except (KeyError, ValueError):
                return None

        for key in STAGES.get(tag, ()):
            value = number(key)
            if value is not None:
                stages[tag + "." + key].append(value)
        if tag == "triple.methodSample" and fields.get("method") == "ocr" and number("ms") is not None:
            models[fields.get("model", "unknown")].append(number("ms"))
        if tag == "triple.confirmation":
            reasons[fields.get("reason", "unknown")] += 1
        if tag == "triple.lane":
            lanes[fields.get("lane", "unknown")] += 1
        if tag == "triple.lookup":
            sources[fields.get("source", "unknown")] += 1
        if tag == "triple.titleResolved" and number("captureAgeMs") is not None:
            title_count += 1
            late_titles += number("captureAgeMs") > 2500
        if tag == "triple.ocrLane":
            completed_lanes += 1
            expired += fields.get("expired") == "true"
        if tag == "triple.health":
            thermal[fields.get("thermal", "unknown")] += 1
            if number("heapMb") is not None:
                heap.append(number("heapMb"))
            drop_max = max(drop_max, number("traceDropped") or 0)
        if tag == "triple.methods":
            methods.append({"at": at, **fields})
        if tag == "triple.languageScope":
            languages.append({"at": at, **fields})
    return {
        "note": "Stage/callback durations, NOT card recognition latency or accuracy. Spans may overlap; do not sum percentiles. Old ocrComplete omits failures/discards; v14 ocrLane records releases including expiration. Method changes make this observational, not a controlled benchmark.",
        "firstAt": first, "lastAt": last,
        "stagesMs": {k: stats(v) for k, v in sorted(stages.items())},
        "ocrModelsMs": {k: stats(v) for k, v in sorted(models.items())},
        "confirmationReasons": dict(reasons), "lanes": dict(lanes),
        "lookupSources": dict(sources), "thermalSamples": dict(thermal), "heapMb": stats(heap),
        "titleCallbacks": title_count, "titleCallbacksOlderThan2500ms": late_titles,
        "ocrLaneReleases": completed_lanes, "ocrLaneExpired": expired,
        "traceDroppedMax": drop_max, "methodChanges": methods, "languageScopeChanges": languages,
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("logs", nargs="+", type=Path)
    parser.add_argument("--from-ms", type=int)
    parser.add_argument("--to-ms", type=int)
    args = parser.parse_args()
    lines = [line for path in args.logs for line in path.read_text(encoding="utf-8-sig").splitlines()]
    print(json.dumps(summarize(lines, args.from_ms, args.to_ms), indent=2, ensure_ascii=False))
