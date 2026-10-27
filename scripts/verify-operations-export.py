#!/usr/bin/env python3
"""Verify fixed-name Operations ZIP bytes and each manifest content digest. No extraction."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile


def verify(path: Path, expected_digest: str | None = None) -> dict:
    archive = path.read_bytes()
    if len(archive) > 5_000_000:
        raise ValueError("Archive exceeds the supported byte limit")
    digest = hashlib.sha256(archive).hexdigest()
    if expected_digest and digest != expected_digest.lower():
        raise ValueError("Archive SHA-256 mismatch")
    expected_names = {"summary.txt", "evidence.json", "policy-history.json", "reconciliation.json", "manifest.json"}
    with zipfile.ZipFile(path) as bundle:
        if set(bundle.namelist()) != expected_names or len(bundle.namelist()) != len(expected_names):
            raise ValueError("Unexpected or duplicate archive entries")
        if sum(entry.file_size for entry in bundle.infolist()) > 50_000_000:
            raise ValueError("Expanded archive exceeds the supported limit")
        manifest = json.loads(bundle.read("manifest.json"))
        listed = set()
        for entry in manifest["files"]:
            name = entry["path"]
            if name in listed or name not in expected_names - {"manifest.json"}:
                raise ValueError("Unexpected or duplicate manifest entry")
            listed.add(name)
            content = bundle.read(name)
            if len(content) != entry["bytes"] or hashlib.sha256(content).hexdigest() != entry["sha256"]:
                raise ValueError(f"Content verification failed: {name}")
        if listed != expected_names - {"manifest.json"}:
            raise ValueError("Manifest does not cover every content file")
    return {"exportId": manifest["exportId"], "sha256": digest, "verifiedFiles": len(listed),
            "note": "Verified against the supplied manifest; no independent signature is asserted."}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--sha256", help="Expected whole-archive digest displayed by the application")
    args = parser.parse_args()
    try:
        print(json.dumps(verify(args.archive, args.sha256), indent=2))
    except (ValueError, KeyError, OSError, zipfile.BadZipFile) as error:
        parser.exit(1, f"Verification failed: {error}\n")
