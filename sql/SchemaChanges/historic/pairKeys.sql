ALTER TABLE job_pairs
ADD INDEX (config_id);

ALTER TABLE job_pairs
ADD INDEX (job_space_id);

ALTER TABLE job_pairs
ADD INDEX (job_space_id, config_id);

ALTER TABLE job_pairs
ADD INDEX (job_id, status_code);
