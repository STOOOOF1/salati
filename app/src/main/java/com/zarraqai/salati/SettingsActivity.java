package com.zarraqai.salati;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** إعدادات بسيطة: المدينة أو الموقع الحالي، ودقائق الإقامة، ومدة الإنذار الأحمر. */
public class SettingsActivity extends Activity {

    private static final int REQ_LOC = 7;
    private static final String[] IQ_LABELS = {"إقامة الفجر بعد الأذان (دقيقة)", "إقامة الظهر", "إقامة العصر", "إقامة المغرب", "إقامة العشاء"};

    private Spinner spCity;
    private TextView tvLoc;
    private final EditText[] iq = new EditText[5];
    private EditText etWarn;
    private boolean spinnerReady = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int text = getColor(R.color.text), muted = getColor(R.color.muted);

        ScrollView sv = new ScrollView(this);
        sv.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        sv.addView(root);

        root.addView(title("الموقع", text));
        tvLoc = label("", muted);
        root.addView(tvLoc);

        String[] items = new String[Prefs.CITY_NAMES.length + 1];
        System.arraycopy(Prefs.CITY_NAMES, 0, items, 0, Prefs.CITY_NAMES.length);
        items[items.length - 1] = "موقعي الحالي (GPS)";
        spCity = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
        spCity.setAdapter(ad);
        int ci = Prefs.cityIndex(this);
        spCity.setSelection(ci < 0 ? items.length - 1 : Math.min(ci, items.length - 1));
        spCity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (!spinnerReady) { spinnerReady = true; return; }
                if (pos < Prefs.CITY_NAMES.length) {
                    Prefs.setPlace(SettingsActivity.this, Prefs.CITY_NAMES[pos],
                            Prefs.CITY_COORDS[pos][0], Prefs.CITY_COORDS[pos][1], pos);
                    showLoc();
                    WidgetUpdater.updateAll(SettingsActivity.this);
                } else {
                    requestLocation();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        root.addView(spCity);

        root.addView(title("الإقامة", text));
        int[] cur = Prefs.iqama(this);
        for (int i = 0; i < 5; i++) {
            root.addView(label(IQ_LABELS[i], muted));
            iq[i] = number(cur[i], text);
            root.addView(iq[i]);
        }

        root.addView(title("التنبيه الأحمر", text));
        root.addView(label("يبدأ اللون بالتحول إلى الأحمر قبل خروج وقت الصلاة بـ (دقيقة):", muted));
        etWarn = number(Prefs.warnMinutes(this), text);
        root.addView(etWarn);

        Button save = new Button(this);
        save.setText("حفظ");
        save.setOnClickListener(v -> save());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(24);
        root.addView(save, lp);

        root.addView(label("طريقة الحساب: أم القرى (الفجر 18.5°، العشاء بعد المغرب بـ90 دقيقة و120 في رمضان). "
                + "نهاية الأوقات: الفجر حتى الشروق، والظهر حتى العصر، والعصر حتى المغرب، والمغرب حتى العشاء، والعشاء حتى منتصف الليل.", muted));

        setContentView(sv);
        showLoc();
    }

    private void showLoc() {
        tvLoc.setText(String.format(Locale.US, "%s  (%.4f, %.4f)", Prefs.placeName(this), Prefs.lat(this), Prefs.lng(this)));
    }

    private void save() {
        int[] v = new int[5];
        try {
            for (int i = 0; i < 5; i++) v[i] = clamp(Integer.parseInt(iq[i].getText().toString().trim()), 0, 90);
            int w = clamp(Integer.parseInt(etWarn.getText().toString().trim()), 1, 180);
            Prefs.save(this, v, w);
            WidgetUpdater.updateAll(this);
            Toast.makeText(this, "تم الحفظ", Toast.LENGTH_SHORT).show();
            finish();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "اكتب أرقامًا صحيحة", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- الموقع الحالي ----------

    private void requestLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOC);
        } else {
            fetchLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ_LOC && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) fetchLocation();
        else Toast.makeText(this, "لم يُسمح بالوصول إلى الموقع", Toast.LENGTH_SHORT).show();
    }

    @SuppressWarnings("MissingPermission")
    private void fetchLocation() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) return;
        try {
            Location best = null;
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
            }
            if (best != null) { useLocation(best); return; }
            Toast.makeText(this, "جارٍ تحديد الموقع…", Toast.LENGTH_SHORT).show();
            String provider = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                    ? LocationManager.NETWORK_PROVIDER : LocationManager.GPS_PROVIDER;
            lm.requestLocationUpdates(provider, 0, 0, new LocationListener() {
                @Override public void onLocationChanged(Location l) {
                    lm.removeUpdates(this);
                    useLocation(l);
                }
                @Override public void onStatusChanged(String p, int s, Bundle e) {}
                @Override public void onProviderEnabled(String p) {}
                @Override public void onProviderDisabled(String p) {}
            }, Looper.getMainLooper());
        } catch (SecurityException | IllegalArgumentException e) {
            Toast.makeText(this, "تعذّر تحديد الموقع، فعّل خدمة الموقع", Toast.LENGTH_LONG).show();
        }
    }

    private void useLocation(Location l) {
        Prefs.setPlace(this, "موقعي الحالي", l.getLatitude(), l.getLongitude(), -1);
        showLoc();
        WidgetUpdater.updateAll(this);
        Toast.makeText(this, "تم تحديث الموقع", Toast.LENGTH_SHORT).show();
    }

    // ---------- أدوات واجهة ----------

    private TextView title(String s, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(20);
        t.setPadding(0, dp(20), 0, dp(6));
        return t;
    }

    private TextView label(String s, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(14);
        t.setPadding(0, dp(8), 0, 0);
        return t;
    }

    private EditText number(int value, int color) {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setText(String.valueOf(value));
        e.setTextColor(color);
        return e;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
