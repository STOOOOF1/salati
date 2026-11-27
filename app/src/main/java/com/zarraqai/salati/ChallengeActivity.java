package com.zarraqai.salati;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.zarraqai.salati.core.AlertPlan;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * شاشة التنبيه فوق قفل الشاشة:
 * إيقاف: زر كبير، وأي زر في الجوال يوقفه.
 * تحدٍّ: لا يتوقف المنبّه إلا بحل مسألة، أو مطابقة بطاقات، أو المشي خطوات، أو مسح رمز.
 * سؤال: «هل أنت مستيقظ؟» خلال دقيقة، وإلا عاد المنبّه بأعلى صوت.
 */
public class ChallengeActivity extends Activity implements SensorEventListener {

    static final String EXTRA_MODE = "mode";
    static final int MODE_STOP = 0, MODE_CHALLENGE = 1, MODE_CHECK = 2;
    private static final int REQ_STEPS = 21;
    private static final String[] SYMBOLS = {"🌙", "⭐", "☀️", "🕌", "📿", "🌴", "💧", "🍃", "🌸", "🔔", "🍎", "🐪"};

    private static WeakReference<ChallengeActivity> instance = new WeakReference<>(null);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private Intent alert;
    private int mode;
    private boolean solved;
    private int roundsLeft;
    private LinearLayout root, body;
    private Typeface bold, med;
    private int cText, cMuted, cAccent, cRed;

    // الخطوات
    private SensorManager sm;
    private int steps, stepsGoal;
    private float counterBase = -1;
    private TextView stepsView;

    /** يُغلق الشاشة إذا أوقف التنبيه من مكان آخر، ما لم يكن التحدي قد حُلّ (تبقى لعرض «صلّيت»). */
    static void closeIfOpen() {
        ChallengeActivity a = instance.get();
        if (a != null && !a.solved && a.mode != MODE_CHECK) a.runOnUiThread(a::finish);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        instance = new WeakReference<>(this);
        bold = getResources().getFont(R.font.tajawal_extrabold);
        med = getResources().getFont(R.font.tajawal_medium);
        cText = getColor(R.color.text);
        cMuted = getColor(R.color.muted);
        cAccent = getColor(R.color.accent);
        cRed = 0xFFFF5A4E;

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(48), dp(24), dp(32));
        sv.addView(root);
        setContentView(sv);
        Insets.on(sv, (top, bottom) -> sv.setPadding(0, top, 0, bottom));
        start(getIntent());
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        start(i);
    }

    private void start(Intent i) {
        alert = AdhanService.current;
        mode = i.getIntExtra(EXTRA_MODE, MODE_STOP);
        if (alert == null) { finish(); return; }
        // إن تحوّل السؤال إلى منبّه كامل
        if (alert.getIntExtra(AlertScheduler.EXTRA_TYPE, 0) != AlertPlan.T_CHECK && mode == MODE_CHECK) mode = MODE_CHALLENGE;
        solved = false;
        stopSteps();
        render();
    }

    private String key() { return alert.getStringExtra(AlertScheduler.EXTRA_KEY); }
    private String name() { return alert.getStringExtra(AlertScheduler.EXTRA_NAME); }
    private int slot() { return alert.getIntExtra(AlertScheduler.EXTRA_SLOT, 0); }
    private boolean intense() { return Prefs.level(this, slot()) == AlertPlan.INTENSE; }

    private void render() {
        root.removeAllViews();
        handler.removeCallbacksAndMessages(null);
        TextView title = text(AlertPlan.title(alert.getIntExtra(AlertScheduler.EXTRA_TYPE, 0), name()), 26, bold, cText);
        root.addView(title);
        TextView clock = text(java.time.ZonedDateTime.now().format(MainActivity.TIME), 48, bold, cText);
        clock.setPadding(0, dp(12), 0, dp(8));
        root.addView(clock);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(body, new LinearLayout.LayoutParams(-1, -2));

        if (mode == MODE_STOP) renderStop();
        else if (mode == MODE_CHECK) renderCheck();
        else renderMenu();
    }

    // ---------- إيقاف ----------

    private void renderStop() {
        body.addView(text("أي زر في الجوال يوقف التنبيه", 15, med, cMuted));
        Button stop = bigButton("إيقاف", cAccent);
        stop.setOnClickListener(v -> { AdhanService.stopByUser(this); finish(); });
        body.addView(stop, btnLp());
        addDoneButton();
    }

    // ---------- سؤال المتابعة ----------

    private void renderCheck() {
        body.addView(text("أكّد خلال دقيقة، وإلا عاد المنبّه بأعلى صوت", 15, med, cMuted));
        TextView count = text("60", 40, bold, cRed);
        body.addView(count);
        Button yes = bigButton("نعم، أنا مستيقظ", cAccent);
        yes.setOnClickListener(v -> {
            solved = true;
            AdhanService.stopByUser(this);
            renderSolved("الحمد لله، صباح الخير");
        });
        body.addView(yes, btnLp());
        final long end = System.currentTimeMillis() + 60_000L;
        handler.post(new Runnable() {
            @Override public void run() {
                long left = (end - System.currentTimeMillis()) / 1000;
                if (left <= 0) {
                    AdhanService.escalate(ChallengeActivity.this);
                    handler.postDelayed(() -> { mode = MODE_CHALLENGE; render(); }, 300);
                    return;
                }
                count.setText(String.valueOf(left));
                handler.postDelayed(this, 1000);
            }
        });
    }

    // ---------- التحدي ----------

    private void renderMenu() {
        body.addView(text("لإيقاف المنبّه اختر طريقة:", 16, med, cMuted));
        roundsLeft = intense() ? 2 : 1;
        addMethod("➕  مسألة جمع سهلة", v -> renderMath());
        addMethod("🃏  اختر البطاقتين المتماثلتين", v -> renderCards());
        if (stepSensor() != null) addMethod("🚶  امشِ " + (intense() ? 30 : 20) + " خطوة", v -> renderSteps());
        String code = Prefs.wakeStrict(this) ? "📷  امسح الرمز الذي سجّلته" : "📷  امسح أي باركود أو QR";
        addMethod(code, v -> scanCode());
    }

    private void addMethod(String label, View.OnClickListener l) {
        Button b = bigButton(label, 0x2634C38F);
        b.setTextColor(cText);
        b.setOnClickListener(l);
        body.addView(b, btnLp());
    }

    private void back() {
        Button b = new Button(this);
        b.setText("طريقة أخرى");
        b.setTextColor(cMuted);
        b.setBackground(null);
        b.setOnClickListener(v -> { stopSteps(); body.removeAllViews(); renderMenu(); });
        body.addView(b);
    }

    private void roundDone() {
        roundsLeft--;
        if (roundsLeft > 0) {
            Toast.makeText(this, "أحسنت، بقيت جولة", Toast.LENGTH_SHORT).show();
            return;
        }
        onSolved();
    }

    private void renderMath() {
        body.removeAllViews();
        int a = 3 + rnd.nextInt(17), b = 2 + rnd.nextInt(8);
        body.addView(text(a + " + " + b + " = ؟", 44, bold, cText));
        EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_NUMBER);
        in.setGravity(Gravity.CENTER);
        in.setTextSize(30);
        in.setTextColor(cText);
        in.setTypeface(bold);
        in.setBackgroundResource(R.drawable.input);
        in.setIncludeFontPadding(false);
        body.addView(in, new LinearLayout.LayoutParams(dp(160), dp(64)));
        Button ok = bigButton("تحقّق", cAccent);
        ok.setOnClickListener(v -> {
            String s = in.getText().toString().trim();
            if (s.equals(String.valueOf(a + b))) {
                roundDone();
                if (!solved) renderMath();
            } else {
                Toast.makeText(this, "ليست صحيحة، جرّب هذه", Toast.LENGTH_SHORT).show();
                renderMath();
            }
        });
        body.addView(ok, btnLp());
        back();
        in.requestFocus();
    }

    private void renderCards() {
        body.removeAllViews();
        body.addView(text("اضغط البطاقتين المتماثلتين", 18, med, cText));
        List<String> pool = new ArrayList<>();
        Collections.addAll(pool, SYMBOLS);
        Collections.shuffle(pool, rnd);
        List<String> cards = new ArrayList<>(pool.subList(0, 5));
        cards.add(cards.get(rnd.nextInt(5)));
        Collections.shuffle(cards, rnd);

        GridLayout g = new GridLayout(this);
        g.setColumnCount(3);
        final int[] first = {-1};
        final Button[] btns = new Button[6];
        for (int i = 0; i < 6; i++) {
            Button c = new Button(this);
            c.setText(cards.get(i));
            c.setTextSize(34);
            c.setBackgroundResource(R.drawable.card);
            final int idx = i;
            c.setOnClickListener(v -> {
                if (first[0] == idx) return;
                if (first[0] < 0) { first[0] = idx; c.setBackgroundResource(R.drawable.row_card_cur); return; }
                if (cards.get(first[0]).equals(cards.get(idx))) {
                    roundDone();
                    if (!solved) renderCards();
                } else {
                    Toast.makeText(this, "غير متماثلتين، جرّب مرة أخرى", Toast.LENGTH_SHORT).show();
                    renderCards();
                }
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(92); lp.height = dp(92);
            lp.setMargins(dp(6), dp(6), dp(6), dp(6));
            g.addView(c, lp);
            btns[i] = c;
        }
        body.addView(g);
        back();
    }

    // ---------- الخطوات ----------

    private Sensor stepSensor() {
        if (sm == null) sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sm == null) return null;
        Sensor s = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        return s != null ? s : sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
    }

    private void renderSteps() {
        if (Build.VERSION.SDK_INT >= 29 && checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, REQ_STEPS);
            return;
        }
        body.removeAllViews();
        steps = 0;
        counterBase = -1;
        stepsGoal = intense() ? 30 : 20;
        body.addView(text("امشِ والجوال في يدك", 18, med, cText));
        stepsView = text("0 / " + stepsGoal, 48, bold, cAccent);
        body.addView(stepsView);
        back();
        Sensor s = stepSensor();
        if (s != null) sm.registerListener(this, s, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] p, int[] res) {
        super.onRequestPermissionsResult(code, p, res);
        if (code != REQ_STEPS) return;
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) renderSteps();
        else Toast.makeText(this, "بدون إذن النشاط البدني لا تعمل الخطوات، اختر طريقة أخرى", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) steps++;
        else {
            if (counterBase < 0) counterBase = e.values[0];
            steps = (int) (e.values[0] - counterBase);
        }
        if (stepsView != null) stepsView.setText(Math.min(steps, stepsGoal) + " / " + stepsGoal);
        if (steps >= stepsGoal) { stopSteps(); onSolved(); }
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}

    private void stopSteps() {
        if (sm != null) sm.unregisterListener(this);
    }

    // ---------- الرمز ----------

    private void scanCode() {
        CodeScan.scan(this, v -> {
            String want = Prefs.wakeStrict(this) ? Prefs.wakeCode(this) : null;
            if (want == null || want.equals(v)) onSolved();
            else Toast.makeText(this, "ليس الرمز المسجّل، امسح الرمز الذي اخترته", Toast.LENGTH_LONG).show();
        });
    }

    // ---------- النجاح ----------

    private void onSolved() {
        if (solved) return;
        solved = true;
        stopSteps();
        String key = key();
        AdhanService.stopByUser(this);
        if (key != null) {
            Prefs.setWoke(this, key, true);
            if (intense()) AlertScheduler.scheduleCheck(this, slot(), key, name(), 4);
        }
        renderSolved(intense() ? "أحسنت! سنسألك بعد 4 دقائق: هل أنت مستيقظ؟" : "أحسنت! قم إلى صلاتك");
    }

    private void renderSolved(String msg) {
        body.removeAllViews();
        handler.removeCallbacksAndMessages(null);
        body.addView(text(msg, 18, med, cText));
        addDoneButton();
        Button close = bigButton("إغلاق", 0x2634C38F);
        close.setTextColor(cText);
        close.setOnClickListener(v -> finish());
        body.addView(close, btnLp());
    }

    private void addDoneButton() {
        String key = key();
        if (key == null || Prefs.isDone(this, key)) return;
        if (slot() == 0 && intense() && !Prefs.woke(this, key)) return;
        Button done = bigButton("صلّيت ✓", 0xFFE3B65C);
        done.setOnClickListener(v -> {
            DoneReceiver.mark(this, key, true);
            Toast.makeText(this, "تقبّل الله", Toast.LENGTH_SHORT).show();
            finish();
        });
        body.addView(done, btnLp());
    }

    // ---------- الأزرار الفعلية ----------

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE || keyCode == KeyEvent.KEYCODE_CAMERA) {
            if (mode == MODE_STOP || solved) {
                AdhanService.stopByUser(this);
                if (mode == MODE_STOP) finish();
            } else {
                Toast.makeText(this, "حلّ التحدي لإيقاف المنبّه", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return super.onKeyDown(keyCode, e);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (mode == MODE_STOP || solved) super.onBackPressed();
        else Toast.makeText(this, "حلّ التحدي لإيقاف المنبّه", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        stopSteps();
        handler.removeCallbacksAndMessages(null);
        if (instance.get() == this) instance = new WeakReference<>(null);
        super.onDestroy();
    }

    // ---------- أدوات ----------

    private TextView text(String s, int size, Typeface tf, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(6), 0, dp(6));
        return t;
    }

    private Button bigButton(String s, int bg) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(19);
        b.setTypeface(bold);
        b.setTextColor(0xFF06231B);
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(16));
        b.setBackground(d);
        b.setStateListAnimator(null);
        return b;
    }

    private LinearLayout.LayoutParams btnLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(60));
        lp.topMargin = dp(12);
        return lp;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
