-- name: list-access-requests
  SELECT *
    FROM access_requests
   WHERE ar_status = COALESCE((:status)::access_request_status, ar_status)
     AND ar_hostname = COALESCE(:hostname, ar_hostname)
     AND ar_username = COALESCE(:username, ar_username)
ORDER BY ar_id DESC
   LIMIT :limit
  OFFSET :offset

-- name: get-expired-access-requests
  SELECT *
    FROM access_requests
   WHERE (ar_status = 'GRANTED'
      OR (ar_status = 'EXPIRED' AND now() > ar_last_modified + interval '5 minutes'))
     AND now() > ar_created + (ar_lifetime_minutes * interval '1 minute')
ORDER BY ar_id ASC

-- name: count-remaining-granted-access-requests
-- return the number of remaining SSH access "connections" to the given hostname excluding the given request ID
SELECT COUNT(*) AS count
  FROM access_requests
 WHERE ar_status = 'GRANTED'
   AND ar_hostname = :hostname
   AND ar_id != :id

-- name: create-access-request
INSERT INTO access_requests
       (ar_username, ar_hostname, ar_reason, ar_remote_host, ar_lifetime_minutes, ar_created_by)
VALUES (:username, :hostname, :reason, :remote_host, :lifetime_minutes, :created_by)
RETURNING ar_id

-- name: update-access-request!
UPDATE access_requests
   SET ar_status = (:status)::access_request_status,
       ar_status_reason = :status_reason,
       ar_last_modified = now(),
       ar_last_modified_by = :last_modified_by
 WHERE ar_id = :id

-- name: acquire-lock
INSERT INTO locks
       (l_resource_name, l_created_by)
VALUES (:resource_name, :created_by)
RETURNING l_id, l_resource_name

-- name: release-lock!
DELETE FROM locks
      WHERE l_resource_name = :resource_name
        AND l_id = :id

-- name: clean-up-old-locks!
DELETE FROM locks
      WHERE now() > l_created + interval '1 hour'