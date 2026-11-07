import { Contract, FetchRequest, JsonRpcProvider, HDNodeWallet, Network, keccak256, toUtf8Bytes } from 'ethers';
import { HttpError } from './billingService.js';

export const TOKEN_ABI = [
  'function balanceOf(address) view returns (uint256)', 'function decimals() view returns (uint8)',
  'function operations(bytes32) view returns (address wallet,uint256 amount,uint32 daysGranted,uint8 kind)',
  'function reward(bytes32,address,uint256)', 'function redeem(bytes32,address,uint256,uint32)',
  'event RewardOperation(bytes32 indexed operationId,address indexed wallet,uint8 kind,uint256 amount,uint32 daysGranted)'
];

// Every network StudyCoin may run on. Public testnets need a deep confirmation policy; 31337 is the private local chain.
export const NETWORKS = {
  // lowGasWei: operator balance below which monitoring alerts (roughly a few dozen mint/burn transactions).
  80002: { slug: 'amoy', name: 'Polygon Amoy', gas: 'POL', explorer: 'https://amoy.polygonscan.com', minConfirmations: 64, lowGasWei: 10n ** 17n },
  11155111: { slug: 'sepolia', name: 'Ethereum Sepolia', gas: 'Sepolia ETH', explorer: 'https://sepolia.etherscan.io', minConfirmations: 64, lowGasWei: 5n * 10n ** 15n },
  31337: { slug: 'local', name: 'Mạng thử nghiệm cục bộ', gas: 'ETH cục bộ', explorer: null, minConfirmations: 1, lowGasWei: 10n ** 17n }
};
export const PUBLIC_TESTNETS = [80002, 11155111];

export function walletIdentity(xpub) { return keccak256(toUtf8Bytes(xpub)); }
export function deriveManagedAddress(xpub, index) {
  let node;
  try { node = HDNodeWallet.fromExtendedKey(xpub); }
  catch { throw new HttpError(503, 'Cấu hình ví quản lý chưa hợp lệ.', 'WALLET_CONFIG'); }
  if (!xpub.startsWith('xpub') || node.depth !== 4 || !Number.isInteger(index) || index < 0 || index >= 2 ** 31)
    throw new HttpError(503, 'Cấu hình ví quản lý chưa hợp lệ.', 'WALLET_CONFIG');
  return node.deriveChild(index).address.toLowerCase();
}

// RPC + network + contract checks are repeated at most every few minutes per server instance:
// each check is a round trip to the chain, and every StudyCoin screen load needs a provider.
const CHAIN_CHECK_MS = 5 * 60_000;
const checkedChains = new Map();

export async function chainClient(config) {
  const url = process.env.REWARDS_RPC_URL;
  if (!url) throw new HttpError(503, 'Mạng token chưa được cấu hình.', 'CHAIN_UNAVAILABLE');
  const request = new FetchRequest(url);
  request.timeout = 10000;
  const key = `${url}|${config.chain_id}|${String(config.token_address).toLowerCase()}`;
  const checked = (checkedChains.get(key) ?? 0) > Date.now();
  // Once verified, pin the network so ethers does not re-query eth_chainId for every new provider.
  const provider = checked
    ? new JsonRpcProvider(request, Network.from(Number(config.chain_id)), { batchMaxCount: 1, cacheTimeout: -1, staticNetwork: true })
    : new JsonRpcProvider(request, undefined, { batchMaxCount: 1, cacheTimeout: -1 });
  try {
  const token = new Contract(config.token_address, TOKEN_ABI, provider);
  if (!checked) {
    const chainId = Number((await provider.getNetwork()).chainId);
    if (!NETWORKS[chainId] || chainId !== Number(config.chain_id)) {
      provider.destroy();
      throw new HttpError(503, 'Mạng token không khớp cấu hình thử nghiệm.', 'CHAIN_MISMATCH');
    }
    const [code, decimals] = await Promise.all([provider.getCode(config.token_address), token.decimals()]);
    if (code === '0x' || Number(decimals) !== 0) {
      provider.destroy();
      throw new HttpError(503, 'Hợp đồng StudyCoin chưa hợp lệ.', 'CONTRACT_MISMATCH');
    }
    checkedChains.set(key, Date.now() + CHAIN_CHECK_MS);
  }
  return { provider, token };
  } catch (e) {
    provider.destroy();
    if (e instanceof HttpError) throw e;
    throw new HttpError(503, 'Chưa kết nối được mạng token. Hãy thử lại sau.', 'CHAIN_UNAVAILABLE');
  }
}

export function confirmationCount(chainId) {
  // Public testnets use a conservative depth (64 blocks ≈ finality on Sepolia); the local EVM settles immediately.
  const minimum = NETWORKS[Number(chainId)]?.minConfirmations ?? 64;
  const requested = Number(process.env.REWARDS_CONFIRMATIONS || minimum);
  if (!Number.isInteger(requested) || requested < minimum || requested > 2048)
    throw new HttpError(503, 'Số xác nhận blockchain chưa hợp lệ.', 'FINALITY_CONFIG');
  return requested;
}

export async function confirmedBalance(config, address) {
  const { provider, token } = await chainClient(config);
  try {
    const latest = await provider.getBlockNumber();
    const block = Math.max(0, latest - confirmationCount(config.chain_id) + 1);
    return { balance: Number(await token.balanceOf(address, { blockTag: block })), balanceBlock: block };
  } catch {
    throw new HttpError(503, 'Chưa đọc được số dư token đã xác nhận. Hãy thử lại sau.', 'CHAIN_UNAVAILABLE');
  } finally { provider.destroy(); }
}

export function validateOperation(job, event) {
  return event && String(event.operationId).toLowerCase() === job.operation_id.toLowerCase()
    && String(event.wallet).toLowerCase() === job.wallet_address.toLowerCase()
    && BigInt(event.amount) === BigInt(job.amount)
    && Number(event.kind) === (job.kind === 'mint' ? 1 : 2)
    && Number(event.daysGranted) === Number(job.days);
}
