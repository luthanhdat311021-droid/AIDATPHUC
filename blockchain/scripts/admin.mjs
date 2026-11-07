import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Contract, JsonRpcProvider, Wallet, getAddress, keccak256 } from 'ethers';
import { compile } from './compile.mjs';
import { NETWORKS } from '../../backend/services/rewardsChain.js';

// Contract administration for incidents and key rotation. Every command prints a plan and only sends
// a transaction with --yes. The worker never needs these; the admin key stays off servers.
export const COMMANDS = {
  pause: 'Dừng mọi mint/burn (sự cố, lộ khóa operator, reorg sâu).',
  unpause: 'Mở lại mint/burn sau khi đã đối soát.',
  'grant-operator': 'Cấp OPERATOR_ROLE cho ví operator mới.',
  'revoke-operator': 'Thu hồi OPERATOR_ROLE của một ví operator.',
  'begin-admin-transfer': 'Bắt đầu chuyển quyền admin (chờ 2 ngày).',
  'cancel-admin-transfer': 'Hủy yêu cầu chuyển admin đang chờ.',
  'accept-admin': 'Admin mới nhận quyền sau thời gian chờ (ký bằng ví admin mới).'
};

export async function adminCommand(token, signer, command, arg, { execute = false } = {}) {
  if (!COMMANDS[command]) throw new Error('Unknown command. Use: ' + Object.keys(COMMANDS).join(', '));
  const me = getAddress(await signer.getAddress()), c = token.connect(signer);
  const [admin, operatorRole, paused] = await Promise.all([token.defaultAdmin(), token.OPERATOR_ROLE(), token.paused()]);
  const target = ['grant-operator', 'revoke-operator', 'begin-admin-transfer'].includes(command) ? getAddress(arg || '') : null;
  if (command !== 'accept-admin' && me !== admin) throw new Error('Signer is not the current contract admin.');
  let send;
  switch (command) {
    case 'pause':
      if (paused) throw new Error('Contract is already paused.');
      send = () => c.pause(); break;
    case 'unpause':
      if (!paused) throw new Error('Contract is not paused.');
      send = () => c.unpause(); break;
    case 'grant-operator':
      if (target === admin) throw new Error('Keep admin and operator wallets separate.');
      if (await token.hasRole(operatorRole, target)) throw new Error('Address already has OPERATOR_ROLE.');
      send = () => c.grantRole(operatorRole, target); break;
    case 'revoke-operator':
      if (!await token.hasRole(operatorRole, target)) throw new Error('Address does not have OPERATOR_ROLE.');
      send = () => c.revokeRole(operatorRole, target); break;
    case 'begin-admin-transfer':
      if (target === admin) throw new Error('Address is already the admin.');
      if (await token.hasRole(operatorRole, target)) throw new Error('New admin must not be an operator wallet.');
      send = () => c.beginDefaultAdminTransfer(target); break;
    case 'cancel-admin-transfer':
      if ((await token.pendingDefaultAdmin()).newAdmin === '0x0000000000000000000000000000000000000000')
        throw new Error('No admin transfer is pending.');
      send = () => c.cancelDefaultAdminTransfer(); break;
    case 'accept-admin': {
      const { newAdmin, schedule } = await token.pendingDefaultAdmin();
      if (getAddress(newAdmin) !== me) throw new Error('Signer is not the pending admin.');
      const now = (await signer.provider.getBlock('latest')).timestamp;
      if (Number(schedule) > now) throw new Error('Admin delay has not passed; accept after ' + new Date(Number(schedule) * 1000).toISOString());
      send = () => c.acceptDefaultAdminTransfer(); break;
    }
  }
  const plan = { command, description: COMMANDS[command], contract: await token.getAddress(), signer: me, target };
  if (!execute) return { plan, executed: false };
  const receipt = await (await send()).wait();
  return { plan, executed: true, transaction: receipt.hash, blockNumber: receipt.blockNumber };
}

async function main() {
  const [command, ...rest] = process.argv.slice(2);
  const execute = rest.includes('--yes'), arg = rest.find(v => v !== '--yes');
  const { REWARDS_RPC_URL, REWARDS_TOKEN_ADDRESS, REWARDS_ADMIN_KEY } = process.env;
  if (!COMMANDS[command]) {
    console.log('Usage: node blockchain/scripts/admin.mjs <command> [address] [--yes]');
    for (const [k, v] of Object.entries(COMMANDS)) console.log(`  ${k.padEnd(22)} ${v}`);
    // exitCode, not exit(): exiting while solc's runtime is loading aborts Node on Windows.
    process.exitCode = command ? 1 : 0;
    return;
  }
  if (!REWARDS_RPC_URL || !REWARDS_TOKEN_ADDRESS || !REWARDS_ADMIN_KEY)
    throw new Error('Set REWARDS_RPC_URL, REWARDS_TOKEN_ADDRESS and REWARDS_ADMIN_KEY (admin or pending admin key).');
  const provider = new JsonRpcProvider(REWARDS_RPC_URL);
  try {
    if (!NETWORKS[Number((await provider.getNetwork()).chainId)]) throw new Error('Only Amoy (80002), Sepolia (11155111) or the local chain (31337) is permitted.');
    const compiled = compile(), address = getAddress(REWARDS_TOKEN_ADDRESS);
    if (keccak256(await provider.getCode(address)) !== keccak256(compiled.runtimeBytecode))
      throw new Error('Deployed bytecode differs from the reviewed StudyCoin source.');
    const result = await adminCommand(new Contract(address, compiled.abi, provider),
      new Wallet(REWARDS_ADMIN_KEY, provider), command, arg, { execute });
    console.log(JSON.stringify(result, null, 2));
    if (!execute) console.log('Dry run only. Re-run with --yes to send this transaction.');
  } finally { provider.destroy(); }
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();
