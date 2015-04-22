-- name: list-access-requests
  SELECT *
    FROM access_requests
ORDER BY ar_id DESC
   LIMIT :limit
  OFFSET :offset

-- name: create-access-request
INSERT INTO access_requests
       (ar_username, ar_hostname, ar_reason, ar_remote_host, ar_created_by)
VALUES (:username, :hostname, :reason, :remote_host, :created_by)
RETURNING ar_id

-- name: update-access-request!
UPDATE access_requests
   SET ar_status = (:status)::access_request_status,
       ar_status_reason = :status_reason,
       ar_last_modified = now(),
       ar_last_modified_by = :last_modified_by
 WHERE ar_id = :id