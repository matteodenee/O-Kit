import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const projectDirectory = join(dirname(fileURLToPath(import.meta.url)), '..');
const read = (path) => readFile(join(projectDirectory, path), 'utf8');

test('the catalogue links each product to its detail screen and exposes cart and orders', async () => {
  const catalogue = await read('src/app/catalogue.tsx');

  assert.match(catalogue, /href=\{`\/produit\/\$\{product\.id\}`\}/u);
  assert.match(catalogue, /href=["']\/panier["']/u);
  assert.match(catalogue, /href=["']\/commandes["']/u);
});

test('the product screen offers size, quantity and personalised printing', async () => {
  const produit = await read('src/app/produit/[id].tsx');

  assert.match(produit, /getProduct\(authenticatedFetch, idProduit\)/u);
  assert.match(produit, /ajouterAuPanier\(authenticatedFetch/u);
  assert.match(produit, /estFlocable === true/u);
  assert.match(produit, /Flocage personnalisé/u);
});

test('the cart and checkout screens call the order service', async () => {
  const panier = await read('src/app/panier.tsx');
  const validation = await read('src/app/validation.tsx');

  assert.match(panier, /getPanier\(authenticatedFetch\)/u);
  assert.match(panier, /router\.push\(['"]\/validation['"]\)/u);
  assert.match(validation, /listUserAddresses\(authenticatedFetch\)/u);
  assert.match(validation, /validerCommande\(authenticatedFetch/u);
  assert.match(validation, /router\.replace\(`\/commandes\/\$\{commande\.idCommande\}\?confirmee=1`\)/u);
});

test('the order API targets the gateway cart and order endpoints', async () => {
  const api = await read('src/api/commande.ts');

  assert.match(api, /\/api\/v1\/panier/u);
  assert.match(api, /\/api\/v1\/panier\/lignes/u);
  assert.match(api, /\/api\/v1\/commandes/u);
  assert.match(api, /\/annulation/u);
});
