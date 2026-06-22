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

package org.apache.hop.catalog.impl.file;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.model.RecordDefinition;
import org.apache.hop.catalog.model.RecordDefinitionKey;
import org.apache.hop.catalog.model.RecordDefinitionQuery;
import org.apache.hop.catalog.model.RecordDefinitionRef;
import org.apache.hop.catalog.plugin.DataCatalogPlugin;
import org.apache.hop.catalog.spi.IDataCatalog;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.plugin.GuiElementType;
import org.apache.hop.core.gui.plugin.GuiPlugin;
import org.apache.hop.core.gui.plugin.GuiWidgetElement;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadataProvider;

/**
 * Reference data catalog plugin: stores record definitions as JSON files under a configurable
 * directory.
 */
@DataCatalogPlugin(
    id = "FILE",
    name = "File data catalog",
    description = "Stores record definitions as JSON files in a local directory")
@GuiPlugin(id = "GUI-FileDataCatalog")
@Getter
@Setter
public class FileDataCatalog implements IDataCatalog {

  public static final String GUI_PLUGIN_ELEMENT_PARENT_ID = "FileDataCatalog-PluginSpecific-Options";

  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  @HopMetadataProperty private String pluginId = "FILE";

  @GuiWidgetElement(
      order = "10",
      type = GuiElementType.TEXT,
      variables = true,
      label = "i18n::FileDataCatalog.StorageDirectory.Label",
      toolTip = "i18n::FileDataCatalog.StorageDirectory.ToolTip",
      parentId = GUI_PLUGIN_ELEMENT_PARENT_ID)
  @HopMetadataProperty
  private String storageDirectory = "${PROJECT_HOME}/catalog-data";

  private transient Path resolvedRoot;
  private transient String connectionName;

  @Override
  public void connect(
      DataCatalogMeta meta, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    connectionName = meta != null ? meta.getName() : null;
    String resolved =
        variables != null ? variables.resolve(storageDirectory) : storageDirectory;
    if (Utils.isEmpty(resolved)) {
      throw new HopException("File data catalog storage directory is not configured");
    }
    resolvedRoot = Path.of(resolved).toAbsolutePath().normalize();
    try {
      Files.createDirectories(resolvedRoot);
    } catch (IOException e) {
      throw new HopException("Unable to create catalog storage directory: " + resolvedRoot, e);
    }
  }

  @Override
  public void disconnect() {
    resolvedRoot = null;
    connectionName = null;
  }

  @Override
  public void create(RecordDefinition definition) throws HopException {
    ensureConnected();
    definition.validate();
    Path target = toRecordPath(definition.getKey());
    if (Files.exists(target)) {
      throw new HopException("Record definition already exists: " + definition.getKey());
    }
    writeRecord(target, definition);
  }

  @Override
  public RecordDefinition read(RecordDefinitionKey key) throws HopException {
    ensureConnected();
    key.validate();
    Path target = toRecordPath(key);
    if (!Files.exists(target)) {
      return null;
    }
    return readRecord(target);
  }

  @Override
  public void update(RecordDefinition definition) throws HopException {
    ensureConnected();
    definition.validate();
    Path target = toRecordPath(definition.getKey());
    if (!Files.exists(target)) {
      throw new HopException("Record definition does not exist: " + definition.getKey());
    }
    writeRecord(target, definition);
  }

  @Override
  public void delete(RecordDefinitionKey key) throws HopException {
    ensureConnected();
    key.validate();
    Path target = toRecordPath(key);
    try {
      Files.deleteIfExists(target);
    } catch (IOException e) {
      throw new HopException("Unable to delete record definition: " + key, e);
    }
  }

  @Override
  public List<RecordDefinitionRef> list(RecordDefinitionQuery query) throws HopException {
    ensureConnected();
    RecordDefinitionQuery effectiveQuery = query != null ? query : new RecordDefinitionQuery();
    List<RecordDefinitionRef> results = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(resolvedRoot)) {
      paths
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".json"))
          .forEach(
              path -> {
                try {
                  RecordDefinition definition = readRecord(path);
                  if (effectiveQuery.matches(definition)) {
                    results.add(RecordDefinitionRef.of(connectionName, definition));
                  }
                } catch (HopException e) {
                  throw new RuntimeException(e);
                }
              });
    } catch (RuntimeException e) {
      if (e.getCause() instanceof HopException hopException) {
        throw hopException;
      }
      throw e;
    } catch (IOException e) {
      throw new HopException("Unable to list record definitions under " + resolvedRoot, e);
    }
    return results;
  }

  private void ensureConnected() throws HopException {
    if (resolvedRoot == null) {
      throw new HopException("File data catalog is not connected");
    }
  }

  private Path toRecordPath(RecordDefinitionKey key) throws HopException {
    Path path = resolvedRoot;
    for (String segment : key.getNamespace().split("/")) {
      if (!segment.isBlank()) {
        path = path.resolve(sanitizePathSegment(segment));
      }
    }
    return path.resolve(sanitizePathSegment(key.getName()) + ".json");
  }

  static String sanitizePathSegment(String segment) {
    if (segment == null) {
      return "_";
    }
    return segment.replace('\\', '/').replace("..", "_");
  }

  private void writeRecord(Path target, RecordDefinition definition) throws HopException {
    try {
      Files.createDirectories(target.getParent());
      RecordDefinitionDocument doc = RecordDefinitionDocument.from(definition);
      MAPPER.writeValue(target.toFile(), doc);
    } catch (IOException e) {
      throw new HopException("Unable to write record definition to " + target, e);
    }
  }

  private RecordDefinition readRecord(Path path) throws HopException {
    try {
      RecordDefinitionDocument doc = MAPPER.readValue(path.toFile(), RecordDefinitionDocument.class);
      return doc.toRecordDefinition();
    } catch (IOException e) {
      throw new HopException("Unable to read record definition from " + path, e);
    }
  }
}