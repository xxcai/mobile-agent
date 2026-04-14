from __future__ import annotations

import base64
import json
import subprocess
from pathlib import Path


class AdbDeviceClient:
    def __init__(self, package_name: str) -> None:
        self.package_name = package_name

    def start_app(self) -> None:
        subprocess.run(
            ["adb", "shell", "am", "start", "-n", f"{self.package_name}/.MainActivity"],
            capture_output=True,
            text=True,
            check=True,
        )

    def run_task(self, run_id: str, task_id: str, prompt: str) -> dict:
        prompt_base64 = base64.b64encode(prompt.encode("utf-8")).decode("ascii")
        command = [
            "adb",
            "shell",
            "content",
            "call",
            "--uri",
            f"content://{self.package_name}.benchmark",
            "--method",
            "run_task",
            "--extra",
            f"run_id:s:{run_id}",
            "--extra",
            f"task_id:s:{task_id}",
            "--extra",
            f"prompt_base64:s:{prompt_base64}",
        ]
        result = subprocess.run(command, capture_output=True, text=True, check=True)
        return _parse_content_call_output(result.stdout)

    def read_task_meta(self, run_id: str) -> dict | None:
        device_meta = self._device_task_path(run_id) + "/meta.json"
        result = subprocess.run(
            ["adb", "shell", "cat", device_meta],
            capture_output=True,
            text=True,
            check=False,
        )
        if result.returncode != 0:
            return None
        raw = result.stdout.strip()
        if not raw:
            return None
        return json.loads(raw)

    def pull_task_dir(self, run_id: str, destination: str | Path) -> Path:
        dest_path = Path(destination)
        dest_path.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(
            ["adb", "pull", self._device_task_path(run_id), str(dest_path)],
            capture_output=True,
            text=True,
            check=True,
        )
        return dest_path

    def list_task_dirs(self) -> list[str]:
        result = subprocess.run(
            ["adb", "shell", "ls", "-1t", self._device_task_root()],
            capture_output=True,
            text=True,
            check=False,
        )
        if result.returncode != 0:
            return []
        lines = [line.strip().replace("\r", "") for line in result.stdout.splitlines()]
        return [line for line in lines if line and not line.startswith("ls:")]

    def latest_task_dir(self) -> str:
        tasks = self.list_task_dirs()
        if not tasks:
            raise RuntimeError(f"no task directory found under {self._device_task_root()}")
        return tasks[0]

    def _device_task_path(self, run_id: str) -> str:
        return f"{self._device_task_root()}/{run_id}"

    def _device_task_root(self) -> str:
        return f"/sdcard/Android/data/{self.package_name}/files/.icraw/tasks"


def _parse_content_call_output(output: str) -> dict:
    parsed: dict[str, str | bool] = {
        "accepted": False,
        "run_id": "",
        "status": "",
        "message": "",
    }
    normalized = output.strip()
    parsed["accepted"] = "accepted=true" in normalized
    for key in ("run_id", "status", "message"):
        marker = f"{key}="
        if marker not in normalized:
            continue
        value = normalized.split(marker, 1)[1]
        for terminator in (", ", "]"):
            if terminator in value:
                value = value.split(terminator, 1)[0]
                break
        parsed[key] = value
    return parsed
