-- ============================================================
-- Yugram Chat Backend - Skema PostgreSQL (FASA 1 + 2 + 3)
-- Padanan 1:1 dengan model Sequelize (src/models/*).
-- Jalankan: psql -d yugram_chat -f database/schema.sql
-- ============================================================

-- gen_random_uuid() terbina dalam PG13+; pgcrypto untuk versi lama.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ------------------------------------------------------------
-- users
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  username      VARCHAR(32)  NOT NULL UNIQUE,
  display_name  VARCHAR(64)  NOT NULL,
  password_hash VARCHAR(128) NOT NULL,
  bio           VARCHAR(280),
  avatar_url    VARCHAR(512),
  last_seen_at  TIMESTAMPTZ,
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
  CONSTRAINT chk_users_username
    CHECK (username ~* '^[a-z0-9_]{3,32}$')
);

-- ------------------------------------------------------------
-- rooms (Room/Namespace)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rooms (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  type        VARCHAR(10)  NOT NULL DEFAULT 'direct'
              CHECK (type IN ('direct', 'group')),
  name        VARCHAR(64),
  -- Kunci unik bilik direct: "<userIdA>:<userIdB>" (diisih)
  direct_key  VARCHAR(80)  UNIQUE,
  created_by  UUID         NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rooms_type ON rooms (type);

-- ------------------------------------------------------------
-- room_members (keahlian + jejak bacaan terakhir)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS room_members (
  id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  room_id              UUID        NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
  user_id              UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role                 VARCHAR(10) NOT NULL DEFAULT 'member'
                       CHECK (role IN ('member', 'admin')),
  last_read_message_id UUID,
  last_read_at         TIMESTAMPTZ,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_room_members_room_user UNIQUE (room_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_room_members_user ON room_members (user_id);

-- ------------------------------------------------------------
-- messages (mesej sembang)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS messages (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  sender_id          UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  room_id            UUID        NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
  text               TEXT        NOT NULL,
  is_silent          BOOLEAN     NOT NULL DEFAULT FALSE,
  is_edited          BOOLEAN     NOT NULL DEFAULT FALSE,
  edited_at          TIMESTAMPTZ,
  reply_to_message_id UUID       REFERENCES messages(id) ON DELETE SET NULL,
  reactions          JSONB       NOT NULL DEFAULT '[]'::jsonb,
  -- FASA 2: lampiran media { url, name, mimeType, size }
  media              JSONB,
  -- FASA 3: nama paparan penghantar asal bagi mesej terusan
  forwarded_from_name VARCHAR(64),
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_messages_text_len
    CHECK (char_length(text) BETWEEN 0 AND 4096),
  CONSTRAINT chk_messages_reactions_json
    CHECK (jsonb_typeof(reactions) = 'array')
);

CREATE INDEX IF NOT EXISTS idx_messages_room_created ON messages (room_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_sender ON messages (sender_id);
CREATE INDEX IF NOT EXISTS idx_messages_media ON messages (room_id) WHERE media IS NOT NULL;

-- ------------------------------------------------------------
-- message_reads (read receipt per-mesej => tick berganda)
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS message_reads (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  message_id UUID        NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  room_id    UUID        NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
  user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  read_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_message_reads_message_user UNIQUE (message_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_message_reads_room_user ON message_reads (room_id, user_id);
