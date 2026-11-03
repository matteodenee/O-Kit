export function hasSellerBackofficeAccess(token: unknown) {
  if (typeof token !== 'object' || token === null) return false;

  const directRoles = (token as Record<string, unknown>).okit_direct_realm_roles;
  if (!Array.isArray(directRoles) || !directRoles.every((role) => typeof role === 'string')) {
    return false;
  }

  return directRoles.includes('SELLER') && !directRoles.includes('BUYER');
}
