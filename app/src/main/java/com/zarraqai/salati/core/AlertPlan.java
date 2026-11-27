package com.zarraqai.salati.core;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * يبني قائمة تنبيهات صلاة واحدة حسب مستوى المتابعة:
 * خفيف: الأذان فقط.
 * متوسط: الأذان، وقبل الإقامة (أو منتصف الوقت لمن يصلي في البيت)، مع تكرار التنبيه إن لم يُستجب له.
 * مكثّف: الأذان، وبعده، وقبل الإقامة، وأثناء الصلاة (اهتزاز)، ومنتصف الوقت، وقبل نهاية الاختيار،
 *        وتنبيه شديد قبل خروج الوقت كله.
 * لا يعتمد على أندرويد حتى يمكن اختباره مباشرة.
 */
public final class AlertPlan {

    public static final int LIGHT = 0, MEDIUM = 1, INTENSE = 2;
    public static final String[] LEVEL_NAMES = {"خفيف", "متوسط", "مكثّف"};

    // أنواع التنبيه
    public static final int T_ADHAN = 0, T_AFTER_ADHAN = 1, T_PRE_IQAMA = 2, T_DURING = 3,
            T_HALF = 4, T_PRE_CHOICE_END = 5, T_PRE_END = 6, T_SNOOZE = 7, T_CHECK = 8;

    // قوة التنبيه
    /** إشعار مع اهتزاز بلا صوت. */
    public static final int S_SILENT = 0;
    /** صوت عادي، يتوقف بأي زر. */
    public static final int S_NORMAL = 1;
    /** صوت عالٍ بشاشة كاملة، يتوقف بأي زر. */
    public static final int S_STRONG = 2;
    /** المنبّه الذكي: لا يتوقف إلا بحل تحدٍّ، ويعلو صوته تدريجيًا. */
    public static final int S_SMART = 3;

    public static final int PRE_IQAMA_MIN = 5, AFTER_ADHAN_MIN = 10, DURING_MIN = 5,
            PRE_CHOICE_END_MIN = 20, PRE_END_MIN = 15;

    public static final class Event {
        public final int slot;            // 0..4
        public final int type;
        public final int strength;
        public final ZonedDateTime at;
        public final String key;          // مفتاح الصلاة (Schedule.key)
        public final String name;         // اسم الصلاة

        public Event(int slot, int type, int strength, ZonedDateTime at, String key, String name) {
            this.slot = slot; this.type = type; this.strength = strength; this.at = at; this.key = key; this.name = name;
        }

        @Override public String toString() { return name + " " + type + "/" + strength + " @" + at.toLocalTime(); }
    }

    private AlertPlan() {}

    /** نص التنبيه حسب نوعه. */
    public static String title(int type, String name) {
        switch (type) {
            case T_ADHAN: return "حان الآن موعد أذان " + name;
            case T_AFTER_ADHAN: return "أُذّن ل" + name + " قبل " + AFTER_ADHAN_MIN + " دقائق";
            case T_PRE_IQAMA: return "الإقامة لصلاة " + name + " بعد " + PRE_IQAMA_MIN + " دقائق";
            case T_DURING: return "الجماعة قائمة الآن لصلاة " + name;
            case T_HALF: return "مضى نصف وقت " + name;
            case T_PRE_CHOICE_END: return "يوشك وقت الاختيار ل" + name + " على الانتهاء";
            case T_PRE_END: return "يوشك وقت " + name + " على الخروج!";
            case T_SNOOZE: return "تذكير: صلاة " + name;
            case T_CHECK: return "هل أنت مستيقظ؟";
            default: return name;
        }
    }

    /**
     * @param atHome يصلي في البيت: لا تنبيهات للإقامة، ويُستبدل بها منتصف الوقت.
     * @param smart  المنبّه الذكي لهذه الصلاة (الفجر دائمًا).
     */
    public static List<Event> forPrayer(Schedule.Prayer p, int level, boolean atHome, boolean smart) {
        int slot = Schedule.slot(p.index);
        String key = Schedule.key(p);
        List<Event> out = new ArrayList<>();
        boolean darura = Schedule.hasDarura(p);
        ZonedDateTime half = p.adhan.plusSeconds(Duration.between(p.adhan, p.choiceEnd).getSeconds() / 2);

        out.add(new Event(slot, T_ADHAN, smart ? S_SMART : (level == LIGHT ? S_NORMAL : S_STRONG), p.adhan, key, p.name));

        if (level >= MEDIUM) {
            if (!atHome) out.add(new Event(slot, T_PRE_IQAMA, S_NORMAL, p.iqama.minusMinutes(PRE_IQAMA_MIN), key, p.name));
            else out.add(new Event(slot, T_HALF, S_NORMAL, half, key, p.name));
        }
        if (level == INTENSE) {
            out.add(new Event(slot, T_AFTER_ADHAN, S_NORMAL, p.adhan.plusMinutes(AFTER_ADHAN_MIN), key, p.name));
            if (!atHome) {
                out.add(new Event(slot, T_DURING, S_SILENT, p.iqama.plusMinutes(DURING_MIN), key, p.name));
                out.add(new Event(slot, T_HALF, S_NORMAL, half, key, p.name));
            }
            if (darura) {
                out.add(new Event(slot, T_PRE_CHOICE_END, S_STRONG, p.choiceEnd.minusMinutes(PRE_CHOICE_END_MIN), key, p.name));
            }
            out.add(new Event(slot, T_PRE_END, smart ? S_SMART : S_STRONG, p.end.minusMinutes(PRE_END_MIN), key, p.name));
        }

        // نحذف ما وقع خارج وقت الصلاة، ونكتفي بأقوى تنبيه إذا تقارب اثنان (أقل من 3 دقائق)
        out.removeIf(e -> e.at.isBefore(p.adhan) || !e.at.isBefore(p.end));
        out.sort(Comparator.comparing((Event e) -> e.at));
        List<Event> merged = new ArrayList<>();
        for (Event e : out) {
            Event last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && Duration.between(last.at, e.at).toMinutes() < 3) {
                if (e.strength > last.strength) merged.set(merged.size() - 1, e);
                continue;
            }
            merged.add(e);
        }
        return merged;
    }
}
