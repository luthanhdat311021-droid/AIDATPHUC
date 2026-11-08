// Biometric confirmation for spending StudyCoin. The phone holds a private key in Android Keystore that only
// signs after the user passes the system prompt; no face or fingerprint data ever reaches this server.
import { createPublicKey, randomBytes, randomUUID, verify } from 'crypto';
import { HttpError } from './billingService.js';
import { checked, rewardsDb } from './rewardsService.js';

const CHALLENGE_TTL_MS = 2 * 60_000;
// A stolen session must not be able to swap in its own key and spend straight away
const KEY_REPLACEMENT_DELAY_MS = 24 * 3600_000;

export function parsePublicKey(base64) {
  try {
    const key = createPublicKey({ key: Buffer.from(String(base64), 'base64'), format: 'der', type: 'spki' });
    if (key.asymmetricKeyType === 'ec' && key.asymmetricKeyDetails?.namedCurve === 'prime256v1') return key;
  } catch { /* reported below */ }
  throw new HttpError(400, 'Khóa xác thực của thiết bị không hợp lệ.', 'BIOMETRIC_KEY_INVALID');
}

export const challengeMessage = c =>
  ['studymind-redeem-v1', c.user_id, c.id, c.nonce, c.catalog_id, c.catalog_version, c.idempotency_key].join('|');

export function signatureMatches(publicKeyBase64, message, signatureBase64) {
  try {
    return verify('sha256', Buffer.from(message), parsePublicKey(publicKeyBase64), Buffer.from(String(signatureBase64), 'base64'));
  } catch {
    return false;
  }
}

export const biometricService = {
  async enroll(uid, publicKey) {
    parsePublicKey(publicKey);
    const db = rewardsDb();
    const existing = checked(await db.from('studymind_biometric_keys').select('public_key,active_from').eq('user_id', uid).maybeSingle());
    if (existing?.public_key === publicKey) return this.status(existing);
    const row = {
      user_id: uid,
      public_key: publicKey,
      active_from: new Date(Date.now() + (existing ? KEY_REPLACEMENT_DELAY_MS : 0)).toISOString()
    };
    checked(await db.from('studymind_biometric_keys').upsert(row));
    return this.status(row);
  },

  status: row => ({ active: Date.parse(row.active_from) <= Date.now(), activeFrom: row.active_from }),

  async challenge(uid, { catalogId, version, key }) {
    const db = rewardsDb();
    const enrolled = checked(await db.from('studymind_biometric_keys').select('active_from').eq('user_id', uid).maybeSingle());
    if (!enrolled) throw new HttpError(403, 'Thiết bị chưa đăng ký xác thực sinh trắc.', 'BIOMETRIC_NOT_ENROLLED');
    if (!this.status(enrolled).active) throw new HttpError(403, 'Thiết bị mới đang trong 24 giờ chờ kích hoạt để bảo vệ tài khoản.', 'BIOMETRIC_KEY_PENDING');
    const row = {
      id: randomUUID(),
      user_id: uid,
      nonce: randomBytes(32).toString('base64url'),
      catalog_id: catalogId,
      catalog_version: version,
      idempotency_key: key,
      expires_at: new Date(Date.now() + CHALLENGE_TTL_MS).toISOString()
    };
    checked(await db.from('studymind_biometric_challenges').insert(row));
    return { challengeId: row.id, message: challengeMessage(row) };
  },

  /** Throws unless the signature covers exactly this redemption. A challenge works once. */
  async assertRedemption(uid, body) {
    const reject = () => { throw new HttpError(403, 'Xác thực sinh trắc không hợp lệ hoặc đã hết hạn. Hãy thử lại.', 'BIOMETRIC_REQUIRED'); };
    const { challengeId, signature } = body.biometric || {};
    if (!challengeId || !signature) reject();
    const db = rewardsDb();
    const c = checked(await db.from('studymind_biometric_challenges').select('*').eq('id', challengeId).eq('user_id', uid).maybeSingle());
    if (!c || c.used_at || Date.parse(c.expires_at) <= Date.now()) reject();
    if (c.catalog_id !== body.catalogId || Number(c.catalog_version) !== body.version || c.idempotency_key !== body.key) reject();
    const enrolled = checked(await db.from('studymind_biometric_keys').select('public_key,active_from').eq('user_id', uid).maybeSingle());
    if (!enrolled || !this.status(enrolled).active || !signatureMatches(enrolled.public_key, challengeMessage(c), signature)) reject();
    const used = checked(await db.from('studymind_biometric_challenges').update({ used_at: new Date().toISOString() })
      .eq('id', c.id).is('used_at', null).select('id'));
    if (!used.length) reject();
  }
};
