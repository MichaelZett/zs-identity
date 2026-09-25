-- Protection against password guessing (issue #2).
--
-- failed_login_count counts wrong passwords for this account since the last
-- successful sign-in, password change or unlock. It is not reset when a lock
-- runs out: the next lock is twice as long, and the count is how the code
-- knows which lock this is. Failures older than the longest lock are
-- forgotten, so a typo last month does not make today's lock longer.
--
-- locked_until is the end of a temporary lock; NULL or in the past means not
-- locked. A lock is never permanent -- a permanent one would let anyone
-- disable any account on purpose, knowing only its address -- and it is not
-- the same as enabled = FALSE, which stays an administrator's decision.
ALTER TABLE auth_user
    ADD COLUMN failed_login_count   INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_failed_login_at TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN locked_until         TIMESTAMP(6) WITH TIME ZONE;
