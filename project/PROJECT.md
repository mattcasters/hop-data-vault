# Sample Hop Data Vault Project

> **Prerequisites**
>
> - The two database connections `CRM` and `Vault` need to be configured in the project metadata (see `metadata/rdbms/CRM.json` and `metadata/rdbms/Vault.json`).
> - Testing has only been done with PostgreSQL.

This folder contains a sample Hop project demonstrating the Data Vault 2.0 plugin.

## Sample Model

The sample Data Vault model is defined in the file:

- `vault1.hdv`

(This is the Hop Data Vault model file. You can open it in Hop GUI to view/edit the visual layout of hubs, links, and satellites.)

## Running and Testing the Sample

The primary way to run and test the sample is with the workflow:

- `update-vault1.hwf`

### What the workflow does

The workflow `update-vault1.hwf` performs an end-to-end demonstration and validation of loading the `vault1.hdv` model:

1. **Create CRM tables** (SQL action on the "CRM" connection):  
   Creates the raw source tables (`customer`, `product`, `order`) in the source ("CRM") database. These represent the operational data that will feed the Data Vault.

2. **load1** (Pipeline executor):  
   Runs `load1.hpl`, which loads the first batch of sample data from the CSV files in `files/` (`customer_load1.csv`, `order_load1.csv`, `product_load1.csv`) into the CRM source tables using CSV Input + Table Output (or equivalent).

3. **Drop Vault tables** (SQL action on the "Vault" connection):  
   Drops all target Data Vault tables (`hub_customer`, `sat_customer`, `hub_product`, `sat_product`, `hub_order`, `sat_order`, `lnk_customer_order`, `lnk_product_order` / `lnk_order_product`, etc.) to guarantee a clean starting state for the test run.

4. **update vault1.hdv** (Data Vault Update action):  
   Executes the core plugin action against `${PROJECT_HOME}/vault1.hdv` using the "local" pipeline run configuration.  
   - The action generates the update pipelines for every table in the model (hubs, satellites, and links).  
   - Because the model uses multiple `DataVaultSource` entries (CRM-customer, CRM-order, CRM-product), it produces one pipeline per source where applicable.  
   - It creates/alters the target tables in the "Vault" database as needed and performs the insert-only Data Vault loads (with hash key calculation, change detection for satellites, etc.).  
   - Model check logging and abort-on-failure are disabled in this sample workflow.

5. **Test hubs and sats** (RunPipelineTests action):  
   Executes the registered Hop unit tests for the core tables:
   - validate-hub-customer UNIT
   - validate-hub-order UNIT
   - validate-hub-product UNIT
   - validate-sat-customer UNIT
   - validate-sat-order UNIT
   - validate-sat-product UNIT

   These tests (defined under `metadata/unit-test/` and implemented in `tests/`) compare the populated hub and satellite tables against the "golden" expected datasets in the `datasets/` folder. Success of these tests confirms that the model-driven generation and loading logic produced the correct results for the first load batch.

### Notes

- The workflow only validates hubs and satellites. Link tables (`lnk_*`) are created/dropped as part of the flow but are not asserted by unit tests in this particular workflow.
- A second load batch exists in the project (`load2.hpl` + `*_load2.csv` files + additional golden data) for manual or extended testing of incremental/delta loads, but it is not exercised by `update-vault1.hwf`.
- The project is configured via `project-config.json`, which tells Hop where to find metadata, unit tests, and dataset CSVs (all relative to `PROJECT_HOME`).
- All database connections, run configurations, Data Vault sources/configurations, and unit test definitions live under `metadata/`.

Open `update-vault1.hwf` in Hop, point a project at this folder (so that `${PROJECT_HOME}` resolves correctly), and run the workflow to exercise the full sample. The unit tests at the end will report pass/fail in the Hop UI.