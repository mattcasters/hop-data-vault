package org.apache.hop.datavault.metadata;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

/** Indicating the source column for a business key */
@Getter
@Setter
public class BusinessKeySource {
  @HopMetadataProperty private String businessKeyField;
  @HopMetadataProperty private String sourceFieldName;

  public BusinessKeySource() {}

  public BusinessKeySource(String businessKeyField, String sourceFieldName) {
    this.businessKeyField = businessKeyField;
    this.sourceFieldName = sourceFieldName;
  }
}
