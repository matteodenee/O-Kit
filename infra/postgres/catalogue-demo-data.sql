BEGIN;

SELECT pg_advisory_xact_lock(hashtext('okit-catalogue-demo-seed-v1'));

ALTER TABLE produit ADD COLUMN IF NOT EXISTS photo_url VARCHAR(2000);

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

CREATE TEMP TABLE demo_products (
    titre VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    prix_de_base NUMERIC(10, 2) NOT NULL,
    poids DOUBLE PRECISION NOT NULL,
    couleurs_principales VARCHAR(255) NOT NULL,
    impact_envi VARCHAR(255) NOT NULL,
    est_flocable BOOLEAN NOT NULL,
    statut VARCHAR(50) NOT NULL,
    en_stock BOOLEAN NOT NULL,
    saison_nom VARCHAR(255) NOT NULL,
    marque_nom VARCHAR(255) NOT NULL,
    equipe_nom VARCHAR(255) NOT NULL,
    photo_url VARCHAR(2000) NOT NULL
) ON COMMIT DROP;

INSERT INTO demo_products (
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
    equipe_nom,
    photo_url
)
VALUES
    (
        'Maillot Montpellier HSC Domicile 2025/2026',
        '[DEMO O-KIT] Maillot domicile fictif du Montpellier HSC, destiné uniquement aux tests de développement.',
        70.83, 0.24, 'Orange et bleu', 'Donnée environnementale non évaluée - démonstration',
        true, 'ACTIF', true, '2025/2026', 'Kappa', 'Montpellier HSC',
        'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 260"><rect width="240" height="260" rx="18" fill="%23EEF2F7"/><path d="M74 36L42 54L20 100L54 116L66 90V226H174V90L186 116L220 100L198 54L166 36C154 52 138 58 120 58C102 58 86 52 74 36Z" fill="%230B2D6B" stroke="%23071D46" stroke-width="2.5"/><rect x="66" y="106" width="108" height="32" fill="%23F36F21"/><path d="M42 54L20 100L32 106L52 64Z" fill="%23F36F21"/><path d="M198 54L220 100L208 106L188 64Z" fill="%23F36F21"/><path d="M84 40C98 58 142 58 156 40" fill="none" stroke="%23F36F21" stroke-width="6"/><circle cx="146" cy="86" r="13" fill="%23F36F21" stroke="%23FFFFFF" stroke-width="2"/><text x="146" y="90" text-anchor="middle" font-family="Arial,sans-serif" font-size="8" font-weight="bold" fill="%23FFFFFF">MHSC</text><text x="120" y="126" text-anchor="middle" font-family="Arial,sans-serif" font-size="11" font-weight="bold" fill="%23FFFFFF">MONTPELLIER</text></svg>'
    ),
    (
        'Maillot PSG Domicile 2025/2026',
        '[DEMO O-KIT] Maillot domicile fictif du Paris Saint-Germain, destiné uniquement aux tests de développement.',
        83.33, 0.23, 'Bleu, rouge et blanc', 'Donnée environnementale non évaluée - démonstration',
        true, 'ACTIF', true, '2025/2026', 'Nike', 'Paris Saint-Germain',
        'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 260"><rect width="240" height="260" rx="18" fill="%23EEF2F7"/><path d="M74 36L42 54L20 100L54 116L66 90V226H174V90L186 116L220 100L198 54L166 36C154 52 138 58 120 58C102 58 86 52 74 36Z" fill="%23004170" stroke="%23002847" stroke-width="2.5"/><rect x="103" y="57" width="34" height="169" fill="%23FFFFFF"/><rect x="107" y="57" width="26" height="169" fill="%23DA291C"/><path d="M84 40C98 58 142 58 156 40" fill="none" stroke="%23DA291C" stroke-width="5"/><circle cx="148" cy="86" r="13" fill="%23004170" stroke="%23FFFFFF" stroke-width="2"/><text x="148" y="90" text-anchor="middle" font-family="Arial,sans-serif" font-size="9" font-weight="bold" fill="%23FFFFFF">PSG</text><text x="120" y="198" text-anchor="middle" font-family="Arial,sans-serif" font-size="11" font-weight="bold" fill="%23FFFFFF">PARIS</text></svg>'
    ),
    (
        'Maillot PSG Extérieur 2025/2026',
        '[DEMO O-KIT] Maillot extérieur fictif du Paris Saint-Germain, destiné uniquement aux tests de développement.',
        79.17, 0.23, 'Blanc et bleu', 'Donnée environnementale non évaluée - démonstration',
        true, 'ACTIF', true, '2025/2026', 'Nike', 'Paris Saint-Germain',
        'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 260"><rect width="240" height="260" rx="18" fill="%23E6ECF5"/><path d="M74 36L42 54L20 100L54 116L66 90V226H174V90L186 116L220 100L198 54L166 36C154 52 138 58 120 58C102 58 86 52 74 36Z" fill="%23FFFFFF" stroke="%23CBD5E1" stroke-width="2.5"/><polygon points="120,62 134,225 106,225" fill="%23004170" opacity="0.9"/><polygon points="120,76 126,225 114,225" fill="%23DA291C"/><path d="M84 40C98 58 142 58 156 40" fill="none" stroke="%23004170" stroke-width="5"/><circle cx="148" cy="86" r="13" fill="%23004170" stroke="%23DA291C" stroke-width="2"/><text x="148" y="90" text-anchor="middle" font-family="Arial,sans-serif" font-size="9" font-weight="bold" fill="%23FFFFFF">PSG</text></svg>'
    ),
    (
        'Maillot OM Domicile 2025/2026',
        '[DEMO O-KIT] Maillot domicile fictif de l''Olympique de Marseille, destiné uniquement aux tests de développement.',
        75.00, 0.24, 'Blanc et bleu ciel', 'Donnée environnementale non évaluée - démonstration',
        true, 'ACTIF', true, '2025/2026', 'Puma', 'Olympique de Marseille',
        'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 260"><rect width="240" height="260" rx="18" fill="%23E5F3FA"/><path d="M74 36L42 54L20 100L54 116L66 90V226H174V90L186 116L220 100L198 54L166 36C154 52 138 58 120 58C102 58 86 52 74 36Z" fill="%23FFFFFF" stroke="%23009FE3" stroke-width="2.5"/><rect x="66" y="100" width="8" height="126" fill="%23009FE3"/><rect x="166" y="100" width="8" height="126" fill="%23009FE3"/><path d="M84 40C98 58 142 58 156 40" fill="none" stroke="%23009FE3" stroke-width="6"/><circle cx="148" cy="88" r="13" fill="%23FFFFFF" stroke="%23009FE3" stroke-width="2"/><text x="148" y="72" text-anchor="middle" font-family="Arial,sans-serif" font-size="10" fill="%23D4AF37">★</text><text x="148" y="92" text-anchor="middle" font-family="Arial,sans-serif" font-size="10" font-weight="bold" fill="%23009FE3">OM</text><text x="120" y="150" text-anchor="middle" font-family="Arial,sans-serif" font-size="11" font-weight="bold" fill="%23009FE3">MARSEILLE</text></svg>'
    ),
    (
        'Maillot OL Domicile 2025/2026',
        '[DEMO O-KIT] Maillot domicile fictif de l''Olympique Lyonnais, destiné uniquement aux tests de développement.',
        75.00, 0.24, 'Blanc, rouge et bleu', 'Donnée environnementale non évaluée - démonstration',
        true, 'ACTIF', true, '2025/2026', 'Adidas', 'Olympique Lyonnais',
        'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 260"><rect width="240" height="260" rx="18" fill="%23EEF2F7"/><path d="M74 36L42 54L20 100L54 116L66 90V226H174V90L186 116L220 100L198 54L166 36C154 52 138 58 120 58C102 58 86 52 74 36Z" fill="%23FFFFFF" stroke="%23CBD5E1" stroke-width="2.5"/><rect x="108" y="57" width="12" height="169" fill="%23DA291C"/><rect x="120" y="57" width="12" height="169" fill="%231D428A"/><path d="M84 40C98 58 142 58 156 40" fill="none" stroke="%231D428A" stroke-width="5"/><circle cx="150" cy="86" r="13" fill="%231D428A" stroke="%23D4AF37" stroke-width="2"/><text x="150" y="90" text-anchor="middle" font-family="Arial,sans-serif" font-size="10" font-weight="bold" fill="%23FFFFFF">OL</text></svg>'
    ),
    (
        'Maillot Real Madrid Domicile 2025/2026',
        '[DEMO O-KIT] Maillot domicile fictif du Real Madrid, destiné uniquement aux tests de développement.',
        87.50, 0.23, 'Blanc', 'Donnée environnementale non évaluée - démonstration',
        true, 'ACTIF', true, '2025/2026', 'Adidas', 'Real Madrid',
        'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 260"><rect width="240" height="260" rx="18" fill="%23F4F4F6"/><path d="M74 36L42 54L20 100L54 116L66 90V226H174V90L186 116L220 100L198 54L166 36C154 52 138 58 120 58C102 58 86 52 74 36Z" fill="%23FFFFFF" stroke="%23D1D5DB" stroke-width="2.5"/><path d="M74 36L42 54" stroke="%23FEBE10" stroke-width="5"/><path d="M166 36L198 54" stroke="%23FEBE10" stroke-width="5"/><path d="M84 40C98 58 142 58 156 40" fill="none" stroke="%2300529F" stroke-width="5"/><circle cx="148" cy="86" r="13" fill="%23FFFFFF" stroke="%23FEBE10" stroke-width="2.5"/><text x="148" y="90" text-anchor="middle" font-family="Arial,sans-serif" font-size="8" font-weight="bold" fill="%2300529F">RMA</text><text x="120" y="146" text-anchor="middle" font-family="Arial,sans-serif" font-size="10" font-weight="bold" fill="%231F2937">REAL MADRID</text></svg>'
    ),
    (
        'Maillot Real Madrid Extérieur 2025/2026',
        '[DEMO O-KIT] Maillot extérieur fictif du Real Madrid, destiné uniquement aux tests de développement.',
        87.50, 0.23, 'Bleu marine', 'Donnée environnementale non évaluée - démonstration',
        true, 'ACTIF', true, '2025/2026', 'Adidas', 'Real Madrid',
        'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 260"><rect width="240" height="260" rx="18" fill="%23E8ECF4"/><path d="M74 36L42 54L20 100L54 116L66 90V226H174V90L186 116L220 100L198 54L166 36C154 52 138 58 120 58C102 58 86 52 74 36Z" fill="%230F224A" stroke="%2309152E" stroke-width="2.5"/><path d="M74 36L42 54" stroke="%23F59E0B" stroke-width="5"/><path d="M166 36L198 54" stroke="%23F59E0B" stroke-width="5"/><path d="M84 40C98 58 142 58 156 40" fill="none" stroke="%23F59E0B" stroke-width="5"/><circle cx="148" cy="86" r="13" fill="%230F224A" stroke="%23FEBE10" stroke-width="2"/><text x="148" y="90" text-anchor="middle" font-family="Arial,sans-serif" font-size="8" font-weight="bold" fill="%23FEBE10">RMA</text><text x="120" y="146" text-anchor="middle" font-family="Arial,sans-serif" font-size="10" font-weight="bold" fill="%23FEBE10">MADRID</text></svg>'
    ),
    (
        'Maillot FC Barcelone Domicile 2025/2026',
        '[DEMO O-KIT] Maillot domicile fictif du FC Barcelone, destiné uniquement aux tests de développement.',
        83.33, 0.23, 'Bleu et grenat', 'Donnée environnementale non évaluée - démonstration',
        true, 'ACTIF', true, '2025/2026', 'Nike', 'FC Barcelone',
        'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 260"><rect width="240" height="260" rx="18" fill="%23EEF2F7"/><path d="M74 36L42 54L20 100L54 116L66 90V226H174V90L186 116L220 100L198 54L166 36C154 52 138 58 120 58C102 58 86 52 74 36Z" fill="%23004D98" stroke="%23003366" stroke-width="2.5"/><rect x="84" y="52" width="24" height="174" fill="%23A50044"/><rect x="132" y="52" width="24" height="174" fill="%23A50044"/><path d="M84 40C98 58 142 58 156 40" fill="none" stroke="%23EDBB00" stroke-width="5"/><circle cx="148" cy="86" r="13" fill="%23004D98" stroke="%23EDBB00" stroke-width="2"/><text x="148" y="90" text-anchor="middle" font-family="Arial,sans-serif" font-size="8" font-weight="bold" fill="%23EDBB00">FCB</text><text x="120" y="148" text-anchor="middle" font-family="Arial,sans-serif" font-size="11" font-weight="bold" fill="%23EDBB00">BARÇA</text></svg>'
    ),
    (
        'Maillot Liverpool Domicile 2025/2026',
        '[DEMO O-KIT] Maillot domicile fictif de Liverpool, destiné uniquement aux tests de développement.',
        79.17, 0.24, 'Rouge', 'Donnée environnementale non évaluée - démonstration',
        true, 'ACTIF', true, '2025/2026', 'Adidas', 'Liverpool',
        'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 260"><rect width="240" height="260" rx="18" fill="%23FBF0F1"/><path d="M74 36L42 54L20 100L54 116L66 90V226H174V90L186 116L220 100L198 54L166 36C154 52 138 58 120 58C102 58 86 52 74 36Z" fill="%23C8102E" stroke="%23960B22" stroke-width="2.5"/><path d="M74 36L42 54" stroke="%23FFFFFF" stroke-width="4"/><path d="M166 36L198 54" stroke="%23FFFFFF" stroke-width="4"/><path d="M84 40C98 58 142 58 156 40" fill="none" stroke="%23FFFFFF" stroke-width="5"/><circle cx="148" cy="86" r="13" fill="%23C8102E" stroke="%23F6EB61" stroke-width="2"/><text x="148" y="90" text-anchor="middle" font-family="Arial,sans-serif" font-size="8" font-weight="bold" fill="%23F6EB61">LFC</text><text x="120" y="148" text-anchor="middle" font-family="Arial,sans-serif" font-size="10" font-weight="bold" fill="%23FFFFFF">LIVERPOOL</text></svg>'
    ),
    (
        'Maillot Manchester United Domicile 2025/2026',
        '[DEMO O-KIT] Maillot domicile fictif de Manchester United, destiné uniquement aux tests de développement.',
        79.17, 0.24, 'Rouge et blanc', 'Donnée environnementale non évaluée - démonstration',
        true, 'ACTIF', true, '2025/2026', 'Adidas', 'Manchester United',
        'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 260"><rect width="240" height="260" rx="18" fill="%23FBF0F1"/><path d="M74 36L42 54L20 100L54 116L66 90V226H174V90L186 116L220 100L198 54L166 36C154 52 138 58 120 58C102 58 86 52 74 36Z" fill="%23DA291C" stroke="%23991B1B" stroke-width="2.5"/><path d="M84 40C98 58 142 58 156 40" fill="none" stroke="%23111111" stroke-width="5"/><path d="M74 36L42 54" stroke="%23FFFFFF" stroke-width="4"/><path d="M166 36L198 54" stroke="%23FFFFFF" stroke-width="4"/><circle cx="148" cy="86" r="13" fill="%23DA291C" stroke="%23FBE122" stroke-width="2"/><text x="148" y="90" text-anchor="middle" font-family="Arial,sans-serif" font-size="7.5" font-weight="bold" fill="%23FBE122">MUFC</text><text x="120" y="148" text-anchor="middle" font-family="Arial,sans-serif" font-size="10" font-weight="bold" fill="%23FFFFFF">MAN UTD</text></svg>'
    ),
    (
        'Maillot Bayern Munich Domicile 2025/2026',
        '[DEMO O-KIT] Maillot domicile fictif du Bayern Munich, destiné uniquement aux tests de développement.',
        79.17, 0.24, 'Rouge et blanc', 'Donnée environnementale non évaluée - démonstration',
        true, 'ACTIF', true, '2025/2026', 'Adidas', 'Bayern Munich',
        'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 260"><rect width="240" height="260" rx="18" fill="%23FBF0F2"/><path d="M74 36L42 54L20 100L54 116L66 90V226H174V90L186 116L220 100L198 54L166 36C154 52 138 58 120 58C102 58 86 52 74 36Z" fill="%23DC052D" stroke="%2399031F" stroke-width="2.5"/><path d="M84 40C98 58 142 58 156 40" fill="none" stroke="%23FFFFFF" stroke-width="5"/><path d="M42 54L20 100L30 105L50 62Z" fill="%23FFFFFF"/><path d="M198 54L220 100L210 105L190 62Z" fill="%23FFFFFF"/><circle cx="148" cy="86" r="13" fill="%230066B2" stroke="%23FFFFFF" stroke-width="2"/><text x="148" y="90" text-anchor="middle" font-family="Arial,sans-serif" font-size="8" font-weight="bold" fill="%23FFFFFF">FCB</text><text x="120" y="148" text-anchor="middle" font-family="Arial,sans-serif" font-size="10" font-weight="bold" fill="%23FFFFFF">BAYERN</text></svg>'
    ),
    (
        'Maillot France Domicile 2024',
        '[DEMO O-KIT] Maillot domicile fictif de la sélection française, destiné uniquement aux tests de développement.',
        83.33, 0.22, 'Bleu', 'Donnée environnementale non évaluée - démonstration',
        true, 'ACTIF', true, '2024', 'Nike', 'France',
        'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 260"><rect width="240" height="260" rx="18" fill="%23EAF0FA"/><path d="M74 36L42 54L20 100L54 116L66 90V226H174V90L186 116L220 100L198 54L166 36C154 52 138 58 120 58C102 58 86 52 74 36Z" fill="%231B3E8A" stroke="%23102657" stroke-width="2.5"/><path d="M84 40C98 58 110 58 110 58" fill="none" stroke="%231B3E8A" stroke-width="5"/><path d="M106 56C116 58 124 58 134 56" fill="none" stroke="%23FFFFFF" stroke-width="5"/><path d="M130 56C142 54 150 48 156 40" fill="none" stroke="%23ED2939" stroke-width="5"/><circle cx="148" cy="88" r="13" fill="%231B3E8A" stroke="%23D4AF37" stroke-width="2"/><text x="148" y="72" text-anchor="middle" font-family="Arial,sans-serif" font-size="9" fill="%23D4AF37">★★</text><text x="148" y="92" text-anchor="middle" font-family="Arial,sans-serif" font-size="8.5" font-weight="bold" fill="%23D4AF37">FFF</text><text x="120" y="148" text-anchor="middle" font-family="Arial,sans-serif" font-size="11" font-weight="bold" fill="%23FFFFFF">FRANCE</text></svg>'
    ),
    (
        'Maillot France Extérieur 2024',
        '[DEMO O-KIT] Maillot extérieur fictif de la sélection française, destiné uniquement aux tests de développement.',
        83.33, 0.22, 'Blanc et bleu', 'Donnée environnementale non évaluée - démonstration',
        true, 'ACTIF', true, '2024', 'Nike', 'France',
        'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 260"><rect width="240" height="260" rx="18" fill="%23EAF0FA"/><path d="M74 36L42 54L20 100L54 116L66 90V226H174V90L186 116L220 100L198 54L166 36C154 52 138 58 120 58C102 58 86 52 74 36Z" fill="%23FFFFFF" stroke="%231B3E8A" stroke-width="2.5"/><line x1="90" y1="54" x2="90" y2="225" stroke="%231B3E8A" stroke-width="2"/><line x1="110" y1="58" x2="110" y2="225" stroke="%231B3E8A" stroke-width="2"/><line x1="130" y1="58" x2="130" y2="225" stroke="%23ED2939" stroke-width="2"/><line x1="150" y1="54" x2="150" y2="225" stroke="%231B3E8A" stroke-width="2"/><circle cx="148" cy="88" r="13" fill="%231B3E8A" stroke="%23D4AF37" stroke-width="2"/><text x="148" y="92" text-anchor="middle" font-family="Arial,sans-serif" font-size="8.5" font-weight="bold" fill="%23D4AF37">FFF</text></svg>'
    ),
    (
        'Maillot Argentine Domicile 2024',
        '[DEMO O-KIT] Maillot domicile fictif de la sélection argentine, destiné uniquement aux tests de développement.',
        79.17, 0.22, 'Bleu ciel et blanc', 'Donnée environnementale non évaluée - démonstration',
        true, 'ACTIF', true, '2024', 'Adidas', 'Argentine',
        'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 260"><rect width="240" height="260" rx="18" fill="%23EAF5FB"/><path d="M74 36L42 54L20 100L54 116L66 90V226H174V90L186 116L220 100L198 54L166 36C154 52 138 58 120 58C102 58 86 52 74 36Z" fill="%23FFFFFF" stroke="%2375AADB" stroke-width="2.5"/><rect x="76" y="46" width="22" height="179" fill="%2375AADB"/><rect x="109" y="58" width="22" height="167" fill="%2375AADB"/><rect x="142" y="46" width="22" height="179" fill="%2375AADB"/><circle cx="148" cy="88" r="13" fill="%23FFFFFF" stroke="%23D4AF37" stroke-width="2"/><text x="148" y="72" text-anchor="middle" font-family="Arial,sans-serif" font-size="8" fill="%23D4AF37">★★★</text><text x="148" y="92" text-anchor="middle" font-family="Arial,sans-serif" font-size="8.5" font-weight="bold" fill="%2375AADB">AFA</text></svg>'
    ),
    (
        'Maillot Brésil Domicile 2024',
        '[DEMO O-KIT] Maillot domicile fictif de la sélection brésilienne, destiné uniquement aux tests de développement.',
        79.17, 0.22, 'Jaune, vert et bleu', 'Donnée environnementale non évaluée - démonstration',
        true, 'ACTIF', true, '2024', 'Nike', 'Brésil',
        'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 260"><rect width="240" height="260" rx="18" fill="%23F0F9F2"/><path d="M74 36L42 54L20 100L54 116L66 90V226H174V90L186 116L220 100L198 54L166 36C154 52 138 58 120 58C102 58 86 52 74 36Z" fill="%23FFDF00" stroke="%23009C3B" stroke-width="2.5"/><path d="M84 40C98 58 142 58 156 40" fill="none" stroke="%23009C3B" stroke-width="6"/><path d="M22 98L54 114" stroke="%23009C3B" stroke-width="6"/><path d="M218 98L186 114" stroke="%23009C3B" stroke-width="6"/><circle cx="148" cy="88" r="13" fill="%23002776" stroke="%23009C3B" stroke-width="2"/><text x="148" y="92" text-anchor="middle" font-family="Arial,sans-serif" font-size="8.5" font-weight="bold" fill="%23FFDF00">CBF</text><text x="120" y="148" text-anchor="middle" font-family="Arial,sans-serif" font-size="11" font-weight="bold" fill="%23009C3B">BRASIL</text></svg>'
    ),
    (
        'Maillot Portugal Domicile 2024',
        '[DEMO O-KIT] Maillot domicile fictif de la sélection portugaise, destiné uniquement aux tests de développement.',
        79.17, 0.22, 'Rouge et vert', 'Donnée environnementale non évaluée - démonstration',
        true, 'ACTIF', true, '2024', 'Puma', 'Portugal',
        'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 260"><rect width="240" height="260" rx="18" fill="%23FBF0F1"/><path d="M74 36L42 54L20 100L54 116L66 90V226H174V90L186 116L220 100L198 54L166 36C154 52 138 58 120 58C102 58 86 52 74 36Z" fill="%23C8102E" stroke="%23006600" stroke-width="2.5"/><rect x="66" y="160" width="108" height="65" fill="%23006600" opacity="0.9"/><path d="M84 40C98 58 142 58 156 40" fill="none" stroke="%23006600" stroke-width="6"/><circle cx="148" cy="88" r="13" fill="%23006600" stroke="%23FFD100" stroke-width="2"/><text x="148" y="92" text-anchor="middle" font-family="Arial,sans-serif" font-size="8.5" font-weight="bold" fill="%23FFD100">FPF</text><text x="120" y="140" text-anchor="middle" font-family="Arial,sans-serif" font-size="10" font-weight="bold" fill="%23FFFFFF">PORTUGAL</text></svg>'
    ),
    (
        'Maillot Arsenal Domicile 2025/2026',
        '[DEMO O-KIT] Maillot domicile fictif d''Arsenal, destiné uniquement aux tests de développement.',
        79.17, 0.24, 'Rouge et blanc', 'Donnée environnementale non évaluée - démonstration',
        true, 'ACTIF', false, '2025/2026', 'Adidas', 'Arsenal',
        'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 260"><rect width="240" height="260" rx="18" fill="%23FBF0F0"/><path d="M74 36L66 90V226H174V90L166 36C154 52 138 58 120 58C102 58 86 52 74 36Z" fill="%23EF0107" stroke="%239C0004" stroke-width="2.5"/><path d="M74 36L42 54L20 100L54 116L66 90Z" fill="%23FFFFFF" stroke="%23CBD5E1" stroke-width="2"/><path d="M166 36L198 54L220 100L186 116L174 90Z" fill="%23FFFFFF" stroke="%23CBD5E1" stroke-width="2"/><path d="M84 40C98 58 142 58 156 40" fill="none" stroke="%23063672" stroke-width="5"/><circle cx="148" cy="86" r="13" fill="%23EF0107" stroke="%23FFFFFF" stroke-width="2"/><text x="148" y="90" text-anchor="middle" font-family="Arial,sans-serif" font-size="8.5" font-weight="bold" fill="%23FFFFFF">AFC</text><text x="120" y="148" text-anchor="middle" font-family="Arial,sans-serif" font-size="10" font-weight="bold" fill="%23FFFFFF">ARSENAL</text></svg>'
    ),
    (
        'Maillot AC Milan Domicile 2025/2026',
        '[DEMO O-KIT] Maillot domicile fictif de l''AC Milan, destiné uniquement aux tests de développement.',
        79.17, 0.24, 'Rouge et noir', 'Donnée environnementale non évaluée - démonstration',
        true, 'ACTIF', true, '2025/2026', 'Puma', 'AC Milan',
        'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 260"><rect width="240" height="260" rx="18" fill="%23F3F4F6"/><path d="M74 36L42 54L20 100L54 116L66 90V226H174V90L186 116L220 100L198 54L166 36C154 52 138 58 120 58C102 58 86 52 74 36Z" fill="%23FB090B" stroke="%23111111" stroke-width="2.5"/><rect x="76" y="46" width="22" height="179" fill="%23151515"/><rect x="109" y="58" width="22" height="167" fill="%23151515"/><rect x="142" y="46" width="22" height="179" fill="%23151515"/><circle cx="148" cy="86" r="13" fill="%23FFFFFF" stroke="%23FB090B" stroke-width="2"/><text x="148" y="90" text-anchor="middle" font-family="Arial,sans-serif" font-size="8" font-weight="bold" fill="%23151515">ACM</text></svg>'
    ),
    (
        'Maillot Montpellier HSC Archive Démo',
        '[DEMO O-KIT] Produit fictif désactivé servant à vérifier le filtrage du catalogue acheteur.',
        50.00, 0.24, 'Orange', 'Donnée environnementale non évaluée - démonstration',
        false, 'DESACTIVE', false, '2024', 'Kappa', 'Montpellier HSC',
        'data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 240 260"><rect width="240" height="260" rx="18" fill="%23FFF4ED"/><path d="M74 36L42 54L20 100L54 116L66 90V226H174V90L186 116L220 100L198 54L166 36C154 52 138 58 120 58C102 58 86 52 74 36Z" fill="%23F36F21" stroke="%230B2D6B" stroke-width="2.5"/><path d="M84 40C98 58 142 58 156 40" fill="none" stroke="%230B2D6B" stroke-width="6"/><circle cx="146" cy="86" r="13" fill="%230B2D6B" stroke="%23FFFFFF" stroke-width="2"/><text x="146" y="90" text-anchor="middle" font-family="Arial,sans-serif" font-size="8" font-weight="bold" fill="%23FFFFFF">MHSC</text><text x="120" y="148" text-anchor="middle" font-family="Arial,sans-serif" font-size="10" font-weight="bold" fill="%230B2D6B">ARCHIVE</text></svg>'
    );

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
    id_equipe,
    photo_url
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
    equipe.id_equipe,
    demo.photo_url
FROM demo_products demo
JOIN saison ON saison.nom = demo.saison_nom
JOIN marque ON marque.nom_marque = demo.marque_nom
JOIN taux_tva ON taux_tva.taux = 20.00
JOIN equipe ON equipe.nom = demo.equipe_nom
WHERE NOT EXISTS (
    SELECT 1 FROM produit existing WHERE existing.titre = demo.titre
);

UPDATE produit
SET photo_url = demo.photo_url
FROM demo_products demo
WHERE produit.titre = demo.titre
  AND (produit.photo_url IS NULL OR produit.photo_url = '');

CREATE TABLE IF NOT EXISTS variante (
    id_variante BIGSERIAL PRIMARY KEY,
    id_produit  BIGINT NOT NULL REFERENCES produit(id_produit),
    taille      VARCHAR(5) NOT NULL
        CHECK (taille IN ('XXS','XS','S','M','L','XL','XXL','XXXL')),
    UNIQUE (id_produit, taille)
);
CREATE INDEX IF NOT EXISTS idx_variante_produit ON variante(id_produit);

INSERT INTO variante (id_produit, taille)
SELECT p.id_produit, t.taille
FROM produit p
CROSS JOIN (VALUES ('XXS'),('XS'),('S'),('M'),('L'),('XL'),('XXL'),('XXXL')) AS t(taille)
WHERE NOT EXISTS (
    SELECT 1
    FROM variante v
    WHERE v.id_produit = p.id_produit
      AND v.taille = t.taille
);

COMMIT;

SELECT
    count(*) FILTER (WHERE statut = 'ACTIF') AS demo_actifs,
    count(*) FILTER (WHERE statut = 'DESACTIVE') AS demo_desactives
FROM produit
WHERE description LIKE '[DEMO O-KIT]%';
