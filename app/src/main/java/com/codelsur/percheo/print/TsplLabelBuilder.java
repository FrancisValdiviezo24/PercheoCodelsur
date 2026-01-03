package com.codelsur.percheo.print;

import com.codelsur.percheo.Product;

import java.text.Normalizer;

public class TsplLabelBuilder {

    // 203 dpi típico => ~8 dots por mm (si tu impresora es 300dpi, esto cambia)
    private static final int MM_TO_DOTS = 8;

    private static final int WIDTH_MM  = 80;  // 8cm ancho
    private static final int HEIGHT_MM = 30;  // 3cm avance

    // Ajusta según tu etiqueta (altura real de la marca negra)
    private static final int BLACK_MARK_HEIGHT_MM = 3;
    private static final int BLACK_MARK_OFFSET_MM = 0;

    public static String build(Product p) {
        String code  = safe(p.code);
        String desc  = safe(p.description);
        String price = safe(p.pvp);
        if (!price.startsWith("$")) price = "$" + price;

        desc  = toAsciiSafe(desc);
        price = toAsciiSafe(price);
        code  = toAsciiSafe(code);

        if (desc.length() > 42) desc = desc.substring(0, 39) + "...";

        int widthDots  = WIDTH_MM * MM_TO_DOTS;
        int heightDots = HEIGHT_MM * MM_TO_DOTS;
        int markHeightDots = BLACK_MARK_HEIGHT_MM * MM_TO_DOTS;

        int x = 20;
        int yDesc  = 38;
        int xPrice = 55;
        int yPrice = 98;
        int priceXMul = 3;
        int priceYMul = 3;
        int yCode  = 195;
        int qrSize = 5;
        int qrY = 78;
        int qrRightMargin = 10;
        int qrX = widthDots - 190 - qrRightMargin;

        String CRLF = "\r\n";
        StringBuilder sb = new StringBuilder();

        // ===== CONFIGURACIÓN =====
        sb.append("SIZE ").append(WIDTH_MM).append(" mm,").append(HEIGHT_MM).append(" mm").append(CRLF);
        sb.append("GAP ").append(markHeightDots).append(" mm,0").append(CRLF);

        // CRÍTICO: Definir altura exacta del label
        sb.append("HEIGHT ").append(heightDots).append(CRLF);

        sb.append("DIRECTION 1,0").append(CRLF);
        sb.append("REFERENCE 0,0").append(CRLF);
        sb.append("OFFSET 0 mm").append(CRLF);
        sb.append("SPEED 3").append(CRLF);
        sb.append("DENSITY 8").append(CRLF);

        // IMPORTANTE: Configurar el modo de salida de etiqueta
        sb.append("SET TEAR ON").append(CRLF);      // Modo tear-off (arrancar manual)
        sb.append("SET PEEL OFF").append(CRLF);     // NO usar modo peel
        sb.append("SET CUTTER OFF").append(CRLF);   // NO usar cortador

        sb.append("CLS").append(CRLF);

        // CONTENIDO
        sb.append("TEXT ").append(x).append(",").append(yDesc)
                .append(",\"3\",0,1,1,\"").append(escape(desc)).append("\"").append(CRLF);

        sb.append("TEXT ").append(xPrice).append(",").append(yPrice)
                .append(",\"3\",0,").append(priceXMul).append(",").append(priceYMul)
                .append(",\"").append(escape(price)).append("\"").append(CRLF);

        sb.append("TEXT ").append(x).append(",").append(yCode)
                .append(",\"3\",0,1,1,\"COD: ").append(escape(code)).append("\"").append(CRLF);

        sb.append("QRCODE ").append(qrX).append(",").append(qrY)
                .append(",M,").append(qrSize).append(",A,0,\"").append(escape(code)).append("\"").append(CRLF);

        // ===== CRÍTICO: PRINT con parámetros correctos =====
        // PRINT cantidad, copia
        // cantidad = número de etiquetas
        // copia = 1 (imprimir y QUEDARSE en posición lista para siguiente)
        sb.append("PRINT 1,0").append(CRLF);

        // NO agregues FORMFEED, EOP ni comandos extra aquí

        return sb.toString();
    }

    private static String safe(String s) {
        return (s == null) ? "" : s.trim();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "'");
    }

    /**
     * Convierte a ASCII “seguro” para TSPL:
     * - Quita tildes/ñ (ñ -> n), elimina símbolos raros.
     */
    private static String toAsciiSafe(String input) {
        if (input == null) return "";
        String n = Normalizer.normalize(input, Normalizer.Form.NFD);
        n = n.replaceAll("\\p{InCombiningDiacriticalMarks}+", ""); // quita tildes
        n = n.replace("ñ", "n").replace("Ñ", "N");
        // deja solo caracteres imprimibles típicos
        n = n.replaceAll("[^\\x20-\\x7E]", " "); // fuera de ASCII visible -> espacio
        n = n.replaceAll("\\s+", " ").trim();
        return n;
    }
}
