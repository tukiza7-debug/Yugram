'use strict';

/**
 * scripts/smoke-test-fasa23.js
 * UJIAN END-TO-END SEBENAR untuk FASA 2 + FASA 3:
 *   FASA 2:
 *     1. Profil: PATCH /users/me (displayName, bio) + GET /auth/me
 *     2. Carian pengguna: GET /users/search?q=
 *     3. Kumpulan: POST /rooms/group (create + ahli), add/remove member,
 *        rename (broadcast room_updated), leave rules, delete rules
 *     4. Unread count dalam GET /rooms
 *     5. Media: POST /api/media (multipart) + mesej bermedia + senarai media
 *   FASA 3:
 *     6. Edit mesej (broadcast message_edited + isEdited/editedAt)
 *     7. Padam mesej (broadcast message_deleted + kebenaran)
 *     8. Teruskan mesej (forwardedFromName)
 *     9. Carian mesej dalam bilik (?q=)
 *
 * Jalankan: node scripts/smoke-test-fasa23.js  (pelayan di :4000)
 */

const { io } = require('socket.io-client');
const fs = require('fs');
const path = require('path');

const API_URL = process.env.API_URL || 'http://localhost:4000';
const TEST_TIMEOUT_MS = 8000;

let passedCount = 0;
let failedCount = 0;

function assert(condition, label) {
  if (condition) {
    passedCount += 1;
    console.log(`  PASS  ${label}`);
  } else {
    failedCount += 1;
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

/** Mencipta fail PNG 1x1 sebenar untuk ujian muat naik multipart. */
function createTestPng() {
  // PNG 1x1 piksel (base64 canonical)
  const base64 =
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';
  return Buffer.from(base64, 'base64');
}

(async () => {
  const stamp = Date.now();

  // ==================================================================
  section('1. FASA 2 - Profil: daftar + PATCH /users/me');
  const alice = (await api('/api/auth/register', {
    method: 'POST',
    body: { username: `fa_alice_${stamp}`, displayName: 'Alice F23', password: 'KataLaluan123' },
  })).data;
  const bob = (await api('/api/auth/register', {
    method: 'POST',
    body: { username: `fa_bob_${stamp}`, displayName: 'Bob F23', password: 'KataLaluan123' },
  })).data;
  const carol = (await api('/api/auth/register', {
    method: 'POST',
    body: { username: `fa_carol_${stamp}`, displayName: 'Carol F23', password: 'KataLaluan123' },
  })).data;
  assert(Boolean(alice.token && bob.token && carol.token), 'Tiga pengguna berdaftar');

  const patched = await api('/api/users/me', {
    method: 'PATCH',
    token: alice.token,
    body: { displayName: 'Alice F23 Edit', bio: 'Suka sembang senyap' },
  });
  assert(patched.ok && patched.data.user.displayName === 'Alice F23 Edit', 'PATCH /users/me mengemas kini displayName');
  assert(patched.data.user.bio === 'Suka sembang senyap', 'PATCH /users/me menyimpan bio');

  const me = await api('/api/auth/me', { token: alice.token });
  assert(me.data.user.bio === 'Suka sembang senyap', 'GET /auth/me memulangkan bio terkini');

  const dupe = await api('/api/users/me', {
    method: 'PATCH',
    token: bob.token,
    body: { username: `fa_alice_${stamp}` },
  });
  assert(dupe.status === 409, 'Username pendua DITOLAK (409 CONFLICT)');

  // ==================================================================
  section('2. FASA 2 - Carian pengguna');
  const search = await api(
    `/api/users/search?q=fa_bob_${stamp}`,
    { token: alice.token }
  );
  assert(
    search.ok && search.data.users.length === 1 && search.data.users[0].username === `fa_bob_${stamp}`,
    'GET /users/search menjumpai Bob (dikhususkan, bukan diri sendiri)'
  );

  // ==================================================================
  section('3. FASA 2 - Kumpulan: cipta + ahli');
  const group = await api('/api/rooms/group', {
    method: 'POST',
    token: alice.token,
    body: {
      name: 'Projek Yugram',
      memberUsernames: [`fa_bob_${stamp}`, `fa_carol_${stamp}`],
    },
  });
  const groupId = group.data ? group.data.room.id : null;
  assert(group.ok && Boolean(groupId), 'POST /rooms/group mencipta kumpulan');
  assert(group.data.members.length === 3, 'Kumpulan mempunyai 3 ahli');
  const aliceMember = group.data.members.find((m) => m.user.id === alice.user.id);
  assert(aliceMember && aliceMember.role === 'admin', 'Pencipta menjadi admin');

  // Socket sambungan untuk ujian broadcast
  const socketA = connectSocket(alice.token, 'alice');
  const socketB = connectSocket(bob.token, 'bob');
  await waitFor(socketA, 'connection_ready');
  await waitFor(socketB, 'connection_ready');
  const joinAPromise = waitFor(socketA, 'room_joined');
  const joinBPromise = waitFor(socketB, 'room_joined');
  socketA.emit('join_room', { roomId: groupId });
  socketB.emit('join_room', { roomId: groupId });
  await joinAPromise;
  await joinBPromise;
  assert(true, 'Alice + Bob menyertai bilik kumpulan (socket)');

  // ==================================================================
  section('4. FASA 3 - Mesej dalam kumpulan: hantar + tick');
  const ackPromise = waitFor(socketA, 'message_ack', (p) => p.tempId === 'g-1');
  const incomingPromise = waitFor(socketB, 'new_message');
  socketA.emit('send_message', { roomId: groupId, text: 'Halo kumpulan!', tempId: 'g-1' });
  const ack = await ackPromise;
  assert(ack.status === 'sent', 'TICK TUNGGAL dalam kumpulan: message_ack OK');
  const firstMessage = ack.message;
  const incoming = await incomingPromise;
  assert(incoming.message.text === 'Halo kumpulan!', 'new_message diterima Bob (kumpulan)');

  const receiptPromise = waitFor(socketA, 'read_receipt', (p) => p.userId === bob.user.id);
  socketB.emit('message_read', { roomId: groupId, lastReadMessageId: firstMessage.id });
  const receipt = await receiptPromise;
  assert(receipt.messageIds.includes(firstMessage.id), 'TICK BERGANDA dalam kumpulan OK');

  // ==================================================================
  section('5. FASA 2 - Unread count dalam GET /rooms');
  // Bob beli mesej tadi => carol belum baca; carol ada 1 unread dalam kumpulan
  const carolRooms = await api('/api/rooms', { token: carol.token });
  const carolGroup = carolRooms.data.rooms.find((r) => r.id === groupId);
  assert(Boolean(carolGroup) && carolGroup.unreadCount >= 1, 'unreadCount >= 1 untuk Carol (belum baca)');

  // ==================================================================
  section('6. FASA 2 - Media: muat naik + mesej bermedia');
  const png = createTestPng();
  const form = new FormData();
  form.append('file', new Blob([png], { type: 'image/png' }), 'ujian.png');
  form.append('name', 'Tangkapan Ujian');
  const upload = await api('/api/media', { method: 'POST', token: alice.token, body: form, raw: true });
  const uploadBody = upload.ok ? await upload.body.json() : null;
  assert(
    upload.status === 201 && uploadBody && uploadBody.media.url.startsWith('/uploads/') && uploadBody.media.mimeType === 'image/png',
    'POST /api/media (multipart PNG) => 201 + url /uploads/'
  );
  const mediaInfo = uploadBody.media;

  const mediaAckPromise = waitFor(socketA, 'message_ack', (p) => p.tempId === 'g-2');
  const mediaIncomingPromise = waitFor(socketB, 'new_message');
  socketA.emit('send_message', {
    roomId: groupId,
    text: '',
    media: mediaInfo,
    tempId: 'g-2',
  });
  const mediaAck = await mediaAckPromise;
  assert(
    mediaAck.status === 'sent' && mediaAck.message.media && mediaAck.message.media.url === mediaInfo.url,
    'Mesej bermedia disimpan (media.url sepadan)'
  );
  const mediaIncoming = await mediaIncomingPromise;
  assert(mediaIncoming.message.media && mediaIncoming.message.media.name === 'Tangkapan Ujian', 'Mesej bermedia diterima Bob');

  const mediaFile = await fetch(`${API_URL}${mediaInfo.url}`);
  assert(mediaFile.ok && mediaFile.headers.get('content-type').includes('image/png'), 'Fail media boleh diakses statik (/uploads/)');

  const mediaList = await api(`/api/rooms/${groupId}/media`, { token: bob.token });
  assert(
    mediaList.ok && mediaList.data.media.length === 1 && mediaList.data.media[0].media.url === mediaInfo.url,
    'GET /rooms/:id/media menyenaraikan media (muat turun pukal)'
  );

  // ==================================================================
  section('7. FASA 3 - Edit mesej (broadcast + isEdited)');
  const editedPromise = waitFor(socketB, 'message_edited');
  const editResponse = await api(`/api/messages/${firstMessage.id}`, {
    method: 'PATCH',
    token: alice.token,
    body: { text: 'Halo kumpulan! (diedit)' },
  });
  assert(editResponse.ok && editResponse.data.message.isEdited === true, 'PATCH /messages/:id => isEdited=true');
  assert(Boolean(editResponse.data.message.editedAt), 'editedAt diisi oleh backend');
  const editedEvent = await editedPromise;
  assert(editedEvent.message.text === 'Halo kumpulan! (diedit)', 'message_edited broadcast kepada bilik');

  const editOthers = await api(`/api/messages/${firstMessage.id}`, {
    method: 'PATCH',
    token: bob.token,
    body: { text: 'Bob cuba edit' },
  });
  assert(editOthers.status === 403, 'Edit mesej orang lain DITOLAK (403)');

  // ==================================================================
  section('8. FASA 3 - Teruskan mesej (forward)');
  const fwdAckPromise = waitFor(socketA, 'message_ack', (p) => p.tempId === 'g-3');
  const fwdIncomingPromise = waitFor(socketB, 'new_message');
  socketA.emit('send_message', {
    roomId: groupId,
    text: 'Berita penting untuk dikongsi',
    forwardedFromName: 'Carol F23',
    tempId: 'g-3',
  });
  const fwdAck = await fwdAckPromise;
  assert(fwdAck.message.forwardedFromName === 'Carol F23', 'forwardedFromName disimpan oleh backend');
  const fwdIncoming = await fwdIncomingPromise;
  assert(fwdIncoming.message.forwardedFromName === 'Carol F23', 'Mesej terusan diterima dengan label asal');

  // ==================================================================
  section('9. FASA 3 - Carian mesej dalam bilik');
  const searchMsg = await api(`/api/rooms/${groupId}/messages?q=diedit`, { token: bob.token });
  assert(
    searchMsg.ok && searchMsg.data.messages.length === 1 && searchMsg.data.messages[0].text.includes('diedit'),
    'GET /rooms/:id/messages?q= menjumpai mesej (tidak kes sensitif)'
  );

  // ==================================================================
  section('10. FASA 2 - Keahlian: add / remove / role / kebenaran');
  const dave = (await api('/api/auth/register', {
    method: 'POST',
    body: { username: `fa_dave_${stamp}`, displayName: 'Dave F23', password: 'KataLaluan123' },
  })).data;

  const added = await api(`/api/rooms/${groupId}/members`, {
    method: 'POST',
    token: bob.token, // ahli biasa boleh tambah (kelakuan Telegram)
    body: { username: `fa_dave_${stamp}` },
  });
  assert(added.ok && added.data.members.length === 4, 'Ahli biasa (Bob) berjaya menambah Dave');

  const daveRemove = await api(`/api/rooms/${groupId}/members/${bob.user.id}`, {
    method: 'DELETE',
    token: dave.token,
  });
  assert(daveRemove.status === 403, 'Bukan admin tidak boleh membuang ahli (403)');

  const promoted = await api(`/api/rooms/${groupId}/members/${bob.user.id}`, {
    method: 'PATCH',
    token: alice.token, // pencipta
    body: { role: 'admin' },
  });
  assert(promoted.ok, 'Pencipta melantik Bob menjadi admin');

  const removed = await api(`/api/rooms/${groupId}/members/${dave.user.id}`, {
    method: 'DELETE',
    token: bob.token, // kini admin
  });
  assert(removed.ok && removed.data.members.length === 3, 'Admin (Bob) berjaya membuang Dave');

  const daveJoin = connectSocket(dave.token, 'dave');
  await waitFor(daveJoin, 'connection_ready');
  let daveRejected = false;
  try {
    daveJoin.emit('join_room', { roomId: groupId });
    await waitFor(daveJoin, 'room_joined', () => true, 2500);
  } catch (err) {
    daveRejected = true;
  }
  assert(daveRejected, 'Dave (dah dibuang) DITOLAK daripada join_room');
  daveJoin.disconnect();

  const renameUpdatePromise = waitFor(socketB, 'room_updated');
  const renamed = await api(`/api/rooms/${groupId}`, {
    method: 'PATCH',
    token: bob.token,
    body: { name: 'Projek Yugram v2' },
  });
  assert(renamed.ok && renamed.data.room.name === 'Projek Yugram v2', 'Admin menamakan semula kumpulan');
  const renameUpdate = await renameUpdatePromise;
  assert(renameUpdate.room.name === 'Projek Yugram v2', 'room_updated broadcast selepas rename');

  // ==================================================================
  section('11. FASA 2 - Leave: peraturan pencipta + ahli biasa');
  // (a) Kumpulan di mana pencipta SATU-SATUNYA admin => keluar disekat (400).
  const group3 = await api('/api/rooms/group', {
    method: 'POST',
    token: alice.token,
    body: { name: 'Ujian Leave', memberUsernames: [`fa_bob_${stamp}`] },
  });
  const group3Id = group3.data.room.id;
  const blockedLeave = await api(`/api/rooms/${group3Id}/leave`, { method: 'POST', token: alice.token });
  assert(blockedLeave.status === 400, 'Pencipta (satu-satunya admin) TIDAK boleh keluar (400)');

  // (b) Lantik Bob admin => pencipta boleh keluar (200).
  await api(`/api/rooms/${group3Id}/members/${bob.user.id}`, {
    method: 'PATCH',
    token: alice.token,
    body: { role: 'admin' },
  });
  const allowedLeave = await api(`/api/rooms/${group3Id}/leave`, { method: 'POST', token: alice.token });
  assert(allowedLeave.ok && allowedLeave.data.left === true, 'Pencipta boleh keluar selepas wujud admin lain (200)');

  // (c) Dalam kumpulan utama, Bob kini admin => Alice boleh keluar + masuk semula.
  const creatorLeave = await api(`/api/rooms/${groupId}/leave`, { method: 'POST', token: alice.token });
  assert(creatorLeave.ok && creatorLeave.data.left === true, 'Alice (pencipta, ada admin lain) berjaya keluar');

  const reAdded = await api(`/api/rooms/${groupId}/members`, {
    method: 'POST',
    token: bob.token,
    body: { username: `fa_alice_${stamp}` },
  });
  assert(reAdded.ok, 'Alice ditambah semula ke kumpulan');

  const carolLeft = await api(`/api/rooms/${groupId}/leave`, { method: 'POST', token: carol.token });
  assert(carolLeft.ok && carolLeft.data.left === true, 'Ahli biasa (Carol) berjaya keluar dari kumpulan');

  // ==================================================================
  section('12. FASA 3 - Padam mesej: sendiri vs admin vs bukan berhak');
  const delAckPromise = waitFor(socketA, 'message_ack', (p) => p.tempId === 'g-4');
  socketA.emit('send_message', { roomId: groupId, text: 'Mesej untuk dipadam', tempId: 'g-4' });
  const delAck = await delAckPromise;
  const delMessage = delAck.message;

  const delByOthers = await api(`/api/messages/${delMessage.id}`, {
    method: 'DELETE',
    token: carol.token, // Carol dah keluar => bukan ahli => 403
  });
  assert(delByOthers.status === 403, 'Bukan ahli tidak boleh memadam mesej (403)');

  const deletedPromise = waitFor(socketB, 'message_deleted', (p) => p.messageId === delMessage.id);
  const delOwn = await api(`/api/messages/${delMessage.id}`, {
    method: 'DELETE',
    token: alice.token, // penghantar sendiri
  });
  assert(delOwn.ok && delOwn.data.messageId === delMessage.id, 'Penghantar memadam mesej sendiri');
  const deletedEvent = await deletedPromise;
  assert(deletedEvent.roomId === groupId, 'message_deleted broadcast kepada bilik');

  const afterDelete = await api(`/api/rooms/${groupId}/messages?limit=100`, { token: bob.token });
  assert(
    !afterDelete.data.messages.some((m) => m.id === delMessage.id),
    'Mesej yang dipadam tiada lagi dalam sejarah'
  );

  // ==================================================================
  section('13. FASA 2 - Padam kumpulan: bukan pencipta DITOLAK, pencipta OK');
  const group2 = await api('/api/rooms/group', {
    method: 'POST',
    token: alice.token,
    body: { name: 'Kumpulan Sementara', memberUsernames: [`fa_bob_${stamp}`] },
  });
  const group2Id = group2.data.room.id;
  const delByBob = await api(`/api/rooms/${group2Id}`, { method: 'DELETE', token: bob.token });
  assert(delByBob.status === 403, 'Bukan pencipta tidak boleh padam kumpulan (403)');
  const delByAlice = await api(`/api/rooms/${group2Id}`, { method: 'DELETE', token: alice.token });
  assert(delByAlice.ok && delByAlice.data.deleted === true, 'Pencipta memadam kumpulan');

  socketA.disconnect();
  socketB.disconnect();

  console.log(`\n===== HASIL FASA 2+3: ${passedCount} PASS, ${failedCount} FAIL =====`);
  process.exit(failedCount === 0 ? 0 : 1);
})().catch((err) => {
  console.error('\nUjian smoke FASA 2+3 CRASH:', err.message);
  process.exit(1);
});
