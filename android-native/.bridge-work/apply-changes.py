from pathlib import Path
import hashlib, json

work = Path(__file__).resolve().parent
repo = work.parent.parent
assert str(repo).lower() == r'C:\Projects\AI-hoc-tap'.lower()
changes = json.loads((work / 'apply-manifest.json').read_text(encoding='utf-8'))
for item in changes:
    target = repo / item['path']
    assert target.resolve().is_relative_to(repo)
    current = hashlib.sha256(target.read_bytes()).hexdigest() if target.exists() else None
    if current != item['before']:
        raise RuntimeError('File changed concurrently: ' + item['path'])
for item in changes:
    target = repo / item['path']
    data = (work / item['path']).read_bytes()
    if item['path'] == 'blockchain/test/bridge.test.mjs':
        data = data.replace(b"from './pglite-local.mjs'", b"from '@electric-sql/pglite'")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    print(item['path'])
