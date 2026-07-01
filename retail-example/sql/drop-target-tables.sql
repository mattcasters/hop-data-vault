-- Drop dimensional model tables
DROP TABLE IF EXISTS f_sales_agg;
DROP TABLE IF EXISTS f_sales;
DROP TABLE IF EXISTS f_order_lifecycle;
DROP TABLE IF EXISTS f_daily_balance;
DROP TABLE IF EXISTS f_coverage;
DROP TABLE IF EXISTS bridge_customer_product;
DROP TABLE IF EXISTS d_order_junk;
DROP TABLE IF EXISTS f_order_lines;
DROP TABLE IF EXISTS f_orders;
DROP TABLE IF EXISTS d_warehouse;
DROP TABLE IF EXISTS d_date;
DROP TABLE IF EXISTS d_product;
DROP TABLE IF EXISTS d_customer;

-- Drop business vault tables
DROP TABLE IF EXISTS customer_360_bv;

-- Drop data vault tables
DROP TABLE IF EXISTS lnk_order;
DROP TABLE IF EXISTS sat_warehouse;
DROP TABLE IF EXISTS hub_warehouse;
DROP TABLE IF EXISTS sat_order;
DROP TABLE IF EXISTS hub_order;
DROP TABLE IF EXISTS sat_product;
DROP TABLE IF EXISTS hub_product;
DROP TABLE IF EXISTS sat_customer_prefs;
DROP TABLE IF EXISTS sat_customer_address;
DROP TABLE IF EXISTS sat_customer_contact;
DROP TABLE IF EXISTS sat_customer_demo;
DROP TABLE IF EXISTS hub_customer;

-- Drop staging views
DROP VIEW IF EXISTS stg_e2e_sales_agg;
DROP VIEW IF EXISTS stg_e2e_sales;
DROP VIEW IF EXISTS stg_e2e_order_lifecycle;
DROP VIEW IF EXISTS stg_e2e_daily_balance;
DROP VIEW IF EXISTS stg_e2e_coverage;
DROP VIEW IF EXISTS stg_e2e_customer_product_bridge;
DROP VIEW IF EXISTS stg_e2e_order_flags;
DROP VIEW IF EXISTS stg_e2e_order_lines;
DROP VIEW IF EXISTS stg_e2e_orders;
DROP VIEW IF EXISTS stg_e2e_warehouse;
DROP VIEW IF EXISTS stg_e2e_product;
DROP VIEW IF EXISTS stg_e2e_customer;
DROP VIEW IF EXISTS stg_e2e_date;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

-- Drop load control
DROP TABLE IF EXISTS retail_load_control;