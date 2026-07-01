UPDATE retail_load_control
SET progress_date = progress_date + (period_months || ' months')::interval,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 1;