import crypto from 'crypto';

/**
 * AI Cache Manager
 * Prevents redundant AI requests when document content + task + model + promptVersion match.
 */
export class AICache {
  constructor() {
    this.cache = new Map();
  }

  static generateKey(content, task, model, promptVersion) {
    const rawStr = `${content}_${task}_${model}_${promptVersion}`;
    return crypto.createHash('sha256').update(rawStr).digest('hex');
  }

  get(key) {
    if (this.cache.has(key)) {
      console.log(`⚡ [AI Cache] Cache HIT for key: ${key.slice(0, 12)}...`);
      return this.cache.get(key);
    }
    return null;
  }

  set(key, value, ttlSeconds = 86400) {
    this.cache.set(key, value);
    console.log(`💾 [AI Cache] Cached result for key: ${key.slice(0, 12)}...`);
  }

  clear() {
    this.cache.clear();
  }
}

export const aiCache = new AICache();
