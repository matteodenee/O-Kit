import { apiBaseUrl } from '../auth/config';
import type { AuthenticatedFetch } from '../auth/AuthProvider';

export type UserAddress = {
  id: string;
  label: string | null;
  recipientFirstName: string;
  recipientLastName: string;
  phoneNumber: string;
  addressLine1: string;
  addressLine2: string | null;
  city: string;
  region: string | null;
  postalCode: string | null;
  countryCode: string;
  defaultAddress: boolean;
  createdAt: string;
  updatedAt: string;
};

export type UserAddressInput = Pick<
  UserAddress,
  | 'label'
  | 'recipientFirstName'
  | 'recipientLastName'
  | 'phoneNumber'
  | 'addressLine1'
  | 'addressLine2'
  | 'city'
  | 'region'
  | 'postalCode'
  | 'countryCode'
>;

type Problem = {
  detail?: string;
  type?: string;
};

export class UserAddressApiError extends Error {
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
  return new UserAddressApiError(
    response.status,
    problem?.type ?? null,
    problem?.detail ?? fallback
  );
}

async function parseAddress(response: Response): Promise<UserAddress> {
  if (!response.ok) {
    throw await toApiError(response, `L'adresse n'a pas pu être enregistrée (${response.status}).`);
  }
  return response.json() as Promise<UserAddress>;
}

export async function listUserAddresses(authenticatedFetch: AuthenticatedFetch) {
  const response = await authenticatedFetch(`${apiBaseUrl}/api/users/me/addresses`);
  if (!response.ok) {
    throw await toApiError(response, `Les adresses sont indisponibles (${response.status}).`);
  }
  return response.json() as Promise<UserAddress[]>;
}

export async function createUserAddress(
  authenticatedFetch: AuthenticatedFetch,
  input: UserAddressInput
) {
  const response = await authenticatedFetch(`${apiBaseUrl}/api/users/me/addresses`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });
  return parseAddress(response);
}

export async function updateUserAddress(
  authenticatedFetch: AuthenticatedFetch,
  addressId: string,
  input: UserAddressInput
) {
  const response = await authenticatedFetch(
    `${apiBaseUrl}/api/users/me/addresses/${encodeURIComponent(addressId)}`,
    {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(input),
    }
  );
  return parseAddress(response);
}

export async function setDefaultUserAddress(
  authenticatedFetch: AuthenticatedFetch,
  addressId: string
) {
  const response = await authenticatedFetch(
    `${apiBaseUrl}/api/users/me/addresses/${encodeURIComponent(addressId)}/default`,
    { method: 'PUT' }
  );
  if (!response.ok) {
    throw await toApiError(response, `L'adresse par défaut n'a pas pu être modifiée (${response.status}).`);
  }
}

export async function archiveUserAddress(
  authenticatedFetch: AuthenticatedFetch,
  addressId: string
) {
  const response = await authenticatedFetch(
    `${apiBaseUrl}/api/users/me/addresses/${encodeURIComponent(addressId)}`,
    { method: 'DELETE' }
  );
  if (!response.ok) {
    throw await toApiError(response, `L'adresse n'a pas pu être masquée (${response.status}).`);
  }
}
