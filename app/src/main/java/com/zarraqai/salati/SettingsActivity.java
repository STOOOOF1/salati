package com.zarraqai.salati;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Switch;
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

    private static final int REQ_LOC = 7, REQ_TONE = 8, REQ_ADHAN = 9, REQ_NOTIF = 10;
    private static final String[] IQ_LABELS = {"الفجر", "الظهر", "العصر", "المغرب", "العشاء"};

    private Spinner spCity;
    private TextView tvLoc;
    private final EditText[] iq = new EditText[5];
    private EditText etWarn;
    private boolean spinnerReady = false;
    private final Switch[] alertSw = new Switch[5];
    private TextView tvTone, tvAdhan;

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

        TextView head = new TextView(this);
        head.setText("الإعدادات");
        head.setTextColor(text);
        head.setTextSize(26);
        head.setTypeface(getResources().getFont(R.font.tajawal_extrabold));
        root.addView(head);

        LinearLayout locCard = card();
        root.addView(locCard, cardLp());
        locCard.addView(title("الموقع", text));
        tvLoc = label("", muted);
        locCard.addView(tvLoc);

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
                    AlertScheduler.reschedule(SettingsActivity.this);
                } else {
                    requestLocation();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        spLp.topMargin = dp(10);
        spCity.setBackgroundResource(R.drawable.input);
        locCard.addView(spCity, spLp);

        LinearLayout iqCard = card();
        root.addView(iqCard, cardLp());
        iqCard.addView(title("الإقامة بعد الأذان (بالدقائق)", text));
        int[] cur = Prefs.iqama(this);
        for (int i = 0; i < 5; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));
            TextView l = label(IQ_LABELS[i], text);
            l.setPadding(0, 0, 0, 0);
            l.setTextSize(16);
            row.addView(l, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            iq[i] = number(cur[i], text);
            row.addView(iq[i], new LinearLayout.LayoutParams(dp(84), dp(46)));
            iqCard.addView(row);
        }

        LinearLayout warnCard = card();
        root.addView(warnCard, cardLp());
        warnCard.addView(title("التنبيه الأحمر", text));
        warnCard.addView(label("يبدأ اللون بالتحول إلى الأحمر قبل خروج وقت الصلاة بهذا العدد من الدقائق:", muted));
        etWarn = number(Prefs.warnMinutes(this), text);
        LinearLayout.LayoutParams wLp = new LinearLayout.LayoutParams(dp(84), dp(46));
        wLp.topMargin = dp(10);
        warnCard.addView(etWarn, wLp);

        // ---------- تنبيه الأذان ----------
        LinearLayout alCard = card();
        root.addView(alCard, cardLp());
        alCard.addView(title("تنبيه الأذان", text));
        alCard.addView(label("اختر الصلوات التي تريد التنبيه عند أذانها:", muted));
        boolean[] on = Prefs.alerts(this);
        for (int i = 0; i < 5; i++) {
            Switch sw = new Switch(this);
            sw.setText(IQ_LABELS[i]);
            sw.setTextColor(text);
            sw.setTextSize(16);
            sw.setTypeface(getResources().getFont(R.font.tajawal_medium));
            sw.setChecked(on[i]);
            sw.setPadding(0, dp(8), 0, dp(8));
            sw.setOnCheckedChangeListener((btn, checked) -> saveAlerts(checked));
            alertSw[i] = sw;
            alCard.addView(sw);
        }

        TextView soundTitle = title("صوت التنبيه", text);
        soundTitle.setPadding(0, dp(14), 0, dp(4));
        alCard.addView(soundTitle);
        RadioGroup rg = new RadioGroup(this);
        String[] modes = {"بدون صوت (إشعار فقط)", "نغمة من نغمات الجهاز", "أذان (ملف صوتي من جهازك)"};
        int[] ids = {101, 102, 103};
        for (int i = 0; i < 3; i++) {
            RadioButton r = new RadioButton(this);
            r.setId(ids[i]);
            r.setText(modes[i]);
            r.setTextColor(text);
            r.setTextSize(15);
            r.setPadding(dp(6), dp(6), dp(6), dp(6));
            rg.addView(r);
        }
        rg.check(ids[Prefs.soundMode(this)]);
        rg.setOnCheckedChangeListener((g, id) -> Prefs.setSoundMode(this, id - 101));
        alCard.addView(rg);

        tvTone = label("", muted);
        alCard.addView(tvTone);
        Button pickTone = secondaryButton("اختيار النغمة");
        pickTone.setOnClickListener(v -> pickTone());
        alCard.addView(pickTone, btnLp());

        tvAdhan = label("", muted);
        alCard.addView(tvAdhan);
        Button pickAdhan = secondaryButton("اختيار ملف الأذان");
        pickAdhan.setOnClickListener(v -> pickAdhan());
        alCard.addView(pickAdhan, btnLp());

        Button testBtn = secondaryButton("تجربة الصوت");
        testBtn.setOnClickListener(v -> AdhanService.start(this, "", true));
        alCard.addView(testBtn, btnLp());
        showSounds();

        // ---------- التحديث ----------
        LinearLayout upCard = card();
        root.addView(upCard, cardLp());
        upCard.addView(title("تحديث التطبيق", text));
        upCard.addView(label("الإصدار الحالي: " + Updater.currentName(this)
                + "\nيتحقق التطبيق تلقائيًا كل 12 ساعة عند فتحه.", muted));
        Button upBtn = secondaryButton("البحث عن تحديث الآن");
        upBtn.setOnClickListener(v -> Updater.check(this, false));
        upCard.addView(upBtn, btnLp());

        Button save = new Button(this);
        save.setText("حفظ");
        save.setTextColor(0xFF06231B);
        save.setTextSize(17);
        save.setTypeface(getResources().getFont(R.font.tajawal_bold));
        save.setBackgroundResource(R.drawable.button);
        save.setStateListAnimator(null);
        save.setOnClickListener(v -> save());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        lp.topMargin = dp(20);
        root.addView(save, lp);

        TextView note = label("طريقة الحساب: أم القرى (الفجر 18.5°، العشاء بعد المغرب بـ90 دقيقة و120 في رمضان). "
                + "نهاية الأوقات: الفجر حتى الشروق، والظهر حتى العصر، والعصر حتى المغرب، والمغرب حتى العشاء، والعشاء حتى منتصف الليل.", muted);
        note.setTextSize(12);
        note.setPadding(dp(4), dp(18), dp(4), 0);
        root.addView(note);

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
            AlertScheduler.reschedule(this);
            Toast.makeText(this, "تم الحفظ", Toast.LENGTH_SHORT).show();
            finish();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "اكتب أرقامًا صحيحة", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- التنبيه ----------

    private void saveAlerts(boolean justEnabled) {
        boolean[] v = new boolean[5];
        for (int i = 0; i < 5; i++) v[i] = alertSw[i].isChecked();
        Prefs.setAlerts(this, v);
        AlertScheduler.reschedule(this);
        if (justEnabled && Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        }
    }

    private void showSounds() {
        tvTone.setText("النغمة: " + Prefs.toneName(this));
        tvAdhan.setText("ملف الأذان: " + Prefs.adhanName(this));
    }

    private void pickTone() {
        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "اختر نغمة التنبيه");
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        String cur = Prefs.toneUri(this);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                cur != null ? Uri.parse(cur) : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
        try { startActivityForResult(i, REQ_TONE); }
        catch (Exception e) { Toast.makeText(this, "لا يتوفر منتقي النغمات", Toast.LENGTH_SHORT).show(); }
    }

    private void pickAdhan() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        try { startActivityForResult(i, REQ_ADHAN); }
        catch (Exception e) { Toast.makeText(this, "لا يتوفر مدير ملفات", Toast.LENGTH_SHORT).show(); }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        if (req == REQ_TONE) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            if (uri == null) return;
            String name = "نغمة مختارة";
            try {
                Ringtone r = RingtoneManager.getRingtone(this, uri);
                if (r != null) name = r.getTitle(this);
            } catch (Exception ignored) {}
            Prefs.setTone(this, uri.toString(), name);
            Prefs.setSoundMode(this, Prefs.SOUND_TONE);
            ((RadioGroup) findViewById(102).getParent()).check(102);
            showSounds();
        } else if (req == REQ_ADHAN) {
            Uri uri = data.getData();
            if (uri == null) return;
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception ignored) {}
            String name = "ملف صوتي";
            try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst()) name = c.getString(0);
            } catch (Exception ignored) {}
            Prefs.setAdhan(this, uri.toString(), name);
            Prefs.setSoundMode(this, Prefs.SOUND_ADHAN);
            ((RadioGroup) findViewById(103).getParent()).check(103);
            showSounds();
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
        if (code == REQ_NOTIF) {
            if (res.length == 0 || res[0] != PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this, "بدون إذن الإشعارات لن يظهر تنبيه الأذان", Toast.LENGTH_LONG).show();
            return;
        }
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
        AlertScheduler.reschedule(this);
        Toast.makeText(this, "تم تحديث الموقع", Toast.LENGTH_SHORT).show();
    }

    // ---------- أدوات واجهة ----------

    private TextView title(String s, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(18);
        t.setTypeface(getResources().getFont(R.font.tajawal_bold));
        t.setPadding(0, 0, 0, dp(4));
        return t;
    }

    private Button secondaryButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(getColor(R.color.accent));
        b.setTextSize(15);
        b.setTypeface(getResources().getFont(R.font.tajawal_bold));
        b.setBackgroundResource(R.drawable.input);
        b.setStateListAnimator(null);
        return b;
    }

    private LinearLayout.LayoutParams btnLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        lp.topMargin = dp(8);
        return lp;
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundResource(R.drawable.card);
        l.setPadding(dp(18), dp(16), dp(18), dp(16));
        return l;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        return lp;
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
        e.setTextSize(18);
        e.setGravity(android.view.Gravity.CENTER);
        e.setTypeface(getResources().getFont(R.font.tajawal_bold));
        e.setBackgroundResource(R.drawable.input);
        return e;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
