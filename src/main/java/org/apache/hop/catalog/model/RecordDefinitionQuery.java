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
 */

package org.apache.hop.catalog.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.util.Utils;

/** Optional filters when listing record definitions from a catalog. */
@Getter
@Setter
public class RecordDefinitionQuery {

  private String namespacePrefix;
  private RecordDefinitionType type;
  private String nameContains;
  private List<String> tags = new ArrayList<>();

  public boolean matches(RecordDefinition definition) {
    if (definition == null || definition.getKey() == null) {
      return false;
    }
    if (!Utils.isEmpty(namespacePrefix)
        && (definition.getKey().getNamespace() == null
            || !definition.getKey().getNamespace().startsWith(namespacePrefix))) {
      return false;
    }
    if (type != null && definition.getType() != type) {
      return false;
    }
    if (!Utils.isEmpty(nameContains)
        && (definition.getKey().getName() == null
            || !definition.getKey().getName().contains(nameContains))) {
      return false;
    }
    if (tags != null && !tags.isEmpty()) {
      if (definition.getTags() == null || definition.getTags().isEmpty()) {
        return false;
      }
      for (String tag : tags) {
        if (!definition.getTags().contains(tag)) {
          return false;
        }
      }
    }
    return true;
  }
}