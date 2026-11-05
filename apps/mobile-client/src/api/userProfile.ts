import { apiBaseUrl } from '../auth/config';
import type { AuthenticatedFetch } from '../auth/AuthProvider';

export type UserProfile = {
  id: string;
  subject: string;
  email: string | null;
  firstName: string;
  lastName: string;
  phoneNumber: string;
  birthDate: string | null;
  createdAt: string;
  updatedAt: string;
};

export type UserProfileInput = Pick<
  UserProfile,
  'firstName' | 'lastName' | 'phoneNumber' | 'birthDate'
>;

export class UserProfileApiError extends Error {
  constructor(
    public readonly status: number,
    message: string
  ) {
    super(message);
  }
}

async function parseProfileResponse(response: Response): Promise<UserProfile> {
  if (!response.ok) {
    throw new UserProfileApiError(
      response.status,
      response.status === 401
        ? 'Ta session a expiré. Reconnecte-toi.'
        : `Le profil n'a pas pu être enregistré (${response.status}).`
    );
  }
  return response.json() as Promise<UserProfile>;
}

export async function getCurrentUserProfile(authenticatedFetch: AuthenticatedFetch) {
  const response = await authenticatedFetch(`${apiBaseUrl}/api/v1/users/me`);

  if (response.status === 404) {
    return null;
  }
  return parseProfileResponse(response);
}

export async function saveCurrentUserProfile(
  authenticatedFetch: AuthenticatedFetch,
  profile: UserProfileInput
) {
  const response = await authenticatedFetch(`${apiBaseUrl}/api/v1/users/me`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(profile),
  });

  return parseProfileResponse(response);
}
