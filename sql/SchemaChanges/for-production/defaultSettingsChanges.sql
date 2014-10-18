USE starexec;

ALTER TABLE space_default_settings DROP FOREIGN KEY space_default_settings_space_id;

ALTER TABLE space_default_settings CHANGE space_id id INT;

ALTER TABLE space_default_settings ADD COLUMN setting_type CHAR(8) DEFAULT "comm";

ALTER TABLE space_default_settings ADD COLUMN name VARCHAR(32) DEFAULT "settings";


ALTER TABLE space_default_settings DROP PRIMARY KEY, ADD PRIMARY KEY(id, setting_type,name);


-- renaming all the foreign keys next to match our naming convention to the name of the table


ALTER TABLE space_default_settings DROP FOREIGN KEY space_default_settings_post_processor, ADD CONSTRAINT default_settings_post_processor FOREIGN KEY (post_processor) REFERENCES processors(id) ON DELETE SET NULL;

ALTER TABLE space_default_settings DROP FOREIGN KEY space_default_settings_bench_processor, ADD CONSTRAINT default_settings_bench_processor FOREIGN KEY (bench_processor) REFERENCES processors(id) ON DELETE SET NULL;

ALTER TABLE space_default_settings DROP FOREIGN KEY space_default_settings_pre_processor, ADD CONSTRAINT default_settings_pre_processor FOREIGN KEY (pre_processor) REFERENCES processors(id) ON DELETE SET NULL;

ALTER TABLE space_default_settings DROP FOREIGN KEY space_default_settings_default_solver, ADD CONSTRAINT default_settings_default_solver FOREIGN KEY (default_solver) REFERENCES solvers(id) ON DELETE SET NULL;

ALTER TABLE space_default_settings DROP FOREIGN KEY space_default_settings_default_benchmark, ADD CONSTRAINT default_settings_default_benchmark FOREIGN KEY (default_benchmark) REFERENCES benchmarks(id) ON DELETE SET NULL;


-- finally, rename the table
RENAME TABLE space_default_settings TO default_settings;