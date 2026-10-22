"""Explicit, identifier-only CameraVision rename. Compatibility literals stay unchanged."""
import re

IDENTIFIERS = {
    "CollectorVisionAssets": "CameraVisionAssets",
    "CollectorVisionMath": "CameraVisionMath",
    "CollectorVisionAcceptanceGate": "CameraVisionAcceptanceGate",
    "NativeCollectorVisionEngine": "NativeCameraVisionEngine",
    "CollectorVisionAcceptanceGateTest": "CameraVisionAcceptanceGateTest",
    "CollectorVisionMathTest": "CameraVisionMathTest",
}
# Keep strings/comments byte-identical, including URLs, persisted keys and provenance.
TOKENS = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\x27(?:\\.|[^\x27\\])*\x27|//[^\n]*|/\*[\s\S]*?\*/|\b[A-Za-z_][A-Za-z_0-9]*\b')


def renamed_source(text):
    return TOKENS.sub(lambda match: IDENTIFIERS.get(match.group(), match.group()), text)


def renamed_path(path):
    if path.startswith("collectorvision-native/"):
        path = "cameraVision/" + path[len("collectorvision-native/"):]
    for old, new in IDENTIFIERS.items():
        if path.endswith("/" + old + ".kt"):
            return path[:-len(old + ".kt")] + new + ".kt"
    return path
