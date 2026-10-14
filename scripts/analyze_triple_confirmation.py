"""Summarize private Triple traces, not recognition accuracy. Python standard library only."""
import argparse
from collections import Counter
import json
from pathlib import Path
import re


def percentile(values, fraction):
    ordered = sorted(values)
    return ordered[round((len(ordered) - 1) * fraction)] if ordered else None


def summarize(lines, slow_ms=1000):
    reasons = Counter()
    stages = {name: [] for name in ("frameMs", "detectMs", "featureMs", "searchMs")}
    confirmations = []
    slow = []
    slow_pending = []
    blocked = []
    intervals = []
    for line in lines:
        fields = dict(re.findall(r"(\w+)=([^\s]+)", line))
        if "triple.acceptInterval " in line and fields.get("deltaMs", "-1") != "-1":
            intervals.append(int(fields["deltaMs"]))
        if "triple.confirmation " not in line:
            continue
        reasons[fields.get("reason", "unknown")] += 1
        if fields.get("reason") in {"scope_pending", "no_detection", "engine_pending"}:
            blocked.append({"timestamp": line.split()[0], **fields})
        elif fields.get("reason") != "confirmed" and int(fields.get("confirmMs", "0")) + int(fields.get("frameMs", "0")) > slow_ms:
            slow_pending.append({"timestamp": line.split()[0], **fields})
        for stage in stages:
            if stage in fields:
                stages[stage].append(int(fields[stage]))
        # Count the transition to two strong samples, not every subsequent stable frame.
        if fields.get("reason") == "confirmed" and fields.get("strongReadings") == "2":
            duration = int(fields["confirmMs"]) + int(fields.get("frameMs", "0"))
            confirmations.append(duration)
            if duration > slow_ms:
                slow.append({"timestamp": line.split()[0], "confirmationMs": duration, **fields})
    return {
        "note": "Latency and rejection diagnostics, not an accuracy percentage; gaps before the first candidate are not included.",
        "reasons": dict(reasons),
        "confirmationCount": len(confirmations),
        "confirmationMs": {"p50": percentile(confirmations, .5), "p95": percentile(confirmations, .95), "max": max(confirmations, default=None)},
        "stagesMs": {name: {"p50": percentile(values, .5), "p95": percentile(values, .95)} for name, values in stages.items()},
        "newCopyIntervalsBelow600ms": sum(value < 600 for value in intervals),
        "slowConfirmations": slow,
        "lastSlowPendingSamples": slow_pending[-50:],
        "lastBlockedSamples": blocked[-50:],
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("logs", nargs="+", type=Path)
    parser.add_argument("--slow-ms", type=int, default=1000)
    args = parser.parse_args()
    lines = [line for path in args.logs for line in path.read_text(encoding="utf-8-sig").splitlines()]
    print(json.dumps(summarize(lines, args.slow_ms), indent=2, ensure_ascii=False))
