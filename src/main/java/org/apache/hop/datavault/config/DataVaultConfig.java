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

package org.apache.hop.datavault.config;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.datavault.layout.ElkLayout;

@Getter
@Setter
public class DataVaultConfig {

  public static final String HOP_CONFIG_DATA_VAULT_CONFIG_KEY = "dataVaultConfig";
  public static final int DEFAULT_MAX_UNDO_OPERATIONS = 200;

  private boolean drawingHashKeysInModel;
  private int maxUndoOperations = DEFAULT_MAX_UNDO_OPERATIONS;
  private ElkLayout elkLayout = ElkLayout.createDefault();

  public DataVaultConfig() {
    drawingHashKeysInModel = true;
  }

  public DataVaultConfig(DataVaultConfig config) {
    this();
    drawingHashKeysInModel = config.drawingHashKeysInModel;
    setMaxUndoOperations(config.maxUndoOperations);
    setElkLayout(new ElkLayout(config.getElkLayout()));
  }

  public int getMaxUndoOperations() {
    return maxUndoOperations > 0 ? maxUndoOperations : DEFAULT_MAX_UNDO_OPERATIONS;
  }

  public void setMaxUndoOperations(int maxUndoOperations) {
    this.maxUndoOperations =
        maxUndoOperations > 0 ? maxUndoOperations : DEFAULT_MAX_UNDO_OPERATIONS;
  }

  public ElkLayout getElkLayout() {
    return elkLayout != null ? elkLayout : ElkLayout.createDefault();
  }

  public void setElkLayout(ElkLayout elkLayout) {
    this.elkLayout = elkLayout != null ? elkLayout : ElkLayout.createDefault();
  }
}