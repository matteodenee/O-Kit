import { apiBaseUrl } from '../auth/config';
import type { AuthenticatedFetch } from '../auth/AuthProvider';

export type StockStatus = 'OUT_OF_STOCK' | 'LOW_STOCK' | 'IN_STOCK';
export type ProductStockLevel = StockStatus;
export type StockStatusDto = { variantId: number; status: StockStatus };
export type StockCheckDto = { variantId: number; available: boolean; status: StockStatus };

export class StockApiError extends Error {
    constructor(public readonly status: number) {
        super(status === 401 ? 'Ta session a expiré. Reconnecte-toi.' : `Le stock est indisponible (${status}).`);
    }
}

export async function getStockStatuses(authenticatedFetch: AuthenticatedFetch, variantIds: number[]) {
    if (variantIds.length === 0) return [];
    const response = await authenticatedFetch(`${apiBaseUrl}/api/v1/stocks?variantIds=${variantIds.join(',')}`);
    if (!response.ok) throw new StockApiError(response.status);
    return response.json() as Promise<StockStatusDto[]>;
}

export async function getStockStatusMap(authenticatedFetch: AuthenticatedFetch, variantIds: number[]) {
    const statuses = await getStockStatuses(authenticatedFetch, variantIds);
    return new Map(statuses.map((status) => [status.variantId, status.status]));
}

/**
 * Niveau de disponibilité d'un produit calculé à partir de ses tailles :
 * une taille pleine rend le produit disponible, sinon on retombe sur les
 * derniers exemplaires, puis sur la rupture.
 */
export function aggregateProductStockLevel(
    varianteIds: number[],
    statuses: Map<number, StockStatus>
): ProductStockLevel {
    if (varianteIds.length === 0) return 'OUT_OF_STOCK';
    let hasLowStock = false;
    for (const id of varianteIds) {
        const status = statuses.get(id) ?? 'OUT_OF_STOCK';
        if (status === 'IN_STOCK') return 'IN_STOCK';
        if (status === 'LOW_STOCK') hasLowStock = true;
    }
    return hasLowStock ? 'LOW_STOCK' : 'OUT_OF_STOCK';
}

export async function checkStock(authenticatedFetch: AuthenticatedFetch, variantId: number, qty: number) {
    const response = await authenticatedFetch(`${apiBaseUrl}/api/v1/stocks/check?variantId=${variantId}&qty=${qty}`);
    if (!response.ok) throw new StockApiError(response.status);
    return response.json() as Promise<StockCheckDto>;
}