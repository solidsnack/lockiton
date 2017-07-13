CREATE UNLOGGED TABLE lock (
  token             int PRIMARY KEY,
  queue             text NOT NULL DEFAULT '?'
);
CREATE INDEX "lock/of" ON lock (of);

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

CREATE FUNCTION obtain(queue text, n int DEFAULT 8)
RETURNS TABLE (token int) AS $$
  DELETE FROM lock WHERE token IN (
    SELECT token FROM lock
     WHERE lock.queue = obtain.queue
       FOR NO KEY UPDATE SKIP LOCKED
     LIMIT n
  ) RETURNING token
$$ LANGUAGE sql;

CREATE FUNCTION redeem(token int) RETURNS job AS $$
--- Record redemption.
--- Return corresponding job.
$$ LANGUAGE sql;

CREATE FUNCTION renew(token int) RETURNS boolean AS $$
  SELECT NOT EXISTS (SELECT * FROM lock WHERE lock.token = renew.token)
$$ LANGUAGE sql;

CREATE FUNCTION release(token int) RETURNS void AS $$
  INSERT INTO lock (token, queue)
       SELECT token, queue
         FROM token NATURAL JOIN job WHERE token.token = release.token
$$ LANGUAGE sql;
