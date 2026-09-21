"""Writes the app's palettes: the ones Settings offers, and the reasoning behind each.

    python tools/theme/schemes.py --apply     # rewrite Theme.kt and the colors.xml files
    python tools/theme/schemes.py --check     # contrast sweep over every palette
    python tools/theme/schemes.py --preview   # write preview.html comparing them

Needs only the standard library. --apply rewrites the generated region of
app/src/main/java/com/japanesedrills/ui/theme/Theme.kt and regenerates
app/src/main/res/values{,-night}/colors.xml; it touches nothing else in those files.
Re-running reproduces them byte for byte.

An earlier version of this file derived every role from one seed colour by walking a
tonal palette. That was dropped: the shipped scheme puts the accent where it means
something rather than where a tone curve lands it, and no seed produces that. Palettes are
written out in full instead, and `--check` is what keeps them honest. The one exception is
the hero card, which follows a single rule in every palette (see HERO_TINT).

To re-tune, edit the palette below and re-run --apply. `--check` must stay clean: every
foreground/background pair the app actually puts together needs 4.5:1.

A palette is more than colour: each names its two faces (files in res/font) and where its
accent goes beyond the shared roles. The type scale itself is Kotlin, in ui/theme/Type.kt.

Each palette name must also be an entry of `Palette` in quiz/QuizOptions.kt. The generated
code maps one to the other with an exhaustive `when`, so a mismatch fails to compile.
"""
import argparse
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]
THEME = ROOT / "app/src/main/java/com/japanesedrills/ui/theme/Theme.kt"
RES = ROOT / "app/src/main/res"

# The palette a fresh install starts in, and the one the window background and splash are
# drawn from: those are resources, resolved before the app has read which palette was chosen.
DEFAULT = "latte"

# The faces a palette may name, by res/font file, with the name the theme picker shows.
FACES = {
    "lora": "Lora",
    "manrope": "Manrope",
    "outfit": "Outfit",
    "fraunces": "Fraunces",
    "nunitosans": "Nunito Sans",
}

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


# The hero cards — the question, the welcome, the primer link and the score — are a light
# wash of the accent over the page, with ordinary ink on top. They used to be a slab of
# their own colour (espresso in Latte, caramel in Caramel), which made the question card the
# loudest object on the screen when the word inside it is already set at 44sp: the size
# carries the emphasis, so the card only has to say "this is the question". One rule for
# every palette, so no alternative can drift back into a slab.
HERO_TINT = 0.16


def mix(fg, bg, t):
    """[fg] laid over [bg] at opacity [t], as an opaque colour."""
    f, b = ([int(c.lstrip("#")[i:i + 2], 16) for i in (0, 2, 4)] for c in (fg, bg))
    return "#%02X%02X%02X" % tuple(round(t * x + (1 - t) * y) for x, y in zip(f, b))


def scheme(**kw):
    """One mode of a palette. Roles the app never touches are filled from ones it does."""
    s = dict(kw)
    s.setdefault("primaryContainer", mix(s["primary"], s["surface"], HERO_TINT))
    s.setdefault("onPrimaryContainer", s["onSurface"])
    s.setdefault("background", s["surface"])
    s.setdefault("onBackground", s["onSurface"])
    s.setdefault("secondary", s["primary"])
    s.setdefault("onSecondary", s["onPrimary"])
    s.setdefault("tertiary", s["primary"])
    s.setdefault("onTertiary", s["onPrimary"])
    s.setdefault("tertiaryContainer", s["secondaryContainer"])
    s.setdefault("onTertiaryContainer", s["onSecondaryContainer"])
    s.setdefault("surfaceBright", s["surface"])
    s.setdefault("surfaceDim", s["surfaceContainerHighest"])
    s.setdefault("surfaceContainerLowest", s["surfaceContainerLow"])
    s.setdefault("inversePrimary", s["primary"])
    missing = [r for r in ROLES + ANSWER_ROLES if r not in s]
    assert not missing, f"missing roles: {missing}"
    return s


# --- The shipped palette ---------------------------------------------------
#
# Latte. A warm cafe: cream page, white cards, a caramel-washed question card, and one
# caramel accent. The accent is spent on three things and nothing else — the form pill, the
# button that moves you on, and the tab you are in — so colour always means "act here".
# Section titles are plain ink for the same reason.

LATTE_LIGHT = scheme(
    primary="#904C1D", onPrimary="#FFFFFF",
    secondaryContainer="#F0E0CF", onSecondaryContainer="#3A2616",
    error="#9C3B2E", onError="#FFFFFF", errorContainer="#FBD9D0", onErrorContainer="#40120B",
    surface="#FCF7F1", onSurface="#2B1D16",
    surfaceVariant="#EFE2D5", onSurfaceVariant="#5A483C",
    outline="#8D7767", outlineVariant="#DECBB9",
    inverseSurface="#33241C", inverseOnSurface="#F7EDE3",
    surfaceContainerLow="#FFFFFF", surfaceContainer="#F8EFE6",
    surfaceContainerHigh="#F3E8DC", surfaceContainerHighest="#EDE0D2",
    correct="#3F6B2B", onCorrect="#FFFFFF", correctContainer="#D2EAC0", onCorrectContainer="#14260A",
)
LATTE_DARK = scheme(
    primary="#F0A868", onPrimary="#4A2708",
    secondaryContainer="#3E2E22", onSecondaryContainer="#F1DFCD",
    error="#FFB4A2", onError="#5C1A0F", errorContainer="#7A2E20", onErrorContainer="#FFDAD2",
    surface="#1C1511", onSurface="#EFE3D9",
    surfaceVariant="#4E3D31", onSurfaceVariant="#D6C3B4",
    outline="#9E8878", outlineVariant="#4E3D31",
    inverseSurface="#EFE3D9", inverseOnSurface="#2B1D16",
    surfaceContainerLow="#231A15", surfaceContainer="#27201A",
    surfaceContainerHigh="#332921", surfaceContainerHighest="#3F332A",
    correct="#A8CF8C", onCorrect="#1E3410", correctContainer="#374E26", onCorrectContainer="#D2EAC0",
)

# --- The other palettes -----------------------------------------------------
#
# Offered in Settings beside Latte. Each was built and compared in the running app, and each
# is a different answer to "which elements carry the accent", which is the decision that
# actually changes how a screen reads — so a palette carries its faces and its accent
# placement with it, not just its colours.

# Kissaten. Latte's structure in a dimmer room: mid-tone cards that sit close to the page,
# and the accent carried by *type* — section titles stay terracotta instead of going ink.
KISSATEN_LIGHT = scheme(
    primary="#8E4419", onPrimary="#FFFFFF",
    secondaryContainer="#E3D2BE", onSecondaryContainer="#2E2013",
    error="#96342A", onError="#FFFFFF", errorContainer="#F6D3CB", onErrorContainer="#3A0F09",
    surface="#F4EDE4", onSurface="#241A14",
    surfaceVariant="#E3D6C5", onSurfaceVariant="#57483A",
    outline="#8A7461", outlineVariant="#CDBCA6",
    inverseSurface="#241A14", inverseOnSurface="#F2E6D8",
    surfaceContainerLow="#FBF6EE", surfaceContainer="#EFE6DA",
    surfaceContainerHigh="#E9DED0", surfaceContainerHighest="#E2D5C4",
    correct="#3C6630", onCorrect="#FFFFFF", correctContainer="#CDE6BE", onCorrectContainer="#12250B",
)
KISSATEN_DARK = scheme(
    primary="#E89B62", onPrimary="#44220A",
    secondaryContainer="#3A2C1E", onSecondaryContainer="#E8D8C4",
    error="#FFB3A1", onError="#57180E", errorContainer="#752B1E", onErrorContainer="#FFDAD2",
    surface="#191310", onSurface="#EADFD3",
    surfaceVariant="#4A3B2D", onSurfaceVariant="#D2C1AE",
    outline="#9A8571", outlineVariant="#4A3B2D",
    inverseSurface="#EADFD3", inverseOnSurface="#241A14",
    surfaceContainerLow="#201813", surfaceContainer="#251D17",
    surfaceContainerHigh="#31271F", surfaceContainerHighest="#3D3128",
    correct="#A4CB88", onCorrect="#1B300F", correctContainer="#344A24", onCorrectContainer="#CDE6BE",
)

# Washi. White cards on warm paper and terracotta on nothing but the one thing you can act
# on. The crispest of the five in light; in dark the cards collapse towards the page and it
# loses its character, which is the argument against it.
WASHI_LIGHT = scheme(
    primary="#A34422", onPrimary="#FFFFFF",
    secondaryContainer="#F2EBE2", onSecondaryContainer="#241C17",
    error="#A03225", onError="#FFFFFF", errorContainer="#FAD6CD", onErrorContainer="#3B0E07",
    surface="#F7F3EC", onSurface="#241C17",
    surfaceVariant="#EAE3D8", onSurfaceVariant="#5C5248",
    outline="#9A8E80", outlineVariant="#D9D0C3",
    inverseSurface="#2E2620", inverseOnSurface="#F7F3EC",
    surfaceContainerLow="#FFFFFF", surfaceContainer="#FCF9F4",
    surfaceContainerHigh="#F2EDE5", surfaceContainerHighest="#EBE5DB",
    correct="#3E6A2A", onCorrect="#FFFFFF", correctContainer="#D6EDC6", onCorrectContainer="#132509",
)
WASHI_DARK = scheme(
    primary="#FF9E72", onPrimary="#54200B",
    secondaryContainer="#332C26", onSecondaryContainer="#EFE6DC",
    error="#FFB4A0", onError="#5C1A0D", errorContainer="#7A2C1C", onErrorContainer="#FFDAD2",
    surface="#151210", onSurface="#EFE6DC",
    surfaceVariant="#463E36", onSurfaceVariant="#CBC1B5",
    outline="#968B7E", outlineVariant="#463E36",
    inverseSurface="#EFE6DC", inverseOnSurface="#241C17",
    surfaceContainerLow="#1B1714", surfaceContainer="#201B18",
    surfaceContainerHigh="#2B2520", surfaceContainerHighest="#362F29",
    correct="#A9CE8E", onCorrect="#1D3311", correctContainer="#364C27", onCorrectContainer="#D6EDC6",
)

# Caramel. The buttons go espresso and the page goes warmer. It was built with the question
# card as a caramel slab, which is why it was not taken: a big saturated field behind the
# thing you must read is tiring, and on the learn path the filled card read as a button.
# That slab is gone now that every palette shares the hero rule.
CARAMEL_LIGHT = scheme(
    primary="#4A3021", onPrimary="#FFF6ED",
    secondaryContainer="#F6E3CC", onSecondaryContainer="#3A2614",
    error="#9B3526", onError="#FFFFFF", errorContainer="#FBD7CD", onErrorContainer="#3C0F07",
    surface="#FDF8F2", onSurface="#2E2118",
    surfaceVariant="#EFE1D0", onSurfaceVariant="#5C4B3C",
    outline="#907B67", outlineVariant="#DFCDB8",
    inverseSurface="#2E2118", inverseOnSurface="#FDF8F2",
    surfaceContainerLow="#FFFFFF", surfaceContainer="#F9F0E5",
    surfaceContainerHigh="#F4E9DB", surfaceContainerHighest="#EEE1D0",
    correct="#3F6B2B", onCorrect="#FFFFFF", correctContainer="#D2EAC0", onCorrectContainer="#14260A",
)
CARAMEL_DARK = scheme(
    primary="#F4D0A6", onPrimary="#3A2513",
    secondaryContainer="#40301F", onSecondaryContainer="#F3E1C9",
    error="#FFB4A2", onError="#5C1A0F", errorContainer="#7A2E20", onErrorContainer="#FFDAD2",
    surface="#1D1611", onSurface="#F1E5D9",
    surfaceVariant="#50402F", onSurfaceVariant="#D8C6B1",
    outline="#A08B74", outlineVariant="#50402F",
    inverseSurface="#F1E5D9", inverseOnSurface="#2E2118",
    surfaceContainerLow="#241B15", surfaceContainer="#29201A",
    surfaceContainerHigh="#352A21", surfaceContainerHighest="#41342A",
    correct="#A8CF8C", onCorrect="#1E3410", correctContainer="#374E26", onCorrectContainer="#D2EAC0",
)

# Mocha. Honey and cocoa, no serif, accent on anything stateful. The cosiest colour and the
# weakest hierarchy: with the accent on every stateful thing, nothing stands out.
MOCHA_LIGHT = scheme(
    primary="#7E5213", onPrimary="#FFFFFF",
    secondaryContainer="#EFE2CE", onSecondaryContainer="#2E2214",
    error="#98362A", onError="#FFFFFF", errorContainer="#F8D6CD", onErrorContainer="#3A1009",
    surface="#FAF4EA", onSurface="#271E14",
    surfaceVariant="#EBE0CE", onSurfaceVariant="#584B39",
    outline="#8C7D67", outlineVariant="#D4C6AE",
    inverseSurface="#271E14", inverseOnSurface="#FAF4EA",
    surfaceContainerLow="#FFFCF6", surfaceContainer="#F5EDE1",
    surfaceContainerHigh="#EFE6D8", surfaceContainerHighest="#E8DECE",
    correct="#3F6B2B", onCorrect="#FFFFFF", correctContainer="#D2EAC0", onCorrectContainer="#14260A",
)
MOCHA_DARK = scheme(
    primary="#F0B75E", onPrimary="#412C00",
    secondaryContainer="#40342A", onSecondaryContainer="#EFE0CE",
    error="#FFB4A2", onError="#5C1A0F", errorContainer="#7E3020", onErrorContainer="#FFDAD2",
    surface="#1F1712", onSurface="#F0E5D8",
    surfaceVariant="#51433A", onSurfaceVariant="#D6C6B5",
    outline="#9F8C79", outlineVariant="#51433A",
    inverseSurface="#F0E5D8", inverseOnSurface="#271E14",
    surfaceContainerLow="#261D17", surfaceContainer="#2C221B",
    surfaceContainerHigh="#382C24", surfaceContainerHighest="#44362C",
    correct="#A8CF8C", onCorrect="#1E3410", correctContainer="#374E26", onCorrectContainer="#D2EAC0",
)

# Where the accent goes beyond the shared roles: section titles, and the word-class
# headings in the grammar reference. Plain ink wherever it is off.
INK_TITLES = dict(titles=False, headings=True)
ACCENT_TITLES = dict(titles=True, headings=True)
QUIET = dict(titles=False, headings=False)

PALETTES = {
    # name: (light, dark, display face, body face, accent placement), in Settings order
    "latte": (LATTE_LIGHT, LATTE_DARK, "lora", "manrope", INK_TITLES),
    "kissaten": (KISSATEN_LIGHT, KISSATEN_DARK, "lora", "manrope", ACCENT_TITLES),
    "washi": (WASHI_LIGHT, WASHI_DARK, "outfit", "outfit", QUIET),
    "caramel": (CARAMEL_LIGHT, CARAMEL_DARK, "fraunces", "nunitosans", INK_TITLES),
    "mocha": (MOCHA_LIGHT, MOCHA_DARK, "manrope", "manrope", ACCENT_TITLES),
}


def faces(display, body):
    return FACES[display] if display == body else f"{FACES[display]} · {FACES[body]}"


# --- Contrast --------------------------------------------------------------

def _lin(c):
    c /= 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def _lum(h):
    h = h.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4))
    return 0.2126 * _lin(r) + 0.7152 * _lin(g) + 0.0722 * _lin(b)


def contrast(a, b):
    la, lb = _lum(a), _lum(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)


# Every pair the app actually renders together. Anything below 4.5 here is a real
# legibility bug, not a theoretical one.
PAIRS = ([(f"on{r[0].upper() + r[1:]}", r) for r in ("primary", "error", "correct", "surface")]
         + [("onPrimaryContainer", "primaryContainer"),
            ("onSecondaryContainer", "secondaryContainer"),
            ("onErrorContainer", "errorContainer"),
            ("onCorrectContainer", "correctContainer"),
            ("onSurfaceVariant", "surfaceVariant")]
         + [(fg, bg)
            for fg in ("onSurface", "onSurfaceVariant", "primary", "error")
            for bg in ("surface", "surfaceContainerLow", "surfaceContainer",
                       "surfaceContainerHigh", "surfaceContainerHighest")])


def check():
    bad = 0
    for name, (light, dark, display, body, _accents) in PALETTES.items():
        for tag, s in (("light", light), ("dark", dark)):
            for fg, bg in PAIRS:
                ratio = contrast(s[fg], s[bg])
                if ratio < 4.5:
                    bad += 1
                    print(f"  {name:9} {tag:5} {ratio:5.2f}  {fg} on {bg}  ({s[fg]}/{s[bg]})")
        print(f"{name:9} {faces(display, body)}")
    print("\nall pairs >= 4.5" if not bad else f"\n{bad} pairs below 4.5")
    return bad


# --- Apply -----------------------------------------------------------------

GENERATED = re.compile(r"(// <generated by tools/theme/schemes.py --apply>\n).*?(// </generated>\n)", re.S)


def kotlin_palettes():
    col = lambda v: "Color(0xFF%s)" % v.lstrip("#")

    def roles(names, s, indent):
        return "".join("%s%s = %s,\n" % (indent, r, col(s[r])) for r in names)

    out = []
    for name, (light, dark, display, body, accents) in PALETTES.items():
        out.append(
            f"private val {name.capitalize()}Spec = PaletteSpec(\n"
            f"    light = lightColorScheme(\n{roles(ROLES, light, ' ' * 8)}    ),\n"
            f"    dark = darkColorScheme(\n{roles(ROLES, dark, ' ' * 8)}    ),\n"
            f"    lightAnswers = AnswerColors(\n{roles(ANSWER_ROLES, light, ' ' * 8)}    ),\n"
            f"    darkAnswers = AnswerColors(\n{roles(ANSWER_ROLES, dark, ' ' * 8)}    ),\n"
            f"    display = R.font.{display},\n"
            f"    body = R.font.{body},\n"
            f'    faces = "{faces(display, body)}",\n'
            f"    accents = Accents(titles = {str(accents['titles']).lower()}, "
            f"headings = {str(accents['headings']).lower()}),\n"
            f")\n")
    cases = "".join(f"    Palette.{n.capitalize()} -> {n.capitalize()}Spec\n" for n in PALETTES)
    out.append(f"private fun specOf(palette: Palette): PaletteSpec = when (palette) {{\n{cases}}}\n")
    return "\n".join(out)


def apply():
    for display, body in {(p[2], p[3]) for p in PALETTES.values()}:
        for face in (display, body):
            assert (RES / "font" / f"{face}.ttf").exists(), f"res/font/{face}.ttf is missing"

    src = THEME.read_text(encoding="utf-8")
    assert GENERATED.search(src), "Theme.kt has lost its generated markers"
    src = GENERATED.sub(lambda m: m.group(1) + kotlin_palettes() + m.group(2), src)
    THEME.write_text(src, encoding="utf-8")

    light, dark = PALETTES[DEFAULT][:2]

    (RES / "values/colors.xml").write_text(
        '<resources>\n    <color name="window_background">#FF%s</color>\n'
        '    <color name="ic_launcher_background">#FF%s</color>\n</resources>\n'
        % (light["surface"].lstrip("#"), light["primary"].lstrip("#")), encoding="utf-8")
    (RES / "values-night/colors.xml").write_text(
        '<resources>\n    <color name="window_background">#FF%s</color>\n</resources>\n'
        % dark["surface"].lstrip("#"), encoding="utf-8")
    print(f"wrote {len(PALETTES)} palettes; window background from {DEFAULT}")


# --- Preview ---------------------------------------------------------------

def preview():
    def ui(s):
        return f'''<div class="phone" style="background:{s['background']};color:{s['onBackground']}">
  <div class="tabs" style="border-color:{s['outlineVariant']}">
    <span style="color:{s['primary']};border-bottom:2px solid {s['primary']}">Learn</span>
    <span style="color:{s['primary']};opacity:.75">Practice</span>
    <span style="color:{s['primary']};opacity:.75">Grammar</span></div>
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
    <div class="t">First verbs</div>
    <div class="sub" style="color:{s['onSurfaceVariant']}">Plain and polite present tense</div></div>
  <div class="card" style="background:{s['surfaceContainerHigh']}">
    <div class="t">Negative</div>
    <div class="sub" style="color:{s['onSurfaceVariant']}">Saying something does not happen</div></div>
  <div class="start">
    <span class="btn" style="border:1px solid {s['outline']};color:{s['primary']}">Reset</span>
    <span class="btn" style="background:{s['primary']};color:{s['onPrimary']}">Check</span></div></div>'''

    cols = ""
    for name, (light, dark, display, body, _accents) in PALETTES.items():
        mark = " (default)" if name == DEFAULT else ""
        cols += (f'<div class="col"><h3>{name}{mark} <em>{faces(display, body)}</em></h3>'
                 f'<div class="cap">light</div>{ui(light)}'
                 f'<div class="cap">dark</div>{ui(dark)}</div>')

    out = pathlib.Path(__file__).resolve().parent / "preview.html"
    out.write_text(f'''<!doctype html><meta charset="utf-8"><title>Colour schemes</title><style>
 body{{margin:0;padding:14px;background:#e9e9ee;font:13px/1.4 system-ui,sans-serif}}
 .wrap{{display:flex;gap:14px;align-items:flex-start}} .col{{flex:0 0 250px}}
 h3{{font-size:13px;margin:0 0 8px;text-transform:capitalize}}
 h3 em{{font-weight:400;color:#666;font-style:normal;font-size:11px}}
 .cap{{font-size:10px;color:#666;margin:0 0 3px 2px}}
 .phone{{border-radius:18px;padding:10px;margin-bottom:12px;box-shadow:0 1px 4px #0003}}
 .tabs{{display:flex;gap:14px;font-size:11px;font-weight:600;padding:0 2px 7px;
        border-bottom:1px solid;margin-bottom:9px}}
 .card{{border-radius:14px;padding:10px 12px;margin-bottom:9px}}
 .t{{font-weight:600;font-size:12px}} .sub{{font-size:10px;margin-top:2px}}
 .q{{text-align:center;padding:14px 12px}} .lbl{{font-size:10px;opacity:.75}}
 .pill{{display:inline-block;border-radius:9px;padding:4px 12px;font-weight:700;font-size:13px;margin:5px 0 8px}}
 .word{{font-size:27px;font-weight:600}} .row{{display:flex;gap:7px;margin-bottom:9px}}
 .ans{{flex:1;border-radius:12px;padding:8px 9px;font-size:12px;display:flex;align-items:center;gap:6px}}
 .dot{{width:16px;height:16px;border-radius:50%;font-size:10px;text-align:center;line-height:16px;flex:0 0 16px}}
 .start{{display:flex;gap:6px;justify-content:flex-end}}
 .btn{{border-radius:14px;padding:5px 14px;font-size:11px;font-weight:600}}
</style><div class="wrap">{cols}</div>''', encoding="utf-8")
    print("wrote", out)


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--apply", action="store_true", help="rewrite Theme.kt and colors.xml")
    ap.add_argument("--check", action="store_true", help="contrast sweep over every palette")
    ap.add_argument("--preview", action="store_true", help="write preview.html")
    args = ap.parse_args()
    if args.check:
        raise SystemExit(1 if check() else 0)
    if args.apply:
        apply()
    if args.preview:
        preview()
    if not (args.apply or args.preview or args.check):
        ap.print_help()
