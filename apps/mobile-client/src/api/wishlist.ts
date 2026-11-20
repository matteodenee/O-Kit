import { apiBaseUrl } from '../auth/config';
import type { AuthenticatedFetch } from '../auth/AuthProvider';

export type WishlistItem = {
  productId: number;
  addedAt: string;
};

type Problem = {
  detail?: string;
  type?: string;
};

export class WishlistApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly type: string | null,
    message: string
  ) {
    super(message);
  }
}

async function toApiError(response: Response, fallback: string) {
  let problem: Problem | null = null;
  try {
    problem = (await response.json()) as Problem;
  } catch {
    // Une réponse sans Problem Details conserve le message de repli.
  }
  return new WishlistApiError(
    response.status,
    problem?.type ?? null,
    problem?.detail ?? fallback
  );
}

export async function listWishlist(authenticatedFetch: AuthenticatedFetch) {
  const response = await authenticatedFetch(`${apiBaseUrl}/api/v1/users/me/wishlist`);
  if (!response.ok) {
    throw await toApiError(
      response,
      `La wishlist est temporairement indisponible (${response.status}).`
    );
  }
  return response.json() as Promise<WishlistItem[]>;
}

export async function addWishlistProduct(
  authenticatedFetch: AuthenticatedFetch,
  productId: number
) {
  const response = await authenticatedFetch(
    `${apiBaseUrl}/api/v1/users/me/wishlist/${encodeURIComponent(productId)}`,
    { method: 'PUT' }
  );
  if (!response.ok) {
    throw await toApiError(
      response,
      `Le produit n'a pas pu être ajouté aux favoris (${response.status}).`
    );
  }
}

export async function removeWishlistProduct(
  authenticatedFetch: AuthenticatedFetch,
  productId: number
) {
  const response = await authenticatedFetch(
    `${apiBaseUrl}/api/v1/users/me/wishlist/${encodeURIComponent(productId)}`,
    { method: 'DELETE' }
  );
  if (!response.ok) {
    throw await toApiError(
      response,
      `Le produit n'a pas pu être retiré des favoris (${response.status}).`
    );
  }
}
