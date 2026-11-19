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
  assert.match(catalogueRoute, /listWishlist\(authenticatedFetch\)/u);
  assert.match(catalogueRoute, /WishlistApiError\s*&&\s*wishlistError\.status\s*===\s*409/u);
  assert.match(catalogueRoute, /toggleFavorite\(product\.id\)/u);
  assert.match(catalogueRoute, /Ajouter .* aux favoris/u);
  assert.match(catalogueRoute, /Retirer .* des favoris/u);
  assert.match(catalogueRoute, /Aucun produit actif/u);
  assert.match(catalogueRoute, /Réessayer/u);
});

test('profile and address screens expose explicit catalogue navigation', async () => {
  const profileRoute = await readFile(join(projectDirectory, 'src/app/index.tsx'), 'utf8');
  const addressesRoute = await readFile(join(projectDirectory, 'src/app/addresses.tsx'), 'utf8');

  assert.match(profileRoute, /href=["']\/catalogue["']/u);
  assert.match(profileRoute, /Retour au catalogue/u);
  assert.match(addressesRoute, /href=["']\/catalogue["']/u);
  assert.match(addressesRoute, /href=["']\/\?profile=1["']/u);
  assert.match(addressesRoute, /href=["']\/wishlist["']/u);
});

test('the catalogue API uses the authenticated gateway product endpoint', async () => {
  const catalogueApi = await readFile(join(projectDirectory, 'src/api/catalogue.ts'), 'utf8');

  assert.match(catalogueApi, /authenticatedFetch/u);
  assert.match(catalogueApi, /\/api\/v1\/products\?statut=ACTIF&size=20/u);
  assert.match(catalogueApi, /photoUrl\?:\s*string\s*\|\s*null/u);
});

test('the catalogue screen renders jersey images, brand/season/club filters and price sorting', async () => {
  const catalogueRoute = await readFile(join(projectDirectory, 'src/app/catalogue.tsx'), 'utf8');

  assert.match(catalogueRoute, /<Image[\s\S]*product\.photoUrl/u);
  assert.match(catalogueRoute, /Marque/u);
  assert.match(catalogueRoute, /Saison/u);
  assert.match(catalogueRoute, /Club \/ Équipe/u);
  assert.match(catalogueRoute, /Prix croissant/u);
  assert.match(catalogueRoute, /Prix décroissant/u);
  assert.match(catalogueRoute, /Réinitialiser les filtres/u);
});
