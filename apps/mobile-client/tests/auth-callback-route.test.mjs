import assert from 'node:assert/strict';
import { access, readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const projectDirectory = join(dirname(fileURLToPath(import.meta.url)), '..');

test('the native OAuth callback resolves to an Expo Router route that returns home', async () => {
  const authConfig = await readFile(
    join(projectDirectory, 'src/auth/config.ts'),
    'utf8'
  );
  const nativeRedirect = authConfig.match(/native:\s*['"]([^'"]+)['"]/u)?.[1];

  assert.ok(nativeRedirect, 'A native OAuth redirect URI must be configured.');

  const callbackUrl = new URL(nativeRedirect);
  const callbackPath = `${callbackUrl.hostname}${callbackUrl.pathname}`.replace(
    /^\/+|\/+$/gu,
    ''
  );
  const callbackRoute = join(projectDirectory, 'src/app', `${callbackPath}.tsx`);

  await access(callbackRoute);

  const callbackRouteSource = await readFile(callbackRoute, 'utf8');
  assert.match(callbackRouteSource, /<Redirect\s+href=["']\/["']\s*\/>/u);
});
