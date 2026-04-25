-- ============================================================
-- V2: Regret Stream redesign — additive migration.
--
-- Creates the new v2 schema alongside the legacy `feed_post` table
-- from V1. Nothing is dropped here; the old table and its endpoints
-- continue to serve the existing FE until a later V3 cutover runs
-- `DROP TABLE feed_post` after the new FE is live.
--
-- Design choices worth re-reading before editing:
--  - `app_user` mirrors Supabase auth; PK is the JWT `sub` (UUID).
--    Populated lazily on first authenticated write, not pre-seeded.
--  - `post.author_id` is a real FK. Only the portfolio owner is
--    supposed to post, but we enforce that at the service layer
--    (via a configured author-UUID check) rather than a DB constraint,
--    so ownership rules can flex without a migration.
--  - Soft-deletes via `deleted_at`. Partial indexes skip tombstones,
--    which keeps the hot-path index small. The feed-list query
--    walks `idx_post_created_live` in one direction only (keyset).
--  - Instagram-style comment replies: `parent_comment_id` can only
--    point at a TOP-LEVEL comment (parent_comment_id IS NULL).
--    Enforced at the service layer — when someone clicks "reply" on
--    a reply, we re-parent the new comment to the original top-level
--    and record the @mention in `mentioned_user_id`. Storing that
--    column avoids parsing handles out of the body at render time.
--  - Tags are normalised. Redis will hold the hot inverted index
--    (`tag:{slug}` ZSET); the DB is the durable source of truth.
--  - `post_like` is the ground-truth for likes. Redis counters will
--    sit in front for read throughput in a later PR.
-- ============================================================

-- 1. Mirror of Supabase auth users. PK is the JWT `sub` (UUID).
CREATE TABLE app_user (
    user_id      UUID         PRIMARY KEY,
    handle       VARCHAR(40)  NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    avatar_url   VARCHAR(500),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 2. Posts — one author (the portfolio owner), anyone reads.
CREATE TABLE post (
    post_id       BIGSERIAL    PRIMARY KEY,
    author_id     UUID         NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    caption       TEXT         NOT NULL CHECK (char_length(caption) BETWEEN 1 AND 2000),
    code_body     TEXT,
    code_language VARCHAR(40),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMPTZ
);

-- Keyset pagination walks this. DESC composite on (created_at, post_id)
-- keeps the sort stable when two posts share a second. Partial skips
-- soft-deleted rows so the index stays as small as the live table.
CREATE INDEX idx_post_created_live
    ON post (created_at DESC, post_id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_post_author_live
    ON post (author_id)
    WHERE deleted_at IS NULL;

-- 3. Comments. `parent_comment_id` is nullable and self-FK'd.
--    App layer enforces the Instagram-style "at most one level" rule.
CREATE TABLE comment (
    comment_id        BIGSERIAL   PRIMARY KEY,
    post_id           BIGINT      NOT NULL REFERENCES post(post_id) ON DELETE CASCADE,
    author_id         UUID        NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    parent_comment_id BIGINT      REFERENCES comment(comment_id) ON DELETE CASCADE,
    mentioned_user_id UUID        REFERENCES app_user(user_id) ON DELETE SET NULL,
    body              TEXT        NOT NULL CHECK (char_length(body) BETWEEN 1 AND 500),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ
);

-- Top-level comments on a post, newest first (keyset).
CREATE INDEX idx_comment_post_toplevel
    ON comment (post_id, created_at DESC, comment_id DESC)
    WHERE deleted_at IS NULL AND parent_comment_id IS NULL;

-- Replies under a top-level comment, oldest first (Instagram order).
CREATE INDEX idx_comment_parent
    ON comment (parent_comment_id, created_at ASC, comment_id ASC)
    WHERE deleted_at IS NULL AND parent_comment_id IS NOT NULL;

-- 4. Tags. slug is lowercase, [a-z0-9-]; display preserves original case.
CREATE TABLE tag (
    tag_id     BIGSERIAL   PRIMARY KEY,
    slug       VARCHAR(50) NOT NULL UNIQUE,
    display    VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE post_tag (
    post_id BIGINT NOT NULL REFERENCES post(post_id) ON DELETE CASCADE,
    tag_id  BIGINT NOT NULL REFERENCES tag(tag_id)   ON DELETE CASCADE,
    PRIMARY KEY (post_id, tag_id)
);

-- Reverse lookup: "which posts carry this tag?" Postgres hot path.
-- (Redis ZSET `tag:{slug}` covers the cached path in a later PR.)
CREATE INDEX idx_post_tag_tag ON post_tag (tag_id);

-- 5. Likes. (post_id, user_id) is the natural primary key —
--    idempotent by construction, no need for a surrogate id.
CREATE TABLE post_like (
    post_id    BIGINT      NOT NULL REFERENCES post(post_id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (post_id, user_id)
);

-- "Which posts has this user liked?" — complements the PK order.
CREATE INDEX idx_post_like_user ON post_like (user_id);
