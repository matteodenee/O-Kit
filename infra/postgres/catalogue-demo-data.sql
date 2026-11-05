BEGIN;

SELECT pg_advisory_xact_lock(hashtext('okit-catalogue-demo-seed-v1'));

INSERT INTO saison (nom)
SELECT demo.nom
FROM (VALUES ('2024'), ('2025/2026')) AS demo(nom)
WHERE NOT EXISTS (
    SELECT 1 FROM saison existing WHERE existing.nom = demo.nom
);

INSERT INTO marque (nom_marque, logo_url)
SELECT demo.nom_marque, NULL
FROM (VALUES ('Adidas'), ('Kappa'), ('Nike'), ('Puma')) AS demo(nom_marque)
WHERE NOT EXISTS (
    SELECT 1 FROM marque existing WHERE existing.nom_marque = demo.nom_marque
);

INSERT INTO taux_tva (taux)
SELECT 20.00
WHERE NOT EXISTS (
    SELECT 1 FROM taux_tva existing WHERE existing.taux = 20.00
);

INSERT INTO equipe (nom, logo_url, type)
SELECT demo.nom, NULL, demo.type
FROM (
    VALUES
        ('AC Milan', 'CLUB'),
        ('Argentine', 'SELECTION'),
        ('Arsenal', 'CLUB'),
        ('Bayern Munich', 'CLUB'),
        ('Brésil', 'SELECTION'),
        ('FC Barcelone', 'CLUB'),
        ('France', 'SELECTION'),
        ('Liverpool', 'CLUB'),
        ('Manchester United', 'CLUB'),
        ('Montpellier HSC', 'CLUB'),
        ('Olympique de Marseille', 'CLUB'),
        ('Olympique Lyonnais', 'CLUB'),
        ('Paris Saint-Germain', 'CLUB'),
        ('Portugal', 'SELECTION'),
        ('Real Madrid', 'CLUB')
) AS demo(nom, type)
WHERE NOT EXISTS (
    SELECT 1 FROM equipe existing WHERE existing.nom = demo.nom
);

WITH demo_products(
    titre,
    description,
    prix_de_base,
    poids,
    couleurs_principales,
    impact_envi,
    est_flocable,
    statut,
    en_stock,
    saison_nom,
    marque_nom,
    equipe_nom
) AS (
    VALUES
        ('Maillot Montpellier HSC Domicile 2025/2026', '[DEMO O-KIT] Maillot domicile fictif du Montpellier HSC, destiné uniquement aux tests de développement.', 70.83, 0.24, 'Orange et bleu', 'Donnée environnementale non évaluée - démonstration', true, 'ACTIF', true, '2025/2026', 'Kappa', 'Montpellier HSC'),
        ('Maillot PSG Domicile 2025/2026', '[DEMO O-KIT] Maillot domicile fictif du Paris Saint-Germain, destiné uniquement aux tests de développement.', 83.33, 0.23, 'Bleu, rouge et blanc', 'Donnée environnementale non évaluée - démonstration', true, 'ACTIF', true, '2025/2026', 'Nike', 'Paris Saint-Germain'),
        ('Maillot PSG Extérieur 2025/2026', '[DEMO O-KIT] Maillot extérieur fictif du Paris Saint-Germain, destiné uniquement aux tests de développement.', 79.17, 0.23, 'Blanc et bleu', 'Donnée environnementale non évaluée - démonstration', true, 'ACTIF', true, '2025/2026', 'Nike', 'Paris Saint-Germain'),
        ('Maillot OM Domicile 2025/2026', '[DEMO O-KIT] Maillot domicile fictif de l''Olympique de Marseille, destiné uniquement aux tests de développement.', 75.00, 0.24, 'Blanc et bleu ciel', 'Donnée environnementale non évaluée - démonstration', true, 'ACTIF', true, '2025/2026', 'Puma', 'Olympique de Marseille'),
        ('Maillot OL Domicile 2025/2026', '[DEMO O-KIT] Maillot domicile fictif de l''Olympique Lyonnais, destiné uniquement aux tests de développement.', 75.00, 0.24, 'Blanc, rouge et bleu', 'Donnée environnementale non évaluée - démonstration', true, 'ACTIF', true, '2025/2026', 'Adidas', 'Olympique Lyonnais'),
        ('Maillot Real Madrid Domicile 2025/2026', '[DEMO O-KIT] Maillot domicile fictif du Real Madrid, destiné uniquement aux tests de développement.', 87.50, 0.23, 'Blanc', 'Donnée environnementale non évaluée - démonstration', true, 'ACTIF', true, '2025/2026', 'Adidas', 'Real Madrid'),
        ('Maillot Real Madrid Extérieur 2025/2026', '[DEMO O-KIT] Maillot extérieur fictif du Real Madrid, destiné uniquement aux tests de développement.', 87.50, 0.23, 'Bleu marine', 'Donnée environnementale non évaluée - démonstration', true, 'ACTIF', true, '2025/2026', 'Adidas', 'Real Madrid'),
        ('Maillot FC Barcelone Domicile 2025/2026', '[DEMO O-KIT] Maillot domicile fictif du FC Barcelone, destiné uniquement aux tests de développement.', 83.33, 0.23, 'Bleu et grenat', 'Donnée environnementale non évaluée - démonstration', true, 'ACTIF', true, '2025/2026', 'Nike', 'FC Barcelone'),
        ('Maillot Liverpool Domicile 2025/2026', '[DEMO O-KIT] Maillot domicile fictif de Liverpool, destiné uniquement aux tests de développement.', 79.17, 0.24, 'Rouge', 'Donnée environnementale non évaluée - démonstration', true, 'ACTIF', true, '2025/2026', 'Adidas', 'Liverpool'),
        ('Maillot Manchester United Domicile 2025/2026', '[DEMO O-KIT] Maillot domicile fictif de Manchester United, destiné uniquement aux tests de développement.', 79.17, 0.24, 'Rouge et blanc', 'Donnée environnementale non évaluée - démonstration', true, 'ACTIF', true, '2025/2026', 'Adidas', 'Manchester United'),
        ('Maillot Bayern Munich Domicile 2025/2026', '[DEMO O-KIT] Maillot domicile fictif du Bayern Munich, destiné uniquement aux tests de développement.', 79.17, 0.24, 'Rouge et blanc', 'Donnée environnementale non évaluée - démonstration', true, 'ACTIF', true, '2025/2026', 'Adidas', 'Bayern Munich'),
        ('Maillot France Domicile 2024', '[DEMO O-KIT] Maillot domicile fictif de la sélection française, destiné uniquement aux tests de développement.', 83.33, 0.22, 'Bleu', 'Donnée environnementale non évaluée - démonstration', true, 'ACTIF', true, '2024', 'Nike', 'France'),
        ('Maillot France Extérieur 2024', '[DEMO O-KIT] Maillot extérieur fictif de la sélection française, destiné uniquement aux tests de développement.', 83.33, 0.22, 'Blanc et bleu', 'Donnée environnementale non évaluée - démonstration', true, 'ACTIF', true, '2024', 'Nike', 'France'),
        ('Maillot Argentine Domicile 2024', '[DEMO O-KIT] Maillot domicile fictif de la sélection argentine, destiné uniquement aux tests de développement.', 79.17, 0.22, 'Bleu ciel et blanc', 'Donnée environnementale non évaluée - démonstration', true, 'ACTIF', true, '2024', 'Adidas', 'Argentine'),
        ('Maillot Brésil Domicile 2024', '[DEMO O-KIT] Maillot domicile fictif de la sélection brésilienne, destiné uniquement aux tests de développement.', 79.17, 0.22, 'Jaune, vert et bleu', 'Donnée environnementale non évaluée - démonstration', true, 'ACTIF', true, '2024', 'Nike', 'Brésil'),
        ('Maillot Portugal Domicile 2024', '[DEMO O-KIT] Maillot domicile fictif de la sélection portugaise, destiné uniquement aux tests de développement.', 79.17, 0.22, 'Rouge et vert', 'Donnée environnementale non évaluée - démonstration', true, 'ACTIF', true, '2024', 'Puma', 'Portugal'),
        ('Maillot Arsenal Domicile 2025/2026', '[DEMO O-KIT] Maillot domicile fictif d''Arsenal, destiné uniquement aux tests de développement.', 79.17, 0.24, 'Rouge et blanc', 'Donnée environnementale non évaluée - démonstration', true, 'ACTIF', false, '2025/2026', 'Adidas', 'Arsenal'),
        ('Maillot AC Milan Domicile 2025/2026', '[DEMO O-KIT] Maillot domicile fictif de l''AC Milan, destiné uniquement aux tests de développement.', 79.17, 0.24, 'Rouge et noir', 'Donnée environnementale non évaluée - démonstration', true, 'ACTIF', true, '2025/2026', 'Puma', 'AC Milan'),
        ('Maillot Montpellier HSC Archive Démo', '[DEMO O-KIT] Produit fictif désactivé servant à vérifier le filtrage du catalogue acheteur.', 50.00, 0.24, 'Orange', 'Donnée environnementale non évaluée - démonstration', false, 'DESACTIVE', false, '2024', 'Kappa', 'Montpellier HSC')
)
INSERT INTO produit (
    titre,
    description,
    prix_de_base,
    poids,
    couleurs_principales,
    impact_envi,
    est_flocable,
    statut,
    en_stock,
    id_saison,
    id_marque,
    id_taux_tva,
    id_equipe
)
SELECT
    demo.titre,
    demo.description,
    demo.prix_de_base,
    demo.poids,
    demo.couleurs_principales,
    demo.impact_envi,
    demo.est_flocable,
    demo.statut,
    demo.en_stock,
    saison.id_saison,
    marque.id_marque,
    taux_tva.id_taux_tva,
    equipe.id_equipe
FROM demo_products demo
JOIN saison ON saison.nom = demo.saison_nom
JOIN marque ON marque.nom_marque = demo.marque_nom
JOIN taux_tva ON taux_tva.taux = 20.00
JOIN equipe ON equipe.nom = demo.equipe_nom
WHERE NOT EXISTS (
    SELECT 1 FROM produit existing WHERE existing.titre = demo.titre
);

COMMIT;

SELECT
    count(*) FILTER (WHERE statut = 'ACTIF') AS demo_actifs,
    count(*) FILTER (WHERE statut = 'DESACTIVE') AS demo_desactives
FROM produit
WHERE description LIKE '[DEMO O-KIT]%';
