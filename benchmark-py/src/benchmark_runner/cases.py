from __future__ import annotations

import json
from pathlib import Path


def load_case(path: str | Path) -> dict:
    case_path = Path(path)
    with case_path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    if not isinstance(data, dict):
        raise ValueError(f"case file must contain a JSON object: {case_path}")
    case_id = str(data.get("id", "")).strip()
    prompt = str(data.get("prompt", "")).strip()
    if not case_id:
        raise ValueError(f"missing case id: {case_path}")
    if not prompt:
        raise ValueError(f"missing prompt: {case_path}")
    return {
        "id": case_id,
        "prompt": prompt,
        "path": str(case_path.resolve()),
    }


def load_cases_from_dir(path: str | Path) -> list[dict]:
    cases_dir = Path(path)
    case_paths = sorted(cases_dir.glob("*.json"))
    return [load_case(case_path) for case_path in case_paths]
