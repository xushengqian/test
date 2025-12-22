from __future__ import annotations

import argparse
import socketserver
from typing import Tuple

from mini_redis.commands import execute
from mini_redis.resp import RespError, RespReader, encode_error
from mini_redis.store import InMemoryStore


class MiniRedisHandler(socketserver.StreamRequestHandler):
    # Shared store across all connections
    store = InMemoryStore()

    def handle(self) -> None:
        reader = RespReader(self.rfile)
        while True:
            try:
                req = reader.read_request()
            except EOFError:
                return
            except RespError as e:
                self.wfile.write(encode_error(f"ERR {e}"))
                self.wfile.flush()
                return

            try:
                res = execute(self.store, req.parts)
            except Exception as e:  # keep server alive
                self.wfile.write(encode_error(f"ERR internal error: {type(e).__name__}"))
                self.wfile.flush()
                return

            self.wfile.write(res.payload)
            self.wfile.flush()


class ThreadingTCPServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    allow_reuse_address = True
    daemon_threads = True


def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Tiny Redis-like server with SETNX")
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=6380)
    return p.parse_args()


def main() -> None:
    args = parse_args()
    addr: Tuple[str, int] = (args.host, args.port)
    with ThreadingTCPServer(addr, MiniRedisHandler) as srv:
        print(f"mini-redis listening on {args.host}:{args.port}")
        srv.serve_forever()


if __name__ == "__main__":
    main()

