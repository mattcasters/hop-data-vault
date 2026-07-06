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

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/** Resolves local and cross-model Data Vault table references to concrete table definitions. */
public final class DvTableResolutionSupport {

  private DvTableResolutionSupport() {}

  public static DvHub resolveHub(
      DataVaultModel model, String hubName, IVariables variables, IHopMetadataProvider metadataProvider) {
    if (model == null || Utils.isEmpty(hubName)) {
      return null;
    }
    String resolvedName = resolve(hubName, variables);
    IDvTable table = model.findTable(resolvedName);
    if (table instanceof DvHub hub) {
      return hub;
    }
    if (table instanceof DvTableReference reference
        && reference.getReferencedTableType() == DvTableType.HUB) {
      IDvTable target = resolveReferenceTarget(model, reference, variables, metadataProvider);
      if (target instanceof DvHub hub) {
        return hub;
      }
    }
    return null;
  }

  public static DvLink resolveLink(
      DataVaultModel model, String linkName, IVariables variables, IHopMetadataProvider metadataProvider) {
    if (model == null || Utils.isEmpty(linkName)) {
      return null;
    }
    String resolvedName = resolve(linkName, variables);
    IDvTable table = model.findTable(resolvedName);
    if (table instanceof DvLink link) {
      return link;
    }
    if (table instanceof DvTableReference reference
        && reference.getReferencedTableType() == DvTableType.LINK) {
      IDvTable target = resolveReferenceTarget(model, reference, variables, metadataProvider);
      if (target instanceof DvLink link) {
        return link;
      }
    }
    return null;
  }

  public static DvSatellite resolveSatellite(
      DataVaultModel model,
      String satelliteName,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (model == null || Utils.isEmpty(satelliteName)) {
      return null;
    }
    String resolvedName = resolve(satelliteName, variables);
    IDvTable table = model.findTable(resolvedName);
    if (table instanceof DvSatellite satellite) {
      return satellite;
    }
    if (table instanceof DvTableReference reference
        && reference.getReferencedTableType() == DvTableType.SATELLITE) {
      IDvTable target = resolveReferenceTarget(model, reference, variables, metadataProvider);
      if (target instanceof DvSatellite satellite) {
        return satellite;
      }
    }
    return null;
  }

  public static IDvTable resolveReferenceTarget(
      DataVaultModel model,
      DvTableReference reference,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    if (model == null || reference == null) {
      return null;
    }
    String referencedName = resolve(reference.getReferencedTableName(), variables);
    if (Utils.isEmpty(referencedName)) {
      return null;
    }
    if (!Utils.isEmpty(reference.getReferencedModelFilename())) {
      try {
        DataVaultModel externalModel =
            DvModelLoadSupport.loadDataVaultModel(
                reference.getReferencedModelFilename(),
                model.getFilename(),
                variables,
                metadataProvider);
        IDvTable referenced = externalModel.findTable(referencedName);
        if (referenced == null || referenced instanceof DvTableReference) {
          return null;
        }
        DvTableType expectedType = reference.getReferencedTableType();
        if (expectedType != null && referenced.getTableType() != expectedType) {
          return null;
        }
        return referenced;
      } catch (HopException ignored) {
        return null;
      }
    }
    IDvTable referenced = model.findTable(referencedName);
    if (referenced == null || referenced instanceof DvTableReference) {
      return null;
    }
    DvTableType expectedType = reference.getReferencedTableType();
    if (expectedType != null && referenced.getTableType() != expectedType) {
      return null;
    }
    return referenced;
  }

  public static String resolvePhysicalTableName(
      DataVaultModel model,
      String tableName,
      IVariables variables,
      IHopMetadataProvider metadataProvider) {
    IDvTable table = model != null ? model.findTable(resolve(tableName, variables)) : null;
    if (table instanceof DvTableReference reference) {
      IDvTable target = resolveReferenceTarget(model, reference, variables, metadataProvider);
      if (target != null && !Utils.isEmpty(target.getTableName())) {
        return resolve(target.getTableName(), variables);
      }
      if (target != null && !Utils.isEmpty(target.getName())) {
        return resolve(target.getName(), variables);
      }
      return null;
    }
    if (table != null && !Utils.isEmpty(table.getTableName())) {
      return resolve(table.getTableName(), variables);
    }
    if (table != null && !Utils.isEmpty(table.getName())) {
      return resolve(table.getName(), variables);
    }
    return null;
  }

  public static boolean isTableReference(DataVaultModel model, String tableName) {
    if (model == null || Utils.isEmpty(tableName)) {
      return false;
    }
    IDvTable table = model.findTable(tableName);
    return table instanceof DvTableReference;
  }

  public static boolean isExternalTableReference(DvTableReference reference) {
    return reference != null && !Utils.isEmpty(reference.getReferencedModelFilename());
  }

  /** Label for the model that owns the referenced table (external path basename or this model). */
  public static String resolveReferenceSourceModelDisplayName(
      DataVaultModel model, DvTableReference reference, IVariables variables) {
    if (reference == null) {
      return "";
    }
    if (isExternalTableReference(reference)) {
      return modelBasename(resolve(reference.getReferencedModelFilename(), variables));
    }
    return resolveModelDisplayName(model, variables);
  }

  public static String resolveModelDisplayName(DataVaultModel model, IVariables variables) {
    if (model == null) {
      return "";
    }
    String name = resolve(model.getName(), variables);
    if (!Utils.isEmpty(name)) {
      return name;
    }
    return modelBasename(resolve(model.getFilename(), variables));
  }

  public static boolean isHubLike(
      DataVaultModel model, String tableName, IVariables variables, IHopMetadataProvider metadataProvider) {
    return resolveHub(model, tableName, variables, metadataProvider) != null;
  }

  public static boolean isLinkLike(
      DataVaultModel model, String tableName, IVariables variables, IHopMetadataProvider metadataProvider) {
    return resolveLink(model, tableName, variables, metadataProvider) != null;
  }

  private static String resolve(String value, IVariables variables) {
    if (value == null) {
      return null;
    }
    return variables != null ? variables.resolve(value) : value;
  }

  private static String modelBasename(String path) {
    if (Utils.isEmpty(path)) {
      return "";
    }
    String normalized = path.replace('\\', '/');
    int slash = normalized.lastIndexOf('/');
    if (slash >= 0) {
      normalized = normalized.substring(slash + 1);
    }
    if (normalized.endsWith(".hdv")) {
      normalized = normalized.substring(0, normalized.length() - 4);
    }
    return normalized;
  }
}