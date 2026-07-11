from __future__ import annotations

import hashlib
import json
from pathlib import Path

from PIL import Image, ImageOps


ROOT = Path(__file__).resolve().parents[1]
DATASET = ROOT / "test-data" / "gemini-food-benchmark"
MANIFEST_PATH = DATASET / "manifest.json"
PROFILES = {
    "conservative": {"maxSidePx": 1600, "quality": 85},
    "balanced": {"maxSidePx": 1280, "quality": 75},
    "aggressive": {"maxSidePx": 960, "quality": 65},
    "realistic": {"maxSidePx": 2400, "quality": 90},
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8-sig"))
    if manifest.get("caseCount") != 10 or len(manifest.get("cases", [])) != 10:
        raise RuntimeError("The controlled dataset must contain exactly ten cases.")

    for case in manifest["cases"]:
        source = DATASET / "originals" / case["fileName"]
        if sha256(source) != case["original"]["sha256"]:
            raise RuntimeError(f"Source hash mismatch for {case['id']}")
        case["prepared"] = {}
        with Image.open(source) as opened:
            oriented = ImageOps.exif_transpose(opened).convert("RGB")
            case["original"]["width"] = oriented.width
            case["original"]["height"] = oriented.height
            case["original"]["bytes"] = source.stat().st_size
            case["original"]["mimeType"] = Image.MIME.get(opened.format, "image/jpeg")
            for profile_id, profile in PROFILES.items():
                prepared = oriented.copy()
                prepared.thumbnail(
                    (profile["maxSidePx"], profile["maxSidePx"]),
                    Image.Resampling.LANCZOS,
                )
                output_dir = DATASET / "prepared" / profile_id
                output_dir.mkdir(parents=True, exist_ok=True)
                output = output_dir / f"{case['id']}.jpg"
                prepared.save(
                    output,
                    format="JPEG",
                    quality=profile["quality"],
                    optimize=False,
                    exif=b"",
                )
                case["prepared"][profile_id] = {
                    "file": str(output.relative_to(DATASET)).replace("\\", "/"),
                    "sha256": sha256(output),
                    "width": prepared.width,
                    "height": prepared.height,
                    "mimeType": "image/jpeg",
                    "quality": profile["quality"],
                    "maxSidePx": profile["maxSidePx"],
                    "bytes": output.stat().st_size,
                }
                prepared.close()

    manifest["preparationProfiles"] = PROFILES
    MANIFEST_PATH.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Prepared {len(manifest['cases'])} sources across {len(PROFILES)} profiles.")


if __name__ == "__main__":
    main()
