import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const cli = fileURLToPath(new URL('../node_modules/openapi-typescript/bin/cli.js', import.meta.url));
const spec = process.env.LIBRO_OPENAPI_URL ?? 'http://localhost:8080/v3/api-docs';
const result = spawnSync(process.execPath, [cli, spec, '-o', 'src/api/schema.d.ts'], { stdio: 'inherit' });

if (result.error) throw result.error;
process.exitCode = result.status ?? 1;
