/**
 * AI Usage & Audit Logger
 * Tracks AI tasks, latency, status, tokens, errors without exposing API keys.
 */
export class AILogger {
  constructor() {
    this.logs = [];
  }

  log({ userId = 'usr-1', task, provider, model, latencyMs, status, error = null, tokens = 0 }) {
    const entry = {
      id: `log-${Date.now()}-${Math.round(Math.random() * 1000)}`,
      userId,
      task,
      provider,
      model,
      tokens,
      latencyMs,
      status,
      error,
      createdAt: new Date().toISOString()
    };

    this.logs.unshift(entry);
    if (this.logs.length > 500) this.logs.pop(); // Keep last 500 logs in memory

    console.log(`📊 [AI Audit Log] ${provider} (${model}) | Task: ${task} | Status: ${status} | Latency: ${latencyMs}ms`);
    return entry;
  }

  getLogs(limit = 50) {
    return this.logs.slice(0, limit);
  }

  getStats() {
    const total = this.logs.length;
    const successes = this.logs.filter(l => l.status === 'SUCCESS').length;
    const failures = this.logs.filter(l => l.status === 'ERROR').length;
    const avgLatency = total > 0 ? (this.logs.reduce((acc, l) => acc + l.latencyMs, 0) / total).toFixed(0) : 0;

    return {
      totalRequests: total,
      successRate: total > 0 ? `${((successes / total) * 100).toFixed(1)}%` : '100%',
      failures,
      averageLatencyMs: Number(avgLatency)
    };
  }
}

export const aiLogger = new AILogger();
