export type ProductStatus = 'ACTIF' | 'DESACTIVE';

export type ProductSummary = {
  id: number;
  titre: string;
  prixHT: number;
  prixTTC: number;
  enStock: boolean;
  nomMarque: string | null;
  nomSaison: string | null;
  nomEquipe: string | null;
  statut: ProductStatus;
};

export type ProductDetail = {
  id: number;
  titre: string;
  description: string | null;
  prixDeBaseHT: number;
  prixTTC: number;
  prixApresPromotionTTC: number;
  poids: number | null;
  couleursPrincipales: string | null;
  impactEnvi: string | null;
  estFlocable: boolean | null;
  enStock: boolean;
  statut: ProductStatus;
  marque: { idMarque: number; nomMarque: string; logoUrl: string | null } | null;
  saison: { idSaison: number; nom: string } | null;
  equipe: { idEquipe: number; nom: string; logoUrl: string | null; type: string } | null;
  tauxTva: number;
  promotionActive: unknown | null;
};

export type ProductUpdate = {
  titre: string;
  description: string;
  prixDeBase: number;
  poids?: number;
  couleursPrincipales: string;
  impactEnvi: string;
  estFlocable: boolean;
  statut: ProductStatus;
};

type ProductPage = {
  content: ProductSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  isLast: boolean;
};

export type CatalogueRequest = (path: string, init?: RequestInit) => Promise<Response>;

async function responseError(response: Response) {
  try {
    const payload = await response.json() as {
      fieldErrors?: Record<string, string>;
      message?: string;
    };
    const fieldMessage = payload.fieldErrors
      ? Object.values(payload.fieldErrors).join(' ')
      : null;
    return fieldMessage || payload.message || `Erreur HTTP ${response.status}`;
  } catch {
    return `Erreur HTTP ${response.status}`;
  }
}

async function jsonResponse<T>(response: Response): Promise<T> {
  if (!response.ok) throw new Error(await responseError(response));
  return response.json() as Promise<T>;
}

export async function listAllProducts(request: CatalogueRequest) {
  const products: ProductSummary[] = [];
  let page = 0;
  let last = false;

  while (!last) {
    const response = await request(`/api/v1/products?page=${page}&size=100`);
    const result = await jsonResponse<ProductPage>(response);
    products.push(...result.content);
    last = result.isLast || page + 1 >= result.totalPages;
    page += 1;
  }

  return products;
}

export async function getProduct(request: CatalogueRequest, id: number) {
  const response = await request(`/api/v1/products/${id}`);
  return jsonResponse<ProductDetail>(response);
}

export async function updateProduct(
  request: CatalogueRequest,
  id: number,
  update: ProductUpdate,
) {
  const response = await request(`/api/v1/products/${id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(update),
  });
  return jsonResponse<ProductDetail>(response);
}

export async function updateProductStock(
  request: CatalogueRequest,
  id: number,
  enStock: boolean,
) {
  const response = await request(`/api/v1/products/${id}/stock-status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enStock }),
  });
  if (!response.ok) throw new Error(await responseError(response));
}
