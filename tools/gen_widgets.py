#!/usr/bin/env python3
"""يولّد تخطيطات الويدجتات السبع وملفات تعريفها وثوابت المعرّفات."""
import os, sys

RES = sys.argv[1]
JAVA = sys.argv[2]
NS = 'xmlns:android="http://schemas.android.com/apk/res/android"'
R, M, B, X = '@font/tajawal_regular', '@font/tajawal_medium', '@font/tajawal_bold', '@font/tajawal_extrabold'
NAMES = ["الفجر", "الظهر", "العصر", "المغرب", "العشاء"]
TIMES = ["5:22", "12:08", "3:12", "5:33", "7:03"]


def tv(id_, size, font=M, color='@color/muted', text='', extra='', w='wrap_content', h='wrap_content'):
    idp = f'android:id="@+id/{id_}" ' if id_ else ''
    t = f'android:text="{text}" ' if text else ''
    return (f'<TextView {idp}android:layout_width="{w}" android:layout_height="{h}" {t}'
            f'android:fontFamily="{font}" android:textColor="{color}" android:textSize="{size}sp" '
            f'android:maxLines="1" android:includeFontPadding="false" {extra}/>')


def chrono(id_, size, font=B, color='@color/text', extra='', w='wrap_content'):
    return (f'<Chronometer android:id="@+id/{id_}" android:layout_width="{w}" android:layout_height="wrap_content" '
            f'android:fontFamily="{font}" android:textColor="{color}" android:textSize="{size}sp" '
            f'android:includeFontPadding="false" {extra}/>')


def clock(size, extra='', w='wrap_content', fmt12='h:mm', color='@color/text'):
    return (f'<TextClock android:id="@+id/clock" android:layout_width="{w}" android:layout_height="wrap_content" '
            f'android:format12Hour="{fmt12}" android:format24Hour="HH:mm" android:fontFamily="{X}" '
            f'android:textColor="{color}" android:textSize="{size}sp" android:includeFontPadding="false" {extra}/>')


def progress(top=6, h=5):
    return (f'<ProgressBar android:id="@+id/progress" style="@android:style/Widget.ProgressBar.Horizontal" '
            f'android:layout_width="match_parent" android:layout_height="{h}dp" android:layout_marginTop="{top}dp" '
            f'android:max="1000" android:progress="620" android:progressDrawable="@drawable/widget_progress" />')


def hrow(children, extra=''):
    return (f'<LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content" '
            f'android:orientation="horizontal" android:gravity="center_vertical" {extra}>' + ''.join(children) + '</LinearLayout>')


def vbox(children, extra='', w='match_parent', h='wrap_content'):
    return (f'<LinearLayout android:layout_width="{w}" android:layout_height="{h}" '
            f'android:orientation="vertical" {extra}>' + ''.join(children) + '</LinearLayout>')


def spacer():
    return '<TextView android:layout_width="0dp" android:layout_height="1dp" android:layout_weight="1" />'


def stat_row(lid, cid, lsize, csize, ltext, ctext, cfont=B, ccolor='@color/text', top=0):
    return hrow([tv(lid, lsize, M, '@color/muted', ltext, 'android:ellipsize="end" ', w='0dp').replace(
        'android:layout_width="0dp"', 'android:layout_width="0dp" android:layout_weight="1"'),
        chrono(cid, csize, cfont, ccolor, f'android:text="{ctext}" ')], f'android:layout_marginTop="{top}dp"')


def root(bg, pad, body, gravity='center_vertical', extra=''):
    return (f'<?xml version="1.0" encoding="utf-8"?>\n<LinearLayout {NS} android:id="@+id/root" '
            f'android:layout_width="match_parent" android:layout_height="match_parent" '
            f'android:background="@drawable/{bg}" android:orientation="vertical" android:layoutDirection="rtl" '
            f'android:padding="{pad}dp" android:gravity="{gravity}" {extra}>' + body + '</LinearLayout>\n')


layouts = {}

# 1) صغير — زمردي، الأذان القادم كبيرًا مع شرائح صغيرة
layouts['widget_small'] = root('widget_bg', 12,
    hrow([clock(15, color='@color/muted'), spacer(),
          tv(None, 11, B, '@color/gold', 'صلاتي')])
    + tv('nextLabel', 12, B, '@color/gold', 'أذان العصر بعد', 'android:layout_marginTop="6dp" ')
    + chrono('nextChrono', 28, X, extra='android:text="1:22:10" ')
    + progress(6)
    + stat_row('curLabel', 'curChrono', 11, 12, 'يخرج الظهر', '58:20', top=6)
    + stat_row('iqLabel', 'iqChrono', 11, 12, 'إقامة العصر', '+20 د', top=3),
    gravity='top')

# 2) دائري — ليلي، حلقة تقدّم والعدّ في الوسط
layouts['widget_ring'] = (f'<?xml version="1.0" encoding="utf-8"?>\n<FrameLayout {NS} android:id="@+id/root" '
    'android:layout_width="match_parent" android:layout_height="match_parent" '
    'android:background="@drawable/widget_bg_night" android:padding="8dp" android:layoutDirection="rtl">'
    '<ImageView android:id="@+id/ring" android:layout_width="match_parent" android:layout_height="match_parent" '
    'android:scaleType="fitCenter" android:src="@drawable/ring_preview" />'
    + vbox([tv('nextLabel', 11, B, '@color/gold', 'العصر', 'android:layout_gravity="center_horizontal" '),
            chrono('nextChrono', 20, X, extra='android:layout_gravity="center_horizontal" android:text="1:22:10" '),
            tv('sub', 10, M, '@color/muted', 'إقامة +20 د', 'android:layout_gravity="center_horizontal" android:layout_marginTop="3dp" '),
            clock(12, 'android:layout_gravity="center_horizontal" android:layout_marginTop="4dp" ', color='@color/muted')],
           'android:layout_gravity="center" android:gravity="center_horizontal" ', w='wrap_content')
    + '</FrameLayout>\n')

# 3) شريط — زجاجي، الصلوات الخمس أفقيًا
cols = ''
for i in range(5):
    cols += vbox([tv(f'cn{i}', 11, M, '@color/muted', NAMES[i], 'android:layout_gravity="center_horizontal" '),
                  tv(f'ct{i}', 14, B, '@color/text', TIMES[i], 'android:layout_gravity="center_horizontal" android:layout_marginTop="3dp" '),
                  chrono(f'cc{i}', 10, B, '@color/gold', 'android:layout_gravity="center_horizontal" android:layout_marginTop="2dp" android:visibility="gone" ')],
                 f'android:id="@+id/c{i}" android:layout_weight="1" android:gravity="center" '
                 'android:paddingTop="5dp" android:paddingBottom="5dp" android:layout_marginStart="2dp" android:layout_marginEnd="2dp" ',
                 w='0dp')
layouts['widget_strip'] = root('widget_bg_glass', 8,
    '<LinearLayout android:layout_width="match_parent" android:layout_height="match_parent" '
    'android:orientation="horizontal" android:gravity="center_vertical">' + cols + '</LinearLayout>', gravity='center')

# 4) متوسط — زمردي، ساعة وتاريخان وشرائح للعدّادات
chip = lambda lid, cid, lt, ct, ccolor='@color/text', cfont=B: vbox(
    [tv(lid, 10, M, '@color/muted', lt, 'android:ellipsize="end" '),
     chrono(cid, 15, cfont, ccolor, f'android:text="{ct}" android:layout_marginTop="3dp" ')],
    'android:layout_weight="1" android:background="@drawable/chip" android:padding="8dp" '
    'android:layout_marginStart="3dp" android:layout_marginEnd="3dp" ', w='0dp')
layouts['widget_medium'] = root('widget_bg', 14,
    hrow([vbox([clock(38), tv('hijri', 12, B, '@color/gold', '3 جمادى الآخرة 1448 هـ', 'android:layout_marginTop="4dp" '),
                tv('greg', 11, M, '@color/muted', '24 نوفمبر 2026 م', 'android:layout_marginTop="2dp" ')],
               '', w='wrap_content'),
          spacer(),
          vbox([tv('nextLabel', 12, B, '@color/gold', 'أذان العصر بعد', 'android:layout_gravity="end" '),
                chrono('nextChrono', 26, X, extra='android:layout_gravity="end" android:text="1:22:10" android:layout_marginTop="2dp" ')],
               '', w='wrap_content')])
    + progress(10)
    + hrow([chip('curLabel', 'curChrono', 'يخرج الظهر', '58:20'),
            chip('iqLabel', 'iqChrono', 'إقامة العصر', '+20 د', '@color/gold'),
            chip('prevLabel', 'prevChrono', 'مضى على الظهر', '2:10:05', '@color/text', M)],
           'android:layout_marginTop="8dp" '),
    gravity='center_vertical')

# 5) كبير — زمردي، كل شيء مع جدول اليوم
rows = ''
for i, (n, t) in enumerate(zip(["الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"],
                               ["5:22 ص", "6:43 ص", "12:08 م", "3:12 م", "5:33 م", "7:03 م"])):
    rows += hrow([tv(f'n{i}', 14, M, '@color/muted', n, w='0dp').replace('android:layout_width="0dp"', 'android:layout_width="0dp" android:layout_weight="1"'),
                  tv(f't{i}', 14, M, '@color/muted', t)],
                 f'android:id="@+id/r{i}" android:paddingStart="10dp" android:paddingEnd="10dp" android:paddingTop="4dp" android:paddingBottom="4dp" ')
layouts['widget_large'] = root('widget_bg', 16,
    hrow([vbox([clock(42), tv('hijri', 13, B, '@color/gold', '3 جمادى الآخرة 1448 هـ', 'android:layout_marginTop="4dp" '),
                tv('greg', 12, M, '@color/muted', '24 نوفمبر 2026 م', 'android:layout_marginTop="2dp" ')], '', w='wrap_content'),
          spacer(),
          vbox([tv('nextLabel', 12, B, '@color/gold', 'أذان العصر بعد', 'android:layout_gravity="end" '),
                chrono('nextChrono', 26, X, extra='android:layout_gravity="end" android:text="1:22:10" ')], '', w='wrap_content')])
    + progress(10)
    + hrow([chip('curLabel', 'curChrono', 'يخرج الظهر', '58:20'),
            chip('iqLabel', 'iqChrono', 'إقامة العصر', '+20 د', '@color/gold'),
            chip('prevLabel', 'prevChrono', 'مضى على الظهر', '2:10:05', '@color/text', M)],
           'android:layout_marginTop="8dp" ')
    + '<TextView android:layout_width="match_parent" android:layout_height="1dp" android:layout_marginTop="10dp" android:layout_marginBottom="4dp" android:background="#1AFFFFFF" />'
    + rows,
    gravity='top')

# 6) مواقيت اليوم — رملي ذهبي، قائمة الصلوات مع الإقامة
trows = ''
for i, (n, t) in enumerate(zip(["الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"],
                               ["5:22", "6:43", "12:08", "3:12", "5:33", "7:03"])):
    trows += hrow([tv(f'n{i}', 14, B, '@color/text', n, w='0dp').replace('android:layout_width="0dp"', 'android:layout_width="0dp" android:layout_weight="1"'),
                   tv(f'q{i}', 10, M, '@color/muted', '+20', 'android:layout_marginEnd="10dp" '),
                   tv(f't{i}', 15, B, '@color/text', t)],
                  f'android:id="@+id/r{i}" android:paddingStart="10dp" android:paddingEnd="10dp" android:paddingTop="6dp" android:paddingBottom="6dp" android:layout_marginTop="2dp" ')
layouts['widget_today'] = root('widget_bg_sand', 14,
    hrow([vbox([tv(None, 15, X, '@color/gold', 'مواقيت اليوم'),
                tv('hijri', 11, B, '@color/text', 'الثلاثاء 3 جمادى الآخرة 1448 هـ', 'android:layout_marginTop="4dp" android:ellipsize="end" '),
                tv('greg', 10, M, '@color/muted', '24 نوفمبر 2026 م', 'android:layout_marginTop="2dp" ')],
               'android:layout_weight="1" ', w='0dp'),
          vbox([tv('nextLabel', 10, M, '@color/muted', 'العصر بعد', 'android:layout_gravity="end" '),
                chrono('nextChrono', 16, X, '@color/gold', 'android:layout_gravity="end" android:text="1:22:10" ')], '', w='wrap_content')])
    + '<TextView android:layout_width="match_parent" android:layout_height="1dp" android:layout_marginTop="10dp" android:layout_marginBottom="4dp" android:background="#26E3B65C" />'
    + trows,
    gravity='top')

# 7) تقويم الأسبوع — ليلي، جدول سبعة أيام × خمس صلوات
head = hrow([tv(None, 10, B, '@color/gold', 'اليوم', w='0dp').replace('android:layout_width="0dp"', 'android:layout_width="0dp" android:layout_weight="1.5"')]
            + [tv(None, 10, B, '@color/gold', n, 'android:gravity="center" ', w='0dp').replace('android:layout_width="0dp"', 'android:layout_width="0dp" android:layout_weight="1"') for n in NAMES],
            'android:paddingStart="6dp" android:paddingEnd="6dp" android:paddingBottom="4dp" ')
wrows = ''
for d in range(7):
    cells = [vbox([tv(f'd{d}', 11, B, '@color/text', 'الثلاثاء'), tv(f'dd{d}', 9, M, '@color/muted', '3 جمادى')],
                  'android:layout_weight="1.5" ', w='0dp')]
    for j in range(5):
        cells.append(tv(f'x{d}_{j}', 11, M, '@color/text', TIMES[j], 'android:gravity="center" ', w='0dp')
                     .replace('android:layout_width="0dp"', 'android:layout_width="0dp" android:layout_weight="1"'))
    wrows += hrow(cells, f'android:id="@+id/w{d}" android:paddingStart="6dp" android:paddingEnd="6dp" '
                         'android:paddingTop="2dp" android:paddingBottom="2dp" android:layout_marginTop="1dp" ')
layouts['widget_week'] = root('widget_bg_night', 12,
    hrow([tv(None, 15, X, '@color/text', 'تقويم الأسبوع'), spacer(),
          tv('month', 11, B, '@color/gold', 'جمادى الآخرة 1448 هـ')])
    + tv('place', 10, M, '@color/muted', 'المدينة المنورة', 'android:layout_marginTop="3dp" ')
    + '<TextView android:layout_width="match_parent" android:layout_height="1dp" android:layout_marginTop="8dp" android:layout_marginBottom="6dp" android:background="#22FFFFFF" />'
    + head + wrows,
    gravity='top')

for name, xml in layouts.items():
    with open(os.path.join(RES, 'layout', name + '.xml'), 'w') as f:
        f.write(xml)

# ملفات التعريف: (الاسم، الخلايا عرضًا، ارتفاعًا، أدنى عرض، أدنى ارتفاع)
infos = {
    'widget_small': (2, 2, 110, 110), 'widget_ring': (2, 2, 110, 110), 'widget_strip': (4, 1, 250, 50),
    'widget_medium': (4, 2, 250, 120), 'widget_large': (4, 4, 250, 300), 'widget_today': (2, 3, 140, 200),
    'widget_week': (4, 3, 250, 230),
}
for name, (cw, ch, mw, mh) in infos.items():
    with open(os.path.join(RES, 'xml', name + '_info.xml'), 'w') as f:
        f.write(f'''<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider {NS}
    android:minWidth="{mw}dp" android:minHeight="{mh}dp"
    android:minResizeWidth="{max(40, mw - 40)}dp" android:minResizeHeight="{max(40, mh - 40)}dp"
    android:targetCellWidth="{cw}" android:targetCellHeight="{ch}"
    android:resizeMode="horizontal|vertical" android:widgetCategory="home_screen"
    android:initialLayout="@layout/{name}" android:previewLayout="@layout/{name}"
    android:updatePeriodMillis="1800000" android:description="@string/{name}" />
''')

# ثوابت معرّفات التقويم والشريط
ids = lambda pat, n: '{' + ', '.join(f'R.id.{pat.format(i)}' for i in range(n)) + '}'
week_cells = '{' + ', '.join('{' + ', '.join(f'R.id.x{d}_{j}' for j in range(5)) + '}' for d in range(7)) + '}'
with open(os.path.join(JAVA, 'WidgetIds.java'), 'w') as f:
    f.write(f'''package com.zarraqai.salati;

/** معرّفات العناصر المتكررة في الويدجتات (مولّدة). */
final class WidgetIds {{
    private WidgetIds() {{}}
    static final int[] ROW = {ids('r{}', 6)};
    static final int[] NAME = {ids('n{}', 6)};
    static final int[] TIME = {ids('t{}', 6)};
    static final int[] IQ = {ids('q{}', 6)};
    static final int[] COL = {ids('c{}', 5)};
    static final int[] COL_NAME = {ids('cn{}', 5)};
    static final int[] COL_TIME = {ids('ct{}', 5)};
    static final int[] COL_CHRONO = {ids('cc{}', 5)};
    static final int[] WEEK_ROW = {ids('w{}', 7)};
    static final int[] WEEK_DAY = {ids('d{}', 7)};
    static final int[] WEEK_DATE = {ids('dd{}', 7)};
    static final int[][] WEEK_CELL = {week_cells};
}}
''')
print('generated', len(layouts), 'layouts')
