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

package org.apache.hop.catalog.xp;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.catalog.impl.file.FileDataCatalog;
import org.apache.hop.catalog.metadata.DataCatalogMeta;
import org.apache.hop.catalog.plugin.DataCatalogPlugin;
import org.apache.hop.catalog.plugin.DataCatalogPluginType;
import org.apache.hop.catalog.registry.RecordDefinitionRegistry;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.extension.ExtensionPoint;
import org.apache.hop.core.extension.ExtensionPointPluginType;
import org.apache.hop.core.extension.IExtensionPoint;
import org.apache.hop.core.logging.ILogChannel;
import org.apache.hop.core.plugins.IPlugin;
import org.apache.hop.core.plugins.PluginRegistry;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.HopMetadata;
import org.apache.hop.metadata.plugin.MetadataPluginType;

@ExtensionPoint(
    id = "RegisterDataCatalogPluginTypeExtensionPoint",
    extensionPointId = "HopEnvironmentAfterInit",
    description = "Register the Data Catalog plugin type and connection metadata")
public class RegisterDataCatalogPluginTypeExtensionPoint
    implements IExtensionPoint<PluginRegistry> {

  @Override
  public void callExtensionPoint(
      ILogChannel log, IVariables variables, PluginRegistry pluginRegistry) throws HopException {

    pluginRegistry.registerType(DataCatalogPluginType.getInstance());

    IPlugin thisPlugin =
        pluginRegistry.findPluginWithId(
            ExtensionPointPluginType.class, "RegisterDataCatalogPluginTypeExtensionPoint");
    ClassLoader classLoader = getClass().getClassLoader();
    List<String> libraries =
        thisPlugin != null ? new ArrayList<>(thisPlugin.getLibraries()) : new ArrayList<>();
    URL pluginUrl = thisPlugin != null ? thisPlugin.getPluginDirectory() : null;

    pluginRegistry.registerPluginClass(
        classLoader,
        libraries,
        pluginUrl,
        DataCatalogMeta.class.getName(),
        MetadataPluginType.class,
        HopMetadata.class,
        false);

    if (pluginRegistry.findPluginWithId(DataCatalogPluginType.class, "FILE") == null) {
      pluginRegistry.registerPluginClass(
          classLoader,
          libraries,
          pluginUrl,
          FileDataCatalog.class.getName(),
          DataCatalogPluginType.class,
          DataCatalogPlugin.class,
          false);
    }

    RecordDefinitionRegistry.getInstance().environmentReady();

    if (log != null) {
      log.logBasic("Data Catalog plugin type and metadata registered");
    }
  }
}