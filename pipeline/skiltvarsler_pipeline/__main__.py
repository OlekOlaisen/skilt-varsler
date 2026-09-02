from __future__ import annotations

import argparse
from pathlib import Path

from .build import build_kommune, norway_grid


def main() -> None:
    parser = argparse.ArgumentParser(description="Bygg NVDB-fliser for Skilt-varsler")
    parser.add_argument(
        "--kommune",
        default="3216",
        help="Kommunenummer, komma-separert (default Vestby 3216)",
    )
    parser.add_argument("--out", type=Path, default=Path("tiles-out"))
    parser.add_argument("--skip-signs", action="store_true")
    parser.add_argument("--skip-if-unchanged", action="store_true")
    parser.add_argument("--print-grid", action="store_true", help="Skriv nasjonalt rutenett til stdout")
    args = parser.parse_args()
    if args.print_grid:
        import json

        print(json.dumps(norway_grid(), indent=2))
        return
    kommuner = [int(token.strip()) for token in str(args.kommune).split(",") if token.strip()]
    wrote = False
    for kommune in kommuner:
        path = build_kommune(
            kommune,
            args.out,
            include_signs=not args.skip_signs,
            skip_if_unchanged=args.skip_if_unchanged,
        )
        if path is None:
            print(f"Ingen endringer for {kommune}")
            continue
        wrote = True
        print(f"Skrev {path}")
    if not wrote:
        print("Ingen endringer")


if __name__ == "__main__":
    main()
