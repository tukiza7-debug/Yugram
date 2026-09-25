# Yugram

<p align="center">
  <img src="docs/logo/yugram-animasi.gif" alt="Logo Yugram" width="240">
</p>

<p align="center"><em>Kapal kertas dalam litar bergradasi — logo rasmi Yugram.</em></p>

Aplikasi sembang gaya Telegram yang dibina sepenuhnya sendiri (self-hosted) — tanpa TDLib, tanpa server Telegram. **Semua fasa pembangunan telah SIAP dan diuji end-to-end dengan 135 semakan automatik (0 gagal).**

<p align="center">
  <strong>Flutter / Dart</strong> &middot; <strong>BLoC</strong> &middot; <strong>Node.js</strong> &middot; <strong>Socket.io</strong> &middot; <strong>PostgreSQL</strong> &middot; <strong>JWT</strong>
</p>

---

## Logo

| Varian | Fail | Kegunaan |
|--------|------|----------|
| Beranimasi (SVG + CSS) | [`docs/logo/yugram-logo-animasi.svg`](docs/logo/yugram-logo-animasi.svg) | web / pratonton pelayar — gelung 4.2s: pop-in, litar terlukis, kapal terbang masuk, terapung + denyar |
| Beranimasi (GIF) | [`docs/logo/yugram-animasi.gif`](docs/logo/yugram-animasi.gif) | README / mesej |
| Statik (SVG) | [`docs/logo/yugram-logo.svg`](docs/logo/yugram-logo.svg) | ikon app, dokumen |
| Lockup melintang | [`docs/logo/yugram-lockup.svg`](docs/logo/yugram-lockup.svg) | header / penjenamaan |
| Ikon launcher | `mobile/assets/icon/` | APK (jana automatik di CI) |

Dalam aplikasi, logo dihidupkan sebagai widget asli `AnimatedYugramLogo` (CustomPainter, tiada pergantungan luar): skrin splas memainkan intro penuh (pop-in → litar → kapal terbang masuk → percikan), kemudian terapung lembut dengan denyar cahaya; skrin log masuk memaparkan varian statik ringkas. Sumber penjana: `scripts/logo_gen.py` pada mesin pembangun.

---

## Status Projek

| Fasa | Kandungan | Status |
|------|-----------|--------|
| **FASA 1** | Sembang peribadi 1-ke-1, ticks hantar/baca (tunggal/berganda), typing realtime, reactions, silent message | ✅ SIAP |
| **FASA 2** | Kumpulan penuh (ahli/role/rename/padam), unread badge, media (muat naik/papar/muat turun), info kumpulan + galeri | ✅ SIAP |
| **FASA 3** | Profil (nama/bio), edit mesej (label "diedit"), padam mesej, teruskan (forward tanpa quote), carian mesej, pagination sejarah, reply (balas mesej) | ✅ SIAP |
| **App** | Binaan APK release automatik melalui GitHub Actions (artifact `yugram-apk`) | ✅ SIAP |

## Ciri-ciri Utama

**Sembang realtime**
- Mesej teks + media (PNG/JPG/WebP/GIF, MP4, PDF) dihantar segera melalui Socket.io
- **Ticks**: jam (menghantar) → tick tunggal (hantar berjaya) → tick berganda (dibaca)
- Petunjuk *typing* realtime dengan nama penghantar
- Reaksi emoji (tambah / ganti / toggle) dengan broadcast langsung
- *Reply* mesej dengan rujukan ke mesej asal
- Mesej senyap (`isSilent`) — penerima tidak diberi notifikasi bunyi

**Kumpulan**
- Cipta kumpulan, tambah/buang ahli, lantik/turunkan admin
- Rename kumpulan + broadcast `room_updated` kepada semua ahli
- Peraturan keselamatan: satu-satunya admin tidak boleh keluar; bukan pencipta tidak boleh padam
- Badge belum-baca (unread) dalam senarai sembang

**Media**
- Muat naik multipart (had 10 MB, jenis fail disaring) + akses statik `/uploads/`
- Pemahir imej skrin penuh (zoom + muat turun) dalam app
- Senarai media per bilik (`GET /rooms/:id/media`) untuk muat turun pukal

**Pengurusan mesej**
- Edit mesej sendiri (`isEdited` + `editedAt` + broadcast)
- Padam mesej (penghantar / admin) + broadcast + hilang dari sejarah
- Teruskan mesej dengan label `forwardedFromName`
- Carian mesej dalam bilik (`?q=`, tidak kes sensitif)
- Pagination sejarah (`?limit=&before=`)

**Profil & keselamatan**
- Daftar/log masuk dengan JWT (bcrypt hash), PATCH profil (nama paparan + bio)
- Carian pengguna ILIKE (diri sendiri dikecualikan)
- Validasi Joi di semua REST + payload socket; had kadar mesej sliding-window
- Bukan ahli DITOLAK pada semua laluan (REST + socket) dengan 403 / event `error` berstruktur

## Seni Bina

```
yugram/
├── backend/                  # Node.js + Express + Socket.io + Sequelize + PostgreSQL
│   ├── src/
│   │   ├── config/           # env (Joi-validated), database
│   │   ├── controllers/      # auth, room, message, user, media
│   │   ├── middlewares/      # auth JWT, validate, upload (multer), error
│   │   ├── models/           # User, Room, RoomMember, Message, MessageRead
│   │   ├── repositories/     # akses data (satu model = satu repo)
│   │   ├── services/         # business logic (auth, room, message, user, notification)
│   │   ├── sockets/          # socketAuth (JWT handshake), chatSocket, socketGateway
│   │   ├── utils/            # logger JSON, ApiError, rateLimiter, constants
│   │   └── validators/       # skema Joi REST + chat
│   ├── scripts/              # db-sync, syntax-check, smoke tests, uji-semua
│   └── database/schema.sql   # DDL penuh
├── mobile/                   # Flutter + BLoC (Clean Architecture)
│   └── lib/
│       ├── core/             # logger, constants, error, network state
│       ├── data/             # datasources (socket singleton, API), models, repos impl
│       ├── domain/           # entities + kontrak repositori
│       └── presentation/     # bloc (chat/auth/rooms), screens, widgets
└── app/                      # (legacy) pelanggan Kotlin/TDLib — dikekalkan untuk rujukan
```

**Prinsip ketat yang dipatuhi 100% kod:** (1) tiada TODO/stub/fungsi kosong — semua ciri berfungsi; (2) Clean Architecture / MVVM — controller→service→repository (backend), presentation→domain→data (mobile); (3) setiap fungsi mempunyai try-catch, validasi input dan log ralat berstruktur.

## Stack Teknologi

| Lapisan | Teknologi |
|---------|-----------|
| Mobile | Flutter 3.35, Dart 3.9, BLoC 8.x, `socket_io_client`, `image_picker`, `path_provider` |
| Backend | Node.js ≥18, Express 4, Socket.io 4.7, Sequelize 6, Joi, multer, bcryptjs, jsonwebtoken |
| Database | PostgreSQL (skema dalam `backend/database/schema.sql`, sync automatik `npm run db:sync`) |
| CI | GitHub Actions → `flutter build apk --release` → artifact `yugram-apk` |

## Cara Jalankan

### 1. Backend

```bash
cd backend
npm install
cp .env.example .env            # kemudian sunting DATABASE_URL + JWT_SECRET
npm run db:sync                 # sediakan jadual (alter:true untuk dev)
npm start                       # pelayan di http://localhost:4000
```

Health check: `curl http://localhost:4000/health` → `{"status":"ok",...}`

### 2. Mobile

```bash
cd mobile
flutter pub get
flutter run                     # titik API lalai: http://10.0.2.2:4000 (emulator Android)
```

Untuk binaan release, lihat workflow `.github/workflows/flutter-build.yml` — setiap push ke `main` menghasilkan APK release sebagai artifact **yugram-apk** pada tab Actions.

## Ujian Automatik (bukti tiada bug)

Tiga suite ujian end-to-end atas PostgreSQL + Socket.io **sebenar** (bukan mock):

```bash
cd backend
npm run syntax-check     # node --check semua fail JS
npm run uji-semua        # UJIAN INDUK: 17 seksyen, 71 semakan
npm run smoke-test       # regresi FASA 1: 20 semakan
npm run smoke-test:fasa23  # regresi FASA 2+3: 44 semakan
```

| Suite | Seksyen | Semakan | Meliputi |
|-------|---------|---------|----------|
| `uji-semua.js` | 17 | 71 | kesihatan, auth+keselamatan, validasi, profil, direct idempoten, kumpulan penuh, ticks, typing, reaksi, reply + rentas bilik ditolak, media (termasuk 413 oversize & mime ditolak), edit, padam, forward/carian/silent/pagination, keselamatan socket |
| `smoke-test.js` | 6 | 20 | FASA 1 penuh |
| `smoke-test-fasa23.js` | 13 | 44 | FASA 2+3 penuh |

Mobile: `flutter analyze` = **0 isu** (ralat/amaran/info bersih) dan `flutter test` = **5/5 PASS**. **Jumlah: 140 semakan hijau, 0 gagal.**

### Kontrak event Socket.io

| Arah | Event | Fungsi |
|------|-------|--------|
| Klien→Pelayan | `join_room`, `send_message`, `message_read`, `typing_status`, `add_reaction` | operasi sembang |
| Pelayan→Klien | `connection_ready`, `room_joined`, `new_message`, `message_ack`, `read_receipt`, `typing_status`, `reaction_updated`, `message_edited`, `message_deleted`, `room_updated`, `room_deleted`, `error` | realtime + kesilapan berstruktur |

### REST API utama

```
POST   /api/auth/register | /api/auth/login        # JWT
GET    /api/auth/me                                 # identiti semasa
PATCH  /api/users/me                                # profil (displayName, bio)
GET    /api/users/search?q=                         # carian pengguna
POST   /api/rooms/direct                            # 1-ke-1 (idempoten)
POST   /api/rooms/group                             # cipta kumpulan
GET    /api/rooms                                   # senarai + unreadCount
GET    /api/rooms/:roomId/messages?limit=&before=&q=# sejarah/carian/pagination
GET    /api/rooms/:roomId/media                     # galeri media bilik
POST   /api/rooms/:roomId/members                   # tambah ahli
PATCH  /api/rooms/:roomId/members/:userId           # role (admin/member)
DELETE /api/rooms/:roomId/members/:userId           # buang ahli
POST   /api/rooms/:roomId/leave                     # keluar kumpulan
PATCH  /api/rooms/:roomId                           # rename
DELETE /api/rooms/:roomId                           # padam (pencipta)
PATCH  /api/messages/:messageId                     # edit (pemilik)
DELETE /api/messages/:messageId                     # padam (pemilik/admin)
POST   /api/media                                   # muat naik multipart (≤10 MB)
```

## Lesen

Projek ini untuk tujuan pendidikan. Telegram dan logo Telegram ialah cap dagangan Telegram FZ-LLC; projek ini tidak berkaitan dengan atau disokong oleh Telegram.
