'use strict';

/**
 * scripts/smoke-test.js
 * UJIAN END-TO-END SEBENAR (dua klien socket) untuk FASA 1:
 *   1. Daftar dua pengguna (REST) + cipta bilik direct
 *   2. join_room kedua-dua klien
 *   3. typing_status "Typing..." diterima rakan
 *   4. send_message (isSilent=true) -> message_ack (tick tunggal)
 *   5. new_message diterima penerima dengan isSilent=true
 *   6. message_read -> read_receipt (tick berganda)
 *   7. add_reaction -> toggle ON/OFF + broadcast
 *   8. Sejarah REST mengandungi mesej tersimpan
 *
 * Jalankan: npm run smoke-test  (pelayan mesti berjalan di :4000)
 */

const { io } = require('socket.io-client');

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

/**
 * Panggilan REST ringkas dengan error handling penuh.
 * @param {string} path
 * @param {{method?: string, token?: string, body?: object}} [options]
 * @returns {Promise<any>}
 */
async function api(path, options = {}) {
  const { method = 'GET', token, body } = options;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), TEST_TIMEOUT_MS);
  try {
    const response = await fetch(`${API_URL}${path}`, {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
      signal: controller.signal,
    });
    const data = await response.json().catch(() => null);
    if (!response.ok) {
      throw new Error(`${method} ${path} -> ${response.status}: ${JSON.stringify(data)}`);
    }
    return data;
  } finally {
    clearTimeout(timeout);
  }
}

/**
 * Menunggu satu event socket yang memenuhi predicate.
 * @param {import('socket.io-client').Socket} socket
 * @param {string} event
 * @param {(payload: any) => boolean} [predicate]
 * @param {number} [timeoutMs]
 * @returns {Promise<any>}
 */
function waitFor(socket, event, predicate = () => true, timeoutMs = TEST_TIMEOUT_MS) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      socket.off(event, listener);
      reject(new Error(`Timeout menunggu event "${event}" (userId=${socket.userIdLabel})`));
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
  socket.on('error', (err) => console.error(`  [socket-error:${label}]`, JSON.stringify(err)));
  socket.on('connect_error', (err) => console.error(`  [connect-error:${label}]`, err.message));
  return socket;
}

(async () => {
  const stamp = Date.now();

  section('1. REST: daftar pengguna + cipta bilik direct');
  const alice = await api('/api/auth/register', {
    method: 'POST',
    body: { username: `alice_${stamp}`, displayName: 'Alice Ujian', password: 'KataLaluan123' },
  });
  const bob = await api('/api/auth/register', {
    method: 'POST',
    body: { username: `bob_${stamp}`, displayName: 'Bob Ujian', password: 'KataLaluan123' },
  });
  assert(Boolean(alice.token && alice.user.id), 'Alice berdaftar + token JWT diterima');
  assert(Boolean(bob.token && bob.user.id), 'Bob berdaftar + token JWT diterima');

  const direct = await api('/api/rooms/direct', {
    method: 'POST',
    token: alice.token,
    body: { peerUsername: bob.user.username },
  });
  const roomId = direct.room.id;
  assert(Boolean(roomId), 'Bilik direct dicipta (Room/Namespace)');

  const directAgain = await api('/api/rooms/direct', {
    method: 'POST',
    token: bob.token,
    body: { peerUsername: alice.user.username },
  });
  assert(directAgain.room.id === roomId, 'Bilik direct idempoten (directKey berfungsi)');

  section('2. Socket: sambungan + join_room');
  const socketA = connectSocket(alice.token, 'alice');
  const socketB = connectSocket(bob.token, 'bob');

  const readyA = await waitFor(socketA, 'connection_ready');
  const readyB = await waitFor(socketB, 'connection_ready');
  assert(readyA.userId === alice.user.id, 'connection_ready Alice (auth JWT socket OK)');
  assert(readyB.userId === bob.user.id, 'connection_ready Bob (auth JWT socket OK)');

  // Pra-daftar kedua-dua pendengar SEBELUM emit supaya event yang tiba
  // dalam bacaan TCP yang sama tidak terlepas (deterministik).
  const joinedAPromise = waitFor(socketA, 'room_joined');
  const joinedBPromise = waitFor(socketB, 'room_joined');
  socketA.emit('join_room', { roomId });
  socketB.emit('join_room', { roomId });
  const joinedA = await joinedAPromise;
  const joinedB = await joinedBPromise;
  assert(joinedA.roomId === roomId && Array.isArray(joinedA.messages), 'room_joined Alice + sejarah');
  assert(joinedB.roomId === roomId && Array.isArray(joinedB.messages), 'room_joined Bob + sejarah');

  section('3. Fitur 12: Typing "Typing..." masa nyata');
  const typingPromise = waitFor(socketA, 'typing_status', (payload) => payload.isTyping === true);
  const typingStopPromise = waitFor(socketA, 'typing_status', (payload) => payload.isTyping === false);
  socketB.emit('typing_status', { roomId, isTyping: true });
  const typing = await typingPromise;
  assert(typing.userId === bob.user.id && typing.displayName === 'Bob Ujian', 'typing_status disiar kepada rakan (dengan nama)');
  socketB.emit('typing_status', { roomId, isTyping: false });
  await typingStopPromise;
  assert(true, 'typing_status berhenti disiar (isTyping=false)');

  section('4. Fitur 10+11+64: hantar mesej SILENT + tick tunggal');
  const ackPromise = waitFor(socketA, 'message_ack', (payload) => payload.tempId === 'temp-1');
  const incomingPromise = waitFor(socketB, 'new_message');
  socketA.emit('send_message', {
    roomId,
    text: 'Halo Bob! Ini mesej senyap tanpa bunyi.',
    isSilent: true,
    tempId: 'temp-1',
  });
  const ack = await ackPromise;
  assert(ack.status === 'sent', 'TICK TUNGGAL: message_ack diterima penghantar (status=sent)');
  assert(ack.message.isSilent === true, 'isSilent=true disimpan oleh backend');

  const incoming = await incomingPromise;
  assert(incoming.message.text === 'Halo Bob! Ini mesej senyap tanpa bunyi.', 'new_message diterima penerima');
  assert(incoming.message.isSilent === true, 'FITUR 64: penerima dapat isSilent=true (tiada bunyi push)');

  section('5. Fitur 11: message_read -> TICK BERGANDA');
  const receiptPromise = waitFor(
    socketA,
    'read_receipt',
    (payload) => payload.userId === bob.user.id
  );
  socketB.emit('message_read', { roomId, lastReadMessageId: incoming.message.id });
  const receipt = await receiptPromise;
  assert(
    Array.isArray(receipt.messageIds) && receipt.messageIds.includes(incoming.message.id),
    'TICK BERGANDA: read_receipt mengandungi messageId (dibaca Bob)'
  );

  section('6. Fitur 67: Reaksi pantas (toggle ON/OFF)');
  const reactionOnPromise = waitFor(socketA, 'reaction_updated');
  socketB.emit('add_reaction', { messageId: incoming.message.id, emoji: '❤️' });
  const reactionOn = await reactionOnPromise;
  assert(
    reactionOn.reactions.some((r) => r.userId === bob.user.id && r.emoji === '❤️'),
    'Reaksi ❤️ ditambah + broadcast'
  );
  const reactionOffPromise = waitFor(socketA, 'reaction_updated', (payload) => payload.reactions.length === 0);
  socketB.emit('add_reaction', { messageId: incoming.message.id, emoji: '❤️' });
  const reactionOff = await reactionOffPromise;
  assert(reactionOff.reactions.length === 0, 'Reaksi ❤️ ditoggle OFF (satu reaksi per pengguna)');

  const reactionSwitchPromise = waitFor(socketA, 'reaction_updated');
  socketB.emit('add_reaction', { messageId: incoming.message.id, emoji: '🔥' });
  const reactionSwitch = await reactionSwitchPromise;
  assert(
    reactionSwitch.reactions.some((r) => r.emoji === '🔥'),
    'Reaksi bertukar emoji (ganti tanpa pendua)'
  );

  section('7. REST: sejarah + kes pengesahan');
  const history = await api(`/api/rooms/${roomId}/messages?limit=10`, { token: alice.token });
  assert(history.messages.length === 1, 'Sejarah REST mengandungi mesej tersimpan');

  const stranger = await api('/api/auth/register', {
    method: 'POST',
    body: { username: `x_${stamp}`, displayName: 'X', password: 'KataLaluan123' },
  });
  let socketStranger = null;
  let rejected = false;
  try {
    socketStranger = connectSocket(stranger.token, 'x');
    await waitFor(socketStranger, 'connection_ready');
    socketStranger.emit('join_room', { roomId });
    await waitFor(socketStranger, 'room_joined', () => true, 2500);
  } catch (err) {
    rejected = true;
  }
  assert(rejected, 'Keselamatan: bukan ahli DITOLAK daripada join_room');

  socketA.disconnect();
  socketB.disconnect();
  if (socketStranger) socketStranger.disconnect();

  console.log(`\n===== HASIL: ${passedCount} PASS, ${failedCount} FAIL =====`);
  process.exit(failedCount === 0 ? 0 : 1);
})().catch((err) => {
  console.error('\nUjian smoke CRASH:', err.message);
  process.exit(1);
});
