package org.apache.hop.datavault.metadata;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Indicates the source column for a satellite attribute field. */
@Getter
@Setter
public class AttributeSource {
  @HopMetadataProperty private String attributeField;
  @HopMetadataProperty private String sourceFieldName;

  public AttributeSource() {}

  public AttributeSource(String attributeField, String sourceFieldName) {
    this.attributeField = attributeField;
    this.sourceFieldName = sourceFieldName;
  }
}