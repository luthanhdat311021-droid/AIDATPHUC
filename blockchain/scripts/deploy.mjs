import fs from 'node:fs';
import path from 'node:path';
import { AbiCoder, ContractFactory, JsonRpcProvider, Wallet, getAddress, keccak256 } from 'ethers';
import { compile, root, standardInput } from './compile.mjs';
import { NETWORKS, PUBLIC_TESTNETS } from '../../backend/services/rewardsChain.js';

// Public testnet deployment (Polygon Amoy or Ethereum Sepolia); the network is taken from the RPC URL.
const { REWARDS_RPC_URL, REWARDS_DEPLOYER_KEY, REWARDS_ADMIN_ADDRESS, REWARDS_OPERATOR_ADDRESS, REWARDS_ALLOW_REDEPLOY } = process.env;
if (!REWARDS_RPC_URL || !REWARDS_DEPLOYER_KEY || !REWARDS_ADMIN_ADDRESS || !REWARDS_OPERATOR_ADDRESS)
  throw new Error('Set RPC URL, deployer key, admin address and operator address; never commit secrets.');
const provider = new JsonRpcProvider(REWARDS_RPC_URL);
try {
  const chainId = Number((await provider.getNetwork()).chainId);
  if (!PUBLIC_TESTNETS.includes(chainId))
    throw new Error(`This deployment script only permits ${PUBLIC_TESTNETS.map(id => `${NETWORKS[id].name} (${id})`).join(' or ')}.`);
  const { slug, name, gas } = NETWORKS[chainId];
  // Deployment records hold public data only (addresses, hashes, verification input) and are safe to commit.
  const folder = path.join(root, 'deployments');
  const previous = fs.existsSync(folder) ? fs.readdirSync(folder).filter(f => new RegExp(`^${slug}-0x[0-9a-fA-F]{40}\\.json$`).test(f)) : [];
  if (previous.length && REWARDS_ALLOW_REDEPLOY !== 'true')
    throw new Error(`StudyCoin already deployed on ${name} (${previous.join(', ')}). Reuse it; set REWARDS_ALLOW_REDEPLOY=true only for a planned new contract.`);
  const admin = getAddress(REWARDS_ADMIN_ADDRESS), operator = getAddress(REWARDS_OPERATOR_ADDRESS);
  if (admin === operator) throw new Error('Use separate admin and operator wallets.');
  const deployer = new Wallet(REWARDS_DEPLOYER_KEY, provider);
  if (deployer.address === operator) throw new Error('The operator wallet must not deploy; keep its nonce for the worker.');
  if (await provider.getBalance(deployer.address) === 0n) throw new Error(`Deployer has no ${gas} for gas.`);
  const a = compile();
  const c = await new ContractFactory(a.abi, a.bytecode, deployer).deploy(admin, operator);
  const receipt = await c.deploymentTransaction().wait();
  const contract = await c.getAddress();
  const record = { chainId, network: name, contract, transaction: receipt.hash, blockNumber: receipt.blockNumber,
    deployer: deployer.address, admin, operator, compiler: 'v' + a.compiler.replace(/\.Emscripten.*$/, ''),
    runtimeBytecodeHash: keccak256(a.runtimeBytecode),
    constructorArguments: AbiCoder.defaultAbiCoder().encode(['address', 'address'], [admin, operator]).slice(2),
    deployedAt: new Date().toISOString() };
  fs.mkdirSync(folder, { recursive: true });
  fs.writeFileSync(path.join(folder, `${slug}-${contract}.json`), JSON.stringify(record, null, 2), { flag: 'wx' });
  fs.writeFileSync(path.join(folder, `${slug}-${contract}.verify.json`), JSON.stringify(standardInput(a)), { flag: 'wx' });
  console.log(JSON.stringify({ chainId, network: name, contract, transaction: receipt.hash,
    explorer: `${NETWORKS[chainId].explorer}/address/${contract}`, record: `blockchain/deployments/${slug}-${contract}.json` }));
} finally { provider.destroy(); }
