from __future__ import annotations

import json
import shutil
from datetime import datetime
from pathlib import Path


def derive_run(raw_dir: str | Path, derived_dir: str | Path) -> dict:
    raw_path = Path(raw_dir)
    derived_path = Path(derived_dir)
    run_root = derived_path.parent
    output_root = run_root.parent
    view_root = output_root.parent / f"{output_root.name}-view"
    derived_path.mkdir(parents=True, exist_ok=True)

    meta = _read_json(raw_path / "meta.json")
    events = _read_events(raw_path / "events.jsonl")

    tool_events = [event for event in events if event.get("type") in {"tool_use", "tool_result"}]
    tool_call_count = sum(1 for event in events if event.get("type") == "tool_use")
    duration_sec = _duration_seconds(meta.get("createdAt", ""), meta.get("endedAt", ""))
    finish_reason = _last_finish_reason(events)
    run_id = str(meta.get("runId", "")).strip() or run_root.name
    raw_display_name = str(meta.get("displayName", "")).strip() or str(meta.get("userInput", "")).strip()
    display_name = _build_display_name(raw_display_name)

    summary = {
        "runId": run_id,
        "taskId": meta.get("taskId", ""),
        "displayName": display_name,
        "status": meta.get("status", ""),
        "errorMessage": meta.get("errorMessage", ""),
        "finishReason": finish_reason,
        "durationSec": duration_sec,
        "toolCallCount": tool_call_count,
    }

    timeline_text = _build_timeline(events)
    (derived_path / "timeline.txt").write_text(timeline_text, encoding="utf-8")
    _write_tool_events(tool_events, derived_path / "tool-events")
    summary["viewDir"] = str(_write_display_view(summary, derived_path, view_root))
    (derived_path / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    shutil.copy2(derived_path / "summary.json", Path(summary["viewDir"]) / "summary.json")
    shutil.copy2(derived_path / "timeline.txt", Path(summary["viewDir"]) / "timeline.txt")
    return summary


def _read_json(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def _read_events(path: Path) -> list[dict]:
    if not path.exists():
        return []
    events: list[dict] = []
    with path.open("r", encoding="utf-8") as handle:
        for raw_line in handle:
            line = raw_line.strip()
            if not line:
                continue
            events.append(json.loads(line))
    return events


def _duration_seconds(created_at: str, ended_at: str) -> float | None:
    if not created_at or not ended_at:
        return None
    start = datetime.fromisoformat(created_at)
    end = datetime.fromisoformat(ended_at)
    return round((end - start).total_seconds(), 3)


def _build_timeline(events: list[dict]) -> str:
    lines: list[str] = []
    buffered_type = ""
    buffered_time = ""
    buffered_text_parts: list[str] = []

    def flush_buffer() -> None:
        nonlocal buffered_type, buffered_time, buffered_text_parts
        if not buffered_type or not buffered_text_parts:
            buffered_type = ""
            buffered_time = ""
            buffered_text_parts = []
            return
        label = "reasoning" if buffered_type == "reasoning_delta" else "text"
        text = "".join(buffered_text_parts).strip()
        if text:
            lines.append(f"[{buffered_time}] {label}:")
            lines.append(text)
        buffered_type = ""
        buffered_time = ""
        buffered_text_parts = []

    for event in events:
        payload = event.get("payload") or {}
        event_type = event.get("type", "")
        time = event.get("time", "")
        if event_type in {"reasoning_delta", "text_delta"}:
            delta = str(payload.get("text", ""))
            if not delta:
                continue
            if buffered_type and buffered_type != event_type:
                flush_buffer()
            if not buffered_type:
                buffered_type = event_type
                buffered_time = time
            buffered_text_parts.append(delta)
            continue
        flush_buffer()
        if event_type == "tool_use":
            lines.append(f"[{time}] tool_use {payload.get('name', '')}")
        elif event_type == "tool_result":
            lines.append(f"[{time}] tool_result {payload.get('id', '')}")
        elif event_type == "message_end":
            lines.append(f"[{time}] message_end {payload.get('finishReason', '')}")
        elif event_type == "error":
            lines.append(f"[{time}] error {payload.get('errorMessage', '')}")
    flush_buffer()
    return "\n".join(lines) + ("\n" if lines else "")


def _last_finish_reason(events: list[dict]) -> str:
    for event in reversed(events):
        if event.get("type") != "message_end":
            continue
        payload = event.get("payload") or {}
        return str(payload.get("finishReason", ""))
    return ""


def _write_tool_events(events: list[dict], output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    for index, event in enumerate(events, start=1):
        payload = event.get("payload") or {}
        tool_name = payload.get("name") or payload.get("id") or "tool"
        filename = f"{index:03d}_{event.get('type', 'event')}_{_slugify(str(tool_name))}.json"
        (output_dir / filename).write_text(
            json.dumps(event, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )


def _write_display_view(summary: dict, derived_dir: Path, view_root: Path) -> Path:
    display_dir = view_root / _display_dir_name(summary.get("runId", ""), summary.get("displayName", ""))
    if display_dir.exists():
        shutil.rmtree(display_dir)
    shutil.copytree(derived_dir, display_dir)
    return display_dir


def _display_dir_name(run_id: str, display_name: str) -> str:
    if not display_name:
        return run_id or "unknown"
    return f"{run_id}-{_sanitize_path_segment(display_name)}"


def _build_display_name(value: str) -> str:
    return value[:10].strip()


def _sanitize_path_segment(value: str) -> str:
    sanitized = []
    for char in value.strip():
        if char in '<>:"/\\|?*':
            sanitized.append("_")
        elif ord(char) < 32:
            sanitized.append("_")
        else:
            sanitized.append(char)
    text = "".join(sanitized).strip(" .")
    while "__" in text:
        text = text.replace("__", "_")
    return text or "unknown"


def _slugify(value: str) -> str:
    normalized = value.strip().replace(" ", "_")
    sanitized = []
    for char in normalized:
        if char.isalnum() or char in {"_", "-"}:
            sanitized.append(char)
        else:
            sanitized.append("_")
    text = "".join(sanitized).strip("_")
    while "__" in text:
        text = text.replace("__", "_")
    return text or "unknown"
