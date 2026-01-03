package com.codelsur.percheo;

/** POJO simple con los datos que “scrapeamos” del WebView. */
public class Product {
    public final String code;
    public final String description;
    public final String pvp;

    public Product(String code, String description, String pvp) {
        this.code = code;
        this.description = description;
        this.pvp = pvp;
    }

    public boolean isValid() {
        return code != null && !code.trim().isEmpty()
                && description != null && !description.trim().isEmpty()
                && pvp != null && !pvp.trim().isEmpty();
    }
}
