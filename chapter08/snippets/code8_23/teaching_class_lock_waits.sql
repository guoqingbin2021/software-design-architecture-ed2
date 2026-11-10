SELECT
    w.REQUESTING_ENGINE_TRANSACTION_ID AS waiting_trx,
    w.BLOCKING_ENGINE_TRANSACTION_ID AS holding_trx,
    l.OBJECT_NAME,
    l.LOCK_MODE,
    l.LOCK_STATUS
FROM
    performance_schema.data_lock_waits AS w
    JOIN performance_schema.data_locks AS l ON l.ENGINE = w.ENGINE
    AND l.ENGINE_LOCK_ID = w.REQUESTING_ENGINE_LOCK_ID
WHERE
    l.OBJECT_SCHEMA = DATABASE()
    AND l.OBJECT_NAME = 'teaching_class';
