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

The workflow `update-vault1.hwf` performs a complete end-to-end demonstration and validation of the `vault1.hdv` model, including both the **initial load** and a subsequent **incremental update** (load2) in a single orchestrated run:

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

5. **Test after initial load** (RunPipelineTests action):  
   Executes the registered Hop unit tests for the core tables after the first load:
   - validate-hub-customer UNIT
   - validate-hub-order UNIT
   - validate-hub-product UNIT
   - validate-sat-customer UNIT
   - validate-sat-order UNIT
   - validate-sat-product UNIT

   These tests (defined under `metadata/unit-test/` and implemented in `tests/`) compare the populated hub and satellite tables against the "golden" expected datasets in the `datasets/` folder. Success here confirms correct results for the initial load batch.

6. **load2** (Pipeline executor):  
   Runs `load2.hpl`, which loads the second (incremental) batch of sample data from the CSV files in `files/` (`customer_load2.csv`, `order_load2.csv`, `product_load2.csv`) into the CRM source tables. This simulates new/changed data arriving after the initial population.

7. **update vault1.hdv** (Data Vault Update action, second execution):  
   Runs the Data Vault Update action again against the same model. The plugin correctly detects and applies only the deltas from the new source data (no unnecessary re-inserts of unchanged rows).

8. **Test after updates** (RunPipelineTests action):  
   Re-runs the unit tests (or a subset focused on the affected tables) to validate that the incremental load produced the expected additional/changed rows in hubs, satellites, and links.

9. **Success**:  
   The workflow completes with an overall success status only when both the post-initial-load and post-incremental-update validations pass.

### Workflow Milestone

The image below captures a successful end-to-end execution of the complete `update-vault1.hwf` workflow in the Hop GUI. It serves as a project milestone demonstrating that the Data Vault 2.0 plugin can reliably handle:

- Initial model-driven population of the Data Vault (load1 + first DV update + validation).
- Incremental / delta loads (load2 + second DV update that correctly identifies only new or changed records).
- Validation after each stage, using golden datasets to assert both the state after the first load and the state after the incremental update.

![Successful run of update-vault1.hwf showing initial load (load1) and incremental load (load2) paths with tests after each stage and overall Success](images/update-vault1-workflow.jpg)

### Notes

- The workflow validates hubs and satellites after both the initial load and the incremental updates. Link tables (`lnk_*`) are created/dropped and loaded as part of the flow; dedicated link validation can be added to the "Test after updates" stage if desired.
- The second load batch (`load2.hpl` + `*_load2.csv` files + the corresponding `*-golden-load2` datasets) is now fully exercised inside `update-vault1.hwf` (no longer "manual only").
- Additional golden datasets for the post-load2 state live alongside the original ones (e.g. `hub-customer-golden-load2`, `lnk-customer-order-golden-load2`, etc.) so that tests can assert the exact expected data after each load stage.
- The project is configured via `project-config.json`, which tells Hop where to find metadata, unit tests, and dataset CSVs (all relative to `PROJECT_HOME`).
- All database connections, run configurations, Data Vault sources/configurations, and unit test definitions live under `metadata/`.

Open `update-vault1.hwf` in Hop, point a project at this folder (so that `${PROJECT_HOME}` resolves correctly), and run the workflow to exercise the full sample. You will see the two-stage execution (initial load + test, then load2 + second update + final test) and the unit tests will report pass/fail for both stages in the Hop UI. The green checkmarks on a successful run (as shown in the milestone image above) confirm that both the first load and the subsequent incremental update produced correct results.