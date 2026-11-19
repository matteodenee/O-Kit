import assert from 'node:assert/strict';
import test from 'node:test';

import type { CatalogueRequest } from '../src/api/catalogue.js';
import { actionsPossibles, changerStatut, listerCommandes } from '../src/api/commande.js';

test('propose uniquement les transitions autorisées par ms-commande', () => {
  assert.deepEqual(actionsPossibles('CONFIRMEE').map((a) => a.cible), ['EN_PREPARATION', 'ANNULEE']);
  assert.deepEqual(actionsPossibles('EN_PREPARATION').map((a) => a.cible), ['EXPEDIEE', 'ANNULEE']);
  assert.deepEqual(actionsPossibles('EXPEDIEE').map((a) => a.cible), ['LIVREE']);
  assert.deepEqual(actionsPossibles('LIVREE'), []);
  assert.deepEqual(actionsPossibles('ANNULEE'), []);
});

test('liste les commandes filtrées par statut, les plus récentes d’abord', async () => {
  let path = '';
  const request: CatalogueRequest = async (p) => {
    path = p;
    return Response.json({ content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } });
  };

  await listerCommandes(request, { statut: 'CONFIRMEE', page: 1 });

  assert.equal(path, '/api/v1/seller/commandes?page=1&size=20&sort=dateCommande%2Cdesc&statut=CONFIRMEE');
});

test('envoie le code de suivi à l’expédition et remonte l’erreur métier', async () => {
  const calls: { path: string; init?: RequestInit }[] = [];
  const request: CatalogueRequest = async (path, init) => {
    calls.push({ path, init });
    return Response.json({ detail: 'Transition interdite' }, { status: 409 });
  };

  await assert.rejects(() => changerStatut(request, 7, 'EXPEDIEE', 'COLIS-1'), /Transition interdite/);

  assert.equal(calls[0].path, '/api/v1/seller/commandes/7/statut');
  assert.equal(calls[0].init?.method, 'PATCH');
  assert.deepEqual(JSON.parse(String(calls[0].init?.body)), { statut: 'EXPEDIEE', codeSuiviTransporteur: 'COLIS-1' });
});
