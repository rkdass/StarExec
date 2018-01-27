-- we are changing our status codes a bit to distinguish between cpu timeout and memout
-- (these were not distinguished previously).

UPDATE status_codes SET description = 'the job was terminated because it exceeded its cpu time limit' WHERE code=15;

UPDATE status_codes SET status = "memout" , description = "the job was terminated because it exceeded its virtual memory limit" WHERE code = 17;

INSERT INTO status_codes VALUES (18, 'error', 'an unknown error occurred which indicates a problem at any point in the job execution pipeline');
INSERT INTO status_codes VALUES (19, 'processing results', 'the job results are currently being processed');
