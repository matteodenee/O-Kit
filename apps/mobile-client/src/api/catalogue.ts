import { apiBaseUrl } from '../auth/config';
import type { AuthenticatedFetch } from '../auth/AuthProvider';

export type ProductSummary = {
  id: number;
  titre: string;
  prixHT: number;
  prixTTC: number;
  enStock: boolean;
  nomMarque: string | null;
  nomSaison: string | null;
  nomEquipe: string | null;
  statut: 'ACTIF' | 'DESACTIVE';
};

export type ProductPage = {
  content: ProductSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  isLast: boolean;
};

type ProductReference = {
  nom?: string;
  nomMarque?: string;
};

export type ProductDetail = {
  id: number;
  titre: string;
  description: string | null;
  prixDeBaseHT: number;
  prixTTC: number;
  prixApresPromotionTTC: number;
  enStock: boolean;
  statut: 'ACTIF' | 'DESACTIVE';
  marque: ProductReference | null;
  saison: ProductReference | null;
  equipe: ProductReference | null;
};

export class CatalogueApiError extends Error {
  constructor(public readonly status: number) {
    super(
      status === 401
        ? 'Ta session a expiré. Reconnecte-toi.'
        : `Le catalogue est temporairement indisponible (${status}).`
    );
  }
}

export async function getActiveProducts(authenticatedFetch: AuthenticatedFetch) {
  const response = await authenticatedFetch(
    `${apiBaseUrl}/api/v1/products?statut=ACTIF&size=20`
  );

  if (!response.ok) {
    throw new CatalogueApiError(response.status);
  }

  return response.json() as Promise<ProductPage>;
}

export async function getProductById(
  authenticatedFetch: AuthenticatedFetch,
  productId: number
): Promise<ProductDetail | null> {
  const response = await authenticatedFetch(
    `${apiBaseUrl}/api/v1/products/${encodeURIComponent(productId)}`
  );

  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new CatalogueApiError(response.status);
  }

  return response.json() as Promise<ProductDetail>;
}
