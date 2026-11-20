import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const projectDirectory = join(dirname(fileURLToPath(import.meta.url)), '..');

test('the wishlist API uses synchronous authenticated user routes', async () => {
  const wishlistApi = await readFile(join(projectDirectory, 'src/api/wishlist.ts'), 'utf8');

  assert.match(wishlistApi, /\/api\/v1\/users\/me\/wishlist/u);
  assert.match(wishlistApi, /method:\s*['"]PUT['"]/u);
  assert.match(wishlistApi, /method:\s*['"]DELETE['"]/u);
  assert.match(wishlistApi, /WishlistApiError/u);
});

test('the wishlist route enriches product identifiers with catalogue details', async () => {
  const wishlistRoute = await readFile(join(projectDirectory, 'src/app/wishlist.tsx'), 'utf8');

  assert.match(wishlistRoute, /listWishlist\(authenticatedFetch\)/u);
  assert.match(wishlistRoute, /getProductById\(authenticatedFetch, item\.productId\)/u);
  assert.match(wishlistRoute, /removeWishlistProduct\(authenticatedFetch, productId\)/u);
  assert.match(wishlistRoute, /Ta wishlist est vide/u);
  assert.match(wishlistRoute, /Indisponible/u);
  assert.match(wishlistRoute, /Réessayer/u);
});

test('catalogue, profile and address routes expose wishlist navigation', async () => {
  const catalogueRoute = await readFile(join(projectDirectory, 'src/app/catalogue.tsx'), 'utf8');
  const profileRoute = await readFile(join(projectDirectory, 'src/app/index.tsx'), 'utf8');
  const addressesRoute = await readFile(join(projectDirectory, 'src/app/addresses.tsx'), 'utf8');

  assert.match(catalogueRoute, /href=["']\/wishlist["']/u);
  assert.match(profileRoute, /href=["']\/wishlist["']/u);
  assert.match(addressesRoute, /href=["']\/wishlist["']/u);
});
