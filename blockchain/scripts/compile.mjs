import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import solc from 'solc';

export const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SETTINGS = { optimizer: { enabled: true, runs: 200 }, evmVersion: 'shanghai' };

function readImport(p) { return fs.readFileSync(path.join(root, 'node_modules', p), 'utf8'); }

export function compile() {
  const input = { language: 'Solidity', sources: { 'StudyCoin.sol': {
    content: fs.readFileSync(path.join(root, 'contracts/StudyCoin.sol'), 'utf8')
  } }, settings: { ...SETTINGS,
    outputSelection: { '*': { '*': ['abi', 'evm.bytecode.object', 'evm.deployedBytecode.object'] } } } };
  const output = JSON.parse(solc.compile(JSON.stringify(input), { import: p => {
    try { return { contents: readImport(p) }; }
    catch { return { error: 'Import not found: ' + p }; }
  } }));
  const errors = (output.errors || []).filter(e => e.severity === 'error');
  if (errors.length) throw new Error(errors.map(e => e.formattedMessage).join('\n'));
  const c = output.contracts['StudyCoin.sol'].StudyCoin;
  return { contractName: 'StudyCoin', compiler: solc.version(), abi: c.abi, bytecode: '0x' + c.evm.bytecode.object,
    runtimeBytecode: '0x' + c.evm.deployedBytecode.object, sourceFiles: Object.keys(output.sources) };
}

/** Self-contained Standard JSON input (all imported sources inlined) for explorer source verification. */
export function standardInput(compiled = compile()) {
  const sources = {};
  for (const file of compiled.sourceFiles)
    sources[file] = { content: file === 'StudyCoin.sol'
      ? fs.readFileSync(path.join(root, 'contracts/StudyCoin.sol'), 'utf8') : readImport(file) };
  return { language: 'Solidity', sources, settings: { ...SETTINGS,
    outputSelection: { '*': { '*': ['abi', 'evm.bytecode', 'evm.deployedBytecode', 'metadata'] } } } };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  fs.mkdirSync(path.join(root, 'artifacts'), { recursive: true });
  fs.writeFileSync(path.join(root, 'artifacts/StudyCoin.json'), JSON.stringify(compile(), null, 2));
  console.log('Compiled StudyCoin (Shanghai EVM).');
}
