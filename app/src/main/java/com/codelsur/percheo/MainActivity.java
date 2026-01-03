package com.codelsur.percheo;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothSocket;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.codelsur.percheo.databinding.ActivityMainBinding;
import com.codelsur.percheo.print.BluetoothTsplPrinter;
import com.codelsur.percheo.print.TsplLabelBuilder;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.io.OutputStream;

/**
 * Flujo:
 * - Modo Web: login manual.
 * - Modo App: WebView sigue vivo minimizado (NO se destruye).
 * - Escaneo -> (opcional) cargar consulta si no está -> esperar un poco -> inyectar JS -> scrape -> UI.
 */
public class MainActivity extends AppCompatActivity implements WebAppBridge.Listener {

    private ActivityMainBinding b;

    private boolean appMode = false;
    private Product currentProduct = null;

    private JsInjector jsInjector;

    private final Handler main = new Handler(Looper.getMainLooper());

    // Ajustes prácticos
    private static final long DELAY_AFTER_LOAD_MS = 1200; // delay para dar tiempo a que el iframe/render exista
    private static final long DELAY_IF_ALREADY_THERE_MS = 250; // si ya estás en consulta, delay mínimo

    private final Object printLock = new Object();
    private volatile boolean isPrinting = false;

    private final ActivityResultLauncher<String> cameraPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startScan();
                else Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());

        setupWebView(b.webView);

        b.fabToggle.setOnClickListener(v -> toggleMode());

        b.btnScan.setOnClickListener(v -> {
            if (!PermissionUtils.hasCamera(this)) {
                cameraPermLauncher.launch(android.Manifest.permission.CAMERA);
                return;
            }
            startScan();
        });

        // NUEVO: Búsqueda manual por código
        b.btnBuscar.setOnClickListener(v -> searchByManualCode());

        // NUEVO: Buscar al presionar Enter en el teclado
        b.editCodigo.setOnEditorActionListener((textView, actionId, keyEvent) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                searchByManualCode();
                return true;
            }
            return false;
        });

        b.btnPrint.setOnClickListener(v -> {
            if (currentProduct == null || !currentProduct.isValid()) return;
            printLabelViaBluetooth();
        });

        // Inicia en login
        WebViewManager.loadLogin(b.webView);
    }

    // NUEVO: Método para buscar por código manual
    private void searchByManualCode() {
        String code = b.editCodigo.getText().toString().trim();

        if (code.isEmpty()) {
            Toast.makeText(this, "Ingresa un código", Toast.LENGTH_SHORT).show();
            return;
        }

        // Ocultar teclado
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null && b.editCodigo.getWindowToken() != null) {
            imm.hideSoftInputFromWindow(b.editCodigo.getWindowToken(), 0);
        }

        // Pasa a modo App si estabas en web
        if (!appMode) toggleMode();

        // Asegurar consulta
        boolean loadedNow = WebViewManager.ensureConsultaLoaded(b.webView);
        long delay = loadedNow ? DELAY_AFTER_LOAD_MS : DELAY_IF_ALREADY_THERE_MS;

        Toast.makeText(this, "Consultando " + code + "...", Toast.LENGTH_SHORT).show();

        // Delay y luego inyectar
        main.postDelayed(() -> jsInjector.searchAndScrape(code), delay);
    }

    private void setupWebView(WebView wv) {
        WebViewManager.setup(this, wv);
        wv.addJavascriptInterface(new WebAppBridge(this), "AndroidBridge");
        jsInjector = new JsInjector(wv);
    }

    private void toggleMode() {
        appMode = !appMode;
        if (appMode) {
            b.nativeContainer.setVisibility(View.VISIBLE);
            minimizeWebContainer();
            b.fabToggle.setText(getString(R.string.mode_web));
        } else {
            b.nativeContainer.setVisibility(View.GONE);
            restoreWebContainer();
            b.fabToggle.setText(getString(R.string.mode_app));
        }
    }

    private void minimizeWebContainer() {
        ViewGroup.LayoutParams lp = b.webContainer.getLayoutParams();
        lp.width = 1;
        lp.height = 1;
        b.webContainer.setLayoutParams(lp);
        b.webContainer.setAlpha(0f);
        b.webContainer.setVisibility(View.VISIBLE);
    }

    private void restoreWebContainer() {
        ViewGroup.LayoutParams lp = b.webContainer.getLayoutParams();
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        b.webContainer.setLayoutParams(lp);
        b.webContainer.setAlpha(1f);
        b.webContainer.setVisibility(View.VISIBLE);
    }

    private void startScan() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.ALL_CODE_TYPES);
        integrator.setPrompt("Escanea el código de barras / QR");
        integrator.setBeepEnabled(true);
        integrator.setOrientationLocked(false);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() == null) {
                Toast.makeText(this, "Escaneo cancelado", Toast.LENGTH_SHORT).show();
            } else {
                String code = result.getContents().trim();

                // Pasa a modo App para ver UI nativa (si estabas en web)
                if (!appMode) toggleMode();

                // Asegurar consulta SOLO después de escanear (y solo si hace falta)
                boolean loadedNow = WebViewManager.ensureConsultaLoaded(b.webView);

                long delay = loadedNow ? DELAY_AFTER_LOAD_MS : DELAY_IF_ALREADY_THERE_MS;

                Toast.makeText(this, "Consultando " + code + "…", Toast.LENGTH_SHORT).show();

                // Delay y luego inyectar (el JS ahora busca #input dentro del iframe también)
                main.postDelayed(() -> jsInjector.searchAndScrape(code), delay);
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    // WebAppBridge.Listener
    @Override
    public void onProductReceived(Product p) {
        currentProduct = p;

        b.txtCodigo.setText("Código: " + safe(p.code));
        b.txtDescripcion.setText("Descripción: " + safe(p.description));
        b.txtPvp.setText("PVP: " + safe(p.pvp));

        boolean canPrint = p != null
                && notEmpty(p.code)
                && notEmpty(p.description)
                && notEmpty(p.pvp);

        b.btnPrint.setEnabled(canPrint);

        if (!canPrint) {
            Toast.makeText(this, "Datos incompletos: no se habilita imprimir", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean notEmpty(String s) {
        return s != null && !s.trim().isEmpty() && !"-".equals(s.trim());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PermissionUtils.REQ_BT12) {
            boolean ok = true;
            for (int r : grantResults) {
                if (r != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                Toast.makeText(this, "Permisos Bluetooth concedidos. Vuelve a imprimir.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Sin permisos Bluetooth no se puede imprimir.", Toast.LENGTH_LONG).show();
            }
        }
    }


    @Override
    public void onScrapeError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private String safe(String s) {
        return (s == null || s.trim().isEmpty()) ? "-" : s.trim();
    }

    private void showLabelDialog(Bitmap bmp) {
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(bmp);
        iv.setAdjustViewBounds(true);
        iv.setPadding(16, 16, 16, 16);

        new AlertDialog.Builder(this)
                .setTitle("Etiqueta (vista previa)")
                .setView(iv)
                .setPositiveButton("Cerrar", (d, w) -> d.dismiss())
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (b != null && b.webView != null) {
            b.webView.destroy();
        }
    }
    private final BluetoothTsplPrinter printer = new BluetoothTsplPrinter();

    private void printLabelViaBluetooth() {
        if (!printer.isBluetoothAvailable()) {
            showErrorDialog("Bluetooth no disponible", "Este dispositivo no tiene Bluetooth");
            return;
        }
        if (!printer.isEnabled()) {
            showErrorDialog("Bluetooth desactivado", "Activa el Bluetooth para imprimir");
            return;
        }

        if (!PermissionUtils.hasBluetooth12Permissions(this)) {
            PermissionUtils.requestBluetooth12Permissions(this);
            Toast.makeText(this, "Concede permisos Bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }

        java.util.Set<android.bluetooth.BluetoothDevice> bonded;
        try {
            bonded = printer.getBondedDevicesSafe();
        } catch (SecurityException se) {
            showErrorDialog("Permisos insuficientes", "Faltan permisos Bluetooth");
            return;
        }

        if (bonded == null || bonded.isEmpty()) {
            showErrorDialog("Sin impresoras", "No hay impresoras emparejadas.\n\nEmpareja una impresora en Ajustes Bluetooth.");
            return;
        }

        showPrinterSelectionDialog(new java.util.ArrayList<>(bonded));
    }

    private void showPrinterSelectionDialog(java.util.List<android.bluetooth.BluetoothDevice> printers) {
        // Crear diálogo personalizado
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);

        // Crear contenedor principal
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, padding);

        // Título
        android.widget.TextView title = new android.widget.TextView(this);
        title.setText("Seleccionar Impresora");
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(0xFF111827);
        title.setPadding(0, 0, 0, padding);
        container.addView(title);

        // Subtítulo
        android.widget.TextView subtitle = new android.widget.TextView(this);
        subtitle.setText("Elige la impresora Bluetooth");
        subtitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        subtitle.setTextColor(0xFF6B7280);
        subtitle.setPadding(0, 0, 0, padding);
        container.addView(subtitle);

        androidx.appcompat.app.AlertDialog dialog = builder.create();

        // Crear cards para cada impresora
        for (android.bluetooth.BluetoothDevice device : printers) {
            com.google.android.material.card.MaterialCardView card = new com.google.android.material.card.MaterialCardView(this);
            android.widget.LinearLayout.LayoutParams cardParams = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            );
            int marginBottom = (int) (12 * getResources().getDisplayMetrics().density);
            cardParams.setMargins(0, 0, 0, marginBottom);
            card.setLayoutParams(cardParams);
            card.setRadius(16 * getResources().getDisplayMetrics().density);
            card.setCardElevation(4 * getResources().getDisplayMetrics().density);
            card.setClickable(true);
            card.setFocusable(true);

            // Contenido del card
            android.widget.LinearLayout cardContent = new android.widget.LinearLayout(this);
            cardContent.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            int cardPadding = (int) (16 * getResources().getDisplayMetrics().density);
            cardContent.setPadding(cardPadding, cardPadding, cardPadding, cardPadding);
            cardContent.setGravity(android.view.Gravity.CENTER_VERTICAL);

            // Ícono
            android.widget.ImageView icon = new android.widget.ImageView(this);
            icon.setImageResource(android.R.drawable.ic_menu_save);
            icon.setColorFilter(0xFF0E9E87);
            int iconSize = (int) (40 * getResources().getDisplayMetrics().density);
            android.widget.LinearLayout.LayoutParams iconParams = new android.widget.LinearLayout.LayoutParams(iconSize, iconSize);
            int iconMargin = (int) (16 * getResources().getDisplayMetrics().density);
            iconParams.setMarginEnd(iconMargin);
            icon.setLayoutParams(iconParams);
            cardContent.addView(icon);

            // Textos
            android.widget.LinearLayout textContainer = new android.widget.LinearLayout(this);
            textContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
            android.widget.LinearLayout.LayoutParams textParams = new android.widget.LinearLayout.LayoutParams(
                    0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    1
            );
            textContainer.setLayoutParams(textParams);

            android.widget.TextView nameText = new android.widget.TextView(this);
            String name;
            try {
                name = device.getName();
            } catch (SecurityException se) {
                name = null;
            }
            if (name == null || name.trim().isEmpty()) {
                name = "Impresora Bluetooth";
            }
            nameText.setText(name);
            nameText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
            nameText.setTypeface(null, android.graphics.Typeface.BOLD);
            nameText.setTextColor(0xFF111827);
            textContainer.addView(nameText);

            android.widget.TextView addressText = new android.widget.TextView(this);
            addressText.setText(device.getAddress());
            addressText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
            addressText.setTextColor(0xFF6B7280);
            int textMargin = (int) (4 * getResources().getDisplayMetrics().density);
            android.widget.LinearLayout.LayoutParams addressParams = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            );
            addressParams.setMargins(0, textMargin, 0, 0);
            addressText.setLayoutParams(addressParams);
            textContainer.addView(addressText);

            cardContent.addView(textContainer);
            card.addView(cardContent);

            // Click listener
            AlertDialog finalDialog = dialog;
            card.setOnClickListener(v -> {
                finalDialog.dismiss();
                showPrintOptions(device);
            });

            container.addView(card);
        }

        scrollView.addView(container);
        builder.setView(scrollView);
        builder.setNegativeButton("Cancelar", null);

        dialog = builder.create();
        dialog.show();
    }

    private void showPrintOptions(android.bluetooth.BluetoothDevice device) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);

        builder.setTitle("Opciones de Impresión")
                .setIcon(android.R.drawable.ic_menu_save)
                .setMessage("Selecciona cómo deseas imprimir")
                .setPositiveButton("🖨️ Imprimir", (d, w) -> {
                    d.dismiss();
                    resetAndPrint(device);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showErrorDialog(String title, String message) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Entendido", null)
                .show();
    }

    @SuppressLint("MissingPermission")
    private void resetAndPrint(android.bluetooth.BluetoothDevice device) {
        if (isPrinting) {
            Toast.makeText(this, "⏳ Espera... impresión en proceso", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Imprimiendo...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            synchronized (printLock) {
                isPrinting = true;

                android.bluetooth.BluetoothSocket socket = null;
                java.io.OutputStream os = null;

                try {
                    socket = device.createRfcommSocketToServiceRecord(
                            java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                    );
                    socket.connect();
                    Thread.sleep(500); // Reducido de 800 a 500

                    os = socket.getOutputStream();

                    // RESET MÍNIMO - Solo lo esencial
                    String resetCmds =
                            "SIZE 80 mm,30 mm\r\n" +
                                    "GAP 3 mm,0\r\n" +
                                    "DIRECTION 1,0\r\n" +
                                    "SPEED 3\r\n" +
                                    "DENSITY 8\r\n" +
                                    "CLS\r\n";

                    os.write(resetCmds.getBytes(java.nio.charset.Charset.forName("US-ASCII")));
                    os.flush();

                    // Reducido de 2000 a 800
                    Thread.sleep(800);

                    // Enviar etiqueta
                    final String tspl = TsplLabelBuilder.build(currentProduct);
                    android.util.Log.d("TSPL_DEBUG", "=== COMANDO TSPL ===\n" + tspl);

                    byte[] data = tspl.getBytes(java.nio.charset.Charset.forName("US-ASCII"));
                    os.write(data);
                    os.flush();

                    // Reducido de 4000 a 3000
                    Thread.sleep(3000);

                    runOnUiThread(() ->
                            Toast.makeText(this, "✅ Etiqueta impresa correctamente", Toast.LENGTH_SHORT).show()
                    );

                    android.util.Log.d("TSPL_DEBUG", "Reset + Print completado");

                } catch (java.io.IOException ioe) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "❌ Error de conexión: " + ioe.getMessage(), Toast.LENGTH_LONG).show()
                    );
                    android.util.Log.e("PRINT_ERROR", "IOException", ioe);
                } catch (SecurityException se) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "❌ Error de permisos", Toast.LENGTH_LONG).show()
                    );
                    android.util.Log.e("PRINT_ERROR", "Security", se);
                } catch (Exception e) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "❌ Error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                    );
                    android.util.Log.e("PRINT_ERROR", "Print failed", e);
                    e.printStackTrace();
                } finally {
                    if (os != null) {
                        try {
                            os.flush();
                            Thread.sleep(300); // Reducido
                            os.close();
                        } catch (Exception ignored) {}
                    }
                    if (socket != null) {
                        try {
                            Thread.sleep(300); // Reducido
                            socket.close();
                        } catch (Exception ignored) {}
                    }

                    isPrinting = false;
                }
            }
        }).start();
    }
    private void sendTsplToPrinter(android.bluetooth.BluetoothDevice target) {
        if (currentProduct == null) return;

        // Agregar botón de prueba en el diálogo
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Modo de impresión")
                .setMessage("¿Deseas imprimir una etiqueta de prueba primero?")
                .setPositiveButton("Etiqueta Real", (d, w) -> {
                    d.dismiss();
                    executePrint(target, false);
                })
                .setNeutralButton("Prueba", (d, w) -> {
                    d.dismiss();
                    executePrint(target, true);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
    // En MainActivity.java - agregar este método
    private void executePrint(android.bluetooth.BluetoothDevice target, boolean isTest) {
        if (isPrinting) {
            Toast.makeText(this, "⏳ Espera... impresión en proceso", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "🖨️ Enviando a impresora...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            synchronized (printLock) {
                isPrinting = true;
                try {
                    if (isTest) {
                        printer.printTestLabel(target);
                    } else {
                        final String tspl = TsplLabelBuilder.build(currentProduct);
                        android.util.Log.d("TSPL_DEBUG", "=== ENVIANDO COMANDO ===\n" + tspl);

                        long startTime = System.currentTimeMillis();
                        printer.printTo(target, tspl);
                        long duration = System.currentTimeMillis() - startTime;

                        android.util.Log.d("TSPL_DEBUG", "Proceso completado en " + duration + "ms");
                    }

                    runOnUiThread(() ->
                            Toast.makeText(this, "✅ Etiqueta impresa", Toast.LENGTH_SHORT).show()
                    );

                    // CRÍTICO: Pausa LARGA entre impresiones
                    Thread.sleep(5000); // 5 segundos completos

                    android.util.Log.d("TSPL_DEBUG", "Sistema listo para siguiente impresión");

                } catch (java.io.IOException ioe) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "❌ Error de conexión: " + ioe.getMessage(), Toast.LENGTH_LONG).show()
                    );
                    android.util.Log.e("PRINT_ERROR", "IOException", ioe);
                } catch (SecurityException se) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "❌ Error de permisos", Toast.LENGTH_LONG).show()
                    );
                    android.util.Log.e("PRINT_ERROR", "Security", se);
                } catch (Exception e) {
                    runOnUiThread(() ->
                            Toast.makeText(this, "❌ Error: " + e.getMessage(), Toast.LENGTH_LONG).show()
                    );
                    android.util.Log.e("PRINT_ERROR", "Print failed", e);
                    e.printStackTrace();
                } finally {
                    isPrinting = false;
                }
            }
        }).start();
    }}
