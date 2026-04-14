from __future__ import annotations

import json
import shutil
import time
from datetime import datetime
from pathlib import Path

from .derive import derive_run
from .device import AdbDeviceClient


FINAL_STATUSES = {"completed", "failed"}


def run_case(case: dict, package_name: str, output_root: str | Path) -> dict:
    run_id = build_run_id(case["id"])
    client = AdbDeviceClient(package_name)
    client.start_app()
    response = _submit_with_retry(client, run_id=run_id, task_id=case["id"], prompt=case["prompt"])

    meta = wait_for_completion(client, run_id)
    run_root = Path(output_root) / run_id
    raw_dir = run_root / "raw"
    derived_dir = run_root / "derived"
    raw_dir.parent.mkdir(parents=True, exist_ok=True)
    if raw_dir.exists():
        shutil.rmtree(raw_dir)
    client.pull_task_dir(run_id, raw_dir)
    summary = derive_run(raw_dir, derived_dir)
    (run_root / "case.json").write_text(
        json.dumps(case, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    return {
        "runId": run_id,
        "meta": meta,
        "summary": summary,
        "runDir": str(run_root),
    }


def sync_task(run_id: str, package_name: str, output_root: str | Path) -> dict:
    client = AdbDeviceClient(package_name)
    meta = client.read_task_meta(run_id)
    if meta is None:
        raise RuntimeError(f"task meta not found for {run_id}")
    run_root = Path(output_root) / run_id
    raw_dir = run_root / "raw"
    derived_dir = run_root / "derived"
    raw_dir.parent.mkdir(parents=True, exist_ok=True)
    if raw_dir.exists():
        shutil.rmtree(raw_dir)
    client.pull_task_dir(run_id, raw_dir)
    summary = derive_run(raw_dir, derived_dir)
    return {
        "runId": run_id,
        "meta": meta,
        "summary": summary,
        "runDir": str(run_root),
    }


def wait_for_completion(client: AdbDeviceClient, run_id: str, poll_interval_sec: float = 1.0) -> dict:
    while True:
        meta = client.read_task_meta(run_id)
        if meta and meta.get("status") in FINAL_STATUSES:
            return meta
        time.sleep(poll_interval_sec)


def _submit_with_retry(client: AdbDeviceClient, run_id: str, task_id: str, prompt: str,
                       max_attempts: int = 20, retry_delay_sec: float = 1.0) -> dict:
    last_response: dict | None = None
    for attempt in range(max_attempts):
        response = client.run_task(run_id=run_id, task_id=task_id, prompt=prompt)
        if response.get("accepted", False):
            return response
        last_response = response
        if attempt < max_attempts - 1:
            time.sleep(retry_delay_sec)
    raise RuntimeError(f"run_task rejected: {(last_response or {}).get('message', '')}")


def run_suite(cases: list[dict], package_name: str, output_root: str | Path, suite_id: str) -> dict:
    results = []
    for case in cases:
        result = run_case(case, package_name=package_name, output_root=output_root)
        summary = result["summary"]
        results.append(
            {
                "caseId": case["id"],
                "runId": result["runId"],
                "status": summary.get("status", ""),
                "finishReason": summary.get("finishReason", ""),
                "durationSec": summary.get("durationSec"),
                "toolCallCount": summary.get("toolCallCount"),
                "runDir": result["runDir"],
            }
        )

    completed = sum(1 for item in results if item["status"] == "completed")
    failed = sum(1 for item in results if item["status"] == "failed")
    suite_summary = {
        "suiteId": suite_id,
        "caseCount": len(results),
        "completed": completed,
        "failed": failed,
        "results": results,
    }

    suite_dir = Path(output_root) / f"suite-{suite_id}"
    suite_dir.mkdir(parents=True, exist_ok=True)
    (suite_dir / "summary.json").write_text(
        json.dumps(suite_summary, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    return suite_summary


def build_run_id(case_id: str) -> str:
    timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    return f"{timestamp}-{case_id}"
