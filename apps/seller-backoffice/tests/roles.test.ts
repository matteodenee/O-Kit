import assert from 'node:assert/strict';
import test from 'node:test';

import { hasSellerBackofficeAccess } from '../src/auth/roles.js';

test('accepts a directly assigned seller with Keycloak technical roles', () => {
  assert.equal(hasSellerBackofficeAccess({
    okit_direct_realm_roles: ['default-roles-okit', 'SELLER'],
  }), true);
});

test('refuses a buyer', () => {
  assert.equal(hasSellerBackofficeAccess({
    okit_direct_realm_roles: ['default-roles-okit', 'BUYER'],
  }), false);
});

test('refuses an account with both business roles directly assigned', () => {
  assert.equal(hasSellerBackofficeAccess({
    okit_direct_realm_roles: ['default-roles-okit', 'BUYER', 'SELLER'],
  }), false);
});

test('fails closed when the signed direct-role claim is absent or malformed', () => {
  assert.equal(hasSellerBackofficeAccess(undefined), false);
  assert.equal(hasSellerBackofficeAccess({}), false);
  assert.equal(hasSellerBackofficeAccess({ okit_direct_realm_roles: 'SELLER' }), false);
});
