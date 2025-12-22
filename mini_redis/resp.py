from __future__ import annotations

from dataclasses import dataclass
from typing import BinaryIO, List, Optional


class RespError(Exception):
    pass


def encode_simple_string(s: str) -> bytes:
    return f"+{s}\r\n".encode("utf-8")


def encode_error(s: str) -> bytes:
    return f"-{s}\r\n".encode("utf-8")


def encode_integer(i: int) -> bytes:
    return f":{i}\r\n".encode("utf-8")


def encode_bulk_string(s: Optional[bytes]) -> bytes:
    if s is None:
        return b"$-1\r\n"
    return b"$%d\r\n%s\r\n" % (len(s), s)


def encode_array(items: List[bytes]) -> bytes:
    out = [f"*{len(items)}\r\n".encode("utf-8")]
    for it in items:
        out.append(encode_bulk_string(it))
    return b"".join(out)


@dataclass
class RespRequest:
    parts: List[bytes]

    def as_strs(self) -> List[str]:
        return [p.decode("utf-8", errors="replace") for p in self.parts]


class RespReader:
    """
    Minimal RESP2 reader that supports only Arrays of Bulk Strings,
    which is what redis-cli uses for commands.
    """

    def __init__(self, rfile: BinaryIO):
        self._r = rfile

    def _readline(self) -> bytes:
        line = self._r.readline()
        if not line:
            raise EOFError()
        if not line.endswith(b"\r\n"):
            raise RespError("protocol error: expected CRLF")
        return line[:-2]

    def read_request(self) -> RespRequest:
        first = self._readline()
        if not first or first[:1] != b"*":
            raise RespError("protocol error: expected array")
        try:
            n = int(first[1:])
        except ValueError as e:
            raise RespError("protocol error: invalid array length") from e
        if n < 0:
            raise RespError("protocol error: invalid array length")

        parts: List[bytes] = []
        for _ in range(n):
            hdr = self._readline()
            if not hdr or hdr[:1] != b"$":
                raise RespError("protocol error: expected bulk string")
            try:
                ln = int(hdr[1:])
            except ValueError as e:
                raise RespError("protocol error: invalid bulk length") from e
            if ln == -1:
                parts.append(b"")
                continue
            if ln < -1:
                raise RespError("protocol error: invalid bulk length")
            data = self._r.read(ln + 2)
            if len(data) != ln + 2 or not data.endswith(b"\r\n"):
                raise RespError("protocol error: bulk not terminated")
            parts.append(data[:-2])

        return RespRequest(parts=parts)

