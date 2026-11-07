import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Wallet,HDNodeWallet,keccak256,toUtf8Bytes } from 'ethers';

// Run manually for STAGING. The server receives only an xpub; the private root stays encrypted offline.
const password=process.env.STUDYMIND_VAULT_PASSWORD;
if(!password||password.length<20)throw new Error('Set STUDYMIND_VAULT_PASSWORD to a strong password of at least 20 characters.');
const repo=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..');
const folder=path.join(repo,'.secrets');
await fs.mkdir(folder,{recursive:true});
const wallet=Wallet.createRandom();
const root=HDNodeWallet.fromPhrase(wallet.mnemonic.phrase,undefined,"m/44'/60'/0'/0");
const encrypted=await root.encrypt(password);
await fs.writeFile(path.join(folder,'studycoin-wallet-root.keystore.json'),encrypted,{flag:'wx',mode:0o600});
const xpub=root.neuter().extendedKey;
console.log(JSON.stringify({xpub,fingerprint:keccak256(toUtf8Bytes(xpub)),vault:'.secrets/studycoin-wallet-root.keystore.json'},null,2));
console.log('Back up the encrypted vault and password separately. No plaintext mnemonic or private key has been printed.');
