-- Description: This file contains all job-related stored procedures for the starexec database
-- The procedures are stored by which table they're related to and roughly alphabetic order. Please try to keep this organized!

DROP PROCEDURE IF EXISTS UpdateJobPairStatus //
CREATE PROCEDURE UpdateJobPairStatus(IN _pairId INT, IN _statusCode INT)
	BEGIN
		UPDATE job_pairs
		SET status_code = _statusCode
		WHERE id = _pairId;
	END //

DROP PROCEDURE IF EXISTS UpdateJobSpaceId //
CREATE PROCEDURE UpdateJobSpaceId(IN _pairId INT, IN _jobSpaceId INT)
	BEGIN
		UPDATE job_pairs
		SET job_space_id = _jobSpaceId
		WHERE id = _pairId;
	END //

DROP PROCEDURE IF EXISTS UpdatePairNodeId //
CREATE PROCEDURE UpdatePairNodeId(IN _jobPairId INT, IN _nodeId INT)
	BEGIN
		UPDATE job_pairs SET node_id=_nodeId WHERE id=_jobPairId;
	END  //

-- Updates a job pair's statistics directly from the execution node
-- Author: Benton McCune
DROP PROCEDURE IF EXISTS UpdatePairRunSolverStats //
CREATE PROCEDURE UpdatePairRunSolverStats(IN _jobPairId INT, IN _nodeName VARCHAR(64), IN _wallClock DOUBLE, IN _cpu DOUBLE, IN _userTime DOUBLE, IN _systemTime DOUBLE, IN _maxVmem DOUBLE, IN _maxResSet BIGINT, IN _stageNumber INT, IN _diskSize BIGINT)
	BEGIN
		UPDATE job_pairs SET node_id=(SELECT id FROM nodes WHERE name=_nodeName) WHERE id=_jobPairId;
		UPDATE users SET users.disk_size=users.disk_size+_diskSize
		WHERE id = (SELECT user_id FROM job_pairs JOIN jobs ON jobs.id = job_pairs.job_id WHERE job_pairs.id=_jobPairId);
		UPDATE jobpair_stage_data
		SET wallclock = _wallClock,
			cpu=_cpu,
			user_time=_userTime,
			system_time=_systemTime,
			max_vmem=_maxVmem,
			max_res_set=_maxResSet,
			disk_size=_diskSize
		WHERE jobpair_id=_jobPairId AND stage_number=_stageNumber;
		UPDATE jobs SET disk_size=disk_size+_diskSize WHERE id=(SELECT job_id FROM job_pairs WHERE id=_jobPairId);
	END //

-- Updates a job pairs node Id
-- Author: Wyatt
DROP PROCEDURE IF EXISTS UpdateNodeId //
CREATE PROCEDURE UpdateNodeId(IN _jobPairId INT, IN _nodeName VARCHAR(128), IN _sandbox INT)
	BEGIN
		DECLARE _nodeId INT;

		SELECT id FROM nodes WHERE name=_nodeName INTO _nodeId;

		UPDATE job_pairs SET node_id=_nodeId WHERE id = _jobPairId;
		UPDATE job_pairs SET sandbox_num=_sandbox WHERE id=_jobPairId;

		-- Next lines finish a pair that is still in the "running" state despite another pair being in the same place now
		-- First, mark the end time of the pairs
		UPDATE job_pairs SET end_time=NOW() WHERE node_id = _nodeID AND status_code = 4 AND id!=_jobPairId AND sandbox_num=_sandbox;
		-- Then, update the stuck pairs to an error code
		UPDATE job_pairs SET status_code = 10 WHERE node_id = _nodeID AND status_code = 4 AND id!=_jobPairId AND sandbox_num=_sandbox;
	END //

-- Sets a pair's disk_usage to 0, updating jobpair_stage_data, jobs, and users
DROP PROCEDURE IF EXISTS RemoveJobPairDiskSize //
CREATE PROCEDURE RemoveJobPairDiskSize(IN _jobPairId INT)
	BEGIN
		DECLARE _sumDiskSize BIGINT;
		SELECT SUM(disk_size) FROM jobpair_stage_data WHERE jobpair_id=_jobPairId INTO _sumDiskSize;
		UPDATE jobs SET jobs.disk_size=jobs.disk_size - (_sumDiskSize) WHERE jobs.id=(SELECT job_id FROM job_pairs WHERE job_pairs.id=_jobPairId);
		UPDATE users SET users.disk_size=users.disk_size - (_sumDiskSize)
		WHERE users.id=(SELECT user_id FROM jobs JOIN job_pairs ON job_pairs.job_id=jobs.id WHERE job_pairs.id=_jobPairId);
		UPDATE jobpair_stage_data SET disk_size=0 WHERE jobpair_id=_jobPairId;
	END //


-- Gets all the nodes that could have pairs that have been enqueued longer than
-- some given amount of time. This works by getting all the queues with pairs that
-- have been enqueued longer than _timeThreshold and then returning all the nodes
-- from that queue that have not run any pairs since the _timeThreshold (basically filtering out
-- nodes that appear to be working).
DROP PROCEDURE IF EXISTS GetNodesThatMayHavePairsEnqueuedLongerThan //
CREATE PROCEDURE GetNodesThatMayHavePairsEnqueuedLongerThan(IN _timeThreshold INT)
  BEGIN
	SELECT DISTINCT qa.node_id as node_id
	FROM job_pairs jp JOIN jobs j ON jp.job_id=j.id
	JOIN queues q ON q.id=j.queue_id
	JOIN queue_assoc qa ON q.id=qa.queue_id
	WHERE MINUTE(TIMEDIFF(NOW(), jp.queuesub_time)) > _timeThreshold
		AND jp.status_code=2
		AND qa.node_id NOT IN
			-- This subquery will get all the working nodes.
			( SELECT i_qa.node_id
			  FROM job_pairs i_jp JOIN jobs i_j ON i_jp.job_id=i_j.id
				JOIN queues i_q ON i_q.id=i_j.queue_id
				JOIN queue_assoc i_qa ON i_q.id=i_qa.queue_id
			  WHERE MINUTE(TIMEDIFF(NOW(), i_jp.start_time)) <= _timeThreshold);
  END //

DROP PROCEDURE IF EXISTS GetPairsEnqueuedLongerThan //
CREATE PROCEDURE GetPairsEnqueuedLongerThan(IN _timeThreshold INT)
  BEGIN
	SELECT DISTINCT jp.id AS pair_id, j.id AS job_id
	FROM job_pairs jp JOIN jobs j ON jp.job_id=j.id
	JOIN queues q ON q.id=j.queue_id
	JOIN queue_assoc qa ON q.id=qa.queue_id
	WHERE MINUTE(TIMEDIFF(NOW(), jp.queuesub_time)) > _timeThreshold AND jp.status_code=2;
  END //

-- Updates a job pair's status
-- Author: Tyler Jensen
DROP PROCEDURE IF EXISTS UpdatePairStatus //
CREATE PROCEDURE UpdatePairStatus(IN _jobPairId INT, IN _statusCode TINYINT)
	BEGIN
		UPDATE job_pairs SET status_code=_statusCode WHERE id=_jobPairId ;
		IF (_statusCode>6 AND _statusCode<19) THEN
			REPLACE INTO job_pair_completion (pair_id) VALUES (_jobPairId);

			-- this checks to see if the job is done and sets its completion id if so.
			-- It checks by trying to find exactly 1 pair (for efficiency) that is not yet complete
			IF (SELECT COUNT(*) FROM (select id from job_pairs WHERE job_id=(SELECT job_id FROM job_pairs WHERE job_pairs.id=_jobPairId) AND (status_code<7 || status_code>18) LIMIT 1) as theCount)=0 THEN
				UPDATE jobs SET completed=CURRENT_TIMESTAMP WHERE id=(SELECT job_id FROM job_pairs WHERE job_pairs.id=_jobPairId);
			END IF;
		END IF;
		IF (_statusCode = 2) THEN
			UPDATE job_pairs SET queuesub_time=NOW(3) WHERE id=_jobPairId;
		END IF;
	END //

-- Sets the status code for the given stage of the given pair
DROP PROCEDURE IF EXISTS UpdatePairStageStatus //
CREATE PROCEDURE UpdatePairStageStatus(IN _jobPairId INT,IN _stageNumber INT, IN _statusCode TINYINT)
	BEGIN
		UPDATE jobpair_stage_data SET status_code=_statusCode WHERE jobpair_id=_jobPairId AND stage_number=_stageNumber;
	END //

-- Sets the status code of every stage occurring after the given stage to the given status code.
-- We do this, for example, when an early stage times out and so later stages are never run
DROP PROCEDURE IF EXISTS UpdateLaterStageStatuses //
CREATE PROCEDURE UpdateLaterStageStatuses(IN _jobPairId INT, IN _stageNumber INT, IN _statusCode TINYINT)
	BEGIN
		UPDATE jobpair_stage_data SET status_code=_statusCode WHERE jobpair_id=_jobPairId AND stage_number>_stageNumber;
	END //

-- Sets all run stats to 0 for stages that come after the given stage. This is used for
-- pipelines where an early stage fails, causing later stages to not run
DROP PROCEDURE IF EXISTS SetRunStatsForLaterStagesToZero //
CREATE PROCEDURE SetRunStatsForLaterStagesToZero(IN _jobPairId INT, IN _stageNumber INT)
	BEGIN
		UPDATE jobpair_stage_data
		SET wallclock = 0,
			cpu=0,
			user_time=0,
			system_time=0,
			max_vmem=0,
			max_res_set=0
		WHERE jobpair_id=_jobPairId AND stage_number>_stageNumber;
	END //

-- Gets all the stages for the given job pair
DROP PROCEDURE IF EXISTS GetJobPairStagesById //
CREATE PROCEDURE GetJobPairStagesById( IN _id INT)
	BEGIN
		SELECT *
		FROM jobpair_stage_data
		LEFT JOIN pipeline_stages ON pipeline_stages.stage_id=jobpair_stage_data.stage_id
		WHERE jobpair_id=_id
		ORDER BY jobpair_stage_data.stage_id ASC;
	END //
-- Gets the job pair with the given id. Only gets the primary stage!
-- Author: Tyler Jensen
DROP PROCEDURE IF EXISTS GetJobPairById //
CREATE PROCEDURE GetJobPairById(IN _Id INT)
	BEGIN
		SELECT *
		FROM job_pairs
		LEFT JOIN job_spaces AS jobSpace ON job_pairs.job_space_id=jobSpace.id
		LEFT JOIN job_pair_completion ON job_pairs.id = job_pair_completion.pair_id
		JOIN jobpair_stage_data ON jobpair_stage_data.jobpair_id = job_pairs.id
		WHERE job_pairs.id=_Id AND jobpair_stage_data.stage_number=job_pairs.primary_jobpair_data;
	END //

-- Retrieves all attributes for a job pair
-- Author: Tyler Jensen
DROP PROCEDURE IF EXISTS GetPairAttrs //
CREATE PROCEDURE GetPairAttrs(IN _pairId INT)
	BEGIN
		SELECT *
		FROM job_attributes
		WHERE pair_id=_pairId
		ORDER BY attr_key ASC;
	END //

-- Updates a job pair's backend ID (SGE, OAR, or so on).
-- Author: Tyler Jensen
DROP PROCEDURE IF EXISTS SetBackendExecId //
CREATE PROCEDURE SetBackendExecId(IN _jobPairId INT, IN _execId INT)
	BEGIN
		UPDATE job_pairs
		SET sge_id=_execId
		WHERE id=_jobPairId;
	END //

-- Gets back only the fields of a job pair that are necessary to determine where it is stored on disk
-- Author: Eric Burns
DROP PROCEDURE IF EXISTS GetJobPairFilePathInfo //
CREATE PROCEDURE GetJobPairFilePathInfo(IN _pairId INT)
	BEGIN
		SELECT job_id,job_pairs.job_space_id,path,jobpair_stage_data.solver_name,
		jobpair_stage_data.config_name,bench_name,jobpair_stage_data.stage_number FROM job_pairs
		JOIN jobpair_stage_data ON jobpair_stage_data.jobpair_id = job_pairs.id
		WHERE job_pairs.id=_pairId and jobpair_stage_data.stage_number = job_pairs.primary_jobpair_data;
	END //

-- Gets every pair_id and processor_id for pairs awaiting processing
DROP PROCEDURE IF EXISTS GetPairsToBeProcessed //
CREATE PROCEDURE GetPairsToBeProcessed(IN _processingStatus INT)
	BEGIN
		SELECT post_processor ,job_pairs.id, jobpair_stage_data.stage_number AS stageNumber
		FROM jobpair_stage_data
		JOIN job_pairs ON job_pairs.id = jobpair_stage_data.jobpair_id
		JOIN job_stage_params ON (job_stage_params.job_id=job_pairs.job_id AND job_stage_params.stage_number=jobpair_stage_data.stage_number)
		WHERE jobpair_stage_data.status_code=_processingStatus;
	END //

DROP PROCEDURE IF EXISTS RemovePairFromCompletedTable //
CREATE PROCEDURE RemovePairFromCompletedTable(IN _id INT)
	BEGIN
		DELETE FROM job_pair_completion
		WHERE pair_id=_id;
	END //

-- Sets the queue submission time to now (the moment this is called) for the pair with the given id
DROP PROCEDURE IF EXISTS SetPairStartTime //
CREATE PROCEDURE SetPairStartTime(IN _id INT)
	BEGIN
		UPDATE job_pairs SET start_time=NOW() WHERE id=_id;
	END //

-- Deletes a job pair from the database. The _pairSize argument is in bytes, and it is only
-- used in cases where the disk_size field is not set in the stages of the pair to be deleted.
-- This is necessary only for old pairs with no disk_size set
DROP PROCEDURE IF EXISTS DeleteJobPair //
CREATE PROCEDURE DeleteJobPair( IN _pairId INT)
	BEGIN
		DECLARE pair_disk_size BIGINT DEFAULT 0;
		SELECT sum(jobpair_stage_data.disk_size) INTO pair_disk_size
		FROM job_pairs JOIN jobpair_stage_data ON jobpair_stage_data.jobpair_id=job_pairs.id
		WHERE job_pairs.id=_pairId;

		UPDATE users
		SET users.disk_size=users.disk_size-pair_disk_size
		WHERE id = (SELECT user_id FROM jobs JOIN job_pairs ON jobs.id=job_pairs.job_id WHERE job_pairs.id=_pairId);

		UPDATE jobs
		SET jobs.disk_size=jobs.disk_size-pair_disk_size,
		total_pairs=total_pairs-1
		WHERE id=(SELECT job_id FROM job_pairs WHERE id=_pairId);

		DELETE FROM job_pairs
		WHERE job_pairs.id = _pairId;
	END //

-- Gets all of the job pairs in a job that contain a given benchmark.
-- Author: Albert Giegerich
DROP PROCEDURE IF EXISTS GetJobPairsInJobContainingBenchmark //
CREATE PROCEDURE GetJobPairsInJobContainingBenchmark(IN _jobId INT, IN _benchmarkId INT )
	BEGIN
		SELECT job_pairs.*
		FROM job_pairs
		WHERE job_id=_jobId AND bench_id=_benchmarkId;
	END //

DROP PROCEDURE IF EXISTS GetJobPairsInJobContainingSolver //
CREATE PROCEDURE GetJobPairsInJobContainingSolver(IN _jobId INT, IN _solverId INT)
  BEGIN
    SELECT job_pairs.*
    FROM job_pairs INNER JOIN jobpair_stage_data ON job_pairs.id=jobpair_stage_data.jobpair_id
	WHERE job_id=_jobId AND solver_id=_solverId;
  END //

-- Sets the completion time to now (the moment this is called) for the pair with the given id
-- Also sets the time_delta for the pair in the jobpair_time_delta table.
DROP PROCEDURE IF EXISTS SetPairEndTime //
CREATE PROCEDURE SetPairEndTime(IN _id INT)
	BEGIN
		UPDATE job_pairs SET end_time=NOW() WHERE id=_id;

		-- save the diff between this pair's timeout and wallclock time to jobpair_time_delta
		INSERT IGNORE INTO jobpair_time_delta(user_id, queue_id, time_delta)
		VALUES ((SELECT user_id FROM jobs JOIN job_pairs ON jobs.id=job_pairs.job_id WHERE job_pairs.id=_id),
		(SELECT queue_id FROM jobs JOIN job_pairs ON jobs.id=job_pairs.job_id WHERE job_pairs.id=_id), 0);

		UPDATE jobpair_time_delta
		SET time_delta = (SELECT jobs.clockTimeout - CEIL(SUM(jobpair_stage_data.wallclock)+1)+time_delta
						  FROM jobpair_stage_data JOIN job_pairs ON job_pairs.id=jobpair_stage_data.jobpair_id
						  JOIN jobs ON jobs.id = job_pairs.job_id WHERE jobpair_stage_data.jobpair_id=_id)
		WHERE user_id=(SELECT user_id FROM jobs JOIN job_pairs ON jobs.id=job_pairs.job_id WHERE job_pairs.id=_id)
		AND queue_id=(SELECT queue_id FROM jobs JOIN job_pairs ON jobs.id=job_pairs.job_id WHERE job_pairs.id=_id);

	END //

-- Counts the number of pairs with the given status code that completed in within the given
-- number of days
DROP PROCEDURE IF EXISTS CountRecentPairsByStatus //
CREATE PROCEDURE CountRecentPairsByStatus(IN _status INT, IN _days INT)
	BEGIN
		SELECT count(*) FROM job_pairs WHERE status_code=_status AND

		status_code=_status;-- end_time BETWEEN DATE_SUB(NOW(), INTERVAL _days DAY) AND NOW();
	END //

-- Adds a single job pair input to the database
DROP PROCEDURE IF EXISTS AddJobPairInput //
CREATE PROCEDURE AddJobPairInput(IN _pairId INT, IN _input INT, IN _benchId INT)
	BEGIN
		INSERT INTO jobpair_inputs (jobpair_id, input_number,bench_id) VALUES (_pairId,_input,_benchId);
	END //

DROP PROCEDURE IF EXISTS GetJobPairInputPaths //
CREATE PROCEDURE GetJobPairInputPaths(IN _pairId INT)
	BEGIN
		SELECT path,input_number FROM jobpair_inputs
		JOIN benchmarks ON benchmarks.id=jobpair_inputs.bench_id
		WHERE jobpair_id=_pairId ORDER BY input_number ASC;
	END //

-- Select all data from the jobpair_time_delta table for a specific
-- queue. -1 means all queues
DROP PROCEDURE IF EXISTS GetJobpairTimeDeltaData //
CREATE PROCEDURE GetJobpairTimeDeltaData(IN _qid INT)
	BEGIN
		SELECT * FROM jobpair_time_delta WHERE queue_id=_qid OR _qid = -1;
	END //


-- Deletes all data from the jobpair_time_delta table for a specific
-- queue. -1 means all queues
DROP PROCEDURE IF EXISTS ClearJobpairTimeDeltaData //
CREATE PROCEDURE ClearJobpairTimeDeltaData(IN _qid INT)
	BEGIN
		DELETE FROM jobpair_time_delta WHERE queue_id=_qid OR _qid=-1;
	END //

DROP PROCEDURE IF EXISTS GetJobPairsWithStatus //
CREATE PROCEDURE GetJobPairsWithStatus(IN _status INT)
	BEGIN
		SELECT * FROM job_pairs
		WHERE status_code = _status;
	END //

DROP PROCEDURE IF EXISTS GetJobPairIdsWithStatusNotRerunAfterDate //
CREATE PROCEDURE GetJobPairIdsWithStatusNotRerunAfterDate(IN _status INT, IN _earliestEndTime DATETIME)
	BEGIN
		SELECT id FROM job_pairs
		WHERE status_code = _status
		AND (job_pairs.end_time >= _earliestEndTime OR job_pairs.end_time < "1970-01-01")
		AND id NOT IN (SELECT pair_id FROM pairs_rerun);
	END //

DROP PROCEDURE IF EXISTS SetBrokenPairStatus //
CREATE PROCEDURE SetBrokenPairStatus(IN _pairId INT, IN _current_status INT, IN _new_status INT)
	BEGIN
		UPDATE jobpair_stage_data
		JOIN job_pairs ON jobpair_stage_data.jobpair_id = job_pairs.id
		SET jobpair_stage_data.status_code = _new_status
		WHERE jobpair_id=_pairId AND job_pairs.status_code=_current_status;

		UPDATE job_pairs
		SET status_code = _new_status
		WHERE id = _pairId AND status_code = _current_status;
	END //


-- Counts the total number of job pairs that satisfy GetNextPageOfJobPairsInJobSpaceHierarchy
DROP PROCEDURE IF EXISTS CountJobPairsInJobSpaceHierarchyByType //
CREATE PROCEDURE CountJobPairsInJobSpaceHierarchyByType(IN _jobSpaceId INT,IN _configId INT, IN _type VARCHAR(16), IN _query TEXT, IN _stageNumber INT)

	BEGIN
		SELECT COUNT(*) as count FROM job_pairs

		JOIN jobpair_stage_data ON jobpair_stage_data.jobpair_id = job_pairs.id
		LEFT JOIN job_pair_completion ON job_pair_completion.pair_id=job_pairs.id

		LEFT JOIN job_attributes on (job_attributes.pair_id=job_pairs.id and job_attributes.stage_number=jobpair_stage_data.stage_number AND job_attributes.attr_key="starexec-result")
		JOIN job_space_closure ON descendant=job_pairs.job_space_id
		LEFT JOIN bench_attributes ON (job_pairs.bench_id=bench_attributes.bench_id AND bench_attributes.attr_key = "starexec-expected-result")

		WHERE ancestor=_jobSpaceId AND jobpair_stage_data.config_id=_configId AND jobpair_stage_data.stage_number = _stageNumber AND
				((_type = "all") OR
				(_type="resource" AND job_pairs.status_code>=14 AND job_pairs.status_code<=17) OR
				(_type = "incomplete" AND job_pairs.status_code!=7 AND !(job_pairs.status_code>=14 AND job_pairs.status_code<=17)) OR
				(_type="failed" AND ((job_pairs.status_code>=8 AND job_pairs.status_code<=13) OR job_pairs.status_code=18)) OR
				(_type ="complete" AND (job_pairs.status_code=7 OR (job_pairs.status_code<=14 ANd job_pairs.status_code<=17))) OR
				(_type= "unknown" AND job_pairs.status_code=7 AND job_attributes.attr_value="starexec-unknown") OR
				(_type = "solved" AND job_pairs.status_code=7 AND (job_attributes.attr_value=bench_attributes.attr_value OR bench_attributes.attr_value is null)) OR
				(_type = "wrong" AND job_pairs.status_code=7 AND (bench_attributes.attr_value is not null) and (job_attributes.attr_value!=bench_attributes.attr_value)))

				AND

				(bench_name 		LIKE 	CONCAT('%', _query, '%')
				OR		jobpair_stage_data.config_name		LIKE	CONCAT('%', _query, '%')
				OR		jobpair_stage_data.solver_name		LIKE	CONCAT('%', _query, '%')
				OR		jobpair_stage_data.status_code 	LIKE 	CONCAT('%', _query, '%')
				OR		jobpair_stage_data.wallclock				LIKE	CONCAT('%', _query, '%')
				OR		cpu				LIKE	CONCAT('%', _query, '%')
				OR      job_attributes.attr_value 			LIKE 	CONCAT('%', _query, '%'));
	END //
