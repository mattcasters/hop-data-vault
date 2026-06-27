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

package org.apache.hop.datavault.command.svg;

import lombok.Getter;
import lombok.Setter;

/** Options controlling SVG generation for pipelines, workflows, and DV/BV/dimensional models. */
@Getter
@Setter
public class SvgRenderOptions {

  public static final float DEFAULT_MAGNIFICATION = 1.0f;

  private boolean includeNotes = true;
  private float magnification = DEFAULT_MAGNIFICATION;
  private boolean showHashKeyFieldNames;

  public static SvgRenderOptions defaults() {
    return new SvgRenderOptions();
  }

  public static SvgRenderOptions fromCli(boolean noNotes, float magnification, boolean showHashKeys) {
    SvgRenderOptions options = new SvgRenderOptions();
    options.setIncludeNotes(!noNotes);
    options.setMagnification(magnification);
    options.setShowHashKeyFieldNames(showHashKeys);
    return options;
  }
}