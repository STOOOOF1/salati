package com.zarraqai.salati;

import android.app.Activity;
import android.widget.Toast;

import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

/** مسح أي باركود أو رمز QR بماسح خدمات Google (بلا إذن كاميرا وبلا إنترنت بعد أول تنزيل). */
final class CodeScan {
    private CodeScan() {}

    interface Result { void onCode(String value); }

    static void scan(Activity a, Result r) {
        try {
            GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(a);
            scanner.startScan()
                    .addOnSuccessListener(b -> {
                        String v = b.getRawValue();
                        if (v != null && !v.isEmpty()) r.onCode(v);
                    })
                    .addOnFailureListener(e -> Toast.makeText(a,
                            "تعذّر تشغيل الماسح (يحتاج خدمات Google)، جرّب طريقة أخرى", Toast.LENGTH_LONG).show());
        } catch (Throwable t) {
            Toast.makeText(a, "الماسح غير متاح على هذا الجهاز، جرّب طريقة أخرى", Toast.LENGTH_LONG).show();
        }
    }
}
