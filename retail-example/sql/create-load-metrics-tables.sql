-- Load-run metrics tables for catalog-backed operations telemetry (Phase 1).
-- Target database: OPS connection (${DB_OPS_NAME}, e.g. test_ops). Not the Vault EDW.
-- Optional manual bootstrap: metrics publishing also auto-creates these tables on first run.

CREATE SCHEMA IF NOT EXISTS dv_ops;

CREATE TABLE IF NOT EXISTS dv_ops.load_run (
  run_id           VARCHAR(64)  NOT NULL,
  started_at       TIMESTAMP    NULL,
  finished_at      TIMESTAMP    NULL,
  model_type       VARCHAR(16)  NULL,
  model_name       VARCHAR(255) NULL,
  workflow_name    VARCHAR(255) NULL,
  log_channel_id   VARCHAR(64)  NULL,
  success          BOOLEAN      NULL,
  error_count      BIGINT       NULL,
  PRIMARY KEY (run_id)
);

CREATE TABLE IF NOT EXISTS dv_ops.load_pipeline_metric (
  run_id                 VARCHAR(64)  NOT NULL,
  pipeline_name          VARCHAR(255) NOT NULL,
  element_type           VARCHAR(64)  NULL,
  element_name           VARCHAR(255) NULL,
  source_name            VARCHAR(255) NULL,
  source_rows_read       BIGINT       NULL,
  target_rows_read       BIGINT       NULL,
  target_rows_inserted   BIGINT       NULL,
  errors                 BIGINT       NULL,
  PRIMARY KEY (run_id, pipeline_name)
);

CREATE TABLE IF NOT EXISTS dv_ops.load_transform_metric (
  run_id                VARCHAR(64)  NOT NULL,
  pipeline_name         VARCHAR(255) NOT NULL,
  transform_name        VARCHAR(255) NOT NULL,
  logical_role          VARCHAR(64)  NULL,
  element_type          VARCHAR(64)  NULL,
  element_name          VARCHAR(255) NULL,
  parent_element_name   VARCHAR(255) NULL,
  rows_read             BIGINT       NULL,
  rows_written          BIGINT       NULL,
  rows_updated          BIGINT       NULL,
  rows_rejected         BIGINT       NULL,
  errors                BIGINT       NULL,
  duration_ms           BIGINT       NULL,
  PRIMARY KEY (run_id, pipeline_name, transform_name)
);

CREATE TABLE IF NOT EXISTS dv_ops.load_insight (
  run_id               VARCHAR(64)  NOT NULL,
  insight_seq          BIGINT       NOT NULL,
  severity             VARCHAR(16)  NULL,
  code                 VARCHAR(64)  NULL,
  message              VARCHAR(2000) NULL,
  element_name         VARCHAR(255) NULL,
  related_element_name VARCHAR(255) NULL,
  metric_json          VARCHAR(4000) NULL,
  PRIMARY KEY (run_id, insight_seq)
);