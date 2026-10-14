#!/usr/bin/env python3
"""
work_history 메트릭 집계 CLI.

의존성 없음 (Python 3.10+ 표준 라이브러리만 — PEP 604 union 문법 `X | None` 사용).
work_history/*.md 파일들의 **평면 key:value YAML frontmatter만** 파싱해 그룹별
집계를 출력한다. nested(dict/list), YAML anchor, 멀티라인 값 등은 지원하지 않는다 —
현재 _REFERENCE.md 규약 기준으로 충분.

사용:
    python3 scripts/wh-metrics.py                  # 전체 요약
    python3 scripts/wh-metrics.py --by type        # type별 소요 시간
    python3 scripts/wh-metrics.py --by assignee    # 담당자별 집계
    python3 scripts/wh-metrics.py --by month       # 월별 처리량
    python3 scripts/wh-metrics.py --dir path       # 다른 디렉터리 지정
    python3 scripts/wh-metrics.py --format json    # JSON 출력

규약은 `.claude/skills/work-manager/_REFERENCE.md` 참조.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any

FRONTMATTER_DELIM = re.compile(r"^---\s*$")
# "key: value" 또는 "key: \"value\"" 를 모두 수용. 값 내 콜론 보존.
KV_LINE = re.compile(r"^([A-Za-z0-9_]+)\s*:\s*(.*)$")


def parse_frontmatter(path: Path) -> dict[str, str] | None:
    """단순 YAML frontmatter 파서. nested/list는 지원하지 않는다 (현재 규약 기준 평면 key:value 만)."""
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except (OSError, UnicodeDecodeError) as exc:
        print(f"[WARN] {path}: 읽기 실패 ({exc})", file=sys.stderr)
        return None

    if not lines or not FRONTMATTER_DELIM.match(lines[0]):
        return None

    data: dict[str, str] = {}
    for raw in lines[1:]:
        if FRONTMATTER_DELIM.match(raw):
            return data
        match = KV_LINE.match(raw)
        if not match:
            continue
        key, value = match.group(1), match.group(2).strip()
        # 양 끝 따옴표 제거 (YAML 평면 문자열)
        if len(value) >= 2 and value[0] == value[-1] and value[0] in ('"', "'"):
            value = value[1:-1]
        data[key] = value
    # frontmatter 종결자가 없는 파일은 유효하지 않다고 간주
    return None


def load_records(root: Path) -> list[dict[str, str]]:
    records: list[dict[str, str]] = []
    skipped: list[Path] = []
    for path in sorted(root.glob("*.md")):
        if path.name.upper() == "README.MD":
            continue
        fm = parse_frontmatter(path)
        if fm is None:
            skipped.append(path)
            continue
        fm["_path"] = str(path.relative_to(root.parent))
        records.append(fm)
    if skipped:
        print(
            f"[WARN] frontmatter 누락·종결자 없음으로 건너뛴 파일 {len(skipped)}개:",
            file=sys.stderr,
        )
        for p in skipped:
            print(f"  - {p}", file=sys.stderr)
    return records


def to_minutes(value: str | None, record_path: str | None = None) -> int | None:
    """정상 정수면 그 값, 비정수·누락이면 None(집계 제외) 반환. 오류 레코드는 stderr로 경고."""
    if not value:
        return None
    try:
        return int(value)
    except ValueError:
        ref = f" ({record_path})" if record_path else ""
        print(
            f"[WARN] totalMinutes가 정수가 아닙니다: {value!r}{ref} — 평균/합계에서 제외합니다.",
            file=sys.stderr,
        )
        return None


def summarize(records: list[dict[str, str]]) -> dict[str, Any]:
    status_counts: dict[str, int] = defaultdict(int)
    for r in records:
        status_counts[r.get("status") or "UNKNOWN"] += 1
    total = len(records)
    completed = status_counts.get("COMPLETED", 0)
    completion_rate = (completed / total * 100.0) if total else 0.0
    return {
        "total": total,
        "status": dict(status_counts),
        "completionRate": round(completion_rate, 1),
    }


def group_by(records: list[dict[str, str]], key: str) -> list[dict[str, Any]]:
    buckets: dict[str, list[dict[str, str]]] = defaultdict(list)
    for r in records:
        if key == "month":
            started = r.get("startedAt", "")
            bucket = started[:7] if len(started) >= 7 else "UNKNOWN"
        else:
            bucket = r.get(key, "") or "UNKNOWN"
        buckets[bucket].append(r)

    out: list[dict[str, Any]] = []
    for bucket, items in sorted(buckets.items()):
        completed_items = [i for i in items if (i.get("status") == "COMPLETED")]
        # 비정수·누락은 None 반환되어 집계에서 제외.
        valid_minutes = [
            m for i in completed_items
            if (m := to_minutes(i.get("totalMinutes"), i.get("_path"))) is not None
        ]
        avg_minutes = (sum(valid_minutes) / len(valid_minutes)) if valid_minutes else 0.0
        out.append(
            {
                "bucket": bucket,
                "count": len(items),
                "completed": len(completed_items),
                "withMinutes": len(valid_minutes),
                "avgMinutes": round(avg_minutes, 1),
                "sumMinutes": sum(valid_minutes),
            }
        )
    return out


def render_table(rows: list[dict[str, Any]], columns: list[tuple[str, str]]) -> str:
    widths = {col: max(len(label), *(len(str(r.get(col, ""))) for r in rows)) for col, label in columns}
    header = " | ".join(label.ljust(widths[col]) for col, label in columns)
    separator = "-+-".join("-" * widths[col] for col, _ in columns)
    body = "\n".join(" | ".join(str(r.get(col, "")).ljust(widths[col]) for col, _ in columns) for r in rows)
    return f"{header}\n{separator}\n{body}" if rows else f"{header}\n{separator}\n(no rows)"


def main() -> int:
    parser = argparse.ArgumentParser(description="work_history 메트릭 집계")
    parser.add_argument("--dir", default="work_history", help="work_history 디렉터리 (기본 work_history/)")
    parser.add_argument("--by", choices=["type", "assignee", "month", "agent", "jobType"], help="그룹 기준")
    parser.add_argument("--format", choices=["table", "json"], default="table", help="출력 형식")
    args = parser.parse_args()

    root = Path(args.dir)
    if not root.is_dir():
        print(f"[ERROR] 디렉터리를 찾을 수 없습니다: {root}", file=sys.stderr)
        return 2

    records = load_records(root)

    if args.by is None:
        summary = summarize(records)
        if args.format == "json":
            print(json.dumps(summary, ensure_ascii=False, indent=2))
        else:
            print(f"total records     : {summary['total']}")
            print(f"completion rate   : {summary['completionRate']}%")
            print("status counts     :")
            for status, count in sorted(summary["status"].items()):
                print(f"  {status:<12} {count}")
        return 0

    rows = group_by(records, args.by)
    if args.format == "json":
        print(json.dumps(rows, ensure_ascii=False, indent=2))
        return 0

    columns = [
        ("bucket", args.by),
        ("count", "count"),
        ("completed", "completed"),
        ("withMinutes", "with_min"),
        ("avgMinutes", "avg_min"),
        ("sumMinutes", "sum_min"),
    ]
    print(render_table(rows, columns))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
