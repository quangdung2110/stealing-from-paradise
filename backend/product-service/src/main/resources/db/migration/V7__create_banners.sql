-- =====================================================
-- Flyway Migration: V7__create_banners
-- Description: Creates the banners table for admin management
--              Banners are displayed on the customer homepage
--              at various positions (HERO, SIDEBAR, POPUP).
-- =====================================================

CREATE TABLE IF NOT EXISTS banners (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    title       VARCHAR(255) NOT NULL,
    image_url   VARCHAR(500) NOT NULL,
    position    VARCHAR(50)  NOT NULL DEFAULT 'HERO',
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    starts_at   TIMESTAMP,
    ends_at     TIMESTAMP,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_banners_position ON banners (position);
CREATE INDEX IF NOT EXISTS idx_banners_active   ON banners (active);

COMMENT ON TABLE  banners                  IS 'Banners for customer-facing homepage display';
COMMENT ON COLUMN banners.position         IS 'Display position: HERO, SIDEBAR, or POPUP';
COMMENT ON COLUMN banners.active           IS 'Whether the banner is currently active';
COMMENT ON COLUMN banners.starts_at        IS 'Scheduled start time (null = immediate)';
COMMENT ON COLUMN banners.ends_at          IS 'Scheduled end time (null = no end)';
COMMENT ON COLUMN banners.sort_order       IS 'Display order (ascending)';
