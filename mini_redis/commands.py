from __future__ import annotations

from dataclasses import dataclass
from typing import List

from .resp import encode_bulk_string, encode_error, encode_integer, encode_simple_string
from .store import InMemoryStore


@dataclass(frozen=True)
class CommandResult:
    payload: bytes


def _wrong_arity(cmd: str) -> CommandResult:
    return CommandResult(payload=encode_error(f"ERR wrong number of arguments for '{cmd.lower()}' command"))


def execute(store: InMemoryStore, parts: List[bytes]) -> CommandResult:
    if not parts:
        return CommandResult(payload=encode_error("ERR empty command"))

    cmd = parts[0].decode("utf-8", errors="replace").upper()
    args = parts[1:]

    if cmd == "PING":
        if len(args) == 0:
            return CommandResult(payload=encode_simple_string("PONG"))
        if len(args) == 1:
            return CommandResult(payload=encode_bulk_string(args[0]))
        return _wrong_arity("PING")

    if cmd == "SETNX":
        if len(args) != 2:
            return _wrong_arity("SETNX")
        key, value = args
        res = store.setnx(key, value)
        return CommandResult(payload=encode_integer(1 if res.was_set else 0))

    if cmd == "GET":
        if len(args) != 1:
            return _wrong_arity("GET")
        val = store.get(args[0])
        return CommandResult(payload=encode_bulk_string(val))

    if cmd == "DEL":
        if len(args) < 1:
            return _wrong_arity("DEL")
        n = 0
        for k in args:
            n += store.delete(k)
        return CommandResult(payload=encode_integer(n))

    if cmd == "EXISTS":
        if len(args) < 1:
            return _wrong_arity("EXISTS")
        n = 0
        for k in args:
            n += 1 if store.exists(k) else 0
        return CommandResult(payload=encode_integer(n))

    return CommandResult(payload=encode_error(f"ERR unknown command '{cmd.lower()}'"))

