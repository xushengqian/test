from __future__ import annotations

import threading
from dataclasses import dataclass
from typing import Dict, Optional


@dataclass
class SetNxResult:
    was_set: bool


class InMemoryStore:
    def __init__(self) -> None:
        self._data: Dict[bytes, bytes] = {}
        self._lock = threading.RLock()

    def get(self, key: bytes) -> Optional[bytes]:
        with self._lock:
            return self._data.get(key)

    def exists(self, key: bytes) -> bool:
        with self._lock:
            return key in self._data

    def delete(self, key: bytes) -> int:
        with self._lock:
            if key in self._data:
                del self._data[key]
                return 1
            return 0

    def setnx(self, key: bytes, value: bytes) -> SetNxResult:
        with self._lock:
            if key in self._data:
                return SetNxResult(was_set=False)
            self._data[key] = value
            return SetNxResult(was_set=True)

