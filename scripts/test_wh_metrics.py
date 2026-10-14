#!/usr/bin/env python3
"""wh-metrics.py 단위 테스트. 핵심 파싱·집계 경로만 커버. 외부 의존성 없음.

실행:
    python3 -m unittest scripts.test_wh_metrics
    또는
    python3 scripts/test_wh_metrics.py
"""

from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
WH = importlib.util.spec_from_file_location("wh_metrics", HERE / "wh-metrics.py")
assert WH and WH.loader
wh_metrics = importlib.util.module_from_spec(WH)
WH.loader.exec_module(wh_metrics)


def write_md(dir: Path, name: str, body: str) -> Path:
    path = dir / name
    path.write_text(body, encoding="utf-8")
    return path


class ParseFrontmatterTest(unittest.TestCase):
    def test_quoted_and_unquoted_values(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            md = write_md(
                root,
                "a.md",
                '---\ntype: FEAT\nissueTitle: "with: colon"\n---\nbody\n',
            )
            fm = wh_metrics.parse_frontmatter(md)
            self.assertEqual(fm, {"type": "FEAT", "issueTitle": "with: colon"})

    def test_missing_closing_delimiter_returns_none(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            md = write_md(root, "a.md", "---\ntype: FEAT\n# no closing ---\nbody\n")
            self.assertIsNone(wh_metrics.parse_frontmatter(md))

    def test_no_frontmatter_returns_none(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            md = write_md(root, "a.md", "# just a heading\nno fm\n")
            self.assertIsNone(wh_metrics.parse_frontmatter(md))


class ToMinutesTest(unittest.TestCase):
    def test_valid_integer(self) -> None:
        self.assertEqual(wh_metrics.to_minutes("37"), 37)

    def test_empty_returns_none(self) -> None:
        self.assertIsNone(wh_metrics.to_minutes(""))
        self.assertIsNone(wh_metrics.to_minutes(None))

    def test_non_integer_returns_none(self) -> None:
        self.assertIsNone(wh_metrics.to_minutes("abc", "sample.md"))


class LoadAndGroupTest(unittest.TestCase):
    def _seed(self, root: Path) -> None:
        write_md(
            root,
            "a.md",
            """---
issueKey: "#1"
type: FEAT
assignee: alice
status: COMPLETED
totalMinutes: 30
startedAt: "2026-10-14 10:00"
---
body
""",
        )
        write_md(
            root,
            "b.md",
            """---
issueKey: "#2"
type: FEAT
assignee: bob
status: COMPLETED
totalMinutes: 60
startedAt: "2026-10-15 12:00"
---
body
""",
        )
        write_md(
            root,
            "c.md",
            """---
issueKey: "#3"
type: CHORE
assignee: alice
status: IN_PROGRESS
totalMinutes: 0
startedAt: "2026-11-01 09:00"
---
body
""",
        )
        write_md(root, "README.md", "# ignored")

    def test_load_skips_readme_and_invalid(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._seed(root)
            write_md(root, "invalid.md", "no frontmatter here")
            records = wh_metrics.load_records(root)
            self.assertEqual(len(records), 3)
            self.assertNotIn("README.md", [r["_path"] for r in records])

    def test_group_by_type_aggregates(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._seed(root)
            records = wh_metrics.load_records(root)
            rows = wh_metrics.group_by(records, "type")
            by_bucket = {r["bucket"]: r for r in rows}
            feat = by_bucket["FEAT"]
            self.assertEqual(feat["count"], 2)
            self.assertEqual(feat["completed"], 2)
            self.assertEqual(feat["avgMinutes"], 45.0)
            self.assertEqual(feat["sumMinutes"], 90)
            # IN_PROGRESS 는 completed 집계에서 제외
            chore = by_bucket["CHORE"]
            self.assertEqual(chore["count"], 1)
            self.assertEqual(chore["completed"], 0)
            self.assertEqual(chore["avgMinutes"], 0.0)

    def test_group_by_month(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._seed(root)
            records = wh_metrics.load_records(root)
            rows = wh_metrics.group_by(records, "month")
            buckets = {r["bucket"]: r for r in rows}
            self.assertIn("2026-10", buckets)
            self.assertIn("2026-11", buckets)
            self.assertEqual(buckets["2026-10"]["count"], 2)

    def test_summarize_completion_rate(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._seed(root)
            records = wh_metrics.load_records(root)
            summary = wh_metrics.summarize(records)
            self.assertEqual(summary["total"], 3)
            # 2 / 3 = 66.7%
            self.assertAlmostEqual(summary["completionRate"], 66.7, places=1)

    def test_invalid_total_minutes_excluded_from_avg(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write_md(
                root,
                "a.md",
                """---
type: FEAT
status: COMPLETED
totalMinutes: 30
---
body
""",
            )
            write_md(
                root,
                "b.md",
                """---
type: FEAT
status: COMPLETED
totalMinutes: not-a-number
---
body
""",
            )
            records = wh_metrics.load_records(root)
            rows = wh_metrics.group_by(records, "type")
            feat = next(r for r in rows if r["bucket"] == "FEAT")
            # withMinutes 는 유효 1건, avg는 30.0 (비정수 제외)
            self.assertEqual(feat["withMinutes"], 1)
            self.assertEqual(feat["avgMinutes"], 30.0)
            self.assertEqual(feat["sumMinutes"], 30)


if __name__ == "__main__":
    unittest.main()
