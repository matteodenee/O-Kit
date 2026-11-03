import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const projectDirectory = join(dirname(fileURLToPath(import.meta.url)), '..');

test('an authenticated buyer is redirected to the catalogue route', async () => {
  const homeRoute = await readFile(join(projectDirectory, 'src/app/index.tsx'), 'utf8');
  const catalogueRoute = await readFile(join(projectDirectory, 'src/app/catalogue.tsx'), 'utf8');

  assert.match(homeRoute, /accessToken\s*&&\s*profile\s*!==\s*['"]1['"]/u);
  assert.match(homeRoute, /<Redirect\s+href=["']\/catalogue["']/u);
  assert.match(catalogueRoute, /getActiveProducts\(authenticatedFetch\)/u);
  assert.match(catalogueRoute, /Aucun produit actif/u);
  assert.match(catalogueRoute, /Réessayer/u);
});

test('the catalogue API uses the authenticated gateway product endpoint', async () => {
  const catalogueApi = await readFile(join(projectDirectory, 'src/api/catalogue.ts'), 'utf8');

  assert.match(catalogueApi, /authenticatedFetch/u);
  assert.match(catalogueApi, /\/api\/v1\/products\?statut=ACTIF&size=20/u);
});
