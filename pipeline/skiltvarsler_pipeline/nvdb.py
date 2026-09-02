from __future__ import annotations

import os
from typing import Any, Iterator

import httpx

BASE_URL = "https://nvdbapiles.atlas.vegvesen.no"
CLIENT_NAME = os.environ.get("NVDB_X_CLIENT") or "skilt-varsler-pipeline"
CONTACT = os.environ.get("NVDB_X_KONTAKTPERSON") or "skilt-varsler@localhost"


class NvdbClient:
    def __init__(self, timeout: float = 60.0) -> None:
        self._http = httpx.Client(
            base_url=BASE_URL,
            timeout=timeout,
            headers={
                "X-Client": CLIENT_NAME,
                "X-Kontaktperson": CONTACT,
                "Accept": "application/json",
            },
        )

    def close(self) -> None:
        self._http.close()

    def __enter__(self) -> "NvdbClient":
        return self

    def __exit__(self, *args: object) -> None:
        self.close()

    def iter_veglenkesekvenser(self, kommune: int, page_size: int = 200) -> Iterator[dict[str, Any]]:
        yield from self._paginate(
            "/vegnett/api/v4/veglenkesekvenser",
            {"kommune": kommune, "antall": page_size},
        )

    def iter_vegobjekter(
        self,
        type_id: int,
        kommune: int,
        page_size: int = 200,
    ) -> Iterator[dict[str, Any]]:
        yield from self._paginate(
            f"/vegobjekter/api/v4/vegobjekter/{type_id}",
            {
                "kommune": kommune,
                "antall": page_size,
                "inkluder": "lokasjon,egenskaper,metadata",
                "inkludergeometri": "ingen",
            },
        )

    def iter_endringer(self, type_id: int, days: int = 30) -> Iterator[dict[str, Any]]:
        yield from self._paginate(
            f"/vegobjekter/api/v4/vegobjekter/{type_id}/endringer",
            {"antall": 200, "antallDager": days},
        )

    def kommune_with_extent(self, kommune: int) -> dict[str, Any] | None:
        response = self._http.get("/omrader/api/v4/kommuner", params={"inkluder": "kartutsnitt"})
        response.raise_for_status()
        for item in response.json():
            if int(item.get("nummer") or 0) == kommune:
                return item
        return None

    def _paginate(self, path: str, params: dict[str, Any]) -> Iterator[dict[str, Any]]:
        start: str | None = None
        while True:
            query = dict(params)
            if start:
                query["start"] = start
            response = self._http.get(path, params=query)
            response.raise_for_status()
            payload = response.json()
            objects = payload.get("objekter") or []
            yield from objects
            metadata = payload.get("metadata") or {}
            neste = metadata.get("neste") or {}
            next_start = neste.get("start")
            if not objects or not next_start or next_start == start:
                break
            start = str(next_start)
