import { apiBaseUrl } from '../auth/config';
import type { AuthenticatedFetch } from '../auth/AuthProvider';

export type StockStatusType = 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK';

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

export type VariantStockStatus = {
  variantId: number;
  status: StockStatusType;
  availableQuantity?: number;
};

export async function getStockStatuses(
    authenticatedFetch: AuthenticatedFetch,
    variantIds: number[]
): Promise<VariantStockStatus[]> {
  if (variantIds.length === 0) return [];
  const query = variantIds.map(id => `variantIds=${id}`).join('&');
  const response = await authenticatedFetch(`${apiBaseUrl}/api/v1/stocks?${query}`);
  if (!response.ok) throw new Error(`Erreur stock: ${response.status}`);
  return response.json() as Promise<VariantStockStatus[]>;
}