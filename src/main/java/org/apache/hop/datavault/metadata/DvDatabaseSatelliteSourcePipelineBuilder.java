package org.apache.hop.datavault.metadata;

import java.util.ArrayList;
import java.util.List;
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
public class DvDatabaseSatelliteSourcePipelineBuilder extends DvDatabaseSourcePipelineBuilder {
  public DvDatabaseSatelliteSourcePipelineBuilder(IVariables variables, IHopMetadataProvider metadataProvider, DataVaultModel model, PipelineMeta pipelineMeta, DataVaultSource recordSource, IDvSource dvSource, DvSatellite dvTable, Point startPoint) {
    super(variables, metadataProvider, model, pipelineMeta, recordSource, dvSource, dvTable, startPoint);
  }

  protected String getSql() throws HopException {
    StringBuilder sql = new StringBuilder("SELECT ");
    DvSatellite satellite = (DvSatellite) dvTable;
    DvHub hub = findHub(satellite.getHubName());
    DvDatabaseSource source = (DvDatabaseSource) dvSource;
    DatabaseMeta sourceDbMeta = loadDatabaseMeta(variables.resolve(source.getDatabaseName()));

    // For the satellite target update we need the business keys to calculate the Hash Key
    appendFields(sql, getQuotedPkFields(hub, sourceDbMeta));

    // The attribute fields
    appendComma(sql);
    appendFields(sql, quotedSatelliteAttributeFields(satellite, sourceDbMeta));

    // The source indicator
    appendComma(sql);
    appendSourceField(hub, sql, sourceDbMeta);

    // FROM
    appendFrom(sourceDbMeta, source, sql);

    return sql.toString();
  }

  private List<String> quotedSatelliteAttributeFields(
      DvSatellite satellite, DatabaseMeta sourceDbMeta) throws HopException {
    List<String> fields = new ArrayList<>();
    if (satellite.getAttributes().isEmpty()) {
      throw new HopException(
          "Please specify at least one attribute field for satellite " + satellite.getName());
    }
    for (SatelliteAttribute attribute : satellite.getAttributes()) {
      String field = sourceDbMeta.quoteField(attribute.getName());
      fields.add(field);
    }
    return fields;
  }
}
