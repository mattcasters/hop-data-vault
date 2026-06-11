package org.apache.hop.datavault.metadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.datavault.metadata.database.DvDatabaseSource;
import org.apache.hop.datavault.metadata.database.DvDatabaseSourcePipelineBuilder;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;

@Getter
@Setter
public class DvDatabaseLinkSourcePipelineBuilder extends DvDatabaseSourcePipelineBuilder {
  private Map<String, List<String>> hubKeyFields = new HashMap<>();
  private Map<String, List<String>> hubDrivingKeyFields = new HashMap<>();

  public DvDatabaseLinkSourcePipelineBuilder(
      IVariables variables,
      IHopMetadataProvider metadataProvider,
      DataVaultModel model,
      PipelineMeta pipelineMeta,
      DataVaultSource recordSource,
      IDvSource dvSource,
      DvLink dvTable,
      Point startPoint) {
    super(
        variables,
        metadataProvider,
        model,
        pipelineMeta,
        recordSource,
        dvSource,
        dvTable,
        startPoint);
  }

  /**
   * We're querying one source of a list of sources in a satellite.
   *
   * @return The SQL needed to query this source table
   * @throws HopException In case there's an error in the model
   */
  protected String getSql() throws HopException {
    StringBuilder sql = new StringBuilder("SELECT ");
    DvLink link = (DvLink) dvTable;
    DvDatabaseSource source = (DvDatabaseSource) dvSource;
    DatabaseMeta sourceDbMeta = loadDatabaseMeta(variables.resolve(source.getDatabaseName()));

    if (dvLinkSource == null) {
      throw new HopException("No DV link source was configured");
    }

    // For the hubs attached we'll process the business keys for the given source
    //
    for (String hubName : link.getHubNames()) {
      for (DvLink.HubSourceKeyField sourceKeyField : dvLinkSource.getHubSourceKeyFields()) {
        if (hubName.equals(sourceKeyField.getHubName())) {
          // This is a business key to record source field mapping for the given hub.
          //
          DvHub hub = findHub(hubName);
          if (hub == null) {
            throw new HopException(
                "No DV link source key field found for hub: "
                    + hubName
                    + " in Link "
                    + link.getName());
          }
          // We know the fields to query from the source for this hub's business keys.
          //
          findKeySourceFieldsForHub(hubName, sourceKeyField, hub, link, sourceDbMeta);

          // Now we want to get the driving key fields in a similar fashion.
          //
          findDrivingKeySourceFieldsForHub(hubName, sourceKeyField, link, sourceDbMeta);
        }
      }
    }

    // These are now the fields to retrieve:
    // For every hub we retrieve the business key and driving key source fields.
    //
    List<String> quotedFields = new ArrayList<>();
    for (String hubName : link.getHubNames()) {
      quotedFields.addAll(hubKeyFields.get(hubName));
      quotedFields.addAll(hubDrivingKeyFields.get(hubName));
    }
    appendFields(sql, quotedFields);

    // FROM
    appendFrom(sourceDbMeta, source, sql);

    return sql.toString();
  }

  private void findKeySourceFieldsForHub(
      String hubName,
      DvLink.HubSourceKeyField sourceKeyField,
      DvHub hub,
      DvLink link,
      DatabaseMeta sourceDbMeta)
      throws HopException {
    List<String> keyFields = hubKeyFields.computeIfAbsent(hubName, f -> new ArrayList<>());

    for (BusinessKeySource businessKeySource : sourceKeyField.getSourceBusinessKeyFields()) {
      String bkTargetField = businessKeySource.getBusinessKeyField();
      String bkSourceField = businessKeySource.getSourceFieldName();

      // Validate that the business key exists
      //
      if (hub.findBusinessKey(bkTargetField) == null) {
        throw new HopException(
            "The specified business key field "
                + bkTargetField
                + " can not be found in Link table "
                + link.getName()
                + " with record source "
                + recordSource.getName());
      }
      // We want to grab this field
      keyFields.add(sourceDbMeta.quoteField(bkSourceField));
    }
  }

  private void findDrivingKeySourceFieldsForHub(
      String hubName,
      DvLink.HubSourceKeyField sourceKeyField,
      DvLink link,
      DatabaseMeta sourceDbMeta)
      throws HopException {
    List<String> drivingKeyFields =
        hubDrivingKeyFields.computeIfAbsent(hubName, f -> new ArrayList<>());

    for (DrivingKeySource keySource : sourceKeyField.getDrivingKeySources()) {
      String drivingKey = keySource.getDrivingKey();
      String drivingKeySource = keySource.getSourceField();

      // Verify that the driving key exists
      //
      if (!link.getDrivingKeyNames().contains(drivingKey)) {
        throw new HopException(
            "The referenced driving key "
                + drivingKey
                + " doesn't exist in Link table "
                + link.getName()
                + ", reading from source "
                + recordSource.getName());
      }

      drivingKeyFields.add(sourceDbMeta.quoteField(drivingKeySource));
    }
  }
}
