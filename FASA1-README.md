# Yugram — FASA 1: Modul Utama Sembang & Masa Nyata

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
yugram/
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
└── mobile/                      # Flutter + BLoC + socket_io_client
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

## Kontrak Event Socket.io

### Client → Server

| Event | Payload |
|---|---|
| `join_room` | `{ roomId }` |
| `send_message` | `{ roomId, text, isSilent, replyToMessageId, tempId }` |
| `message_read` | `{ roomId, lastReadMessageId }` |
| `typing_status` | `{ roomId, isTyping }` |
| `add_reaction` | `{ messageId, emoji }` |

### Server → Client

| Event | Payload |
|---|---|
| `connection_ready` | `{ userId, username, displayName, serverTime }` |
| `room_joined` | `{ roomId, members[], messages[], serverTime }` |
| `message_ack` | `{ tempId, message, status: "sent" }` → **tick tunggal** |
| `new_message` | `{ message }` (termasuk `isSilent`) |
| `read_receipt` | `{ roomId, userId, messageIds[], readAt }` → **tick berganda** |
| `typing_status` | `{ roomId, userId, displayName, isTyping }` |
| `reaction_updated` | `{ messageId, roomId, reactions[], updatedBy }` |
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
  text                TEXT NOT NULL (1..4096),
  is_silent           BOOLEAN NOT NULL DEFAULT FALSE,   -- Fitur 64
  is_edited           BOOLEAN NOT NULL DEFAULT FALSE,
  reply_to_message_id UUID -> messages (SET NULL),
  reactions           JSONB NOT NULL DEFAULT '[]',      -- Fitur 67
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
npm run smoke-test           # 20 semakan: ticks, typing, reactions, silent, keselamatan
```

Hasil rujukan semasa pembinaan: **20 PASS / 0 FAIL** terhadap PostgreSQL sebenar.

### 3. Frontend Flutter

```bash
cd mobile
flutter pub get
flutter run                  # emulator Android (API: http://10.0.2.2:4000)
```

Untuk peranti fizikal, tukar `AppConstants.apiBaseUrl`
(`lib/core/constants/app_constants.dart`) ke IP komputer anda, cth:

```bash
flutter run --dart-define=YUGRAM_API_URL=http://192.168.1.10:4000
```

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
