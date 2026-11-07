import 'dotenv/config';
import { createClient } from '@supabase/supabase-js';
import { Contract,JsonRpcProvider,Wallet,getAddress,keccak256 } from 'ethers';
import { compile } from './compile.mjs';
import { NETWORKS,deriveManagedAddress,walletIdentity } from '../../backend/services/rewardsChain.js';

// Explicit staging project identity is mandatory to avoid configuring the existing live project by accident.
const {SUPABASE_URL,SUPABASE_SERVICE_ROLE_KEY,REWARDS_STAGING_PROJECT_REF,REWARDS_RPC_URL,
 REWARDS_TOKEN_ADDRESS,REWARDS_OPERATOR_KEY,REWARDS_WALLET_XPUB}=process.env;
if(!SUPABASE_URL||!SUPABASE_SERVICE_ROLE_KEY||!REWARDS_STAGING_PROJECT_REF||!REWARDS_RPC_URL||
 !REWARDS_TOKEN_ADDRESS||!REWARDS_OPERATOR_KEY||!REWARDS_WALLET_XPUB)throw new Error('Missing staging configuration; see .env.rewards.example.');
if(new URL(SUPABASE_URL).hostname!==REWARDS_STAGING_PROJECT_REF+'.supabase.co')throw new Error('Staging project reference does not match URL.');
deriveManagedAddress(REWARDS_WALLET_XPUB,0);
const provider=new JsonRpcProvider(REWARDS_RPC_URL);
try{
 // A public testnet (Amoy/Sepolia), or the private local chain from local-chain.mjs (31337) for internal testing.
 const chainId=Number((await provider.getNetwork()).chainId);
 if(!NETWORKS[chainId])throw new Error('Only Amoy (80002), Sepolia (11155111) or the local chain (31337) is permitted.');
 const compiled=compile(),address=getAddress(REWARDS_TOKEN_ADDRESS);
 const code=await provider.getCode(address);
 if(keccak256(code)!==keccak256(compiled.runtimeBytecode))throw new Error('Deployed bytecode differs from the reviewed StudyCoin source.');
 const token=new Contract(address,compiled.abi,provider),operator=new Wallet(REWARDS_OPERATOR_KEY).address;
 if(!await token.hasRole(await token.OPERATOR_ROLE(),operator))throw new Error('Operator wallet does not have OPERATOR_ROLE.');
 const db=createClient(SUPABASE_URL,SUPABASE_SERVICE_ROLE_KEY,{auth:{persistSession:false}});
 const prior=await db.from('studymind_reward_config').select('*').eq('id',true).single();
 if(prior.error)throw new Error('Apply migrations 005–007 on staging first.');
 const wallets=await db.from('studymind_reward_wallets').select('user_id',{count:'exact',head:true});
 if(wallets.error)throw new Error('Cannot inspect staging wallet bindings.');
 const fingerprint=walletIdentity(REWARDS_WALLET_XPUB);
 if(wallets.count>0&&(prior.data.token_address!==address.toLowerCase()||Number(prior.data.chain_id)!==chainId||prior.data.wallet_xpub_fingerprint!==fingerprint))
  throw new Error('Existing wallets are bound to a different contract, network or xpub. Migration requires a separate recovery plan.');
 const result=await db.from('studymind_reward_config').update({enabled:true,chain_id:chainId,
  token_address:address.toLowerCase(),wallet_xpub_fingerprint:fingerprint,updated_at:new Date().toISOString()}).eq('id',true);
 if(result.error)throw new Error('Could not configure staging rewards.');
 console.log(JSON.stringify({configured:true,chainId,contract:address,project:REWARDS_STAGING_PROJECT_REF}));
}finally{provider.destroy();}
