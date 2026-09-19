#!/usr/bin/env python3
"""Copy retained art-hash test captures from the known Sony without changing app data."""

from __future__ import annotations

import argparse
import io
from pathlib import Path
import re
import subprocess
import tarfile


PACKAGE = "io.asv.mtgocr.ocrreader"
DEFAULT_SERIAL = "QV770HG2JD"
SAMPLE_NAME = re.compile(r"\d{13}-[0-9a-f-]{36}\.jpg")


def adb(serial: str, *args: str, timeout: int = 30) -> subprocess.CompletedProcess[bytes]:
    return subprocess.run(["adb", "-s", serial, *args], capture_output=True, timeout=timeout)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default=DEFAULT_SERIAL)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    devices = subprocess.run(["adb", "devices", "-l"], capture_output=True, text=True, check=True).stdout
    if not any(line.split()[:2] == [args.serial, "device"] for line in devices.splitlines()):
        parser.error(f"El dispositivo {args.serial} no está conectado/autorizado")
    model = adb(args.serial, "shell", "getprop", "ro.product.model").stdout.decode().strip()
    if model != "XQ-DQ54":
        parser.error(f"Modelo inesperado para {args.serial}: {model!r}")
    listed = adb(args.serial, "exec-out", "run-as", PACKAGE, "ls", "files/art_hash_samples")
    if b"No such file or directory" in listed.stdout:
        print("Todavía no hay capturas de prueba en el Sony.")
        return 0
    if listed.returncode != 0 or b"not debuggable" in listed.stdout:
        raise RuntimeError(listed.stdout.decode(errors="replace"))
    archive = adb(
        args.serial, "exec-out", "run-as", PACKAGE,
        "tar", "-cf", "-", "files/art_hash_samples", timeout=120,
    )
    if archive.returncode != 0 or archive.stdout.startswith(b"tar:"):
        raise RuntimeError((archive.stderr + archive.stdout[:300]).decode(errors="replace"))
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    copied = skipped = 0
    with tarfile.open(fileobj=io.BytesIO(archive.stdout), mode="r:") as tar:
        for member in tar:
            if not member.isfile() or not member.name.startswith("files/art_hash_samples/"):
                continue
            name = Path(member.name).name
            if not SAMPLE_NAME.fullmatch(name):
                continue
            destination = output / name
            if destination.exists():
                if destination.stat().st_size != member.size:
                    raise RuntimeError(f"La captura local difiere: {destination}")
                skipped += 1
                continue
            source = tar.extractfile(member)
            if source is None:
                raise RuntimeError(f"No se pudo leer {name}")
            with source, destination.open("wb") as target:
                while chunk := source.read(1024 * 1024):
                    target.write(chunk)
            copied += 1
    print(f"Sony {args.serial}: {copied} capturas copiadas, {skipped} ya existentes; {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
