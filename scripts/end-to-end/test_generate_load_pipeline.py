#!/usr/bin/env python3
"""Tests for generated CRM load pipeline behavior on update waves."""

#  Licensed to the Apache Software Foundation (ASF) under one or more
#  contributor license agreements.  See the NOTICE file distributed with
#  this work for additional information regarding copyright ownership.
#  The ASF licenses this file to You under the Apache License, Version 2.0
#  (the "License"); you may not use this file except in compliance with
#  the License.  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path

_MODULE_PATH = Path(__file__).resolve().parent / "generate-load-pipeline.py"
_spec = importlib.util.spec_from_file_location("generate_load_pipeline", _MODULE_PATH)
_module = importlib.util.module_from_spec(_spec)
assert _spec.loader is not None
sys.modules[_spec.name] = _module
_spec.loader.exec_module(_module)


class GenerateLoadPipelineTest(unittest.TestCase):
    def test_update_wave_upserts_customer_satellites(self) -> None:
        pipeline = _module.build_pipeline("2024-02")
        self.assertIn("<type>InsertUpdate</type>", pipeline)
        self.assertIn("<name>customer_demo_out</name>", pipeline)
        self.assertIn("<truncate>N</truncate>", pipeline)

    def test_initial_wave_truncates_with_table_output_only(self) -> None:
        pipeline = _module.build_pipeline("initial")
        self.assertNotIn("<type>InsertUpdate</type>", pipeline)
        self.assertIn("<truncate>Y</truncate>", pipeline)


if __name__ == "__main__":
    unittest.main()