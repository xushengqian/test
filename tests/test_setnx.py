import unittest

from mini_redis.commands import execute
from mini_redis.resp import encode_bulk_string
from mini_redis.store import InMemoryStore


class TestSetNx(unittest.TestCase):
    def test_setnx_sets_only_if_absent(self) -> None:
        store = InMemoryStore()

        r1 = execute(store, [b"SETNX", b"k", b"v1"]).payload
        self.assertEqual(r1, b":1\r\n")

        r2 = execute(store, [b"SETNX", b"k", b"v2"]).payload
        self.assertEqual(r2, b":0\r\n")

        r3 = execute(store, [b"GET", b"k"]).payload
        self.assertEqual(r3, encode_bulk_string(b"v1"))

    def test_wrong_arity(self) -> None:
        store = InMemoryStore()
        r = execute(store, [b"SETNX", b"only_key"]).payload
        self.assertTrue(r.startswith(b"-ERR wrong number of arguments"))


if __name__ == "__main__":
    unittest.main()

