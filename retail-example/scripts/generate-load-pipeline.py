#!/usr/bin/env python3
"""Generate load-e2e-sources-to-crm.hpl for the retail example project."""

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
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

from __future__ import annotations

import argparse
import importlib.util
from pathlib import Path

_catalog_module_path = Path(__file__).resolve().parent / "generate-catalog-sources.py"
_spec = importlib.util.spec_from_file_location("generate_catalog_sources", _catalog_module_path)
_catalog_module = importlib.util.module_from_spec(_spec)
assert _spec.loader is not None
_spec.loader.exec_module(_catalog_module)
SOURCE_DEFINITIONS = _catalog_module.SOURCE_DEFINITIONS

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_PROJECT_HOME = REPO_ROOT / "retail-example"

HOP_TYPE = {
    "Integer": ("Integer", "#", "0"),
    "String": ("String", "", "-1"),
    "Number": ("Number", "", "2"),
    "Timestamp": ("Date", "yyyy-MM-dd", "-1"),
}


def csv_field_xml(name: str, data_type: str, length: str, precision: str, _hop_type: int) -> str:
    hop_type, fmt, prec = HOP_TYPE[data_type]
    length_val = length if length else "-1"
    precision_val = precision if precision and precision != "0" else prec
    if data_type == "Integer":
        precision_val = "0"
    return f"""      <field>
        <name>{name}</name>
        <length>{length_val}</length>
        <type>{hop_type}</type>
        <format>{fmt}</format>
        <trim_type>none</trim_type>
        <precision>{precision_val}</precision>
        <currency>$</currency>
        <decimal>.</decimal>
        <group>,</group>
      </field>"""


def csv_input_transform(prefix: str, wave: str, yloc: int) -> str:
    fields = []
    for _name, definition in SOURCE_DEFINITIONS.items():
        if definition["prefix"] == prefix:
            for field in definition["fields"]:
                fields.append(csv_field_xml(*field))
            break
    fields_xml = "\n".join(fields)
    return f"""  <transform>
    <type>CSVInput</type>
    <name>{prefix}_csv</name>
    <filename>${{PROJECT_HOME}}/files/{prefix}_{wave}.csv</filename>
    <include_filename>N</include_filename>
    <rownum_field/>
    <header>Y</header>
    <separator>,</separator>
    <enclosure/>
    <buffer_size>50000</buffer_size>
    <lazy_conversion>N</lazy_conversion>
    <add_filename_result>N</add_filename_result>
    <parallel>N</parallel>
    <encoding/>
    <newline_possible>N</newline_possible>
    <schemaDefinition/>
    <ignoreFields>N</ignoreFields>
    <fields>
{fields_xml}
    </fields>
    <distribute>Y</distribute>
    <copies>1</copies>
    <GUI>
      <xloc>160</xloc>
      <yloc>{yloc}</yloc>
    </GUI>
    <partitioning>
      <method>none</method>
      <schema_name/>
    </partitioning>
    <attributes/>
  </transform>"""


def table_output_transform(table_name: str, yloc: int, truncate: str) -> str:
    return f"""  <transform>
    <type>TableOutput</type>
    <name>{table_name}_out</name>
    <connection>CRM</connection>
    <schema/>
    <table>{table_name}</table>
    <commit>1000</commit>
    <truncate>{truncate}</truncate>
    <only_when_have_rows>N</only_when_have_rows>
    <ignore_errors>N</ignore_errors>
    <use_batch>Y</use_batch>
    <partitioning_enabled>N</partitioning_enabled>
    <partitioning_field/>
    <partitioning_daily>N</partitioning_daily>
    <partitioning_monthly>Y</partitioning_monthly>
    <tablename_in_field>N</tablename_in_field>
    <tablename_field/>
    <tablename_in_table>Y</tablename_in_table>
    <return_keys>N</return_keys>
    <return_field/>
    <specify_fields>N</specify_fields>
    <auto_update_table_structure>N</auto_update_table_structure>
    <always_drop_and_recreate>N</always_drop_and_recreate>
    <add_columns>N</add_columns>
    <drop_columns>N</drop_columns>
    <change_column_types>N</change_column_types>
    <distribute>Y</distribute>
    <copies>1</copies>
    <GUI>
      <xloc>400</xloc>
      <yloc>{yloc}</yloc>
    </GUI>
    <partitioning>
      <method>none</method>
      <schema_name/>
    </partitioning>
    <attributes/>
  </transform>"""


def build_pipeline(wave: str) -> str:
    truncate = "Y" if wave == "initial" else "N"
    prefixes = [definition["prefix"] for definition in SOURCE_DEFINITIONS.values()]
    transforms = []
    hops = []
    for index, prefix in enumerate(prefixes):
        yloc = 64 + index * 48
        transforms.append(csv_input_transform(prefix, wave, yloc))
        transforms.append(table_output_transform(prefix, yloc, truncate))
        hops.append(
            f"""    <hop>
      <from>{prefix}_csv</from>
      <to>{prefix}_out</to>
      <enabled>Y</enabled>
    </hop>"""
        )
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<pipeline>
  <info>
    <capture_transform_performance>N</capture_transform_performance>
    <transform_performance_capturing_delay>1000</transform_performance_capturing_delay>
    <transform_performance_capturing_size_limit>100</transform_performance_capturing_size_limit>
    <pipeline_type>Normal</pipeline_type>
    <pipeline_status>-1</pipeline_status>
    <parameters/>
    <name>load-e2e-sources-to-crm</name>
    <name_sync_with_filename>Y</name_sync_with_filename>
    <created_user>-</created_user>
    <modified_user>-</modified_user>
    <created_date>2026/07/01 00:00:00.000</created_date>
    <modified_date>2026/07/01 00:00:00.000</modified_date>
  </info>
{chr(10).join(transforms)}
  <order>
{chr(10).join(hops)}
  </order>
  <notepads/>
  <attributes/>
  <transform_error_handling/>
</pipeline>
"""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project-home", type=Path, default=DEFAULT_PROJECT_HOME)
    parser.add_argument(
        "--wave",
        default="initial",
        help="CSV wave suffix embedded in generated pipeline filenames",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    pipeline_dir = args.project_home / "pipelines"
    pipeline_dir.mkdir(parents=True, exist_ok=True)
    path = pipeline_dir / "load-e2e-sources-to-crm.hpl"
    path.write_text(build_pipeline(args.wave), encoding="utf-8")
    print(f"Wrote {path}")


if __name__ == "__main__":
    main()