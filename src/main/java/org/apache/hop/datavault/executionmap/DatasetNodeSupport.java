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

package org.apache.hop.datavault.executionmap;

import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNode;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapNodeType;

/** Creates and deduplicates dataset leaf nodes in execution maps. */
public final class DatasetNodeSupport {

  public static final String DATASET_PATH_PREFIX = "dataset://";

  private DatasetNodeSupport() {}

  public static String datasetPath(String namespace, String name) {
    String ns = Utils.isEmpty(namespace) ? "default" : namespace;
    String table = Utils.isEmpty(name) ? "unknown" : name;
    return DATASET_PATH_PREFIX + ns + "/" + table;
  }

  public static String qualifiedLabel(String namespace, String name) {
    if (Utils.isEmpty(namespace)) {
      return name;
    }
    if (Utils.isEmpty(name)) {
      return namespace;
    }
    return namespace + "." + name;
  }

  public static String resolveValue(IVariables variables, String value) {
    if (Utils.isEmpty(value) || variables == null) {
      return value;
    }
    return variables.resolve(value);
  }

  public static String getOrCreateDatasetNode(
      ExecutionMapContext context,
      ExecutionMapNodeType nodeType,
      String namespace,
      String datasetName,
      String datasetKind,
      String parentNodeId) {
    if (context == null || nodeType == null) {
      return null;
    }
    String resolvedNamespace = resolveValue(context.getVariables(), namespace);
    String resolvedName = resolveValue(context.getVariables(), datasetName);
    String path = datasetPath(resolvedNamespace, resolvedName);
    String existing = context.existingNodeIdForPath(context.resolvePath(path));
    if (!Utils.isEmpty(existing)) {
      return existing;
    }
    ExecutionMapNode node = new ExecutionMapNode();
    node.setNodeType(nodeType);
    node.setName(qualifiedLabel(resolvedNamespace, resolvedName));
    node.setPath(path);
    node.setParentNodeId(parentNodeId);
    if (!Utils.isEmpty(datasetKind)) {
      node.setProperty("datasetKind", datasetKind);
    }
    node.setProperty("datasetNamespace", resolvedNamespace);
    node.setProperty("datasetName", resolvedName);
    context.addNode(node);
    return node.getId();
  }
}