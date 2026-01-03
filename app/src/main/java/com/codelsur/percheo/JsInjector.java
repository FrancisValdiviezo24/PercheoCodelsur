package com.codelsur.percheo;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

/**
 * Inyección de JS robusta:
 * - Soporta consultaart en DOM directo o dentro de iframe (Legacy View).
 * - Espera a que exista el #input antes de escribir y disparar Enter.
 * - Luego espera a que se llenen CODIGO/DESCRIPCION/PVP y devuelve JSON a AndroidBridge.
 */
public class JsInjector {

    private final WebView webView;
    private final Handler main = new Handler(Looper.getMainLooper());

    public JsInjector(WebView webView) {
        this.webView = webView;
    }

    /** Punto único: asegura input (en iframe o no), escribe código, Enter y scrape. */
    public void searchAndScrape(String code) {
        String safe = code == null ? "" : code.replace("\\", "\\\\").replace("'", "\\'").trim();

        String js =
                "(function(){\n" +
                        "  try {\n" +
                        "    function sendErr(m){ try{ if(window.AndroidBridge && AndroidBridge.onError){ AndroidBridge.onError(m); } }catch(_e){} }\n" +
                        "\n" +
                        "    function getLegacyDoc(){\n" +
                        "      // 1) Si el input está en el documento actual\n" +
                        "      try{\n" +
                        "        if(document && document.querySelector && document.querySelector('#input')) return document;\n" +
                        "      }catch(e){}\n" +
                        "\n" +
                        "      // 2) Buscar iframe Legacy View\n" +
                        "      var ifr = null;\n" +
                        "      try{\n" +
                        "        ifr = document.querySelector('iframe[title=\"Legacy View\"]');\n" +
                        "        if(!ifr){\n" +
                        "          // fallback: primer iframe cuyo src contenga /v2/home?embed=1\n" +
                        "          var frames = document.querySelectorAll('iframe');\n" +
                        "          for(var i=0;i<frames.length;i++){\n" +
                        "            var s = (frames[i].getAttribute('src')||'');\n" +
                        "            if(s.indexOf('/v2/home?embed=1') !== -1){ ifr = frames[i]; break; }\n" +
                        "          }\n" +
                        "        }\n" +
                        "      }catch(e){ ifr=null; }\n" +
                        "\n" +
                        "      if(!ifr) return null;\n" +
                        "\n" +
                        "      // Acceder al documento del iframe (mismo dominio, sandbox allow-same-origin)\n" +
                        "      try{\n" +
                        "        var w = ifr.contentWindow;\n" +
                        "        var d = ifr.contentDocument || (w ? w.document : null);\n" +
                        "        return d;\n" +
                        "      }catch(e){\n" +
                        "        return null;\n" +
                        "      }\n" +
                        "    }\n" +
                        "\n" +
                        "    function waitForInputThenRun(){\n" +
                        "      var tries = 0;\n" +
                        "      var maxTries = 60; // 60 * 200ms = 12s (ajusta si tu red es lenta)\n" +
                        "\n" +
                        "      (function loop(){\n" +
                        "        var d = getLegacyDoc();\n" +
                        "        if(d && d.querySelector){\n" +
                        "          var input = d.querySelector('#input');\n" +
                        "          if(input){\n" +
                        "            // Tenemos input listo -> ejecutar búsqueda\n" +
                        "            try {\n" +
                        "              input.focus();\n" +
                        "              input.value = '" + safe + "';\n" +
                        "              input.dispatchEvent(new d.defaultView.Event('input', {bubbles:true}));\n" +
                        "              input.dispatchEvent(new d.defaultView.Event('change', {bubbles:true}));\n" +
                        "\n" +
                        "              // Enter (Angular ng-enter suele enganchar key events)\n" +
                        "              var KD = new d.defaultView.KeyboardEvent('keydown', {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true});\n" +
                        "              var KP = new d.defaultView.KeyboardEvent('keypress', {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true});\n" +
                        "              var KU = new d.defaultView.KeyboardEvent('keyup', {key:'Enter', code:'Enter', keyCode:13, which:13, bubbles:true});\n" +
                        "              input.dispatchEvent(KD);\n" +
                        "              input.dispatchEvent(KP);\n" +
                        "              input.dispatchEvent(KU);\n" +
                        "\n" +
                        "              // Ahora esperar respuesta y scrapear\n" +
                        "              waitForResultAndSend(d);\n" +
                        "              return;\n" +
                        "            } catch(e){\n" +
                        "              sendErr('Error escribiendo en #input: ' + e.message);\n" +
                        "              return;\n" +
                        "            }\n" +
                        "          }\n" +
                        "        }\n" +
                        "\n" +
                        "        tries++;\n" +
                        "        if(tries >= maxTries){\n" +
                        "          sendErr('No se encontró #input (aún) - la consulta no terminó de cargar');\n" +
                        "          return;\n" +
                        "        }\n" +
                        "        setTimeout(loop, 200);\n" +
                        "      })();\n" +
                        "    }\n" +
                        "\n" +
                        "    function waitForResultAndSend(d){\n" +
                        "      var tries = 0;\n" +
                        "      var maxTries = 60; // 12s\n" +
                        "\n" +
                        "      function scrape(){\n" +
                        "        try{\n" +
                        "          var rows = d.querySelectorAll('.panel-body .row');\n" +
                        "          var map = {};\n" +
                        "          rows.forEach(function(r){\n" +
                        "            var cols = r.querySelectorAll('div');\n" +
                        "            if(cols.length >= 2){\n" +
                        "              var k = (cols[0].innerText || '').trim().replace(':','').toUpperCase();\n" +
                        "              var v = (cols[1].innerText || '').trim();\n" +
                        "              if(k){ map[k] = v; }\n" +
                        "            }\n" +
                        "          });\n" +
                        "          return {\n" +
                        "            code: (map['CODIGO'] || '').trim(),\n" +
                        "            description: (map['DESCRIPCION'] || '').trim(),\n" +
                        "            pvp: (map['PVP'] || '').trim()\n" +
                        "          };\n" +
                        "        }catch(e){\n" +
                        "          return {__err:e.message, code:'', description:'', pvp:''};\n" +
                        "        }\n" +
                        "      }\n" +
                        "\n" +
                        "      (function loop(){\n" +
                        "        var p = scrape();\n" +
                        "        if(p.__err){ sendErr('Scrape error: ' + p.__err); return; }\n" +
                        "        if(p.code && p.description && p.pvp){\n" +
                        "          try{\n" +
                        "            if(window.AndroidBridge && AndroidBridge.onProductJson){\n" +
                        "              AndroidBridge.onProductJson(JSON.stringify(p));\n" +
                        "            }\n" +
                        "          }catch(e){ sendErr('Error enviando JSON a Android: ' + e.message); }\n" +
                        "          return;\n" +
                        "        }\n" +
                        "\n" +
                        "        tries++;\n" +
                        "        if(tries >= maxTries){\n" +
                        "          sendErr('Timeout esperando respuesta (la página tardó demasiado o no devolvió datos)');\n" +
                        "          return;\n" +
                        "        }\n" +
                        "        setTimeout(loop, 200);\n" +
                        "      })();\n" +
                        "    }\n" +
                        "\n" +
                        "    // Ejecutar\n" +
                        "    waitForInputThenRun();\n" +
                        "\n" +
                        "  }catch(e){\n" +
                        "    try{ if(window.AndroidBridge && AndroidBridge.onError){ AndroidBridge.onError('JS fatal: ' + e.message); } }catch(_e){}\n" +
                        "  }\n" +
                        "})();";

        main.post(() -> webView.evaluateJavascript(js, null));
    }
}
