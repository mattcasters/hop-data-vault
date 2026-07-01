CREATE TABLE IF NOT EXISTS retail_load_control (
  id INTEGER PRIMARY KEY DEFAULT 1,
  progress_date DATE NOT NULL,
  period_months INTEGER NOT NULL DEFAULT 1,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT retail_load_control_single_row CHECK (id = 1)
);

DELETE FROM retail_load_control;

INSERT INTO retail_load_control (id, progress_date, period_months)
VALUES (1, DATE '2024-01-01', 1);