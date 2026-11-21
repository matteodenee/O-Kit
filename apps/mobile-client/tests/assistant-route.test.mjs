import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const projectDirectory = join(dirname(fileURLToPath(import.meta.url)), '..');
const read = (path) => readFile(join(projectDirectory, path), 'utf8');

test('the chat API posts the question and recent history to the gateway chatbot endpoint', async () => {
  const api = await read('src/api/chat.ts');

  assert.match(api, /\$\{apiBaseUrl\}\/api\/v1\/chat/u);
  assert.match(api, /method: 'POST'/u);
  assert.match(api, /historique: historique\.slice\(-HISTORIQUE_MAX\)/u);
  assert.match(api, /status === 429/u);
});

test('the assistant screen requires a session and sends questions through the chat API', async () => {
  const screen = await read('src/app/assistant.tsx');

  assert.match(screen, /if \(!accessToken\) \{\s*return <Redirect href="\/" \/>;/u);
  assert.match(screen, /envoyerQuestion\(authenticatedFetch, question, historique\)/u);
  assert.match(screen, /maxLength=\{MESSAGE_MAX\}/u);
});

test('the assistant is reachable from the catalogue and the orders list', async () => {
  const catalogue = await read('src/app/catalogue.tsx');
  const commandes = await read('src/app/commandes/index.tsx');

  assert.match(catalogue, /href=["']\/assistant["']/u);
  assert.match(commandes, /href=["']\/assistant["']/u);
});
