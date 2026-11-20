-- ms-commande : Commande, Ligne_Commande, Flocage_Personnalise
-- Le panier n'a pas de table dédiée : c'est une commande au statut 'PANIER'.
-- id_utilisateur, id_produit, id_variante sont des identifiants d'autres microservices :
-- AUCUNE clé étrangère inter-service.

CREATE TABLE commandes (
    id_commande             BIGSERIAL PRIMARY KEY,
    id_utilisateur          VARCHAR(64)    NOT NULL,          -- "sub" du JWT Keycloak
    statut                  VARCHAR(20)    NOT NULL,
    date_commande           TIMESTAMPTZ,                      -- NULL tant que c'est un panier
    prix_ht                 NUMERIC(10, 2) NOT NULL DEFAULT 0,
    frais_livraison         NUMERIC(10, 2) NOT NULL DEFAULT 0,
    code_suivi_transporteur VARCHAR(100),
    id_transaction          VARCHAR(100),                     -- renvoyé par ms-paiement
    reference_stock         VARCHAR(80),                      -- "orderId" envoyé à ms-stock (clé d'idempotence)
    -- Adresse de livraison copiée au moment de la commande (snapshot)
    livraison_rue           VARCHAR(255),
    livraison_complement    VARCHAR(255),
    livraison_code_postal   VARCHAR(10),
    livraison_ville         VARCHAR(100),
    date_creation           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    date_modification       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version                 BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_commandes_statut CHECK (statut IN
        ('PANIER', 'CONFIRMEE', 'EN_PREPARATION', 'EXPEDIEE', 'LIVREE', 'ANNULEE'))
);

-- Un seul panier par utilisateur
CREATE UNIQUE INDEX ux_commandes_panier_par_utilisateur
    ON commandes (id_utilisateur) WHERE statut = 'PANIER';
CREATE INDEX ix_commandes_utilisateur ON commandes (id_utilisateur, date_commande DESC);

CREATE TABLE lignes_commande (
    id_ligne_commande  BIGSERIAL PRIMARY KEY,
    id_commande        BIGINT         NOT NULL REFERENCES commandes (id_commande) ON DELETE CASCADE,
    id_produit         BIGINT         NOT NULL,
    id_variante        BIGINT         NOT NULL,
    titre_produit      VARCHAR(255)   NOT NULL,   -- snapshot pour l'historique
    taille             VARCHAR(10),               -- snapshot pour l'historique
    quantite           INT            NOT NULL CHECK (quantite > 0),
    prix_unitaire_ht   NUMERIC(10, 2) NOT NULL,   -- prix au moment de l'achat (il peut changer après)
    taux_tva           NUMERIC(5, 2)  NOT NULL,   -- en pourcentage, ex. 20.00
    statut             VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT ck_lignes_statut CHECK (statut IN ('ACTIVE', 'ANNULEE'))
);
CREATE INDEX ix_lignes_commande ON lignes_commande (id_commande);

CREATE TABLE flocages (
    id_flocage         BIGSERIAL PRIMARY KEY,
    id_ligne_commande  BIGINT         NOT NULL UNIQUE
                       REFERENCES lignes_commande (id_ligne_commande) ON DELETE CASCADE,
    texte              VARCHAR(30)    NOT NULL,
    numero             INT            NOT NULL CHECK (numero BETWEEN 0 AND 99),
    prix               NUMERIC(10, 2) NOT NULL    -- HT, figé au moment de l'ajout
);
