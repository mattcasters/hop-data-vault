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

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.core.vfs.HopVfs;
import org.apache.hop.core.xml.XmlFormatter;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.hopgui.file.executionmap.HopExecutionMapFileType;
import org.apache.hop.datavault.metadata.executionmap.ExecutionMapDocument;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** Loads and saves `.hem` execution map documents. */
public final class ExecutionMapPersistence {

  private ExecutionMapPersistence() {}

  public static ExecutionMapDocument load(
      String filename, IHopMetadataProvider metadataProvider, IVariables variables)
      throws HopException {
    try {
      Document document = XmlHandler.loadXmlFile(filename);
      Node rootNode = XmlHandler.getSubNode(document, HopExecutionMapFileType.XML_TAG);
      if (rootNode == null) {
        rootNode = document.getDocumentElement();
      }
      ExecutionMapDocument executionMap = new ExecutionMapDocument();
      XmlMetadataUtil.deSerializeFromXml(
          rootNode, ExecutionMapDocument.class, executionMap, metadataProvider);
      executionMap.setFilename(filename);
      return executionMap;
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to load execution map file: " + filename, e);
    }
  }

  public static void save(
      ExecutionMapDocument document, String filename, IVariables variables) throws HopException {
    if (document == null) {
      throw new HopException("No execution map document to save");
    }
    try {
      String xml =
          XmlHandler.getLicenseHeader(
              variables != null ? variables : Variables.getADefaultVariableSpace())
              + XmlFormatter.format(
                  XmlHandler.aroundTag(
                      HopExecutionMapFileType.XML_TAG,
                      XmlMetadataUtil.serializeObjectToXml(document)));
      try (OutputStream outputStream =
          HopVfs.getOutputStream(filename, false)) {
        outputStream.write(xml.getBytes(StandardCharsets.UTF_8));
      }
      document.setFilename(filename);
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to save execution map file: " + filename, e);
    }
  }
}