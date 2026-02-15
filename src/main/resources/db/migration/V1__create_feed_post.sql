-- ============================================================
-- V1: Create feed_post table for the Regret Stream
-- ============================================================

CREATE TABLE feed_post (
    post_id       BIGSERIAL     PRIMARY KEY,
    author        VARCHAR(100)  NOT NULL,
    avatar        VARCHAR(10)   NOT NULL DEFAULT 'S',
    timestamp     VARCHAR(100)  NOT NULL,
    location      VARCHAR(100),
    content_type  VARCHAR(10)   NOT NULL CHECK (content_type IN ('code', 'image')),
    code_snippet  JSONB,
    image_content JSONB,
    caption       TEXT          NOT NULL,
    hashtags      TEXT[]        NOT NULL DEFAULT '{}',
    sort_order    INT           NOT NULL DEFAULT 0
);

CREATE INDEX idx_feed_post_sort ON feed_post(sort_order);
