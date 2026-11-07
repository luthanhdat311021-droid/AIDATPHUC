import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import ganache from 'ganache';
import { HDNodeWallet, Mnemonic, Wallet, randomBytes } from 'ethers';

// Persistent private EVM (chain 31337) for internal testing without testnet POL.
// Tokens here have no value outside this machine. Never reuse these keys on a public network.
export const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
export const LOCAL_DIR = path.join(repo, '.local', 'chain');
export const SECRET_FILE = path.join(repo, '.secrets', 'local-chain.json');
export const LOCAL_RPC = `http://127.0.0.1:${process.env.LOCAL_CHAIN_PORT || 8545}`;

/** Created once; the same mnemonic must be reused or the persisted chain's accounts no longer match. */
export function localSecrets() {
  if (fs.existsSync(SECRET_FILE)) return JSON.parse(fs.readFileSync(SECRET_FILE, 'utf8'));
  const chainMnemonic = Mnemonic.fromEntropy(randomBytes(16)).phrase;
  // A separate root for learners' managed wallets; the server only ever receives its xpub.
  const userRoot = HDNodeWallet.fromPhrase(Mnemonic.fromEntropy(randomBytes(16)).phrase, undefined, "m/44'/60'/0'/0");
  const account = i => HDNodeWallet.fromPhrase(chainMnemonic, undefined, `m/44'/60'/0'/0/${i}`);
  const secrets = { chainId: 31337, chainMnemonic,
    admin: { address: account(0).address, key: account(0).privateKey },
    operator: { address: account(1).address, key: account(1).privateKey },
    walletXpub: userRoot.neuter().extendedKey, createdAt: new Date().toISOString() };
  fs.mkdirSync(path.dirname(SECRET_FILE), { recursive: true });
  fs.writeFileSync(SECRET_FILE, JSON.stringify(secrets, null, 2), { flag: 'wx', mode: 0o600 });
  return secrets;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const s = localSecrets();
  fs.mkdirSync(LOCAL_DIR, { recursive: true });
  const host = process.env.LOCAL_CHAIN_HOST || '127.0.0.1', port = Number(process.env.LOCAL_CHAIN_PORT || 8545);
  const server = ganache.server({
    chain: { chainId: 31337, hardfork: 'shanghai' },
    wallet: { mnemonic: s.chainMnemonic, totalAccounts: 3, defaultBalance: 1000 },
    database: { dbPath: path.join(LOCAL_DIR, 'db') },
    logging: { quiet: true }
  });
  await server.listen(port, host);
  console.log(`StudyMind local chain running at http://${host}:${port} (chain ID 31337). Ctrl+C to stop; state is kept in .local/chain.`);
  console.log(JSON.stringify({ admin: s.admin.address, operator: s.operator.address }));
  if (new Wallet(s.operator.key).address !== s.operator.address) throw new Error('Local secrets are inconsistent.');
  process.on('SIGINT', async () => { await server.close(); process.exitCode = 0; });
}
