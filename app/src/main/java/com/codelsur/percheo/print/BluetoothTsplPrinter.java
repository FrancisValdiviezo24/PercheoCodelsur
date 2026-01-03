package com.codelsur.percheo.print;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Set;
import java.util.UUID;

public class BluetoothTsplPrinter {

    // UUID estándar SPP (Serial Port Profile) para impresoras BT clásicas
    private static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final BluetoothAdapter adapter;

    public BluetoothTsplPrinter() {
        adapter = BluetoothAdapter.getDefaultAdapter();
    }

    public boolean isBluetoothAvailable() {
        return adapter != null;
    }

    public boolean isEnabled() {
        return adapter != null && adapter.isEnabled();
    }

    /**
     * Devuelve dispositivos emparejados (bonded).
     * En Android 12+ requiere BLUETOOTH_CONNECT en runtime.
     */
    @SuppressLint("MissingPermission")
    public Set<BluetoothDevice> getBondedDevicesSafe() throws SecurityException {
        if (adapter == null) return null;
        return adapter.getBondedDevices();
    }

    /**
     * Envía TSPL por BT (SPP).
     * OJO:
     * - Usar CRLF en TSPL (lo construimos así en TsplLabelBuilder).
     * - Enviar en bytes y, si es grande, por chunks para evitar cortes.
     */
    @SuppressLint("MissingPermission")
    public void printTo(BluetoothDevice device, String tspl) throws Exception {
        if (adapter == null) throw new IllegalStateException("Bluetooth no disponible");
        if (device == null) throw new IllegalArgumentException("Device null");

        try {
            if (adapter.isDiscovering()) adapter.cancelDiscovery();
        } catch (SecurityException ignored) {}

        BluetoothSocket socket = null;
        OutputStream os = null;
        java.io.InputStream is = null;

        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();
            Thread.sleep(1000);

            os = socket.getOutputStream();
            is = socket.getInputStream(); // NUEVO: Para leer respuesta

            // Convertir a bytes ASCII
            byte[] data = tspl.getBytes(Charset.forName("US-ASCII"));

            // Enviar todo
            os.write(data);
            os.flush();

            android.util.Log.d("TSPL_PRINT", "Datos enviados, esperando que imprima...");

            // CRÍTICO: Esperar a que la impresora termine
            // No cerramos hasta estar seguros que terminó
            Thread.sleep(6000); // 6 segundos mínimo

            // Intentar leer cualquier respuesta (algunas impresoras envían confirmación)
            try {
                if (is.available() > 0) {
                    byte[] response = new byte[128];
                    int read = is.read(response);
                    if (read > 0) {
                        String resp = new String(response, 0, read);
                        android.util.Log.d("TSPL_PRINT", "Respuesta impresora: " + resp);
                    }
                }
            } catch (Exception e) {
                android.util.Log.d("TSPL_PRINT", "No hay respuesta de impresora (normal)");
            }

            // Pausa adicional de seguridad
            Thread.sleep(1000);

        } finally {
            // Cerrar en orden: flush, esperar, cerrar streams, esperar, cerrar socket
            if (os != null) {
                try {
                    os.flush();
                    Thread.sleep(800);
                    os.close();
                } catch (Exception ignored) {}
            }
            if (is != null) {
                try {
                    is.close();
                } catch (Exception ignored) {}
            }
            if (socket != null) {
                try {
                    Thread.sleep(1500); // AUMENTADO: Esperar más antes de cerrar socket
                    socket.close();
                    android.util.Log.d("TSPL_PRINT", "Conexión cerrada correctamente");
                } catch (Exception ignored) {}
            }

            // Pausa DESPUÉS de cerrar todo
            Thread.sleep(2000);
        }
    }

    // En BluetoothTsplPrinter.java
    /**
    @SuppressLint("MissingPermission")
    public void resetPrinter(BluetoothDevice device) throws Exception {
        if (adapter == null || device == null) return;

        // Cancel discovery
        try {
            if (adapter.isDiscovering()) adapter.cancelDiscovery();
        } catch (SecurityException ignored) {}

        BluetoothSocket socket = null;
        OutputStream os = null;

        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            socket.connect();
            Thread.sleep(300);

            os = socket.getOutputStream();

            // Comandos de reset/limpieza para TSPL
            String resetCommands =
                    "~!T\r\n" +           // Reset (TSC)
                            "INITIALPRINTER\r\n" + // Inicializar impresora
                            "CLS\r\n";            // Limpiar buffer

            os.write(resetCommands.getBytes(Charset.forName("US-ASCII")));
            os.flush();
            Thread.sleep(400);

        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (Exception ignored) {}
            }
            if (socket != null) {
                try {
                    Thread.sleep(200);
                    socket.close();
                } catch (Exception ignored) {}
            }
        }
    }**/
    @SuppressLint("MissingPermission")
    public void printTestLabel(BluetoothDevice device) throws Exception {
        String testTspl =
                "SIZE 80 mm,30 mm\r\n" +
                        "HEIGHT 240\r\n" +
                        "GAP 3 mm,0\r\n" +
                        "DIRECTION 1,0\r\n" +
                        "CLS\r\n" +
                        "TEXT 50,50,\"3\",0,2,2,\"PRUEBA\"\r\n" +
                        "TEXT 50,100,\"3\",0,3,3,\"$12.99\"\r\n" +
                        "BARCODE 50,150,\"128\",80,1,0,2,2,\"123456\"\r\n" +
                        "PRINT 1,1\r\n" +
                        "END\r\n";

        printTo(device, testTspl);
    }
}
