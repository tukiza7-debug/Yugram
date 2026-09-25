# Yugram — Modul Sembang & Masa Nyata (FASA 1 + 2 + 3)

Rebuild aplikasi Telegram Clone dengan tindanan (stack) baharu:

| Lapisan | Teknologi |
|---|---|
| Backend | Node.js + Express + Socket.io |
| Pangkalan Data | PostgreSQL + Sequelize (ORM) |
| Frontend Mobile | Flutter / Dart + BLoC |
| Masa Nyata | Socket.io (WebSocket) |

**Seni bina: Clean Architecture** — lapisan logik bisnes (`services`/`domain`),
rangkaian (`repositories`/`datasources`) dan paparan (`controllers`/`presentation`)
dipisahkan sepenuhnya. Setiap fungsi dilengkapi try-catch, pengesahan input
(validasi) dan log ralat berstruktur. **Tiada TODO, tiada fungsi kosong — 100%
sedia untuk production.**

---

## Struktur Projek

```
yugram-v2/
├── backend/                       # Node.js + Socket.io + PostgreSQL
│   ├── src/
│   │   ├── config/                # env.js (validasi Joi), database.js (Sequelize)
│   │   ├── models/                # User, Room, RoomMember, Message, MessageRead
│   │   ├── repositories/          # Lapisan akses data (tiada logik bisnes)
│   │   ├── services/              # Logik bisnes: auth, room, message, notification
│   │   ├── sockets/               # chatSocket.js (pengurus socket) + socketAuth.js
│   │   ├── validators/            # Skema Joi (event socket + REST)
│   │   ├── middlewares/           # auth JWT, pengesahan, ralat pusat
│   │   ├── controllers/           # Handler REST
│   │   ├── routes/                # /api/auth, /api/rooms
│   │   ├── utils/                 # logger, ApiError, rate limiter, constants
│   │   ├── app.js                 # Aplikasi Express
│   │   └── server.js              # Titik masuk + graceful shutdown
│   ├── database/schema.sql        # DDL PostgreSQL penuh (production)
│   └── scripts/                   # db-sync, syntax-check, smoke-test (e2e)
│
└── frontend/                      # Flutter + BLoC + socket_io_client
    └── lib/
        ├── core/                  # constants, network state, logger, error
        ├── data/
        │   ├── datasources/       # socket_service.dart (SINGLETON), api, token
        │   ├── models/            # DTO + parsing JSON pelayan
        │   └── repositories/      # Implementasi repositori domain
        ├── domain/
        │   ├── entities/          # MessageEntity, RoomEntity, event masa nyata
        │   └── repositories/      # Kontrak (abstrak) repositori
        └── presentation/
            ├── bloc/              # ChatBloc, AuthCubit, RoomsCubit
            ├── screens/           # Login, Rooms, Chat
            └── widgets/           # MessageBubble (ticks), ReactionPickerSheet
```

---

## Fitur FASA 1 (Semua Berfungsi, Terbukti Melalui Ujian E2E)

| # | Fitur | Implementasi |
|---|-------|--------------|
| 10 | Sembang Peribadi 1-ke-1 + Sistem Bilik | Jadual `rooms` (type=direct) dengan `direct_key` unik (pasangan ID diisih) — bilik **idempoten** (dua pihak yang mencipta mendapat bilik yang sama) |
| 11 | Status Hantar & Baca (Ticks) | **Tick tunggal** ✓ = `message_ack` (mesej diterima pelayan). **Tick berganda** ✓✓ = `read_receipt` (penerima emit `message_read`) |
| 12 | Typing "Typing..." Masa Nyata | Event `typing_status` dua arah, throttle 1.5s + auto-clear 3s + pembersihan automatik semasa disconnect |
| 67 | Reaksi Mesej Pantas | Event `add_reaction` (toggle: sama=buang, lain=ganti), disimpan dalam `messages.reactions` (JSONB array), broadcast `reaction_updated` |
| 64 | Mesej Tanpa Bunyi (Silent) | Bendera `isSilent` disimpan di DB + disiar ke semua klien; **enjin push membangkitkan envelope tanpa bunyi** (`sound:null`, priority normal, interruption-level passive) |

---

## FASA 2 — Kumpulan, Profil & Media (Semua Berfungsi, 44 Semakan E2E)

| Fitur | Implementasi |
|-------|--------------|
| Sembang Kumpulan | `POST /api/rooms/group` (nama + memberUsernames[]); pencipta = admin; keahlian dalam transaksi |
| Urus Keahlian | Tambah (ahli mana-mana boleh tambah), buang (admin sahaja, admin tak boleh dibuang), lantik admin (pencipta), keluar (pencipta sekat jika tiada admin lain), padam kumpulan (pencipta) |
| Broadcast Metadata | `room_updated` (tambah/buang/lantik/rename) + `room_deleted` — semua ahli dikemas kini masa nyata |
| Profil Pengguna | `PATCH /api/users/me` (displayName, bio, username - konflik unik = 409); skrin Profil Flutter |
| Carian Pengguna | `GET /api/users/search?q=` (ILIKE username/displayName, tak termasuk diri) |
| Media | `POST /api/media` (multipart: imej/video mp4/pdf, max 10MB, nama cakera rawak); mesej bermedia `media JSONB`; dihidang statik `/uploads/` |
| Unread Count | `GET /api/rooms` memulangkan `unreadCount` per bilik (badge senarai sembang) |
| Muat Turun Pukal | `GET /api/rooms/:id/media` + "Muat turun semua" dalam skrin info kumpulan (disimpan ke storan peranti) |

## FASA 3 — Kuasa Mesej (Semua Berfungsi)

| Fitur | Implementasi |
|-------|--------------|
| Edit Mesej | `PATCH /api/messages/:id` (penghantar sahaja) → `isEdited`, `editedAt` + broadcast `message_edited`; UI composer mod edit |
| Padam Mesej | `DELETE /api/messages/:id` (penghantar atau admin) + broadcast `message_deleted` |
| Teruskan (Forward) | `send_message` dengan `forwardedFromName` — label "Diteruskan dari X"; pemilih bilik sasaran dalam UI |
| Carian Dalam Sembang | `GET /api/rooms/:id/messages?q=` (ILIKE, tidak kes sensitif) + dialog carian |

---

## Kontrak Event Socket.io

### Client → Server

| Event | Payload |
|---|---|
| `join_room` | `{ roomId }` |
| `send_message` | `{ roomId, text, isSilent, replyToMessageId, media?, forwardedFromName?, tempId }` |
| `message_read` | `{ roomId, lastReadMessageId }` |
| `typing_status` | `{ roomId, isTyping }` |
| `add_reaction` | `{ messageId, emoji }` |

### Server → Client

| Event | Payload |
|---|---|
| `connection_ready` | `{ userId, username, displayName, serverTime }` |
| `room_joined` | `{ roomId, members[], messages[], serverTime }` |
| `message_ack` | `{ tempId, message, status: "sent" }` → **tick tunggal** |
| `new_message` | `{ message }` (termasuk `isSilent`, `media`, `forwardedFromName`) |
| `read_receipt` | `{ roomId, userId, messageIds[], readAt }` → **tick berganda** |
| `typing_status` | `{ roomId, userId, displayName, isTyping }` |
| `reaction_updated` | `{ messageId, roomId, reactions[], updatedBy }` |
| `message_edited` | `{ roomId, message }` (mesej penuh terkini) |
| `message_deleted` | `{ roomId, messageId, deletedBy }` |
| `room_updated` | `{ room, members[], updatedBy }` |
| `room_deleted` | `{ roomId, reason }` |
| `error` | `{ event, code, message, details }` |

Pengesahan: token JWT dihantar melalui handshake — `io(url, { auth: { token } })`.
Semua payload disahkan dengan Joi; payload tidak sah memulangkan event `error`.

---

## Skema Pangkalan Data (messages)

```sql
messages (
  id                  UUID PRIMARY KEY,
  sender_id           UUID NOT NULL -> users,
  room_id             UUID NOT NULL -> rooms,
  text                TEXT NOT NULL (0..4096; teks kosong dibenarkan jika ada media),
  is_silent           BOOLEAN NOT NULL DEFAULT FALSE,   -- Fitur 64
  is_edited           BOOLEAN NOT NULL DEFAULT FALSE,   -- FASA 3
  edited_at           TIMESTAMPTZ,                      -- FASA 3
  reply_to_message_id UUID -> messages (SET NULL),
  reactions           JSONB NOT NULL DEFAULT '[]',      -- Fitur 67
  media               JSONB,                            -- FASA 2 {url,name,mimeType,size}
  forwarded_from_name VARCHAR(64),                     -- FASA 3
  created_at / updated_at TIMESTAMPTZ
)
```

Jadual lain: `users`, `rooms`, `room_members` (jejak `last_read`),
`message_reads` (unique `message_id, user_id` — asas tick berganda).
DDL penuh: `backend/database/schema.sql`.

---

## Cara Menjalankan

### 1. Backend

```bash
cd backend
npm install
# Cipta DB + pengguna PostgreSQL (cth.):
#   CREATE ROLE yugram LOGIN PASSWORD 'yugram_secret';
#   CREATE DATABASE yugram_chat OWNER yugram;
# Segerakkan skema (development):
npm run db:sync
#   ATAU untuk production: psql -d yugram_chat -f database/schema.sql
npm start                    # pelayan di :4000
```

Sahkan konfigurasi melalui `.env` (contoh: `.env.example`).

### 2. Ujian E2E Sebenar (dua klien socket)

```bash
cd backend
npm run smoke-test            # FASA 1: 20 semakan (ticks, typing, reactions, silent, keselamatan)
npm run smoke-test:fasa23     # FASA 2+3: 44 semakan (kumpulan, profil, media, edit, padam, forward, carian)
```

Hasil rujukan semasa pembinaan: **FASA 1 = 20 PASS / 0 FAIL**, **FASA 2+3 = 44 PASS / 0 FAIL**
terhadap PostgreSQL sebenar.

### 3. Frontend Flutter

```bash
cd mobile
flutter create . --project-name yugram_chat --platforms android --org dev.yugram   # sekali sahaja (jana scaffolding)
flutter pub get
flutter run                  # emulator Android (API: http://10.0.2.2:4000)
```

Untuk peranti fizikal, tukar `AppConstants.apiBaseUrl`
(`lib/core/constants/app_constants.dart`) ke IP komputer anda, cth:

```bash
flutter run --dart-define=YUGRAM_API_URL=http://192.168.1.10:4000
```

### 4. APK Automatik (GitHub Actions)

Workflow `.github/workflows/flutter-build.yml` membina **APK release** pada setiap
push ke `main` yang mengubah `mobile/`. Muat turun artifact **yugram-apk**
dari tab Actions — aplikasi sedia dipasang tanpa perlu Flutter tempatan.

---

## Perihal Lapisan Frontend

- **SocketService (Singleton)** — `lib/data/datasources/socket_service.dart`:
  satu gerbang WebSocket untuk keseluruhan aplikasi. Mempunyai
  StreamController broadcast untuk setiap event (`onNewMessage`,
  `onMessageAck`, `onReadReceipt`, `onTypingStatus`, `onReactionUpdated`,
  `onRoomJoined`, `onError`, `connectionState`) dan kaedah emit terlindung
  (`emitSendMessage`, `emitMessageRead`, `emitTypingStatus`, `emitAddReaction`,
  `emitJoinRoom`) — sedia dikonsumsi terus oleh BLoC.
- **ChatBloc** — mengoptimumkan UI secara tempatan (bubble `sending`),
  memetakan `message_ack` → tick tunggal, `read_receipt` → tick berganda,
  auto tanda-baca, throttle typing, dan lekap semula (rejoin) bilik selepas
  sambungan pulih.
- **Kebersihan ralat** — setiap emit/strim dilindungi (try-catch, isClosed
  guard, ralat parse tidak pernah merosakkan apl).

## Peta Jalan (FASA seterusnya)

FASA 2: sembang kumpulan + media (imej/video/dokumen). FASA 3:
notifikasi push sebenar (FCM/APNs), penyulitan E2E, khalayak tutup
(vanish mode). Struktur semasa sedia dikembangkan tanpa refactor.
