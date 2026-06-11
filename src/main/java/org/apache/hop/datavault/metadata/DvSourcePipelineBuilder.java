package org.apache.hop.datavault.metadata;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.hop.core.database.DatabaseMeta;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.gui.Point;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.metadata.api.IHopMetadataProvider;
import org.apache.hop.pipeline.PipelineMeta;
import org.apache.hop.pipeline.transform.TransformMeta;

/**
 * When reading from a DataVaultSource we can have different types of sources: Databases, files, and
 * so on. It makes sense to have the various types of different sources (e.g. DvDatabaseSource)
 * generate the appropriate transforms to not only read the source data in a consistent way but also
 * to sort it as needed for the updates of Hubs, Satellites and Links. This class receives
 * information about what's required: - The DataVaultSource and IDvSource to reference - the
 * pipeline to put the transforms in - where to start placing the transforms - what the last
 * transform them, and
 */
@Getter
@Setter
public abstract class DvSourcePipelineBuilder {

  public static final int TRANSFORM_SPACING_X = 128;

  protected final IVariables variables;
  protected final IHopMetadataProvider metadataProvider;
  protected final DataVaultModel model;
  protected final DataVaultConfiguration configuration;
  protected final DataVaultSource recordSource;
  protected final IDvSource dvSource;
  protected final IDvTable dvTable;
  protected final PipelineMeta pipelineMeta;
  protected final Point startPoint;

  /** The transform that will continue the source data. */
  protected TransformMeta resultTransform;

  // Fields specific for Link updates
  protected DvLink.DvLinkSource dvLinkSource;

  public DvSourcePipelineBuilder(
          IVariables variables,
          IHopMetadataProvider metadataProvider,
          DataVaultModel model,
          PipelineMeta pipelineMeta,
          DataVaultSource recordSource,
          IDvSource dvSource,
          IDvTable dvTable,
          Point startPoint) {
    this.variables = variables;
    this.metadataProvider = metadataProvider;
    this.model = model;
    this.configuration = model.getConfiguration();
    this.pipelineMeta = pipelineMeta;
    this.recordSource = recordSource;
    this.dvSource = dvSource;
    this.dvTable = dvTable;
    this.startPoint = startPoint;
  }

  public abstract void build() throws HopException;

  protected DatabaseMeta loadDatabaseMeta(String name) throws HopException {
    if (StringUtils.isEmpty(name)) {
      throw new HopException("Please specify a database name to load");
    }
    DatabaseMeta databaseMeta = metadataProvider.getSerializer(DatabaseMeta.class).load(name);
    if (databaseMeta==null) {
      throw new HopException("Referenced database '"+name+"' couldn't be found in the metadata");
    }
    return databaseMeta;
  }

  /**
   * Look for the hub with the given name in the model.
   *
   * @param hubName The hub to load
   * @return The hub from the model
   */
  protected DvHub findHub(String hubName) throws HopException {
    if (StringUtils.isEmpty(hubName)) {
      throw new HopException("No hub name provided to look for");
    }
    DvHub hub = model.findHub(variables.resolve(hubName));
    if (hub == null) {
      throw new HopException(
              "Hub " + hubName + " could not be found in data vault model " + model.getName());
    }
    return hub;
  }
}
