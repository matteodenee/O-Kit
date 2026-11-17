import { apiBaseUrl } from '../auth/config';
import type { AuthenticatedFetch } from '../auth/AuthProvider';

export type StockStatus = 'OUT_OF_STOCK' | 'LOW_STOCK' | 'IN_STOCK';
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

export async function checkStock(authenticatedFetch: AuthenticatedFetch, variantId: number, qty: number) {
    const response = await authenticatedFetch(`${apiBaseUrl}/api/v1/stocks/check?variantId=${variantId}&qty=${qty}`);
    if (!response.ok) throw new StockApiError(response.status);
    return response.json() as Promise<StockCheckDto>;
}