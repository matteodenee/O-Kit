import type { CatalogueRequest } from './catalogue';

export type StockStatus = 'OUT_OF_STOCK' | 'LOW_STOCK' | 'IN_STOCK';
export type StockStatusDto = { variantId: number; status: StockStatus };
export type StockDto = { variantId: number; stock: number; threshold: number; status: StockStatus };
export type NotificationDto = {
    id: number; variantId: number; type: 'LOW_STOCK' | 'OUT_OF_STOCK';
    message: string; stock: number; threshold: number; read: boolean; createdAt: string;
};
// Spring Page, différent du PageResponse du catalogue (number / last)
export type Page<T> = { content: T[]; number: number; size: number; totalElements: number; totalPages: number; last: boolean };

async function problem(response: Response): Promise<Error> {
    try {
        const p = (await response.json()) as { detail?: string };
        return new Error(p.detail ?? `Erreur HTTP ${response.status}`);
    } catch {
        return new Error(`Erreur HTTP ${response.status}`);
    }
}

async function json<T>(response: Response): Promise<T> {
    if (!response.ok) throw await problem(response);
    return response.json() as Promise<T>;
}

const jsonInit = (method: string, body: unknown): RequestInit => ({
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
});

export async function listStocks(request: CatalogueRequest) {
    // 19 produits x 8 tailles = 152 lignes : on demande une grande page
    const page = await json<Page<StockDto>>(await request('/api/v1/seller/stocks?size=500&sort=idVariante'));
    return page.content;
}

export async function createStock(request: CatalogueRequest, variantId: number, quantity: number) {
    return json<StockDto>(await request('/api/v1/seller/stocks', jsonInit('POST', { variantId, quantity })));
}

export async function restock(request: CatalogueRequest, variantId: number, quantity: number) {
    return json<StockDto>(await request(`/api/v1/seller/stocks/${variantId}/restock`, jsonInit('POST', { quantity })));
}

export async function setThreshold(request: CatalogueRequest, variantId: number, threshold: number) {
    return json<StockDto>(await request(`/api/v1/seller/stocks/${variantId}/threshold`, jsonInit('PATCH', { threshold })));
}

export async function listNotifications(request: CatalogueRequest, unreadOnly = false) {
    const page = await json<Page<NotificationDto>>(await request(`/api/v1/seller/notifications?unread=${unreadOnly}`));
    return page.content;
}

export async function getUnreadCount(request: CatalogueRequest) {
    return (await json<{ count: number }>(await request('/api/v1/seller/notifications/unread-count'))).count;
}

export async function markNotificationRead(request: CatalogueRequest, id: number) {
    const response = await request(`/api/v1/seller/notifications/${id}/read`, { method: 'POST' });
    if (!response.ok) throw await problem(response);
}
export async function adjustStock(request: CatalogueRequest, variantId: number, targetQuantity: number) {
    return json<StockDto>(
        await request(`/api/v1/seller/stocks/${variantId}/adjust`, jsonInit('POST', { targetQuantity })),
    );
}
export type MovementDto = {
    id: number;
    variantId: number;
    change: number;
    stockAfter: number;
    reason: 'INITIAL' | 'ORDER_PAID' | 'SELLER_ADJUSTMENT' | 'RESTOCK' | 'ORDER_CANCELLED';
    reference: string | null;
    createdAt: string;
};

export async function listMovements(request: CatalogueRequest, variantId: number) {
    const page = await json<Page<MovementDto>>(
        await request(`/api/v1/seller/stocks/${variantId}/movements?size=20`),
    );
    return page.content;
}

export async function createStocksBulk(request: CatalogueRequest, variantIds: number[], quantity: number) {
    return json<StockDto[]>(
        await request('/api/v1/seller/stocks/bulk', jsonInit('POST', { variantIds, quantity })),
    );
}