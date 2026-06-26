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

package org.apache.hop.datavault.metadata.dimensional;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.CheckResult;
import org.apache.hop.core.Const;
import org.apache.hop.core.ICheckResult;
import org.apache.hop.core.changed.ChangedFlag;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.HopMetadataBase;
import org.apache.hop.metadata.api.HopMetadataProperty;
import org.apache.hop.metadata.api.IHopMetadata;
import org.apache.hop.metadata.api.IHopMetadataProvider;

@Getter
@Setter
public abstract class DmTableBase extends HopMetadataBase implements IHopMetadata, IDmTable {

  private static final Class<?> PKG = DmTableBase.class;

  @HopMetadataProperty(key = "tableName")
  protected String tableName;

  @HopMetadataProperty protected String description;

  @HopMetadataProperty protected DmTableType tableType;

  @HopMetadataProperty(inline = true)
  private Point location = new Point(0, 0);

  private boolean selected;
  private int drawnBoxWidth = 140;
  private int drawnBoxHeight = 70;

  protected final ChangedFlag changedFlag = new ChangedFlag();

  protected DmTableBase(DmTableType tableType) {
    this.tableType = tableType;
  }

  @Override
  public void setLocation(int x, int y) {
    if (location == null) {
      location = new Point(x, y);
    } else {
      location.x = x;
      location.y = y;
    }
  }

  @Override
  public Point getLocation() {
    if (location == null) {
      location = new Point(0, 0);
    }
    return location;
  }

  @Override
  public boolean isSelected() {
    return selected;
  }

  @Override
  public void setSelected(boolean selected) {
    this.selected = selected;
  }

  @Override
  public boolean hasChanged() {
    return changedFlag.hasChanged();
  }

  @Override
  public void setChanged() {
    changedFlag.setChanged();
  }

  @Override
  public void setChanged(boolean changed) {
    changedFlag.setChanged(changed);
  }

  @Override
  public void clearChanged() {
    changedFlag.setChanged(false);
  }

  @Override
  public void check(
      List<ICheckResult> remarks,
      IHopMetadataProvider metadataProvider,
      IVariables variables,
      DimensionalModel model) {
    if (Utils.isEmpty(getName())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(PKG, "DmTableBase.CheckResult.MissingName"),
              this));
    }
    if (Utils.isEmpty(getTableName())) {
      remarks.add(
          new CheckResult(
              ICheckResult.TYPE_RESULT_ERROR,
              BaseMessages.getString(
                  PKG, "DmTableBase.CheckResult.MissingTableName", Const.NVL(getName(), "?")),
              this));
    }
  }
}