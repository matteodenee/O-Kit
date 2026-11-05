import { useCallback, useEffect, useState } from 'react';

import type { CatalogueRequest } from './api/catalogue';
import { applicationUrl, isExclusiveSeller, keycloak } from './auth/keycloak';
import CatalogueManager from './catalogue/CatalogueManager';
import './styles.css';

const apiUrl = (import.meta.env.VITE_API_URL ?? 'http://localhost:8080').replace(/\/$/, '');

type TokenIdentity = {
  email?: string;
  name?: string;
  preferred_username?: string;
};

type AuthenticationState = {
  authenticated: boolean;
  authorized: boolean;
  error: string | null;
  identity: TokenIdentity | null;
  loading: boolean;
};

const initialState: AuthenticationState = {
  authenticated: false,
  authorized: false,
  error: null,
  identity: null,
  loading: true,
};

function currentAuthenticationState(): AuthenticationState {
  return {
    authenticated: Boolean(keycloak.authenticated),
    authorized: Boolean(keycloak.authenticated && isExclusiveSeller()),
    error: null,
    identity: (keycloak.tokenParsed as TokenIdentity | undefined) ?? null,
    loading: false,
  };
}

export default function App() {
  const [authentication, setAuthentication] =
    useState<AuthenticationState>(initialState);
  const [activeSection, setActiveSection] = useState<'overview' | 'catalogue'>('overview');

  const synchronizeAuthentication = useCallback(() => {
    setAuthentication(currentAuthenticationState());
  }, []);

  const expireSession = useCallback(() => {
    keycloak.clearToken();
    setAuthentication({
      ...initialState,
      error: 'La session vendeur a expiré. Reconnecte-toi.',
      loading: false,
    });
  }, []);

  useEffect(() => {
    let active = true;
    let refreshTimer: number | undefined;

    keycloak
      .init({
        checkLoginIframe: false,
        onLoad: 'check-sso',
        pkceMethod: 'S256',
      })
      .then(() => {
        if (!active) return;
        synchronizeAuthentication();
        refreshTimer = window.setInterval(async () => {
          if (!keycloak.authenticated) return;
          try {
            const refreshed = await keycloak.updateToken(30);
            if (refreshed && active) synchronizeAuthentication();
          } catch {
            if (active) expireSession();
          }
        }, 20_000);
      })
      .catch(() => {
        if (active) {
          setAuthentication({
            ...initialState,
            error: "Le service d'authentification est indisponible.",
            loading: false,
          });
        }
      });

    return () => {
      active = false;
      if (refreshTimer) window.clearInterval(refreshTimer);
    };
  }, [expireSession, synchronizeAuthentication]);

  const login = () =>
    keycloak.login({ redirectUri: applicationUrl() }).catch(() => {
      setAuthentication((current) => ({
        ...current,
        error: 'La connexion au backoffice a échoué.',
      }));
    });

  const logout = () =>
    keycloak.logout({ redirectUri: applicationUrl() }).catch(() => {
      keycloak.clearToken();
      setAuthentication({ ...initialState, loading: false });
    });

  const catalogueRequest = useCallback<CatalogueRequest>(async (path, init = {}) => {
    try {
      await keycloak.updateToken(30);
    } catch {
      expireSession();
      throw new Error('La session vendeur a expiré. Reconnecte-toi.');
    }

    if (!keycloak.token || !isExclusiveSeller()) {
      throw new Error('Le compte connecté ne possède pas l’autorisation vendeur requise.');
    }

    const headers = new Headers(init.headers);
    headers.set('Authorization', `Bearer ${keycloak.token}`);
    return fetch(`${apiUrl}${path}`, { ...init, headers });
  }, [expireSession]);

  if (authentication.loading) {
    return (
      <main className="centered-page" aria-busy="true">
        <div className="loader" />
        <p>Ouverture de l’espace vendeur…</p>
      </main>
    );
  }

  if (!authentication.authenticated) {
    return (
      <main className="login-page">
        <section className="brand-panel">
          <div className="brand-mark">O’kit</div>
          <p className="eyebrow">BACKOFFICE O’KIT</p>
          <h1>Le vestiaire de l’équipe.</h1>
          <p className="brand-copy">
            Un espace distinct de l’application acheteur, réservé aux employés O’kit.
          </p>
          <div className="security-note">
            <span>01</span>
            <p>Compte créé et géré par un administrateur O’kit</p>
          </div>
          <div className="security-note">
            <span>02</span>
            <p>Connexion sécurisée par Keycloak et PKCE</p>
          </div>
        </section>

        <section className="login-card" aria-labelledby="login-title">
          <p className="eyebrow">ESPACE EMPLOYÉ</p>
          <h2 id="login-title">Bienvenue au backoffice.</h2>
          <p>
            Connecte-toi avec le compte vendeur qui t’a été attribué. Il n’existe
            aucune inscription publique pour cet espace.
          </p>
          {authentication.error && (
            <p className="alert error" role="alert">{authentication.error}</p>
          )}
          <button className="primary-button" type="button" onClick={login}>
            Se connecter avec Keycloak
          </button>
          <p className="fine-print">
            Réservé aux comptes auxquels le rôle SELLER est attribué directement.
          </p>
        </section>
      </main>
    );
  }

  if (!authentication.authorized) {
    return (
      <main className="centered-page">
        <div className="denied-card">
          <p className="eyebrow">ACCÈS REFUSÉ</p>
          <h1>Ce compte n’est pas un compte vendeur.</h1>
          <p>
            Le backoffice est réservé aux employés portant directement le rôle
            SELLER sans attribution directe de BUYER. Utilise l’application acheteur
            avec un compte BUYER.
          </p>
          <button className="secondary-button" type="button" onClick={logout}>
            Se déconnecter
          </button>
        </div>
      </main>
    );
  }

  const displayName =
    authentication.identity?.name ??
    authentication.identity?.preferred_username ??
    'Employé O’kit';

  return (
    <div className="dashboard-shell">
      <aside className="sidebar">
        <div className="wordmark">O’kit</div>
        <nav aria-label="Navigation vendeur">
          <button
            className={activeSection === 'overview' ? 'active' : undefined}
            type="button"
            onClick={() => setActiveSection('overview')}
          >
            Vue d’ensemble
          </button>
          <button
            className={activeSection === 'catalogue' ? 'active' : undefined}
            type="button"
            onClick={() => setActiveSection('catalogue')}
          >
            Catalogue
          </button>
        </nav>
        <button className="logout-button" type="button" onClick={logout}>
          Déconnexion
        </button>
      </aside>
      <main className="dashboard" id={activeSection}>
        {activeSection === 'catalogue' ? (
          <CatalogueManager request={catalogueRequest} />
        ) : (
          <>
            <header className="dashboard-header">
              <div>
                <p className="eyebrow">ESPACE VENDEUR</p>
                <h1>Bonjour, {displayName}.</h1>
              </div>
              <div className="role-pill">SELLER</div>
            </header>

            <section className="status-card">
              <div>
                <p className="eyebrow">AUTHENTIFICATION ACTIVE</p>
                <h2>Ton accès vendeur est opérationnel.</h2>
                <p>
                  Keycloak a validé ton identité et ton rôle. Tu peux maintenant
                  consulter et maintenir les produits du Catalogue.
                </p>
              </div>
              <div className="checkmark" aria-hidden="true">✓</div>
            </section>

            <section className="dashboard-grid">
              <article>
                <p className="eyebrow">IDENTITÉ KEYCLOAK</p>
                <h3>{displayName}</h3>
                <p>{authentication.identity?.email ?? 'E-mail non fourni'}</p>
                <small>Aucun profil vendeur n’est dupliqué dans PostgreSQL.</small>
              </article>
              <article className="catalogue-card">
                <p className="eyebrow">CATALOGUE</p>
                <h3>Gérer les produits</h3>
                <p>Consulte, modifie, active ou désactive les produits O’kit.</p>
                <button className="inline-link" type="button" onClick={() => setActiveSection('catalogue')}>
                  Ouvrir le Catalogue →
                </button>
              </article>
            </section>
          </>
        )}
      </main>
    </div>
  );
}
