'use strict';

/**
 * scripts/uji-semua.js
 * ================================================================
 * UJIAN INDUK END-TO-END YUGRAM (semua fasa, 17 seksyen).
 * Matlamat: bukti "tiada langsung bug" dengan liputan penuh:
 *
 *   1.  Kesihatan sistem (/health + DB)
 *   2.  Auth: daftar + login + /auth/me
 *   3.  Auth: keselamatan (kata laluan salah, pendua, JWT palsu, 401)
 *   4.  Validasi input Joi (daftar/PATCH/limit tak sah)
 *   5.  Profil: PATCH /users/me + carian pengguna
 *   6.  Bilik direct idempoten + senarai bilik
 *   7.  Kumpulan: cipta + ahli + role pencipta
 *   8.  Kumpulan: add / promote / demote / remove + kebenaran
 *   9.  Kumpulan: rename broadcast + peraturan padam
 *   10. Mesej: ticks tunggal/berganda + sejarah REST
 *   11. Typing realtime + reaksi (tambah/ganti/toggle off)
 *   12. Reply: replyToMessageId + rentas bilik DITOLAK
 *   13. Media: upload multipart + statik + bermedia + senarai
 *       + oversize 413 + mime tak dibenarkan 400
 *   14. Edit mesej: isEdited/editedAt + broadcast + 403
 *   15. Padam mesej: sendiri + broadcast + hilang dari sejarah + 403
 *   16. Forward + carian mesej + silent message + pagination
 *   17. Keselamatan socket: bukan ahli join/send DITOLAK (event error)
 *
 * Jalankan: node scripts/uji-semua.js   (pelayan di :4000)
 * Exit code: 0 = semua PASS, 1 = ada FAIL/crash.
 */

const { io } = require('socket.io-client');
const fs = require('fs');
const path = require('path');

const API_URL = process.env.API_URL || 'http://localhost:4000';
const TEST_TIMEOUT_MS = 8000;

let passedCount = 0;
let failedCount = 0;
const failedLabels = [];

function assert(condition, label) {
  if (condition) {
    passedCount += 1;
    console.log(`  PASS  ${label}`);
  } else {
    failedCount += 1;
    failedLabels.push(label);
    console.error(`  FAIL  ${label}`);
  }
}

function section(title) {
  console.log(`\n== ${title} ==`);
}

async function api(path, options = {}) {
  const { method = 'GET', token, body, raw = false } = options;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), TEST_TIMEOUT_MS);
  try {
    const response = await fetch(`${API_URL}${path}`, {
      method,
      headers: {
        ...(raw ? {} : { 'Content-Type': 'application/json' }),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body instanceof FormData ? body : body ? JSON.stringify(body) : undefined,
      signal: controller.signal,
    });
    if (raw) {
      return { status: response.status, ok: response.ok, body: response };
    }
    const data = await response.json().catch(() => null);
    return { status: response.status, ok: response.ok, data };
  } finally {
    clearTimeout(timeout);
  }
}

function waitFor(socket, event, predicate = () => true, timeoutMs = TEST_TIMEOUT_MS) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      socket.off(event, listener);
      reject(new Error(`Timeout menunggu event "${event}" (${socket.userIdLabel})`));
    }, timeoutMs);

    const listener = (payload) => {
      try {
        if (predicate(payload)) {
          clearTimeout(timer);
          socket.off(event, listener);
          resolve(payload);
        }
      } catch (err) {
        clearTimeout(timer);
        socket.off(event, listener);
        reject(err);
      }
    };
    socket.on(event, listener);
  });
}

function connectSocket(token, label) {
  const socket = io(API_URL, {
    auth: { token },
    transports: ['websocket'],
    reconnection: false,
  });
  socket.userIdLabel = label;
  socket.on('connect_error', (err) => console.error(`  [connect-error:${label}]`, err.message));
  return socket;
}

/** PNG 1x1 sah (base64 canonical) untuk ujian muat naik. */
function createTestPng() {
  const base64 =
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';
  return Buffer.from(base64, 'base64');
}

/** Fail kosong/buta bersaiz tertentu (bytes) untuk ujian had saiz. */
function createTestBlob(sizeBytes, mime) {
  return new Blob([Buffer.alloc(sizeBytes, 7)], { type: mime });
}

(async () => {
  const stamp = Date.now();

  // Rujukan lintas-seksyen (dihulurkan di peringkat atas supaya semua
  // seksyen boleh mengakses mesej/bilik yang sama tanpa ReferenceError).
  let firstMessage = null;

  // ==================================================================
  section('1. Kesihatan sistem');
  {
    const health = await api('/health');
    assert(health.ok && health.data && health.data.status === 'ok', '/health => 200 {status:"ok"}');
    const dbRoute = await api('/api/laluan-tiada', { token: null });
    assert(dbRoute.status === 404, 'Laluan tidak diketahui => 404 berstruktur');
  }

  // ==================================================================
  section('2. Auth: daftar + login + /auth/me');
  const alice = (await api('/api/auth/register', {
    method: 'POST',
    body: { username: `ua_alice_${stamp}`, displayName: 'Alice Uji', password: 'KataLaluan123' },
  })).data;
  assert(Boolean(alice.token && alice.user && alice.user.id), 'Daftar Alice => token JWT + user');
  const bob = (await api('/api/auth/register', {
    method: 'POST',
    body: { username: `ua_bob_${stamp}`, displayName: 'Bob Uji', password: 'KataLaluan123' },
  })).data;
  const carol = (await api('/api/auth/register', {
    method: 'POST',
    body: { username: `ua_carol_${stamp}`, displayName: 'Carol Uji', password: 'KataLaluan123' },
  })).data;
  const dave = (await api('/api/auth/register', {
    method: 'POST',
    body: { username: `ua_dave_${stamp}`, displayName: 'Dave Uji', password: 'KataLaluan123' },
  })).data;
  assert(Boolean(bob.token && carol.token && dave.token), 'Daftar Bob + Carol + Dave');

  {
    const login = await api('/api/auth/login', {
      method: 'POST',
      body: { username: `ua_alice_${stamp}`, password: 'KataLaluan123' },
    });
    assert(login.ok && Boolean(login.data.token), 'Login kata laluan betul => 200 + token');
    const me = await api('/api/auth/me', { token: alice.token });
    assert(me.ok && me.data.user.username === `ua_alice_${stamp}`, 'GET /auth/me => identiti betul');
  }

  // ==================================================================
  section('3. Auth: keselamatan');
  {
    const wrongPw = await api('/api/auth/login', {
      method: 'POST',
      body: { username: `ua_alice_${stamp}`, password: 'SalahBetul123' },
    });
    assert(wrongPw.status === 401, 'Login kata laluan salah => 401');
    const dupe = await api('/api/auth/register', {
      method: 'POST',
      body: { username: `ua_alice_${stamp}`, displayName: 'X', password: 'KataLaluan123' },
    });
    assert(dupe.status === 409, 'Daftar username pendua => 409');
    const badJwt = await api('/api/auth/me', { token: 'abc.def.ghi' });
    assert(badJwt.status === 401, 'JWT palsu => 401');
    const noAuth = await api('/api/rooms');
    assert(noAuth.status === 401, 'REST tanpa token => 401');
  }

  // ==================================================================
  section('4. Validasi input Joi');
  {
    const noPw = await api('/api/auth/register', {
      method: 'POST',
      body: { username: `ua_bad_${stamp}`, displayName: 'X' },
    });
    assert(noPw.status === 400, 'Daftar tanpa kata laluan => 400 VALIDATION_ERROR');
    const badRoom = await api('/api/rooms/00000000-0000-4000-8000-000000000000/messages', {
      token: alice.token,
    });
    assert(badRoom.status === 404 || badRoom.status === 403, 'Bilik UUID rawak => 404/403 (tiada crash 500)');
    const badPatch = await api('/api/users/me', {
      method: 'PATCH',
      token: alice.token,
      body: { bio: 'x'.repeat(500) },
    });
    assert(badPatch.status === 400, 'PATCH bio melebihi had => 400');
  }

  // ==================================================================
  section('5. Profil: PATCH /users/me + carian pengguna');
  {
    const patched = await api('/api/users/me', {
      method: 'PATCH',
      token: alice.token,
      body: { displayName: 'Alice Uji Edit', bio: 'Bio rasmi Alice' },
    });
    assert(patched.ok && patched.data.user.displayName === 'Alice Uji Edit', 'PATCH /users/me => displayName dikemas kini');
    assert(patched.data.user.bio === 'Bio rasmi Alice', 'PATCH /users/me => bio disimpan');
    const search = await api(`/api/users/search?q=ua_bob_${stamp}`, { token: alice.token });
    assert(
      search.ok && search.data.users.length === 1 && search.data.users[0].username === `ua_bob_${stamp}`,
      'Carian pengguna menjumpai Bob sahaja (bukan diri sendiri)'
    );
  }

  // ==================================================================
  section('6. Bilik direct: idempoten + senarai bilik');
  {
    const r1 = await api('/api/rooms/direct', {
      method: 'POST',
      token: alice.token,
      body: { peerUsername: `ua_bob_${stamp}` },
    });
    const r2 = await api('/api/rooms/direct', {
      method: 'POST',
      token: bob.token,
      body: { peerUsername: `ua_alice_${stamp}` },
    });
    assert(r1.ok && r2.ok && r1.data.room.id === r2.data.room.id, 'Direct room idempoten (sama id dari dua hala)');
    const rooms = await api('/api/rooms', { token: alice.token });
    const direct = rooms.data.rooms.find((r) => r.id === r1.data.room.id);
    assert(Boolean(direct), 'GET /rooms menyenaraikan bilik direct Alice-Bob');
  }

  // ==================================================================
  section('7. Kumpulan: cipta + ahli + role pencipta');
  const group = await api('/api/rooms/group', {
    method: 'POST',
    token: alice.token,
    body: {
      name: 'Ujian Induk Yugram',
      memberUsernames: [`ua_bob_${stamp}`, `ua_carol_${stamp}`],
    },
  });
  const groupId = group.data ? group.data.room.id : null;
  assert(group.ok && Boolean(groupId), 'POST /rooms/group => kumpulan dicipta');
  assert(group.data.members.length === 3, 'Kumpulan mempunyai 3 ahli awal');
  {
    const creator = group.data.members.find((m) => m.user.id === alice.user.id);
    assert(creator && creator.role === 'admin', 'Pencipta kumpulan berrole admin');
  }

  // Socket sambungan untuk ujian realtime
  const socketA = connectSocket(alice.token, 'alice');
  const socketB = connectSocket(bob.token, 'bob');
  const socketC = connectSocket(carol.token, 'carol');
  await waitFor(socketA, 'connection_ready');
  await waitFor(socketB, 'connection_ready');
  await waitFor(socketC, 'connection_ready');
  const joinP = [waitFor(socketA, 'room_joined'), waitFor(socketB, 'room_joined'), waitFor(socketC, 'room_joined')];
  socketA.emit('join_room', { roomId: groupId });
  socketB.emit('join_room', { roomId: groupId });
  socketC.emit('join_room', { roomId: groupId });
  await Promise.all(joinP);
  assert(true, 'Alice + Bob + Carol menyertai bilik kumpulan (socket)');

  // ==================================================================
  section('8. Kumpulan: add / promote / demote / remove + kebenaran');
  {
    const added = await api(`/api/rooms/${groupId}/members`, {
      method: 'POST',
      token: bob.token, // ahli biasa boleh tambah (kelakuan Telegram)
      body: { username: `ua_dave_${stamp}` },
    });
    assert(added.ok && added.data.members.length === 4, 'Ahli biasa (Bob) menambah Dave');

    const daveRemove = await api(`/api/rooms/${groupId}/members/${bob.user.id}`, {
      method: 'DELETE',
      token: dave.token,
    });
    assert(daveRemove.status === 403, 'Bukan admin gagal membuang ahli (403)');

    const promoted = await api(`/api/rooms/${groupId}/members/${bob.user.id}`, {
      method: 'PATCH',
      token: alice.token,
      body: { role: 'admin' },
    });
    assert(promoted.ok, 'Pencipta melantik Bob menjadi admin');

    const demoted = await api(`/api/rooms/${groupId}/members/${bob.user.id}`, {
      method: 'PATCH',
      token: alice.token,
      body: { role: 'member' },
    });
    assert(demoted.ok, 'Pencipta menurunkan Bob kepada ahli biasa');

    await api(`/api/rooms/${groupId}/members/${bob.user.id}`, {
      method: 'PATCH',
      token: alice.token,
      body: { role: 'admin' },
    });

    const removed = await api(`/api/rooms/${groupId}/members/${dave.user.id}`, {
      method: 'DELETE',
      token: bob.token,
    });
    assert(removed.ok && removed.data.members.length === 3, 'Admin (Bob) membuang Dave');
  }

  // ==================================================================
  section('9. Kumpulan: rename broadcast + peraturan padam');
  {
    const renameUpdatePromise = waitFor(socketB, 'room_updated');
    const renamed = await api(`/api/rooms/${groupId}`, {
      method: 'PATCH',
      token: bob.token,
      body: { name: 'Ujian Induk Yugram v2' },
    });
    assert(renamed.ok && renamed.data.room.name === 'Ujian Induk Yugram v2', 'Admin menamakan semula kumpulan');
    const renameUpdate = await renameUpdatePromise;
    assert(renameUpdate.room.name === 'Ujian Induk Yugram v2', 'room_updated disiar kepada ahli');

    const group2 = await api('/api/rooms/group', {
      method: 'POST',
      token: alice.token,
      body: { name: 'Sementara', memberUsernames: [`ua_bob_${stamp}`] },
    });
    const group2Id = group2.data.room.id;
    const delByBob = await api(`/api/rooms/${group2Id}`, { method: 'DELETE', token: bob.token });
    assert(delByBob.status === 403, 'Bukan pencipta gagal padam kumpulan (403)');
    const delByAlice = await api(`/api/rooms/${group2Id}`, { method: 'DELETE', token: alice.token });
    assert(delByAlice.ok && delByAlice.data.deleted === true, 'Pencipta memadam kumpulan');

    const group3 = await api('/api/rooms/group', {
      method: 'POST',
      token: alice.token,
      body: { name: 'Ujian Leave', memberUsernames: [`ua_bob_${stamp}`] },
    });
    const blockedLeave = await api(`/api/rooms/${group3.data.room.id}/leave`, {
      method: 'POST',
      token: alice.token,
    });
    assert(blockedLeave.status === 400, 'Satu-satunya admin disekat dari keluar (400)');
    await api(`/api/rooms/${group3.data.room.id}`, { method: 'DELETE', token: alice.token });
  }

  // ==================================================================
  section('10. Mesej: ticks tunggal/berganda + sejarah REST');
  {
    const ackPromise = waitFor(socketA, 'message_ack', (p) => p.tempId === 'u-1');
    const incomingPromise = waitFor(socketB, 'new_message');
    socketA.emit('send_message', { roomId: groupId, text: 'Halo daripada Alice', tempId: 'u-1' });
    const ack = await ackPromise;
    assert(ack.status === 'sent' && ack.message.id, 'TICK TUNGGAL: message_ack (status=sent)');
    firstMessage = ack.message;
    const incoming = await incomingPromise;
    assert(incoming.message.text === 'Halo daripada Alice', 'new_message diterima Bob');

    const receiptPromise = waitFor(socketA, 'read_receipt', (p) => p.userId === bob.user.id);
    socketB.emit('message_read', { roomId: groupId, lastReadMessageId: firstMessage.id });
    const receipt = await receiptPromise;
    assert(receipt.messageIds.includes(firstMessage.id), 'TICK BERGANDA: read_receipt merangkumi mesej');

    const history = await api(`/api/rooms/${groupId}/messages?limit=50`, { token: carol.token });
    assert(
      history.ok && history.data.messages.some((m) => m.id === firstMessage.id),
      'Sejarah REST mengandungi mesej (Carol membaca)'
    );
  }

  // ==================================================================
  section('11. Typing realtime + reaksi (tambah/ganti/toggle)');
  {
    const typingStart = waitFor(socketB, 'typing_status', (p) => p.isTyping === true);
    socketA.emit('typing_status', { roomId: groupId, isTyping: true });
    const ts = await typingStart;
    assert(
      ts.isTyping === true && ts.userId === alice.user.id && typeof ts.displayName === 'string' && ts.displayName.length > 0,
      'typing_status disiar dengan identiti + nama penghantar'
    );

    const typingStop = waitFor(socketB, 'typing_status', (p) => p.isTyping === false);
    socketA.emit('typing_status', { roomId: groupId, isTyping: false });
    await typingStop;
    assert(true, 'typing_status berhenti disiar (isTyping=false)');

    const reactAdd = waitFor(socketB, 'reaction_updated');
    socketA.emit('add_reaction', { roomId: groupId, messageId: firstMessage.id, emoji: '👍' });
    const ru1 = await reactAdd;
    assert(
      Array.isArray(ru1.reactions) && ru1.reactions.some((r) => r.userId === alice.user.id && r.emoji === '👍'),
      'Reaksi 👍 ditambah + broadcast'
    );

    const reactSwap = waitFor(socketB, 'reaction_updated');
    socketA.emit('add_reaction', { roomId: groupId, messageId: firstMessage.id, emoji: '❤️' });
    const ru2 = await reactSwap;
    assert(
      ru2.reactions.some((r) => r.userId === alice.user.id && r.emoji === '❤️') &&
        !ru2.reactions.some((r) => r.userId === alice.user.id && r.emoji === '👍'),
      'Reaksi bertukar 👍 -> ❤️ (satu per pengguna)'
    );

    const reactOff = waitFor(socketB, 'reaction_updated');
    socketA.emit('add_reaction', { roomId: groupId, messageId: firstMessage.id, emoji: '❤️' });
    const ru3 = await reactOff;
    assert(
      !ru3.reactions.some((r) => r.userId === alice.user.id),
      'Reaksi ditoggle OFF (tiada reaksi Alice)'
    );
  }

  // ==================================================================
  section('12. Reply: replyToMessageId + rentas bilik DITOLAK');
  {
    const replyAckPromise = waitFor(socketA, 'message_ack', (p) => p.tempId === 'u-2');
    const replyIncomingPromise = waitFor(socketB, 'new_message');
    socketA.emit('send_message', {
      roomId: groupId,
      text: 'Balasan kepada mesej pertama',
      replyToMessageId: firstMessage.id,
      tempId: 'u-2',
    });
    const replyAck = await replyAckPromise;
    assert(
      replyAck.status === 'sent' && replyAck.message.replyToMessageId === firstMessage.id,
      'Mesej balasan menyimpan replyToMessageId'
    );
    const replyIncoming = await replyIncomingPromise;
    assert(
      replyIncoming.message.replyToMessageId === firstMessage.id,
      'new_message balasan diterima Bob dengan rujukan'
    );

    // Balas mesej dari bilik lain => ditolak dengan event error berstruktur
    const otherRoom = await api('/api/rooms/direct', {
      method: 'POST',
      token: alice.token,
      body: { peerUsername: `ua_carol_${stamp}` },
    });
    socketA.emit('join_room', { roomId: otherRoom.data.room.id });
    await waitFor(socketA, 'room_joined', (p) => p.roomId === otherRoom.data.room.id);
    const foreignAckPromise = waitFor(socketA, 'message_ack', (p) => p.tempId === 'u-2f');
    socketA.emit('send_message', { roomId: otherRoom.data.room.id, text: 'Mesej bilik lain', tempId: 'u-2f' });
    const foreignAck = await foreignAckPromise;
    const foreignMessage = foreignAck.message;
    assert(Boolean(foreignMessage), 'Alice menghantar mesej di direct Alice-Carol (bilik kedua)');

    const badReplyPromise = waitFor(socketA, 'error', (p) => p.event === 'send_message');
    socketA.emit('send_message', {
      roomId: groupId,
      text: 'Balasan silang bilik',
      replyToMessageId: foreignMessage.id,
      tempId: 'u-2x',
    });
    const badReply = await badReplyPromise;
    assert(
      badReply && badReply.code === 'VALIDATION_ERROR' && Boolean(badReply.message),
      'Reply rentas bilik DITOLAK dengan event error berstruktur'
    );
  }

  // ==================================================================
  section('13. Media: upload + statik + bermedia + senarai + had');
  {
    const png = createTestPng();
    const form = new FormData();
    form.append('file', new Blob([png], { type: 'image/png' }), 'ujian.png');
    form.append('name', 'Tangkapan Induk');
    const upload = await api('/api/media', { method: 'POST', token: alice.token, body: form, raw: true });
    const uploadBody = upload.ok ? await upload.body.json() : null;
    assert(
      upload.status === 201 && uploadBody && uploadBody.media.url.startsWith('/uploads/'),
      'POST /api/media (multipart PNG) => 201 + url /uploads/'
    );
    const mediaInfo = uploadBody.media;

    const mediaFile = await fetch(`${API_URL}${mediaInfo.url}`);
    assert(
      mediaFile.ok && mediaFile.headers.get('content-type').includes('image/png'),
      'Fail media dihidang statik dengan content-type betul'
    );

    const mediaAckPromise = waitFor(socketA, 'message_ack', (p) => p.tempId === 'u-3');
    const mediaIncomingPromise = waitFor(socketB, 'new_message');
    socketA.emit('send_message', { roomId: groupId, text: '', media: mediaInfo, tempId: 'u-3' });
    const mediaAck = await mediaAckPromise;
    assert(
      mediaAck.status === 'sent' && mediaAck.message.media && mediaAck.message.media.url === mediaInfo.url,
      'Mesej bermedia disimpan (media.url sepadan)'
    );
    await mediaIncomingPromise;

    const mediaList = await api(`/api/rooms/${groupId}/media`, { token: bob.token });
    assert(
      mediaList.ok && mediaList.data.media.some((m) => m.media.url === mediaInfo.url),
      'GET /rooms/:id/media menyenaraikan media kumpulan'
    );

    const badMime = new FormData();
    badMime.append('file', new Blob([Buffer.from('bukan fail sebenar')], { type: 'application/x-msdownload' }), 'virus.exe');
    const badMimeRes = await api('/api/media', { method: 'POST', token: alice.token, body: badMime, raw: true });
    assert(badMimeRes.status === 400, 'Mime type tidak dibenarkan (.exe) => 400');

    const oversize = new FormData();
    oversize.append('file', createTestBlob(11 * 1024 * 1024, 'image/png'), 'besar.png');
    const oversizeRes = await api('/api/media', { method: 'POST', token: alice.token, body: oversize, raw: true });
    assert(oversizeRes.status === 413, 'Fail > 10MB => 413 PAYLOAD_TOO_LARGE (bukan 500)');

    const noFile = await api('/api/media', { method: 'POST', token: alice.token, raw: true });
    assert(noFile.status === 400, 'Upload tanpa fail => 400');
  }

  // ==================================================================
  section('14. Edit mesej: isEdited/editedAt + broadcast + 403');
  {
    const editedPromise = waitFor(socketB, 'message_edited');
    const editResponse = await api(`/api/messages/${firstMessage.id}`, {
      method: 'PATCH',
      token: alice.token,
      body: { text: 'Halo daripada Alice (diedit)' },
    });
    assert(editResponse.ok && editResponse.data.message.isEdited === true, 'PATCH /messages/:id => isEdited=true');
    assert(Boolean(editResponse.data.message.editedAt), 'editedAt diisi backend');
    const editedEvent = await editedPromise;
    assert(editedEvent.message.text.includes('diedit'), 'message_edited disiar kepada bilik');

    const editOthers = await api(`/api/messages/${firstMessage.id}`, {
      method: 'PATCH',
      token: bob.token,
      body: { text: 'Bob cuba edit' },
    });
    assert(editOthers.status === 403, 'Edit mesej orang lain DITOLAK (403)');
  }

  // ==================================================================
  section('15. Padam mesej: sendiri + broadcast + hilang + 403');
  {
    const delAckPromise = waitFor(socketA, 'message_ack', (p) => p.tempId === 'u-4');
    socketA.emit('send_message', { roomId: groupId, text: 'Mesej untuk dipadam', tempId: 'u-4' });
    const delAck = await delAckPromise;
    const delMessage = delAck.message;

    const delByNonMember = await api(`/api/messages/${delMessage.id}`, {
      method: 'DELETE',
      token: dave.token, // Dave dibuang => bukan ahli
    });
    assert(delByNonMember.status === 403, 'Bukan ahli gagal memadam (403)');

    const deletedPromise = waitFor(socketB, 'message_deleted', (p) => p.messageId === delMessage.id);
    const delOwn = await api(`/api/messages/${delMessage.id}`, {
      method: 'DELETE',
      token: alice.token,
    });
    assert(delOwn.ok && delOwn.data.messageId === delMessage.id, 'Penghantar memadam mesej sendiri');
    const deletedEvent = await deletedPromise;
    assert(deletedEvent.roomId === groupId, 'message_deleted disiar kepada bilik');

    const afterDelete = await api(`/api/rooms/${groupId}/messages?limit=100`, { token: bob.token });
    assert(
      !afterDelete.data.messages.some((m) => m.id === delMessage.id),
      'Mesej dipadam tiada lagi dalam sejarah'
    );
  }

  // ==================================================================
  section('16. Forward + carian + silent + pagination');
  {
    const fwdAckPromise = waitFor(socketA, 'message_ack', (p) => p.tempId === 'u-5');
    const fwdIncomingPromise = waitFor(socketB, 'new_message');
    socketA.emit('send_message', {
      roomId: groupId,
      text: 'Berita penting untuk dikongsi',
      forwardedFromName: 'Carol Uji',
      tempId: 'u-5',
    });
    const fwdAck = await fwdAckPromise;
    assert(fwdAck.message.forwardedFromName === 'Carol Uji', 'forwardedFromName disimpan backend');
    const fwdIncoming = await fwdIncomingPromise;
    assert(fwdIncoming.message.forwardedFromName === 'Carol Uji', 'Mesej terusan diterima dengan label asal');

    const silentAckPromise = waitFor(socketA, 'message_ack', (p) => p.tempId === 'u-6');
    const silentIncomingPromise = waitFor(socketB, 'new_message');
    socketA.emit('send_message', { roomId: groupId, text: 'Mesej senyap', isSilent: true, tempId: 'u-6' });
    const silentAck = await silentAckPromise;
    assert(silentAck.message.isSilent === true, 'isSilent=true disimpan (tanpa bunyi push)');
    const silentIncoming = await silentIncomingPromise;
    assert(silentIncoming.message.isSilent === true, 'Penerima menerima isSilent=true');

    const searchMsg = await api(`/api/rooms/${groupId}/messages?q=diedit`, { token: bob.token });
    assert(
      searchMsg.ok && searchMsg.data.messages.length >= 1 && searchMsg.data.messages[0].text.includes('diedit'),
      'Carian mesej ?q= menjumpai mesej (tidak kes sensitif)'
    );

    const page1 = await api(`/api/rooms/${groupId}/messages?limit=1`, { token: bob.token });
    assert(page1.ok && page1.data.messages.length === 1, 'Pagination limit=1 => 1 mesej');
    const newest = page1.data.messages[0];
    assert(Boolean(page1.data.nextBefore), 'Pagination memulangkan nextBefore');
    const page2 = await api(`/api/rooms/${groupId}/messages?limit=1&before=${encodeURIComponent(page1.data.nextBefore)}`, {
      token: bob.token,
    });
    assert(
      page2.ok && page2.data.messages.length === 1 && page2.data.messages[0].id !== newest.id,
      'Halaman kedua (before=) memulangkan mesej lebih lama'
    );
  }

  // ==================================================================
  section('17. Keselamatan socket: bukan ahli DITOLAK');
  {
    const socketD = connectSocket(dave.token, 'dave');
    await waitFor(socketD, 'connection_ready');

    let joinRejected = false;
    try {
      socketD.emit('join_room', { roomId: groupId });
      await waitFor(socketD, 'room_joined', () => true, 2000);
    } catch (err) {
      joinRejected = true;
    }
    assert(joinRejected, 'Bukan ahli DITOLAK daripada join_room');

    // join bilik direct Bob-Carol juga ditolak untuk Dave
    const secretRoom = (await api('/api/rooms/direct', {
      method: 'POST',
      token: bob.token,
      body: { peerUsername: `ua_carol_${stamp}` },
    })).data;

    let sendRejected = false;
    try {
      socketD.emit('join_room', { roomId: secretRoom.room.id });
      await waitFor(socketD, 'room_joined', () => true, 1500);
      socketD.emit('send_message', { roomId: secretRoom.room.id, text: 'Suntikan', tempId: 'u-x1' });
      await waitFor(socketD, 'message_ack', () => true, 2000);
    } catch (err) {
      sendRejected = true;
    }
    assert(sendRejected, 'Bukan ahli tidak dapat join/hantar mesej (tiada ack)');

    const historyDenied = await api(`/api/rooms/${secretRoom.room.id}/messages`, { token: dave.token });
    assert(historyDenied.status === 403, 'Bukan ahli gagal baca sejarah REST (403)');

    socketD.disconnect();
  }

  socketA.disconnect();
  socketB.disconnect();
  socketC.disconnect();

  console.log('\n=========================================================');
  console.log(`  HASIL AKHIR UJIAN INDUK: ${passedCount} PASS, ${failedCount} FAIL`);
  console.log('=========================================================');
  if (failedLabels.length > 0) {
    console.error('\nSenarai FAIL:');
    for (const label of failedLabels) console.error(`  - ${label}`);
  }
  process.exit(failedCount === 0 ? 0 : 1);
})().catch((err) => {
  console.error('\nUjian induk CRASH:', err.message);
  console.error(err.stack);
  process.exit(1);
});
