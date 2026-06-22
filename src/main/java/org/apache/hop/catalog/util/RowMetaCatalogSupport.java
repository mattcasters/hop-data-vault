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

package org.apache.hop.catalog.util;

import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.RowMeta;
import org.apache.hop.core.xml.XmlHandler;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** Serialize and deserialize {@link IRowMeta} for catalog persistence. */
public final class RowMetaCatalogSupport {

  private RowMetaCatalogSupport() {}

  public static String toXml(IRowMeta rowMeta) throws HopException {
    if (rowMeta == null) {
      return null;
    }
    try {
      return rowMeta.getMetaXml();
    } catch (Exception e) {
      throw new HopException("Unable to serialize row metadata to XML", e);
    }
  }

  public static IRowMeta fromXml(String rowMetaXml) throws HopException {
    if (rowMetaXml == null || rowMetaXml.isBlank()) {
      return null;
    }
    try {
      Document document = XmlHandler.loadXmlString(rowMetaXml.trim());
      Node rowMetaNode = document.getDocumentElement();
      if (rowMetaNode == null || !RowMeta.XML_META_TAG.equals(rowMetaNode.getNodeName())) {
        throw new HopException("Missing row-meta element in stored record definition");
      }
      return new RowMeta(rowMetaNode);
    } catch (HopException e) {
      throw e;
    } catch (Exception e) {
      throw new HopException("Unable to deserialize row metadata from XML", e);
    }
  }
}