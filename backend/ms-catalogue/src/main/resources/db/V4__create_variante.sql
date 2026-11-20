CREATE TABLE variante (
                          id_variante BIGSERIAL PRIMARY KEY,
                          id_produit  BIGINT NOT NULL REFERENCES produit(id_produit),
                          taille      VARCHAR(5) NOT NULL
                              CHECK (taille IN ('XXS','XS','S','M','L','XL','XXL','XXXL')),
                          UNIQUE (id_produit, taille)
);
CREATE INDEX idx_variante_produit ON variante(id_produit);

-- Produits déjà en base : leur donner les 8 tailles
INSERT INTO variante (id_produit, taille)
SELECT p.id_produit, t.taille
FROM produit p
         CROSS JOIN (VALUES ('XXS'),('XS'),('S'),('M'),('L'),('XL'),('XXL'),('XXXL')) AS t(taille);