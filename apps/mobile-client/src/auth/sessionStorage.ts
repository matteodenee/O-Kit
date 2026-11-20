import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';

export type StoredSession = {
  accessToken: string;
  refreshToken?: string;
  idToken?: string;
  issuedAt: number;
  expiresIn?: number;
};

const sessionKey = 'okit.auth.session';

type WebSessionStorage = Pick<Storage, 'getItem' | 'removeItem' | 'setItem'>;

function getWebSessionStorage(): WebSessionStorage | null {
  if (Platform.OS !== 'web' || typeof globalThis.sessionStorage === 'undefined') {
    return null;
  }
  return globalThis.sessionStorage;
}

export function isSessionValid(session: StoredSession) {
  if (!session.expiresIn) {
    return true;
  }

  const expiresAt = (session.issuedAt + session.expiresIn) * 1000;
  return expiresAt > Date.now() + 30_000;
}

export async function loadStoredSession(): Promise<StoredSession | null> {
  const webStorage = getWebSessionStorage();
  if (Platform.OS === 'web' && !webStorage) {
    return null;
  }
  const serialized = webStorage
    ? webStorage.getItem(sessionKey)
    : await SecureStore.getItemAsync(sessionKey);
  if (!serialized) {
    return null;
  }

  try {
    const session = JSON.parse(serialized) as StoredSession;
    if (
      !session.accessToken ||
      !session.issuedAt ||
      (!isSessionValid(session) && !session.refreshToken)
    ) {
      await clearStoredSession();
      return null;
    }
    return session;
  } catch {
    await clearStoredSession();
    return null;
  }
}

export async function saveStoredSession(session: StoredSession) {
  const serialized = JSON.stringify(session);
  const webStorage = getWebSessionStorage();
  if (webStorage) {
    webStorage.setItem(sessionKey, serialized);
    return;
  }
  if (Platform.OS === 'web') {
    return;
  }
  if (await SecureStore.isAvailableAsync()) {
    await SecureStore.setItemAsync(sessionKey, serialized);
  }
}

export async function clearStoredSession() {
  const webStorage = getWebSessionStorage();
  if (webStorage) {
    webStorage.removeItem(sessionKey);
    return;
  }
  if (Platform.OS === 'web') {
    return;
  }
  if (await SecureStore.isAvailableAsync()) {
    await SecureStore.deleteItemAsync(sessionKey);
  }
}
