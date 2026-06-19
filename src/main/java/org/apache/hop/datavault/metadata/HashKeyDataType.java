/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.hop.datavault.metadata;

/**
 * The physical data type used to store hash keys in the Data Vault tables.
 *
 * <p>The actual representation depends on the HashKeyDataType configured on the DvHashKey transform:
 * <ul>
 *   <li>STRING: decimal numbers 0-255 separated by "-" (DvHashKey STRING mode).
 *       For MD5: max 63 chars. Larger for SHA*.</li>
 *   <li>HEX: lowercase hexadecimal (DvHashKey HEX mode). MD5=32, SHA256=64, etc.</li>
 *   <li>BINARY: raw bytes (16 for MD5, 32 for SHA256, etc.). Preferred for production.</li>
 * </ul>
 * STRING and HEX are easier for debugging and some tools but use more space and may have collation issues.
 * BINARY is strongly recommended for production DV2.0 for storage efficiency and performance.
 */
public enum HashKeyDataType {
  /** Store using the decimal-dash string format from DvHashKey (e.g. "213-29-140-..."). */
  STRING,

  /** Store as hexadecimal string (e.g. "d41d8cd98f00b204e9800998ecf8427e" for MD5). */
  HEX,

  /** Store as raw binary bytes. */
  BINARY
}
