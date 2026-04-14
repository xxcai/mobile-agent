from __future__ import annotations

import argparse
import json
from pathlib import Path

from .device import AdbDeviceClient
from .cases import load_case, load_cases_from_dir
from .derive import derive_run
from .runner import run_case, run_suite, sync_task


def main() -> None:
    parser = argparse.ArgumentParser(prog="benchmark-runner")
    subparsers = parser.add_subparsers(dest="command", required=True)

    run_case_parser = subparsers.add_parser("run-case")
    run_case_parser.add_argument("--case", required=True, dest="case_path")
    run_case_parser.add_argument("--package", default="com.hh.agent", dest="package_name")
    run_case_parser.add_argument(
        "--output-root",
        default=str(Path("build") / "benchmark-results"),
    )

    run_suite_parser = subparsers.add_parser("run-suite")
    run_suite_parser.add_argument("--cases-dir", required=True)
    run_suite_parser.add_argument("--package", default="com.hh.agent", dest="package_name")
    run_suite_parser.add_argument(
        "--output-root",
        default=str(Path("build") / "benchmark-results"),
    )

    derive_parser = subparsers.add_parser("derive")
    derive_parser.add_argument("--raw-dir", required=True)
    derive_parser.add_argument("--derived-dir", required=True)

    sync_task_parser = subparsers.add_parser("sync-task")
    sync_task_parser.add_argument("--package", default="com.hh.agent", dest="package_name")
    sync_task_parser.add_argument("--run-id")
    sync_task_parser.add_argument("--latest", action="store_true")
    sync_task_parser.add_argument(
        "--output-root",
        default=str(Path("build") / "benchmark-results" / "manual"),
    )

    list_tasks_parser = subparsers.add_parser("list-device-tasks")
    list_tasks_parser.add_argument("--package", default="com.hh.agent", dest="package_name")

    args = parser.parse_args()

    if args.command == "run-case":
        case = load_case(args.case_path)
        result = run_case(case, package_name=args.package_name, output_root=args.output_root)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return

    if args.command == "run-suite":
        cases = load_cases_from_dir(args.cases_dir)
        suite_id = Path(args.cases_dir).name
        summary = run_suite(
            cases,
            package_name=args.package_name,
            output_root=args.output_root,
            suite_id=suite_id,
        )
        print(json.dumps(summary, ensure_ascii=False, indent=2))
        return

    if args.command == "derive":
        summary = derive_run(args.raw_dir, args.derived_dir)
        print(json.dumps(summary, ensure_ascii=False, indent=2))
        return

    if args.command == "sync-task":
        if bool(args.run_id) == bool(args.latest):
            raise SystemExit("exactly one of --run-id or --latest is required")
        client = AdbDeviceClient(args.package_name)
        run_id = args.run_id or client.latest_task_dir()
        result = sync_task(run_id, package_name=args.package_name, output_root=args.output_root)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return

    if args.command == "list-device-tasks":
        client = AdbDeviceClient(args.package_name)
        print(json.dumps(client.list_task_dirs(), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
