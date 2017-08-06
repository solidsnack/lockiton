CREATE UNLOGGED TABLE free (
  token             int PRIMARY KEY,
  t                 timestamptz NOT NULL DEFAULT now(),
  queue             text NOT NULL DEFAULT '?'
);
CREATE INDEX "free/queue" ON free (queue);

CREATE UNLOGGED TABLE held (LIKE free INCLUDING ALL);

CREATE TABLE token (
  token             serial PRIMARY KEY,
  job               uuid NOT NULL
);

CREATE TABLE job (
  job               uuid PRIMARY KEY,
  t                 timestamptz NOT NULL DEFAULT now(),
  queue             text NOT NULL,
  data              jsonb NOT NULL DEFAULT '{}'
);
CREATE INDEX "job/queue" ON job (queue);

CREATE FUNCTION obtain(queue text DEFAULT '*', n int DEFAULT 8)
RETURNS TABLE (token int) AS $$
  WITH taken AS (DELETE FROM free
                  WHERE token IN (SELECT token FROM free
                                   WHERE obtain.queue = '*'
                                      OR free.queue = obtain.queue
                                     -- TODO: Does not make any difference?
                                     -- FOR UPDATE SKIP LOCKED
                                   LIMIT n)
                 RETURNING token, queue)
  INSERT INTO held (token, queue) SELECT * FROM taken RETURNING token
$$ LANGUAGE sql STRICT;

--CREATE FUNCTION redeem(token int) RETURNS job AS $$
----- Record redemption.
----- Return corresponding job.
--$$ LANGUAGE sql;

CREATE FUNCTION renew(token int) RETURNS boolean AS $$
  SELECT NOT EXISTS (SELECT * FROM free WHERE free.token = renew.token)
$$ LANGUAGE sql;

CREATE FUNCTION release(token int) RETURNS void AS $$
  WITH taken AS (DELETE FROM held
                  WHERE token IN (SELECT token FROM held
                                   WHERE held.token = release.token)
                 RETURNING token, queue)
  INSERT INTO free (token, queue) SELECT * FROM taken
$$ LANGUAGE sql;
