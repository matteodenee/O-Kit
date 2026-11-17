package com.okit.commande.domain.exception;

import java.util.List;

/** Le stock ne suffit pas pour une ou plusieurs tailles -> HTTP 409 avec le détail. */
public class StockInsuffisantException extends RuntimeException {

    public record Manque(Long variantId, int requested, int available) {
    }

    private final List<Manque> manques;

    public StockInsuffisantException(List<Manque> manques) {
        super("Stock insuffisant");
        this.manques = manques;
    }

    public List<Manque> getManques() {
        return manques;
    }
}
