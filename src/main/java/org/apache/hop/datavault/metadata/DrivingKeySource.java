package org.apache.hop.datavault.metadata;

import lombok.Getter;
import lombok.Setter;
import org.apache.hop.metadata.api.HopMetadataProperty;

@Getter
@Setter
public class DrivingKeySource {
  @HopMetadataProperty private String drivingKey;
  @HopMetadataProperty private String sourceField;

  public DrivingKeySource() {}

  public DrivingKeySource(String drivingKey, String sourceField) {
    this.drivingKey = drivingKey;
    this.sourceField = sourceField;
  }
}
