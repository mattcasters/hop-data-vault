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

package org.apache.hop.catalog.discovery;

import java.util.regex.Pattern;
import org.apache.commons.vfs2.FileObject;
import org.apache.hop.core.Const;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.i18n.BaseMessages;

/** Resolved physical location used for record-definition schema discovery. */
public final class PhysicalSourceRef {

  private static final Class<?> PKG = PhysicalSourceRef.class;

  private final String databaseConnectionName;
  private final String schemaName;
  private final String tableName;
  private final String filePath;
  private final String folder;
  private final String includeFileMask;
  private final String excludeFileMask;
  private final boolean includeSubfolders;

  private PhysicalSourceRef(Builder builder) {
    this.databaseConnectionName = builder.databaseConnectionName;
    this.schemaName = builder.schemaName;
    this.tableName = builder.tableName;
    this.filePath = builder.filePath;
    this.folder = builder.folder;
    this.includeFileMask = builder.includeFileMask;
    this.excludeFileMask = builder.excludeFileMask;
    this.includeSubfolders = builder.includeSubfolders;
  }

  public String getDatabaseConnectionName() {
    return databaseConnectionName;
  }

  public String getSchemaName() {
    return schemaName;
  }

  public String getTableName() {
    return tableName;
  }

  public String getFilePath() {
    return filePath;
  }

  public String getFolder() {
    return folder;
  }

  public String getIncludeFileMask() {
    return includeFileMask;
  }

  public String getExcludeFileMask() {
    return excludeFileMask;
  }

  public boolean isIncludeSubfolders() {
    return includeSubfolders;
  }

  public String resolveDiscoveryFilePath(IVariables variables) throws HopException {
    String resolvedPath = resolve(variables, filePath);
    if (!Utils.isEmpty(resolvedPath)) {
      return resolvedPath;
    }

    String resolvedFolder = resolve(variables, folder);
    if (Utils.isEmpty(resolvedFolder)) {
      throw new HopException(
          BaseMessages.getString(PKG, "PhysicalSourceRef.Error.MissingFileLocation"));
    }

    String includeMask = Const.NVL(resolve(variables, includeFileMask), ".*");
    String excludeMask = Const.NVL(resolve(variables, excludeFileMask), "");
    Pattern includePattern = Pattern.compile(includeMask);
    Pattern excludePattern = Utils.isEmpty(excludeMask) ? null : Pattern.compile(excludeMask);

    try {
      FileObject folderObject = HopVfs.getFileObject(resolvedFolder, variables);
      if (!folderObject.exists() || !folderObject.isFolder()) {
        throw new HopException(
            BaseMessages.getString(PKG, "PhysicalSourceRef.Error.MissingFolder", resolvedFolder));
      }
      String match = findFirstMatchingFile(folderObject, includePattern, excludePattern);
      if (Utils.isEmpty(match)) {
        throw new HopException(
            BaseMessages.getString(
                PKG, "PhysicalSourceRef.Error.NoMatchingFile", resolvedFolder, includeMask));
      }
      return match;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException(
          BaseMessages.getString(PKG, "PhysicalSourceRef.Error.ResolveFile", resolvedFolder), e);
    }
  }

  private static String findFirstMatchingFile(
      FileObject folderObject, Pattern includePattern, Pattern excludePattern)
      throws Exception {
    FileObject[] children = folderObject.getChildren();
    if (children == null) {
      return null;
    }
    for (FileObject child : children) {
      if (child == null || !child.exists()) {
        continue;
      }
      if (child.isFolder()) {
        continue;
      }
      String baseName = child.getName().getBaseName();
      if (!includePattern.matcher(baseName).matches()) {
        continue;
      }
      if (excludePattern != null && excludePattern.matcher(baseName).matches()) {
        continue;
      }
      return HopVfs.getFilename(child);
    }
    return null;
  }

  private static String resolve(IVariables variables, String value) {
    return variables != null ? variables.resolve(value) : value;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String databaseConnectionName;
    private String schemaName;
    private String tableName;
    private String filePath;
    private String folder;
    private String includeFileMask;
    private String excludeFileMask;
    private boolean includeSubfolders;

    public Builder databaseConnectionName(String value) {
      this.databaseConnectionName = value;
      return this;
    }

    public Builder schemaName(String value) {
      this.schemaName = value;
      return this;
    }

    public Builder tableName(String value) {
      this.tableName = value;
      return this;
    }

    public Builder filePath(String value) {
      this.filePath = value;
      return this;
    }

    public Builder folder(String value) {
      this.folder = value;
      return this;
    }

    public Builder includeFileMask(String value) {
      this.includeFileMask = value;
      return this;
    }

    public Builder excludeFileMask(String value) {
      this.excludeFileMask = value;
      return this;
    }

    public Builder includeSubfolders(boolean value) {
      this.includeSubfolders = value;
      return this;
    }

    public PhysicalSourceRef build() {
      return new PhysicalSourceRef(this);
    }
  }
}