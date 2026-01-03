package com.codelsur.percheo;

import android.graphics.Bitmap;

import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

/** Utilidades para generar QR en Bitmap. */
public class QrUtils {
    public static Bitmap qrBitmap(String content, int sizePx) throws Exception {
        BarcodeEncoder enc = new BarcodeEncoder();
        return enc.encodeBitmap(content, BarcodeFormat.QR_CODE, sizePx, sizePx);
    }
}
