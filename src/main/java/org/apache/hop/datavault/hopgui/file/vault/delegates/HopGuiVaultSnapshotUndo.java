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

package org.apache.hop.datavault.hopgui.file.vault.delegates;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.xml.XmlHandler;
import org.apache.hop.datavault.config.DataVaultConfigSingleton;
import org.apache.hop.datavault.hopgui.file.vault.HopVaultFileType;
import org.apache.hop.datavault.metadata.DataVaultModel;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.metadata.serializer.xml.XmlMetadataUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** Gzip-compressed XML snapshots of a {@link DataVaultModel} for undo/redo. */
public class HopGuiVaultSnapshotUndo {

  private final List<byte[]> undoStack = new ArrayList<>();
  private final List<byte[]> redoStack = new ArrayList<>();
  private boolean applyingSnapshot;

  public void clear() {
    undoStack.clear();
    redoStack.clear();
  }

  public boolean canUndo() {
    return !undoStack.isEmpty();
  }

  public boolean canRedo() {
    return !redoStack.isEmpty();
  }

  public boolean isApplyingSnapshot() {
    return applyingSnapshot;
  }

  /** Capture the current model state and push it onto the undo stack. Clears redo. */
  public void markChange(DataVaultModel model, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (applyingSnapshot || model == null) {
      return;
    }
    pushSnapshot(captureSnapshot(model, metadataProvider));
  }

  /** Push a previously captured snapshot onto the undo stack. Clears redo. */
  public void pushSnapshot(byte[] snapshot) {
    if (applyingSnapshot || snapshot == null) {
      return;
    }
    undoStack.add(snapshot);
    trimStack(undoStack);
    redoStack.clear();
  }

  public byte[] captureSnapshot(DataVaultModel model, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (model == null) {
      throw new HopException("Cannot capture snapshot of a null Data Vault model");
    }
    try {
      String xml =
          XmlHandler.aroundTag(
              HopVaultFileType.XML_TAG, XmlMetadataUtil.serializeObjectToXml(model));
      return compress(xml);
    } catch (Exception e) {
      throw new HopException("Error capturing Data Vault model snapshot", e);
    }
  }

  public DataVaultModel undo(
      DataVaultModel model, IHopMetadataProvider metadataProvider, String preserveFilename)
      throws HopException {
    if (!canUndo() || model == null) {
      return null;
    }
    applyingSnapshot = true;
    try {
      redoStack.add(captureSnapshot(model, metadataProvider));
      trimStack(redoStack);
      byte[] previous = undoStack.remove(undoStack.size() - 1);
      return restoreSnapshot(previous, metadataProvider, preserveFilename);
    } finally {
      applyingSnapshot = false;
    }
  }

  public DataVaultModel redo(
      DataVaultModel model, IHopMetadataProvider metadataProvider, String preserveFilename)
      throws HopException {
    if (!canRedo() || model == null) {
      return null;
    }
    applyingSnapshot = true;
    try {
      undoStack.add(captureSnapshot(model, metadataProvider));
      trimStack(undoStack);
      byte[] next = redoStack.remove(redoStack.size() - 1);
      return restoreSnapshot(next, metadataProvider, preserveFilename);
    } finally {
      applyingSnapshot = false;
    }
  }

  private DataVaultModel restoreSnapshot(
      byte[] snapshot, IHopMetadataProvider metadataProvider, String preserveFilename)
      throws HopException {
    try {
      String xml = decompress(snapshot);
      Document document = XmlHandler.loadXmlString(xml);
      Node rootNode = XmlHandler.getSubNode(document, HopVaultFileType.XML_TAG);
      if (rootNode == null) {
        rootNode = document.getDocumentElement();
      }
      DataVaultModel restored = new DataVaultModel();
      XmlMetadataUtil.deSerializeFromXml(
          rootNode, DataVaultModel.class, restored, metadataProvider);
      if (preserveFilename != null) {
        restored.setFilename(preserveFilename);
      }
      restored.clearChanged();
      return restored;
    } catch (Exception e) {
      throw new HopException("Error restoring Data Vault model snapshot", e);
    }
  }

  private static byte[] compress(String xml) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream(xml.length());
    try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
      gzip.write(xml.getBytes(StandardCharsets.UTF_8));
    }
    return baos.toByteArray();
  }

  private static String decompress(byte[] compressed) throws IOException {
    try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed));
        ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      gzip.transferTo(baos);
      return baos.toString(StandardCharsets.UTF_8);
    }
  }

  private static void trimStack(List<byte[]> stack) {
    int max = DataVaultConfigSingleton.getConfig().getMaxUndoOperations();
    while (stack.size() > max) {
      stack.remove(0);
    }
  }
}