import { apiBaseUrl } from '../auth/config';
import type { AuthenticatedFetch } from '../auth/AuthProvider';

/** Doit correspondre à `okit.commande.prix-flocage-ht` côté ms-commande (affichage seulement). */
export const FLOCAGE_PRIX_HT = 12.5;
export const FLOCAGE_TEXTE_MAX = 15;
export const QUANTITE_MAX = 10;

export type StatutCommande =
  | 'PANIER'
  | 'CONFIRMEE'
  | 'EN_PREPARATION'
  | 'EXPEDIEE'
  | 'LIVREE'
  | 'ANNULEE';

export type Flocage = { idFlocage: number; texte: string; numero: number; prix: number };

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
  flocage: Flocage | null;
  totalHt: number;
  totalTtc: number;
};

export type AdresseLivraison = {
  rue: string;
  complement: string | null;
  codePostal: string;
  ville: string;
};

/** Un panier et une commande ont la même forme côté API. */
export type Commande = {
  idCommande: number;
  statut: StatutCommande;
  dateCommande: string | null;
  lignes: LigneCommande[];
  prixHt: number;
  fraisLivraison: number;
  totalTtc: number;
  adresseLivraison: AdresseLivraison | null;
  codeSuiviTransporteur: string | null;
  idTransaction: string | null;
};

export type AjoutLigneInput = {
  idProduit: number;
  idVariante: number;
  quantite: number;
  flocage?: { texte: string; numero: number };
};

export type Manque = { variantId: number; requested: number; available: number };

type Problem = { detail?: string; manques?: Manque[] };

export class CommandeApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
    public readonly manques: Manque[] = []
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
  if (response.status === 401) {
    return new CommandeApiError(401, 'Ta session a expiré. Reconnecte-toi.');
  }
  return new CommandeApiError(response.status, problem?.detail ?? fallback, problem?.manques ?? []);
}

async function send<T>(
  authenticatedFetch: AuthenticatedFetch,
  path: string,
  fallback: string,
  init?: { method: string; body?: unknown }
): Promise<T> {
  const response = await authenticatedFetch(`${apiBaseUrl}${path}`, {
    method: init?.method ?? 'GET',
    headers: init?.body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: init?.body !== undefined ? JSON.stringify(init.body) : undefined,
  });
  if (!response.ok) {
    throw await toApiError(response, `${fallback} (${response.status}).`);
  }
  return response.json() as Promise<T>;
}

// ------------------------------------------------------------------ panier

export function getPanier(authenticatedFetch: AuthenticatedFetch) {
  return send<Commande>(authenticatedFetch, '/api/v1/panier', 'Le panier est indisponible');
}

export function ajouterAuPanier(authenticatedFetch: AuthenticatedFetch, input: AjoutLigneInput) {
  return send<Commande>(authenticatedFetch, '/api/v1/panier/lignes', "L'article n'a pas pu être ajouté", {
    method: 'POST',
    body: input,
  });
}

export function modifierQuantite(
  authenticatedFetch: AuthenticatedFetch,
  idLigne: number,
  quantite: number
) {
  return send<Commande>(authenticatedFetch, `/api/v1/panier/lignes/${idLigne}`, 'La quantité n’a pas pu être modifiée', {
    method: 'PATCH',
    body: { quantite },
  });
}

export function supprimerLigne(authenticatedFetch: AuthenticatedFetch, idLigne: number) {
  return send<Commande>(authenticatedFetch, `/api/v1/panier/lignes/${idLigne}`, "L'article n'a pas pu être retiré", {
    method: 'DELETE',
  });
}

// ------------------------------------------------------------------ commandes

export function validerCommande(authenticatedFetch: AuthenticatedFetch, adresseLivraison: AdresseLivraison) {
  return send<Commande>(authenticatedFetch, '/api/v1/commandes', "La commande n'a pas pu être validée", {
    method: 'POST',
    body: { adresseLivraison },
  });
}

export function getCommandes(authenticatedFetch: AuthenticatedFetch) {
  return send<Commande[]>(authenticatedFetch, '/api/v1/commandes', 'Tes commandes sont indisponibles');
}

export function getCommande(authenticatedFetch: AuthenticatedFetch, idCommande: number) {
  return send<Commande>(authenticatedFetch, `/api/v1/commandes/${idCommande}`, 'Cette commande est indisponible');
}

export function annulerCommande(authenticatedFetch: AuthenticatedFetch, idCommande: number) {
  return send<Commande>(authenticatedFetch, `/api/v1/commandes/${idCommande}/annulation`, "La commande n'a pas pu être annulée", {
    method: 'POST',
  });
}

export function annulerLigne(authenticatedFetch: AuthenticatedFetch, idCommande: number, idLigne: number) {
  return send<Commande>(
    authenticatedFetch,
    `/api/v1/commandes/${idCommande}/lignes/${idLigne}/annulation`,
    "L'article n'a pas pu être annulé",
    { method: 'POST' }
  );
}
