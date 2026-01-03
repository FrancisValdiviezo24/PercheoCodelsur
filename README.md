# Percheo ISYPLUS (WebView + Nativo)

Proyecto Android (Java, minSdk 29 / Android 10) para auditoría de precios (“percheo”) usando un WebView oculto como motor de consulta.

## URLs
- Login: https://www.isyplus.com/nfv3/login
- Consulta: https://www.isyplus.com/nfv3/legacy/consultaart

## Flujo
1. Abre la app -> Modo Web -> login manual.
2. Cambia a “Modo App”.
3. Escanea producto -> la app inyecta JS en consultaart (input id="input") -> scrapea CODIGO / DESCRIPCION / PVP -> muestra en Card.
4. Imprimir etiqueta -> genera Bitmap 50x30mm (aprox) con QR y preview.

## Notas técnicas
- Cookies habilitadas (CookieManager) para mantener sesión.
- JS injection espera la respuesta con polling (hasta ~5s por defecto). Ajusta en JsInjector si tu red es más lenta.

## Pendiente (fase siguiente)
- Enviar la etiqueta a impresora Bluetooth (módulo separado). Recomendado: crear un módulo `printer/` con interfaces e implementar por marca/modelo.
