from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from .build import build_kommune, merge_release, norway_grid, plan_chunks
from .nvdb import NvdbClient


def parse_kommune_arg(raw: str) -> list[int] | None:
    text = (raw or "").strip()
    if text.lower() in {"all", "*", ""}:
        return None
    return [int(token.strip()) for token in text.split(",") if token.strip()]


def resolve_kommuner(raw: str) -> list[int]:
    parsed = parse_kommune_arg(raw)
    if parsed is not None:
        return parsed
    with NvdbClient() as client:
        numbers = client.list_kommune_numbers()
    if not numbers:
        raise SystemExit("NVDB returnerte ingen kommuner")
    return numbers


def main() -> None:
    parser = argparse.ArgumentParser(description="Bygg NVDB-fliser for Skilt-varsler")
    parser.add_argument(
        "--kommune",
        default="3216",
        help="Kommunenummer, komma-separert, eller all (default Vestby 3216)",
    )
    parser.add_argument("--out", type=Path, default=Path("tiles-out"))
    parser.add_argument("--skip-signs", action="store_true")
    parser.add_argument("--skip-if-unchanged", action="store_true")
    parser.add_argument("--print-grid", action="store_true", help="Skriv nasjonalt rutenett til stdout")
    parser.add_argument("--plan-chunks", type=int, metavar="SIZE", help="Skriv JSON-chunks for GitHub Actions")
    parser.add_argument("--merge-release", nargs=3, metavar=("EXISTING", "INCOMING", "OUT"))
    args = parser.parse_args()
    if args.print_grid:
        print(json.dumps(norway_grid(), indent=2))
        return
    if args.merge_release:
        existing, incoming, output = (Path(item) for item in args.merge_release)
        path = merge_release(existing, incoming, output)
        print(f"Skrev {path}")
        return
    if args.plan_chunks:
        chunks = plan_chunks(resolve_kommuner(args.kommune), args.plan_chunks)
        json.dump(chunks, sys.stdout)
        print()
        return
    kommuner = resolve_kommuner(args.kommune)
    wrote = False
    failed = 0
    for kommune in kommuner:
        try:
            path = build_kommune(
                kommune,
                args.out,
                include_signs=not args.skip_signs,
                skip_if_unchanged=args.skip_if_unchanged,
            )
        except Exception as error:
            failed += 1
            print(f"FEIL {kommune}: {error}")
            continue
        if path is None:
            print(f"Ingen endringer for {kommune}")
            continue
        wrote = True
        print(f"Skrev {path}")
    if failed:
        print(f"{failed} kommuner feilet")
        if not wrote:
            raise SystemExit(1)
    if not wrote:
        print("Ingen endringer")


if __name__ == "__main__":
    main()
