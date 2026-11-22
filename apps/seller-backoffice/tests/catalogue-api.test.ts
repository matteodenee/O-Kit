import assert from 'node:assert/strict';
import test from 'node:test';

import {
  type CatalogueRequest,
  getProduct,
  listAllProducts,
  updateProduct,
  updateProductStock,
} from '../src/api/catalogue.js';

test('loads every catalogue page including inactive products', async () => {
  const requestedPaths: string[] = [];
  const request: CatalogueRequest = async (path) => {
    requestedPaths.push(path);
    const page = path.includes('page=0') ? 0 : 1;
    return Response.json({
      content: [{
        id: page + 1,
        titre: page === 0 ? 'Produit actif' : 'Produit désactivé',
        prixHT: 10,
        prixTTC: 12,
        enStock: true,
        nomMarque: null,
        nomSaison: null,
        nomEquipe: null,
        statut: page === 0 ? 'ACTIF' : 'DESACTIVE',
        photoUrl: page === 0 ? 'data:image/svg+xml;utf8,<svg></svg>' : null,
      }],
      page,
      size: 1,
      totalElements: 2,
      totalPages: 2,
      isLast: page === 1,
    });
  };

  const products = await listAllProducts(request);

  assert.deepEqual(requestedPaths, [
    '/api/v1/products?page=0&size=100',
    '/api/v1/products?page=1&size=100',
  ]);
  assert.deepEqual(products.map((product) => product.statut), ['ACTIF', 'DESACTIVE']);
  assert.equal(products[0]?.photoUrl, 'data:image/svg+xml;utf8,<svg></svg>');
  assert.equal(products[1]?.photoUrl, null);
});

test('sends only the supported product update fields with PATCH', async () => {
  let receivedPath = '';
  let receivedInit: RequestInit | undefined;
  const request: CatalogueRequest = async (path, init) => {
    receivedPath = path;
    receivedInit = init;
    return Response.json({ id: 7, titre: 'Maillot modifié' });
  };

  await updateProduct(request, 7, {
    titre: 'Maillot modifié',
    description: 'Description',
    prixDeBase: 75,
    poids: 0.25,
    couleursPrincipales: 'Bleu',
    impactEnvi: 'Non évalué',
    estFlocable: true,
    statut: 'DESACTIVE',
  });

  assert.equal(receivedPath, '/api/v1/products/7');
  assert.equal(receivedInit?.method, 'PATCH');
  assert.deepEqual(JSON.parse(String(receivedInit?.body)), {
    titre: 'Maillot modifié',
    description: 'Description',
    prixDeBase: 75,
    poids: 0.25,
    couleursPrincipales: 'Bleu',
    impactEnvi: 'Non évalué',
    estFlocable: true,
    statut: 'DESACTIVE',
  });
});

test('forwards optional photoUrl in PATCH when provided and retrieves photoUrl in detail', async () => {
  let receivedBody = '';
  const request: CatalogueRequest = async (path, init) => {
    if (init?.method === 'PATCH') {
      receivedBody = String(init.body ?? '');
      return Response.json({ id: 7, titre: 'Maillot PSG', photoUrl: 'https://cdn.okit.fr/psg.svg' });
    }
    return Response.json({
      id: 7,
      titre: 'Maillot PSG',
      photoUrl: 'https://cdn.okit.fr/psg.svg',
      variantes: [{ id: 1, taille: 'M' }],
    });
  };

  const detail = await getProduct(request, 7);
  assert.equal(detail.photoUrl, 'https://cdn.okit.fr/psg.svg');
  assert.equal(detail.variantes.length, 1);

  const updated = await updateProduct(request, 7, {
    titre: 'Maillot PSG',
    description: 'Description',
    prixDeBase: 83.33,
    couleursPrincipales: 'Bleu',
    impactEnvi: 'Démo',
    estFlocable: true,
    statut: 'ACTIF',
    photoUrl: 'https://cdn.okit.fr/psg.svg',
  });

  assert.equal(updated.photoUrl, 'https://cdn.okit.fr/psg.svg');
  assert.equal((JSON.parse(receivedBody) as { photoUrl?: string }).photoUrl, 'https://cdn.okit.fr/psg.svg');
});

test('updates the global stock status through its dedicated endpoint', async () => {
  let receivedPath = '';
  let receivedInit: RequestInit | undefined;
  const request: CatalogueRequest = async (path, init) => {
    receivedPath = path;
    receivedInit = init;
    return Response.json({ id: 3, enStock: false });
  };

  await updateProductStock(request, 3, false);

  assert.equal(receivedPath, '/api/v1/products/3/stock-status');
  assert.equal(receivedInit?.method, 'PATCH');
  assert.deepEqual(JSON.parse(String(receivedInit?.body)), { enStock: false });
});

test('surfaces catalogue validation messages', async () => {
  const request: CatalogueRequest = async () => Response.json(
    { fieldErrors: { titre: 'Le titre est obligatoire' } },
    { status: 400 },
  );

  await assert.rejects(
    () => updateProduct(request, 1, {
      titre: '',
      description: '',
      prixDeBase: 10,
      couleursPrincipales: '',
      impactEnvi: '',
      estFlocable: false,
      statut: 'ACTIF',
    }),
    /Le titre est obligatoire/u,
  );
});
