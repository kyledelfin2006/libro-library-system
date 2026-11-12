import { writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import openapiTS, { astToString } from 'openapi-typescript';

const spec = process.env.LIBRO_OPENAPI_URL ?? 'http://localhost:8080/v3/api-docs';
const response = await fetch(spec);
if (!response.ok) throw new Error(`OpenAPI request failed: ${response.status}`);

// Examples do not affect types, and their map-key order varies between JVM runs.
function stable(value) {
  if (Array.isArray(value)) return value.map(stable);
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value)
      .filter(([key]) => key !== 'example' && key !== 'examples')
      .sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0)
      .map(([key, child]) => [key, stable(child)]));
  }
  return value;
}

const output = fileURLToPath(new URL('../src/api/schema.d.ts', import.meta.url));
await writeFile(output, astToString(await openapiTS(stable(await response.json()))));
