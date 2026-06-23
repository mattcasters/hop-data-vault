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

package org.apache.hop.datavault.metadata.file;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.row.IRowMeta;
import org.apache.hop.core.row.IValueMeta;
import org.apache.hop.core.row.value.ValueMetaFactory;
import org.apache.hop.core.util.Utils;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.SourceField;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineHopMeta;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.anon.AnonymousPipelineResults;
import org.apache.hop.pipeline.anon.AnonymousPipelineRunner;
import org.apache.hop.pipeline.transform.TransformMeta;
import org.apache.hop.pipeline.transforms.dummy.DummyMeta;
import org.apache.hop.pipeline.transforms.filemetadata.FileMetadataMeta;

/** Discovers CSV / delimited file layout using the Hop File Metadata transform. */
public final class CsvFileMetadataDiscovery {

  private static final Class<?> PKG = CsvFileMetadataDiscovery.class;

  private static final String FILE_METADATA_TRANSFORM_NAME = "File metadata";
  private static final String CAPTURE_TRANSFORM_NAME = "Capture metadata";

  private static final int IDX_CHARSET = 0;
  private static final int IDX_DELIMITER = 1;
  private static final int IDX_ENCLOSURE = 2;
  private static final int IDX_SKIP_HEADER_LINES = 4;
  private static final int IDX_HEADER_LINE_PRESENT = 6;
  private static final int IDX_NAME = 7;
  private static final int IDX_TYPE = 8;
  private static final int IDX_LENGTH = 9;
  private static final int IDX_PRECISION = 10;

  private CsvFileMetadataDiscovery() {}

  public record DiscoveryResult(
      String charset,
      String delimiter,
      String enclosure,
      boolean headerPresent,
      int headerLines,
      List<SourceField> fields) {}

  public static DiscoveryResult discover(
      String filePath, IVariables variables, IHopMetadataProvider metadataProvider)
      throws HopException {
    if (Utils.isEmpty(filePath)) {
      throw new HopException(
          BaseMessages.getString(PKG, "CsvFileMetadataDiscovery.Error.MissingFile"));
    }

    PipelineMeta pipelineMeta = buildDiscoveryPipeline(filePath);
    AnonymousPipelineResults results =
        AnonymousPipelineRunner.executePipeline(
            pipelineMeta, variables, metadataProvider, CAPTURE_TRANSFORM_NAME);

    if (results.getResult() != null && results.getResult().getNrErrors() > 0) {
      throw new HopException(
          BaseMessages.getString(PKG, "CsvFileMetadataDiscovery.Error.PipelineFailed"));
    }

    List<Object[]> rows = results.getResultRows();
    if (rows == null || rows.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "CsvFileMetadataDiscovery.Error.NoMetadata"));
    }

    return parseRows(rows);
  }

  private static PipelineMeta buildDiscoveryPipeline(String filePath) {
    PipelineMeta pipelineMeta = new PipelineMeta();
    pipelineMeta.setName("Discover CSV file metadata");

    FileMetadataMeta fileMetadataMeta = new FileMetadataMeta();
    fileMetadataMeta.setDefault();
    fileMetadataMeta.setFileName(filePath);

    TransformMeta fileMetadataTransform =
        new TransformMeta(FILE_METADATA_TRANSFORM_NAME, fileMetadataMeta);
    fileMetadataTransform.setLocation(100, 100);
    pipelineMeta.addTransform(fileMetadataTransform);

    DummyMeta dummyMeta = new DummyMeta();
    TransformMeta captureTransform = new TransformMeta(CAPTURE_TRANSFORM_NAME, dummyMeta);
    captureTransform.setLocation(300, 100);
    pipelineMeta.addTransform(captureTransform);

    pipelineMeta.addPipelineHop(new PipelineHopMeta(fileMetadataTransform, captureTransform));
    return pipelineMeta;
  }

  private static DiscoveryResult parseRows(List<Object[]> rows) throws HopException {
    Object[] firstRow = rows.getFirst();
    String charset = asCharsetName(firstRow[IDX_CHARSET]);
    String delimiter = asDelimiter(firstRow[IDX_DELIMITER]);
    String enclosure = asString(firstRow[IDX_ENCLOSURE]);
    boolean headerPresent = asBoolean(firstRow[IDX_HEADER_LINE_PRESENT]);
    int headerLines = resolveHeaderLines(headerPresent, asLong(firstRow[IDX_SKIP_HEADER_LINES]));

    List<SourceField> fields = new ArrayList<>();
    for (Object[] row : rows) {
      String name = asString(row[IDX_NAME]);
      if (Utils.isEmpty(name)) {
        continue;
      }
      String typeDesc = asString(row[IDX_TYPE]);
      int hopType = resolveHopType(typeDesc);
      SourceField field = new SourceField(name);
      field.setDescription("");
      field.setSourceDataType(typeDesc);
      field.setHopType(hopType);
      Long length = asLong(row[IDX_LENGTH]);
      field.setLength(length != null && length > 0 ? String.valueOf(length) : "");
      Long precision = asLong(row[IDX_PRECISION]);
      field.setPrecision(precision != null && precision >= 0 ? String.valueOf(precision) : "");
      fields.add(field);
    }

    if (fields.isEmpty()) {
      throw new HopException(
          BaseMessages.getString(PKG, "CsvFileMetadataDiscovery.Error.NoFields"));
    }

    return new DiscoveryResult(
        charset, delimiter, enclosure, headerPresent, headerLines, fields);
  }

  private static int resolveHeaderLines(boolean headerPresent, Long skipHeaderLines) {
    int skip = skipHeaderLines != null ? skipHeaderLines.intValue() : 0;
    if (!headerPresent) {
      return Math.max(0, skip);
    }
    return Math.max(1, skip + 1);
  }

  private static int resolveHopType(String typeDesc) {
    if (Utils.isEmpty(typeDesc)) {
      return IValueMeta.TYPE_STRING;
    }
    try {
      return ValueMetaFactory.getIdForValueMeta(typeDesc);
    } catch (Exception e) {
      return IValueMeta.TYPE_STRING;
    }
  }

  private static String asCharsetName(Object value) {
    if (value instanceof Charset charset) {
      return charset.name();
    }
    return ConstOrEmpty(value);
  }

  private static String asDelimiter(Object value) {
    if (value instanceof Character character) {
      return character.toString();
    }
    return ConstOrEmpty(value);
  }

  private static boolean asBoolean(Object value) {
    if (value instanceof Boolean b) {
      return b;
    }
    if (value != null) {
      return Boolean.parseBoolean(value.toString());
    }
    return false;
  }

  private static Long asLong(Object value) {
    if (value instanceof Long l) {
      return l;
    }
    if (value instanceof Integer i) {
      return i.longValue();
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value != null && !Utils.isEmpty(value.toString())) {
      try {
        return Long.parseLong(value.toString().trim());
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    return null;
  }

  private static String asString(Object value) {
    if (value == null) {
      return "";
    }
    return value.toString();
  }

  private static String ConstOrEmpty(Object value) {
    return value != null ? value.toString() : "";
  }
}