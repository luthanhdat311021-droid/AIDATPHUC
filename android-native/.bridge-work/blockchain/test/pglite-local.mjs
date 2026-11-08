import { createRequire } from 'node:module';
const require = createRequire(new URL('../../../../blockchain/package.json', import.meta.url));
export const { PGlite } = require('@electric-sql/pglite');
