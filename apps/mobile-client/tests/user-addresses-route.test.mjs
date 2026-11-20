import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const projectDirectory = join(dirname(fileURLToPath(import.meta.url)), '..');

test('the buyer profile links to the protected address management route', async () => {
  const profileRoute = await readFile(
    join(projectDirectory, 'src/app/index.tsx'),
    'utf8'
  );
  const addressesRoute = await readFile(
    join(projectDirectory, 'src/app/addresses.tsx'),
    'utf8'
  );

  assert.match(profileRoute, /href=["']\/addresses["']/u);
  assert.match(addressesRoute, /<Redirect\s+href=["']\/["']\s*\/>/u);
  assert.match(addressesRoute, /href=["']\/\?profile=1["']/u);
  assert.match(addressesRoute, /href=["']\/catalogue["']/u);
  assert.match(addressesRoute, /Choisis d'abord une autre adresse par défaut/u);
});

test('the address API client covers create update default and archive operations', async () => {
  const addressApi = await readFile(
    join(projectDirectory, 'src/api/userAddresses.ts'),
    'utf8'
  );

  assert.match(addressApi, /method:\s*['"]POST['"]/u);
  assert.match(addressApi, /method:\s*['"]PUT['"]/u);
  assert.match(addressApi, /method:\s*['"]DELETE['"]/u);
  assert.match(addressApi, /\/addresses\/\$\{encodeURIComponent\(addressId\)\}\/default/u);
});
