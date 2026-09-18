"""Generates the app's Material 3 colour scheme from a single seed colour.

    python tools/theme/schemes.py --apply     # rewrite Theme.kt and the colors.xml files
    python tools/theme/schemes.py --preview   # write preview.html comparing candidate seeds

Needs only the standard library. --apply rewrites the colour blocks in
app/src/main/java/com/japanesedrills/ui/theme/Theme.kt in place and regenerates
app/src/main/res/values{,-night}/colors.xml; it touches nothing else in those files.
Re-running with ACTIVE unchanged reproduces them byte for byte.

To re-tune, edit ACTIVE below and re-run --apply:

  seed      the one colour everything else is derived from
  neutral   how much of the seed's hue bleeds into the greys. 0.09 gives the warm
            off-white/near-black paper feel; near 0 gives neutral grey surfaces
  soften    dark-mode chroma for filled areas, as a fraction of the light palette's

Why `soften` exists at all: light and dark pick different tones from the same palette.
Light containers sit at tone 90, where sRGB cannot hold much chroma, so they come out
pastel whether or not you intended it. The same roles in dark sit near tone 30, where
it can, so at equal chroma a dark container becomes a saturated slab and the dark theme
feels far harsher than the light one. Dark chroma is therefore scaled down; accents keep
more than containers (ACCENT below) because they are small and go lifelessly grey first.

Use --preview to compare seeds before committing to one. It renders the screens where
colour actually shows - question card, correct/wrong answers, chips, start bar - in
light and dark, side by side, which is the only reliable way to judge this.
"""
import argparse
import math
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]
THEME = ROOT / "app/src/main/java/com/japanesedrills/ui/theme/Theme.kt"
RES = ROOT / "app/src/main/res"

# The scheme the app ships: 墨 sumi, ink and gold.
ACTIVE = dict(seed="#8A6A1F", neutral=0.09, soften=0.65)

# Alternatives kept for --preview; the first entry is whatever ACTIVE currently is.
CANDIDATES = [
    ("Sumi - ink + gold (active)", "#8A6A1F", 0.09),
    ("Ai - indigo", "#2E4D8F", 0.055),
    ("Cha - warm paper", "#8A4B2A", 0.075),
    ("Edomurasaki - the old purple", "#77428D", 0.055),
]

CONTAINER_TONE, ACCENT = 26, 0.85      # dark-mode container tone, dark-mode accent chroma
GREEN = "#3A6B23"                      # "correct", deliberately independent of the seed
RED = "#BA1A1A"                        # "wrong"

# --- sRGB <-> CIELAB -------------------------------------------------------
WP = (0.95047, 1.0, 1.08883)


def _lin(c):
    c /= 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def _unlin(c):
    c = 12.92 * c if c <= 0.0031308 else 1.055 * (c ** (1 / 2.4)) - 0.055
    return max(0, min(255, round(c * 255)))


def _xyz(lab):
    L, a, b = lab
    fy = (L + 16) / 116
    fx, fz = fy + a / 500, fy - b / 200
    inv = lambda t: t ** 3 if t ** 3 > 0.008856 else (t - 16 / 116) / 7.787
    return inv(fx) * WP[0], inv(fy) * WP[1], inv(fz) * WP[2]


def _rgb(lab):
    x, y, z = _xyz(lab)
    return (3.2406 * x - 1.5372 * y - 0.4986 * z,
            -0.9689 * x + 1.8758 * y + 0.0415 * z,
            0.0557 * x - 0.2040 * y + 1.0570 * z)


def rgb_to_lab(rgb):
    r, g, b = (_lin(v) for v in rgb)
    x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / WP[0]
    y = (0.2126 * r + 0.7152 * g + 0.0722 * b) / WP[1]
    z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / WP[2]
    f = lambda t: t ** (1 / 3) if t > 0.008856 else 7.787 * t + 16 / 116
    fx, fy, fz = f(x), f(y), f(z)
    return (116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))


def parse(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def tone(seed, t, chroma_scale=1.0):
    """The seed's hue at lightness `t`, the way an M3 tonal palette works.

    Chroma drops only as far as sRGB actually requires, so pale tones keep the faint
    tint that gives surfaces their character instead of going flat white.
    """
    L, a, b = rgb_to_lab(parse(seed))
    want, h = math.hypot(a, b) * chroma_scale, math.atan2(b, a)
    lo, hi = 0.0, want
    for _ in range(24):                       # binary search the largest in-gamut chroma
        mid = (lo + hi) / 2
        if all(-0.001 <= c <= 1.001 for c in _rgb((t, mid * math.cos(h), mid * math.sin(h)))):
            lo = mid
        else:
            hi = mid
    return "#%02X%02X%02X" % tuple(_unlin(c) for c in _rgb((t, lo * math.cos(h), lo * math.sin(h))))


def scheme(seed, neutral, soften, dark=False):
    k = soften if dark else 1.0
    P = lambda t, c=1.0: tone(seed, t, c * k)
    S = lambda t, c=1.0: tone(seed, t, 0.34 * c * k)
    N = lambda t: tone(seed, t, neutral)
    NV = lambda t: tone(seed, t, neutral * 2.5)
    E = lambda t, c=1.0: tone(RED, t, c * k)
    G = lambda t, c=1.0: tone(GREEN, t, c * k)
    if not dark:
        return dict(
            primary=P(40), onPrimary=P(100), primaryContainer=P(90), onPrimaryContainer=P(10),
            secondary=S(40), onSecondary=S(100), secondaryContainer=S(90), onSecondaryContainer=S(10),
            tertiary=S(40), onTertiary=S(100), tertiaryContainer=S(90), onTertiaryContainer=S(10),
            error=E(40), onError=E(100), errorContainer=E(90), onErrorContainer=E(10),
            background=N(99), onBackground=N(10), surface=N(99), onSurface=N(10),
            surfaceVariant=NV(90), onSurfaceVariant=NV(30), outline=NV(50), outlineVariant=NV(80),
            inverseSurface=N(20), inverseOnSurface=N(95), inversePrimary=P(80),
            surfaceBright=N(99), surfaceDim=N(87),
            surfaceContainerLowest=N(100), surfaceContainerLow=N(96), surfaceContainer=N(94),
            surfaceContainerHigh=N(92), surfaceContainerHighest=N(90),
            correct=G(40), onCorrect=G(100), correctContainer=G(90), onCorrectContainer=G(10))
    C, A, ct = soften, ACCENT, CONTAINER_TONE
    return dict(
        primary=P(80, A), onPrimary=P(20, A), primaryContainer=P(ct, C), onPrimaryContainer=P(90, C),
        secondary=S(80, A), onSecondary=S(20, A), secondaryContainer=S(ct, C), onSecondaryContainer=S(90, C),
        tertiary=S(80, A), onTertiary=S(20, A), tertiaryContainer=S(ct, C), onTertiaryContainer=S(90, C),
        error=E(80, A), onError=E(20, A), errorContainer=E(30, C), onErrorContainer=E(90, C),
        background=N(10), onBackground=N(90), surface=N(10), onSurface=N(90),
        surfaceVariant=NV(30), onSurfaceVariant=NV(80), outline=NV(60), outlineVariant=NV(30),
        inverseSurface=N(90), inverseOnSurface=N(20), inversePrimary=P(40),
        surfaceBright=N(24), surfaceDim=N(6),
        surfaceContainerLowest=N(4), surfaceContainerLow=N(10), surfaceContainer=N(12),
        surfaceContainerHigh=N(17), surfaceContainerHighest=N(22),
        correct=G(80, A), onCorrect=G(20, A), correctContainer=G(30, C), onCorrectContainer=G(90, C))


ROLES = """primary onPrimary primaryContainer onPrimaryContainer
secondary onSecondary secondaryContainer onSecondaryContainer
tertiary onTertiary tertiaryContainer onTertiaryContainer
error onError errorContainer onErrorContainer
background onBackground surface onSurface
surfaceVariant onSurfaceVariant outline outlineVariant
inverseSurface inverseOnSurface inversePrimary
surfaceBright surfaceDim
surfaceContainerLowest surfaceContainerLow surfaceContainer
surfaceContainerHigh surfaceContainerHighest""".split()
ANSWER_ROLES = ["correct", "onCorrect", "correctContainer", "onCorrectContainer"]


def apply():
    light = scheme(ACTIVE["seed"], ACTIVE["neutral"], ACTIVE["soften"])
    dark = scheme(ACTIVE["seed"], ACTIVE["neutral"], ACTIVE["soften"], dark=True)
    col = lambda v: "Color(0xFF%s)" % v.lstrip("#")
    block = lambda s: "\n".join("    %s = %s," % (r, col(s[r])) for r in ROLES)
    answers = lambda n, s: ("private val %s = AnswerColors(\n" % n
                            + "".join("    %s = %s,\n" % (r, col(s[r])) for r in ANSWER_ROLES) + ")")

    src = THEME.read_text(encoding="utf-8")
    src = re.sub(r"(private val LightColors = lightColorScheme\(\n).*?(\n\)\n)",
                 lambda m: m.group(1) + block(light) + m.group(2), src, flags=re.S)
    src = re.sub(r"(private val DarkColors = darkColorScheme\(\n).*?(\n\)\n)",
                 lambda m: m.group(1) + block(dark) + m.group(2), src, flags=re.S)
    src = re.sub(r"private val LightAnswerColors = AnswerColors\(.*?\n\)",
                 answers("LightAnswerColors", light), src, flags=re.S)
    src = re.sub(r"private val DarkAnswerColors = AnswerColors\(.*?\n\)",
                 answers("DarkAnswerColors", dark), src, flags=re.S)
    THEME.write_text(src, encoding="utf-8")

    icon = tone(ACTIVE["seed"], 42)            # launcher background, behind the white あ
    (RES / "values/colors.xml").write_text(
        '<resources>\n    <color name="window_background">#FF%s</color>\n'
        '    <color name="ic_launcher_background">#FF%s</color>\n</resources>\n'
        % (light["surface"].lstrip("#"), icon.lstrip("#")), encoding="utf-8")
    (RES / "values-night/colors.xml").write_text(
        '<resources>\n    <color name="window_background">#FF%s</color>\n</resources>\n'
        % dark["surface"].lstrip("#"), encoding="utf-8")
    print("applied seed=%(seed)s neutral=%(neutral)s soften=%(soften)s" % ACTIVE)
    print("  light surface %s  dark surface %s  icon %s" % (light["surface"], dark["surface"], icon))


def preview():
    def ui(s):
        return f'''<div class="phone" style="background:{s['background']};color:{s['onBackground']}">
  <div class="card" style="background:{s['surfaceContainerLow']}">
    <div class="t" style="color:{s['primary']}">Forms</div>
    <div class="chips">
      <span class="chip" style="background:{s['secondaryContainer']};color:{s['onSecondaryContainer']}">✓ Plain</span>
      <span class="chip o" style="border-color:{s['outline']};color:{s['onSurface']}">Polite</span></div></div>
  <div class="card q" style="background:{s['primaryContainer']};color:{s['onPrimaryContainer']}">
    <div class="lbl">change to</div>
    <div class="pill" style="background:{s['primary']};color:{s['onPrimary']}">past tense</div>
    <div class="word">食べる</div></div>
  <div class="row">
    <div class="ans" style="background:{s['correctContainer']};color:{s['onCorrectContainer']}">
      <span class="dot" style="background:{s['correct']};color:{s['onCorrect']}">✓</span> たべた</div>
    <div class="ans" style="background:{s['errorContainer']};color:{s['onErrorContainer']}">
      <span class="dot" style="background:{s['error']};color:{s['onError']}">✕</span> たべない</div></div>
  <div class="card" style="background:{s['surfaceContainerLow']}">
    <div class="sec" style="color:{s['primary']}">SOLUTION</div>
    <div class="box" style="background:{s['surfaceContainerHighest']};color:{s['onSurface']}">食べる → 食べた</div></div>
  <div class="start" style="background:{s['surfaceContainer']}">
    <div class="counts" style="color:{s['onSurfaceVariant']}">Words <b style="color:{s['onSurface']}">744</b></div>
    <span class="btn" style="border:1px solid {s['outline']};color:{s['primary']}">Reset</span>
    <span class="btn" style="background:{s['primary']};color:{s['onPrimary']}">Go</span></div></div>'''

    cols = ""
    for title, seed, nc in CANDIDATES:
        light = scheme(seed, nc, ACTIVE["soften"])
        dark = scheme(seed, nc, ACTIVE["soften"], dark=True)
        cols += (f'<div class="col"><h3>{title} <em>{seed}</em></h3>'
                 f'<div class="cap">light</div>{ui(light)}'
                 f'<div class="cap">dark &mdash; soften {ACTIVE["soften"]}</div>{ui(dark)}</div>')

    out = pathlib.Path(__file__).resolve().parent / "preview.html"
    out.write_text(f'''<!doctype html><meta charset="utf-8"><title>Colour schemes</title><style>
 body{{margin:0;padding:14px;background:#e9e9ee;font:13px/1.4 system-ui,sans-serif}}
 .wrap{{display:flex;gap:14px;align-items:flex-start}} .col{{flex:0 0 240px}}
 h3{{font-size:13px;margin:0 0 8px}} h3 em{{font-weight:400;color:#666;font-style:normal;font-size:11px}}
 .cap{{font-size:10px;color:#666;margin:0 0 3px 2px}}
 .phone{{border-radius:18px;padding:10px;margin-bottom:12px;box-shadow:0 1px 4px #0003}}
 .card{{border-radius:14px;padding:10px 12px;margin-bottom:9px}}
 .t{{font-weight:600;font-size:12px;margin-bottom:6px}}
 .chips{{display:flex;gap:4px}} .chip{{border-radius:7px;padding:3px 8px;font-size:10px}}
 .chip.o{{border:1px solid}} .q{{text-align:center;padding:14px 12px}}
 .lbl{{font-size:10px;opacity:.75}}
 .pill{{display:inline-block;border-radius:9px;padding:4px 12px;font-weight:700;font-size:13px;margin:5px 0 8px}}
 .word{{font-size:27px;font-weight:600}} .row{{display:flex;gap:7px;margin-bottom:9px}}
 .ans{{flex:1;border-radius:12px;padding:8px 9px;font-size:12px;display:flex;align-items:center;gap:6px}}
 .dot{{width:16px;height:16px;border-radius:50%;font-size:10px;text-align:center;line-height:16px;flex:0 0 16px}}
 .sec{{font-size:9px;letter-spacing:.9px;font-weight:600;margin-bottom:6px}}
 .box{{display:inline-block;border-radius:7px;padding:4px 8px;font-size:12px}}
 .start{{border-radius:12px;padding:8px 10px;display:flex;align-items:center;gap:6px}}
 .counts{{flex:1;font-size:9px}} .btn{{border-radius:14px;padding:5px 12px;font-size:11px;font-weight:600}}
</style><div class="wrap">{cols}</div>''', encoding="utf-8")
    print("wrote", out)


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--apply", action="store_true", help="rewrite Theme.kt and colors.xml")
    ap.add_argument("--preview", action="store_true", help="write preview.html")
    args = ap.parse_args()
    if args.apply:
        apply()
    if args.preview:
        preview()
    if not (args.apply or args.preview):
        ap.print_help()
