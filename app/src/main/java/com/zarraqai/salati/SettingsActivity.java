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
    private TextView tvTone, tvAdhan, tvCode, tvRel;
    private Switch swStrict;

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

        // ---------- أوقات الصلاة ----------
        LinearLayout mCard = card();
        root.addView(mCard, cardLp());
        mCard.addView(title("أوقات الصلاة (على ما عليه الفتوى في السعودية)", text));
        mCard.addView(label("الفجر: إلى طلوع الشمس.\n"
                + "الظهر: إلى دخول وقت العصر.\n"
                + "العصر: الاختيار إلى اصفرار الشمس، ثم وقت ضرورة إلى غروبها.\n"
                + "المغرب: إلى مغيب الشفق الأحمر (دخول العشاء).\n"
                + "العشاء: الاختيار إلى نصف الليل، ثم وقت ضرورة إلى طلوع الفجر.\n"
                + "ولا يجوز تأخير العصر إلى الاصفرار ولا العشاء إلى ما بعد نصف الليل بلا عذر (فتاوى الشيخ ابن باز).", text));
        TextView mNote = label("نصف الليل يُحسب من غروب الشمس إلى طلوع الفجر. والاصفرار يُقدَّر حسابيًا عند ارتفاع الشمس 5° فوق الأفق، فهو تقريبي.", muted);
        mNote.setTextSize(12);
        mCard.addView(mNote);

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
            final int slot = i;
            onNumber(iq[i], 0, 90, v -> Prefs.setIqama(this, slot, v));
            row.addView(iq[i], new LinearLayout.LayoutParams(dp(84), dp(46)));
            iqCard.addView(row);
        }

        LinearLayout warnCard = card();
        root.addView(warnCard, cardLp());
        warnCard.addView(title("التنبيه الأحمر", text));
        warnCard.addView(label("يبدأ اللون بالتحول إلى الأحمر قبل خروج وقت الصلاة بهذا العدد من الدقائق:", muted));
        etWarn = number(Prefs.warnMinutes(this), text);
        onNumber(etWarn, 1, 180, v -> Prefs.setWarn(this, v));
        LinearLayout.LayoutParams wLp = new LinearLayout.LayoutParams(dp(84), dp(46));
        wLp.topMargin = dp(10);
        warnCard.addView(etWarn, wLp);

        // ---------- التنبيهات ----------
        LinearLayout alCard = card();
        root.addView(alCard, cardLp());
        alCard.addView(title("التنبيهات", text));
        TextView lvlHelp = label("لكل صلاة مستوى متابعة:\n"
                + "• خفيف: تنبيه عند الأذان فقط.\n"
                + "• متوسط: الأذان، وقبل الإقامة بـ5 دقائق، ويتكرر إن لم تستجب.\n"
                + "• مكثّف: الأذان، وبعده، وقبل الإقامة، وأثناء الجماعة (اهتزاز)، ومنتصف الوقت، وقبل نهاية الاختيار، وتنبيه شديد قبل خروج الوقت.\n"
                + "المنبّه الذكي لا يتوقف إلا بحل تحدٍّ (مسألة، بطاقات، خطوات، أو مسح رمز)، وهو مفعّل دائمًا للفجر. "
                + "وفي المكثّف يسألك بعد 4 دقائق: هل أنت مستيقظ؟ وزر «صلّيت» يوقف بقية تنبيهات الصلاة.", muted);
        lvlHelp.setTextSize(13);
        alCard.addView(lvlHelp);
        boolean[] on = Prefs.alerts(this);
        for (int i = 0; i < 5; i++) alCard.addView(prayerAlertBox(i, on[i], text, muted));

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
        testBtn.setOnClickListener(v -> AdhanService.startTest(this));
        alCard.addView(testBtn, btnLp());
        showSounds();

        // ---------- رمز الاستيقاظ ----------
        LinearLayout codeCard = card();
        root.addView(codeCard, cardLp());
        codeCard.addView(title("رمز الاستيقاظ", text));
        codeCard.addView(label("يمكنك إيقاف المنبّه الذكي بمسح أي باركود. وللأقوى: سجّل باركود شيء بعيد عن سريرك "
                + "(قارورة ماء في المطبخ أو معجون عند المغسلة) ولا يُقبل غيره.", muted));
        tvCode = label("", text);
        codeCard.addView(tvCode);
        Button reg = secondaryButton("تسجيل رمز (مسحه الآن)");
        reg.setOnClickListener(v -> CodeScan.scan(this, code -> {
            Prefs.setWakeCode(this, code);
            Prefs.setWakeStrict(this, true);
            showCode();
            Toast.makeText(this, "سُجّل الرمز", Toast.LENGTH_SHORT).show();
        }));
        codeCard.addView(reg, btnLp());
        swStrict = new Switch(this);
        swStrict.setText("لا يُقبل إلا الرمز المسجّل");
        swStrict.setTextColor(text);
        swStrict.setTextSize(15);
        swStrict.setPadding(0, dp(10), 0, dp(4));
        swStrict.setOnCheckedChangeListener((x, c) -> {
            if (c && Prefs.wakeCode(this) == null) {
                Toast.makeText(this, "سجّل رمزًا أولًا", Toast.LENGTH_SHORT).show();
                x.setChecked(false);
                return;
            }
            Prefs.setWakeStrict(this, c);
        });
        codeCard.addView(swStrict);
        Button clr = secondaryButton("حذف الرمز المسجّل");
        clr.setOnClickListener(v -> { Prefs.setWakeCode(this, null); showCode(); });
        codeCard.addView(clr, btnLp());
        showCode();

        // ---------- موثوقية التنبيه ----------
        LinearLayout relCard = card();
        root.addView(relCard, cardLp());
        relCard.addView(title("ضمان وصول التنبيه", text));
        relCard.addView(label("بعض الجوالات (هواوي، هونر، شاومي، أوبو…) توقف التطبيقات في الخلفية فلا يرنّ المنبّه. "
                + "فعّل ما يلي، ومن إعدادات التطبيق اسمح له بالتشغيل التلقائي وفي الخلفية.", muted));
        tvRel = label("", text);
        relCard.addView(tvRel);
        Button bNotif = secondaryButton("السماح بالإشعارات");
        bNotif.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        });
        relCard.addView(bNotif, btnLp());
        Button bFull = secondaryButton("السماح بشاشة المنبّه فوق القفل");
        bFull.setOnClickListener(v -> openSettings(Build.VERSION.SDK_INT >= 34
                ? new Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName()))
                : appDetails()));
        relCard.addView(bFull, btnLp());
        Button bBat = secondaryButton("استثناء التطبيق من توفير البطارية");
        bBat.setOnClickListener(v -> openSettings(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName()))));
        relCard.addView(bBat, btnLp());
        Button bApp = secondaryButton("إعدادات التطبيق (التشغيل التلقائي والخلفية)");
        bApp.setOnClickListener(v -> openSettings(appDetails()));
        relCard.addView(bApp, btnLp());

        // ---------- التحديث ----------
        LinearLayout upCard = card();
        root.addView(upCard, cardLp());
        upCard.addView(title("تحديث التطبيق", text));
        upCard.addView(label("الإصدار الحالي: " + Updater.currentName(this)
                + "\nيتحقق التطبيق تلقائيًا كل 12 ساعة عند فتحه.", muted));
        Button upBtn = secondaryButton("البحث عن تحديث الآن");
        upBtn.setOnClickListener(v -> Updater.check(this, false));
        upCard.addView(upBtn, btnLp());

        TextView note = label("طريقة الحساب: أم القرى (الفجر 18.5°، العشاء بعد المغرب بـ90 دقيقة و120 في رمضان، والعصر بظل المثل). "
                + "تُحفظ الإعدادات تلقائيًا، وكل بياناتك على جهازك فقط.", muted);
        note.setTextSize(12);
        note.setPadding(dp(4), dp(18), dp(4), 0);
        root.addView(note);

        setContentView(sv);
        sv.setClipToPadding(false);
        Insets.on(sv, (top, bottom) -> sv.setPadding(0, top, 0, bottom));
        showLoc();
    }

    private void showLoc() {
        tvLoc.setText(String.format(Locale.US, "%s  (%.4f, %.4f)", Prefs.placeName(this), Prefs.lat(this), Prefs.lng(this)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        showReliability();
    }

    @Override
    protected void onPause() {
        super.onPause();
        WidgetUpdater.updateAll(this);
        AlertScheduler.reschedule(this);
    }

    interface IntSink { void put(int v); }

    /** حفظ تلقائي للحقول الرقمية عند كل تعديل صحيح. */
    private void onNumber(EditText e, int lo, int hi, IntSink sink) {
        e.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                try {
                    int v = Integer.parseInt(s.toString().trim());
                    if (v >= lo && v <= hi) sink.put(v);
                } catch (NumberFormatException ignored) {}
            }
        });
    }

    // ---------- التنبيه لكل صلاة ----------

    private LinearLayout prayerAlertBox(int slot, boolean enabled, int text, int muted) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.input);
        box.setPadding(dp(14), dp(10), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        box.setLayoutParams(lp);

        LinearLayout opts = new LinearLayout(this);
        opts.setOrientation(LinearLayout.VERTICAL);

        Switch sw = new Switch(this);
        sw.setText(IQ_LABELS[slot]);
        sw.setTextColor(text);
        sw.setTextSize(18);
        sw.setTypeface(getResources().getFont(R.font.tajawal_bold));
        sw.setChecked(enabled);
        tintSwitch(sw);
        sw.setOnCheckedChangeListener((b, c) -> {
            boolean[] v = Prefs.alerts(this);
            v[slot] = c;
            Prefs.setAlerts(this, v);
            opts.setAlpha(c ? 1f : 0.45f);
            AlertScheduler.reschedule(this);
            if (c && Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            }
        });
        box.addView(sw);

        RadioGroup lv = segmented(com.zarraqai.salati.core.AlertPlan.LEVEL_NAMES, 300 + slot * 10, Prefs.level(this, slot), text);
        lv.setOnCheckedChangeListener((g, id) -> { Prefs.setLevel(this, slot, id - (300 + slot * 10)); AlertScheduler.reschedule(this); });
        opts.addView(small("مستوى المتابعة", muted));
        opts.addView(lv);

        RadioGroup place = segmented(new String[]{"أصلّي في المسجد", "أصلّي في البيت"}, 400 + slot * 10, Prefs.atHome(this, slot) ? 1 : 0, text);
        place.setOnCheckedChangeListener((g, id) -> { Prefs.setAtHome(this, slot, id - (400 + slot * 10) == 1); AlertScheduler.reschedule(this); });
        opts.addView(small("مكان الصلاة (لتنبيهات الإقامة)", muted));
        opts.addView(place);

        Switch smart = new Switch(this);
        smart.setText(slot == 0 ? "المنبّه الذكي (دائمًا للفجر)" : "المنبّه الذكي (يُوقَف بحل تحدٍّ)");
        smart.setTextColor(text);
        smart.setTextSize(15);
        smart.setPadding(0, dp(10), 0, 0);
        smart.setChecked(Prefs.smart(this, slot));
        smart.setEnabled(slot != 0);
        tintSwitch(smart);
        smart.setOnCheckedChangeListener((b, c) -> { Prefs.setSmart(this, slot, c); AlertScheduler.reschedule(this); });
        opts.addView(smart);

        // تظهر الخيارات دائمًا (باهتة إن كان التنبيه موقوفًا) حتى يعرف المستخدم مكانها
        opts.setAlpha(enabled ? 1f : 0.45f);
        box.addView(opts);
        return box;
    }

    private RadioGroup segmented(String[] labels, int baseId, int checked, int text) {
        RadioGroup g = new RadioGroup(this);
        g.setOrientation(RadioGroup.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            RadioButton r = new RadioButton(this);
            r.setId(baseId + i);
            r.setText(labels[i]);
            r.setTextColor(text);
            r.setTextSize(14);
            r.setPadding(dp(2), dp(4), dp(10), dp(4));
            g.addView(r);
        }
        g.check(baseId + checked);
        return g;
    }

    private TextView small(String s, int color) {
        TextView t = label(s, color);
        t.setTextSize(12);
        t.setPadding(0, dp(8), 0, 0);
        return t;
    }

    /** مفتاح واضح: أخضر عند التفعيل ورمادي عند الإيقاف. */
    private void tintSwitch(Switch sw) {
        int[][] st = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new android.content.res.ColorStateList(st, new int[]{getColor(R.color.accent), 0xFF9AA5A3}));
        sw.setTrackTintList(new android.content.res.ColorStateList(st, new int[]{0x8834C38F, 0x44FFFFFF}));
    }

    // ---------- رمز الاستيقاظ والموثوقية ----------

    private void showCode() {
        String c = Prefs.wakeCode(this);
        tvCode.setText(c == null ? "لم يُسجَّل رمز (يُقبل أي باركود)" : "رمز مسجّل ✓");
        swStrict.setChecked(Prefs.wakeStrict(this));
    }

    private void showReliability() {
        if (tvRel == null) return;
        StringBuilder b = new StringBuilder();
        boolean notif = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        b.append(notif ? "✓ الإشعارات مسموحة" : "✗ الإشعارات غير مسموحة");
        android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
        boolean full = Build.VERSION.SDK_INT < 34 || (nm != null && nm.canUseFullScreenIntent());
        b.append(full ? "\n✓ شاشة المنبّه فوق القفل مسموحة" : "\n✗ شاشة المنبّه فوق القفل غير مسموحة");
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        boolean bat = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        b.append(bat ? "\n✓ مستثنى من توفير البطارية" : "\n✗ غير مستثنى من توفير البطارية");
        tvRel.setText(b.toString());
    }

    private Intent appDetails() {
        return new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
    }

    private void openSettings(Intent i) {
        try { startActivity(i); }
        catch (Exception e) {
            try { startActivity(appDetails()); } catch (Exception ignored) {}
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
        e.setIncludeFontPadding(false);
        e.setPadding(0, 0, 0, 0);
        return e;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
