import type { CatalogueRequest } from './catalogue.js';

/** Miroir de StatutCommande côté ms-commande (le PANIER n'est jamais visible côté vendeur). */
export type StatutCommande = 'CONFIRMEE' | 'EN_PREPARATION' | 'EXPEDIEE' | 'LIVREE' | 'ANNULEE';

export type LigneCommande = {
    idLigneCommande: number;
    idProduit: number;
    idVariante: number;
    titreProduit: string;
    taille: string | null;
    quantite: number;
    prixUnitaireHt: number;
    tauxTva: number;
    statut: 'ACTIVE' | 'ANNULEE';
    flocage: { idFlocage: number; texte: string; numero: number; prix: number } | null;
    totalHt: number;
    totalTtc: number;
};

export type Commande = {
    idCommande: number;
    statut: StatutCommande;
    dateCommande: string | null;
    lignes: LigneCommande[];
    prixHt: number;
    fraisLivraison: number;
    totalTtc: number;
    adresseLivraison: { rue: string; complement: string | null; codePostal: string; ville: string } | null;
    codeSuiviTransporteur: string | null;
    idTransaction: string | null;
};

/** Spring Data PagedModel : { content, page: { size, number, totalElements, totalPages } }. */
export type PageCommandes = {
    content: Commande[];
    page: { size: number; number: number; totalElements: number; totalPages: number };
};

export const libelleStatut: Record<StatutCommande, string> = {
    CONFIRMEE: 'À traiter',
    EN_PREPARATION: 'En préparation',
    EXPEDIEE: 'Expédiée',
    LIVREE: 'Livrée',
    ANNULEE: 'Annulée',
};

/** Action proposée au vendeur pour chaque transition autorisée (cf. StatutCommande.peutPasserA). */
export type ActionStatut = { cible: StatutCommande; libelle: string; danger?: boolean };

export function actionsPossibles(statut: StatutCommande): ActionStatut[] {
    switch (statut) {
        case 'CONFIRMEE':
            return [
                { cible: 'EN_PREPARATION', libelle: 'Accepter et préparer' },
                { cible: 'ANNULEE', libelle: 'Annuler la commande', danger: true },
            ];
        case 'EN_PREPARATION':
            return [
                { cible: 'EXPEDIEE', libelle: 'Expédier' },
                { cible: 'ANNULEE', libelle: 'Annuler la commande', danger: true },
            ];
        case 'EXPEDIEE':
            return [{ cible: 'LIVREE', libelle: 'Marquer comme livrée' }];
        default:
            return [];
    }
}

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

export async function listerCommandes(
    request: CatalogueRequest,
    options: { statut?: StatutCommande | null; page?: number; size?: number } = {},
) {
    const params = new URLSearchParams({
        page: String(options.page ?? 0),
        size: String(options.size ?? 20),
        sort: 'dateCommande,desc',
    });
    if (options.statut) params.set('statut', options.statut);
    return json<PageCommandes>(await request(`/api/v1/seller/commandes?${params.toString()}`));
}

export async function getCommande(request: CatalogueRequest, idCommande: number) {
    return json<Commande>(await request(`/api/v1/seller/commandes/${idCommande}`));
}

export async function changerStatut(
    request: CatalogueRequest,
    idCommande: number,
    statut: StatutCommande,
    codeSuiviTransporteur?: string,
) {
    return json<Commande>(await request(`/api/v1/seller/commandes/${idCommande}/statut`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(codeSuiviTransporteur ? { statut, codeSuiviTransporteur } : { statut }),
    }));
}
