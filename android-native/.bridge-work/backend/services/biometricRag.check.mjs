// Run: node backend/services/biometricRag.check.mjs
import assert from 'node:assert/strict';
import { createSign, generateKeyPairSync } from 'node:crypto';
import { challengeMessage, parsePublicKey, signatureMatches } from './biometricService.js';
import { citedSources, splitForRetrieval } from './ragService.js';

// Same formats Android Keystore produces: SPKI public key, DER ECDSA signature
const { publicKey, privateKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const spki = publicKey.export({ format: 'der', type: 'spki' }).toString('base64');
const challenge = { id: 'c', user_id: 'u', nonce: 'n', catalog_id: 'pro', catalog_version: 1, idempotency_key: 'k' };
const message = challengeMessage(challenge);
const signature = createSign('sha256').update(message).sign(privateKey).toString('base64');
assert.equal(signatureMatches(spki, message, signature), true);
assert.equal(signatureMatches(spki, challengeMessage({ ...challenge, catalog_id: 'other' }), signature), false);
assert.equal(signatureMatches(spki, message, 'not-a-signature'), false);
assert.throws(() => parsePublicKey('garbage'));
const rsa = generateKeyPairSync('rsa', { modulusLength: 2048 }).publicKey.export({ format: 'der', type: 'spki' }).toString('base64');
assert.throws(() => parsePublicKey(rsa));

const text = Array.from({ length: 200 }, (_, i) => `Câu số ${i} nói về một ý trong bài học.`).join(' ');
const chunks = splitForRetrieval(text);
assert.ok(chunks.length > 5);
assert.ok(chunks.every(c => c.length <= 1000));
assert.ok(chunks[0].endsWith('.'));
assert.ok(chunks[1].includes(chunks[0].slice(-40)), 'neighbouring chunks overlap');
assert.ok(chunks.at(-1).endsWith('Câu số 199 nói về một ý trong bài học.'));
assert.deepEqual(splitForRetrieval('  '), []);

const passages = [{ n: 1, content: 'a' }, { n: 2, content: 'b'.repeat(400) }];
assert.deepEqual(citedSources('Không trích dẫn', passages), []);
const cited = citedSources('Ý này [2].', passages);
assert.equal(cited.length, 1);
assert.equal(cited[0].n, 2);
assert.ok(cited[0].excerpt.length <= 281);
console.log('biometric + rag OK');
