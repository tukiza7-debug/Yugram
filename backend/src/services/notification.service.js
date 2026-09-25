'use strict';

/**
 * services/notification.service.js
 * Enjin push notification SEDAR-SENYAP (silent-aware).
 *
 * Fitur 64 - Silent Messages:
 * Jika mesej mempunyai isSilent = true, envelope push yang dijana
 * TIDAK mengandungi bunyi (sound=null, priority=normal,
 * interruption-level=passive) supaya peranti penerima tidak berbunyi.
 *
 * Transport lalai: LogTransport (payload berstruktur dilog).
 * Jika PUSH_ENDPOINT ditetapkan, HttpTransport akan POST payload
 * yang sama ke endpoint relay FCM/APNs.
 */

const config = require('../config/env');
const logger = require('../utils/logger');
const roomRepository = require('../repositories/room.repository');

const log = logger.child('Notification');
const PUSH_HTTP_TIMEOUT_MS = 5000;
const NOTIFICATION_BODY_MAX_LENGTH = 140;

class LogTransport {
  /**
   * @param {object} envelope
   * @returns {Promise<{delivered: boolean, transport: string}>}
   */
  async send(envelope) {
    log.info('PUSH (log-transport)', {
      isSilent: envelope.meta.isSilent,
      title: envelope.notification ? envelope.notification.title : null,
      sound: envelope.android ? envelope.android.notification.sound : null,
      recipients: envelope.tokens.length,
    });
    return { delivered: true, transport: 'log' };
  }
}

class HttpTransport {
  /**
   * @param {string} endpoint
   */
  constructor(endpoint) {
    this.endpoint = endpoint;
  }

  /**
   * @param {object} envelope
   * @returns {Promise<{delivered: boolean, transport: string, status?: number}>}
   */
  async send(envelope) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), PUSH_HTTP_TIMEOUT_MS);
    try {
      const response = await fetch(this.endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(envelope),
        signal: controller.signal,
      });
      if (!response.ok) {
        log.error('PUSH endpoint menolak payload', { status: response.status });
        return { delivered: false, transport: 'http', status: response.status };
      }
      return { delivered: true, transport: 'http', status: response.status };
    } finally {
      clearTimeout(timeout);
    }
  }
}

function createTransport() {
  if (config.pushEndpoint) {
    return new HttpTransport(config.pushEndpoint);
  }
  return new LogTransport();
}

class NotificationService {
  constructor() {
    this.transport = createTransport();
  }

  /**
   * Membina envelope push serasi FCM v1 / APNs.
   * isSilent=true => tiada bunyi, keutamaan normal, interruption passive.
   * @param {{message: object, sender: object}} input
   * @returns {object}
   */
  buildPushEnvelope({ message, sender }) {
    const isSilent = Boolean(message.isSilent);
    const truncatedBody =
      message.text.length > NOTIFICATION_BODY_MAX_LENGTH
        ? `${message.text.slice(0, NOTIFICATION_BODY_MAX_LENGTH - 1)}…`
        : message.text;

    return {
      tokens: [],
      notification: {
        title: sender ? sender.displayName : 'Mesej baharu',
        body: truncatedBody,
      },
      data: {
        roomId: message.roomId,
        messageId: message.id,
        senderId: message.senderId,
        isSilent: String(isSilent),
      },
      android: {
        priority: isSilent ? 'normal' : 'high',
        notification: {
          channel_id: isSilent ? 'messages_silent' : 'messages_sound',
          sound: isSilent ? null : 'default',
        },
      },
      apns: {
        payload: {
          aps: {
            alert: {
              title: sender ? sender.displayName : 'Mesej baharu',
              body: truncatedBody,
            },
            sound: isSilent ? null : 'default',
            'interruption-level': isSilent ? 'passive' : 'time-sensitive',
          },
        },
      },
      meta: { isSilent, sentAt: new Date().toISOString() },
    };
  }

  /**
   * Menghantar push kepada semua ahli bilik (kecuali penghantar).
   * TIDAK pernah membuang exception ke lapisan socket - kegagalan dilog.
   * @param {{message: object, excludeUserId: string}} input
   * @returns {Promise<{delivered: boolean, transport: string, recipients: number}>}
   */
  async notifyRoomMembers({ message, excludeUserId }) {
    try {
      const members = await roomRepository.getRoomMembers(message.roomId);
      const recipientIds = members
        .filter((member) => member.userId !== excludeUserId)
        .map((member) => member.userId);

      if (recipientIds.length === 0) {
        return { delivered: false, transport: 'none', recipients: 0 };
      }

      const envelope = this.buildPushEnvelope({ message, sender: message.sender });
      envelope.tokens = recipientIds;

      const result = await this.transport.send(envelope);
      log.info('Push notification diproses', {
        messageId: message.id,
        isSilent: message.isSilent,
        recipients: recipientIds.length,
        delivered: result.delivered,
        transport: result.transport,
      });
      return { ...result, recipients: recipientIds.length };
    } catch (err) {
      log.error('Gagal menghantar push notification', {
        messageId: message.id,
        message: err.message,
      });
      return { delivered: false, transport: 'error', recipients: 0 };
    }
  }
}

module.exports = new NotificationService();
