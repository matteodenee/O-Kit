import { apiBaseUrl } from '../auth/config';
import type { AuthenticatedFetch } from '../auth/AuthProvider';

/** Doit rester ≤ `okit.chatbot.historique-max` côté ms-chatbot. */
export const HISTORIQUE_MAX = 10;
/** Doit correspondre à `okit.chatbot.message-max` côté ms-chatbot. */
export const MESSAGE_MAX = 1000;

export type RoleMessage = 'UTILISATEUR' | 'ASSISTANT';

export type MessageChat = { role: RoleMessage; contenu: string };

export class ChatApiError extends Error {
  constructor(
    public readonly status: number,
    message: string
  ) {
    super(message);
  }
}

function messageErreur(status: number, detail: string | undefined) {
  if (status === 401) return 'Ta session a expiré. Reconnecte-toi.';
  if (status === 429) return detail ?? 'Tu as atteint la limite de questions pour aujourd’hui. Réessaie demain.';
  if (status >= 500) return 'L’assistant est momentanément indisponible. Réessaie dans un instant.';
  return detail ?? `L’assistant n’a pas pu répondre (${status}).`;
}

/**
 * Envoie une question à l'assistant O'Kit. Le service est sans état :
 * on lui renvoie les derniers messages pour qu'il garde le fil de la conversation.
 */
export async function envoyerQuestion(
  authenticatedFetch: AuthenticatedFetch,
  message: string,
  historique: MessageChat[]
): Promise<string> {
  const response = await authenticatedFetch(`${apiBaseUrl}/api/v1/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, historique: historique.slice(-HISTORIQUE_MAX) }),
  });
  if (!response.ok) {
    let detail: string | undefined;
    try {
      detail = ((await response.json()) as { detail?: string }).detail;
    } catch {
      // Réponse sans Problem Details : message générique.
    }
    throw new ChatApiError(response.status, messageErreur(response.status, detail));
  }
  const { reponse } = (await response.json()) as { reponse: string };
  return reponse;
}
