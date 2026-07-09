#!/usr/bin/env python3
"""Tests for retail CSV data generation, including PK-unique warehouse_product pairs."""

from __future__ import annotations

import csv
import importlib.util
import random
import sys
import tempfile
import unittest
from pathlib import Path

_MODULE_PATH = Path(__file__).resolve().parent / "generate-retail-data.py"
_spec = importlib.util.spec_from_file_location("generate_retail_data", _MODULE_PATH)
_module = importlib.util.module_from_spec(_spec)
assert _spec.loader is not None
sys.modules[_spec.name] = _module
_spec.loader.exec_module(_module)


class UniqueRandomPairsTest(unittest.TestCase):
    def test_returns_unique_pairs_up_to_requested_count(self) -> None:
        rng = random.Random(42)
        pairs = _module.unique_random_pairs(rng, list(range(1, 6)), list(range(1, 11)), 20)
        self.assertEqual(20, len(pairs))
        self.assertEqual(20, len(set(pairs)))

    def test_caps_at_cartesian_product_size(self) -> None:
        rng = random.Random(7)
        pairs = _module.unique_random_pairs(rng, [1, 2], [10, 20], 100)
        self.assertEqual(4, len(pairs))
        self.assertEqual(4, len(set(pairs)))

    def test_empty_inputs_return_empty_list(self) -> None:
        rng = random.Random(1)
        self.assertEqual([], _module.unique_random_pairs(rng, [], [1, 2], 5))
        self.assertEqual([], _module.unique_random_pairs(rng, [1], [], 5))
        self.assertEqual([], _module.unique_random_pairs(rng, [1], [2], 0))


class WarehouseProductCsvTest(unittest.TestCase):
    def test_initial_wave_csv_has_unique_primary_keys(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            files_dir = Path(tmp)
            scale = _module.Scale(customers=100, products=200, orders=500, warehouses=10)
            rng = random.Random(99)
            _module.generate_initial(files_dir, scale, rng)

            with (files_dir / "warehouse_product_initial.csv").open(encoding="utf-8") as handle:
                rows = list(csv.DictReader(handle))
            keys = [(row["warehouse_id"], row["product_id"]) for row in rows]
            self.assertGreater(len(keys), 0)
            self.assertEqual(len(keys), len(set(keys)), "duplicate warehouse_id/product_id in CSV")


if __name__ == "__main__":
    unittest.main()