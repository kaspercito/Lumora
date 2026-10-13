"""Genera el icono de Lumora: vectores XML + PNGs (mismo diseño en ambos)."""
import os, numpy as np
from PIL import Image, ImageDraw, ImageFont

RES = "./app/src/main/res"
MAIN = "./app/src/main"
CYAN, VIOLET, PINK = (34, 211, 238), (167, 139, 250), (244, 114, 182)
BG1, BG2 = (42, 27, 94), (11, 7, 32)

# ---------------------------------------------------------------- XML
NS = ('xmlns:android="http://schemas.android.com/apk/res/android" '
      'xmlns:aapt="http://schemas.android.com/aapt"')

def grad_attr(name):
    return f'''<aapt:attr name="android:{name}">
            <gradient android:type="linear" android:startX="28" android:startY="26"
                android:endX="82" android:endY="82">
                <item android:offset="0.0" android:color="#22D3EE"/>
                <item android:offset="0.55" android:color="#A78BFA"/>
                <item android:offset="1.0" android:color="#F472B6"/>
            </gradient>
        </aapt:attr>'''

RING = "M29,54 A25,25 0 1,1 79,54 A25,25 0 1,1 29,54 Z"
TRI = "M44,41 L44,67 L66,54 Z"
SPARK = "M76,23 L78,30 L85,32 L78,34 L76,41 L74,34 L67,32 L74,30 Z"
SPARK2 = "M32,74 L33,77.5 L36.5,78.5 L33,79.5 L32,83 L31,79.5 L27.5,78.5 L31,77.5 Z"

def mark_paths(mono=False, solid=None):
    if mono or solid:
        c = "#000000" if mono else solid
        return f'''<path android:pathData="{RING}" android:strokeWidth="4.5" android:strokeColor="{c}"/>
    <path android:pathData="{TRI}" android:fillColor="{c}" android:strokeColor="{c}" android:strokeWidth="4" android:strokeLineJoin="round"/>
    <path android:pathData="{SPARK}" android:fillColor="{c}"/>
    <path android:pathData="{SPARK2}" android:fillColor="{c}"/>'''
    return f'''<path android:pathData="{RING}" android:strokeWidth="4.5">
        {grad_attr("strokeColor")}
    </path>
    <path android:pathData="{TRI}" android:strokeWidth="4" android:strokeLineJoin="round">
        {grad_attr("fillColor")}
        {grad_attr("strokeColor")}
    </path>
    <path android:pathData="{SPARK}" android:fillColor="#FFFFFF"/>
    <path android:pathData="{SPARK2}" android:fillColor="#A5F3FC"/>'''

def vec(inner, w, h, vw, vh, crop=False):
    # crop=True: recorta el logo a la zona 18..90 (viewport 72) para usarlo suelto
    if crop:
        inner = f'<group android:translateX="-18" android:translateY="-18">\n    {inner}\n  </group>'
    return f'''<?xml version="1.0" encoding="utf-8"?>
<vector {NS}
    android:width="{w}dp" android:height="{h}dp"
    android:viewportWidth="{vw}" android:viewportHeight="{vh}">
    {inner}
</vector>
'''

def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w", encoding="utf-8").write(text)

# adaptive icon
write(f"{RES}/drawable/ic_launcher_foreground.xml", vec(mark_paths(), 108, 108, 108, 108))
write(f"{RES}/drawable/ic_launcher_background_lumora.xml", f'''<?xml version="1.0" encoding="utf-8"?>
<vector {NS}
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="0" android:startY="0" android:endX="108" android:endY="108">
                <item android:offset="0.0" android:color="#2A1B5E"/>
                <item android:offset="1.0" android:color="#0B0720"/>
            </gradient>
        </aapt:attr>
    </path>
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="54" android:centerY="54" android:gradientRadius="46">
                <item android:offset="0.0" android:color="#667C3AED"/>
                <item android:offset="1.0" android:color="#007C3AED"/>
            </gradient>
        </aapt:attr>
    </path>
</vector>
''')
write(f"{RES}/values/ic_launcher_background.xml", '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#1B1036</color>
</resources>
''')
for n in ("ic_launcher", "ic_launcher_round"):
    write(f"{RES}/mipmap-anydpi-v26/{n}.xml", '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background_lumora"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
    <monochrome android:drawable="@drawable/ic_cloudstream_monochrome"/>
</adaptive-icon>
''')
# monocromo (Android 13 themed icons) y notificaciones
write(f"{RES}/drawable/ic_cloudstream_monochrome.xml", vec(mark_paths(mono=True), 108, 108, 108, 108))
write(f"{RES}/drawable/ic_cloudstream_monochrome_big.xml", vec(mark_paths(mono=True), 108, 108, 72, 72, crop=True))
# logos sueltos dentro de la app
for n in ("cloud_2", "cloud_2_gradient", "cloud_2_gradient_beta", "cloud_2_gradient_beta_old", "cloud_2_gradient_debug"):
    write(f"{RES}/drawable/{n}.xml", vec(mark_paths(), 200, 200, 72, 72, crop=True))
write(f"{RES}/drawable/cloud_2_solid.xml", vec(mark_paths(solid="#FFFFFF"), 200, 200, 72, 72, crop=True))

# ---------------------------------------------------------------- PNG
SS = 4
def lerp(a, b, t): return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))

def gradient_img(w, h, p0, p1, stops):
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    t = np.clip(((xs - p0[0]) * dx + (ys - p0[1]) * dy) / (dx * dx + dy * dy), 0, 1)
    out = np.zeros((h, w, 3), np.float32)
    for i in range(len(stops) - 1):
        (o0, c0), (o1, c1) = stops[i], stops[i + 1]
        m = (t >= o0) & (t <= o1)
        u = ((t - o0) / (o1 - o0))[..., None]
        seg = np.array(c0) + (np.array(c1) - np.array(c0)) * u
        out = np.where(m[..., None], seg, out)
    return Image.fromarray(out.astype(np.uint8), "RGB")

MARK_STOPS = [(0, CYAN), (0.55, VIOLET), (1, PINK)]

def draw_mark(canvas, k, ox, oy):
    """Dibuja el logo (coordenadas 108) sobre canvas: px = (v - o) * k"""
    W, H = canvas.size
    P = lambda x, y: ((x - ox) * k, (y - oy) * k)
    mask = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(mask)
    cx, cy = P(54, 54); r = 25 * k; sw = int(4.5 * k)
    d.ellipse([cx - r - sw / 2, cy - r - sw / 2, cx + r + sw / 2, cy + r + sw / 2], outline=255, width=sw)
    tri = [P(44, 41), P(44, 67), P(66, 54)]
    d.polygon(tri, fill=255)
    d.line(tri + [tri[0]], fill=255, width=int(4 * k), joint="curve")
    for (x, y) in tri:
        rr = 2 * k; d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=255)
    g = gradient_img(W, H, P(28, 26), P(82, 82), MARK_STOPS)
    canvas.paste(g, (0, 0), mask)
    d2 = ImageDraw.Draw(canvas)
    sp = lambda pts: [P(x, y) for x, y in pts]
    d2.polygon(sp([(76,23),(78,30),(85,32),(78,34),(76,41),(74,34),(67,32),(74,30)]), fill=(255, 255, 255))
    d2.polygon(sp([(32,74),(33,77.5),(36.5,78.5),(33,79.5),(32,83),(31,79.5),(27.5,78.5),(31,77.5)]), fill=(165, 243, 252))

def background(w, h):
    bg = gradient_img(w, h, (0, 0), (w, h), [(0, BG1), (1, BG2)]).convert("RGBA")
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    dist = np.sqrt((xs - w / 2) ** 2 + (ys - h / 2) ** 2) / (min(w, h) * 0.45)
    a = np.clip(1 - dist, 0, 1) * 100
    arr = np.zeros((h, w, 4), np.uint8); arr[..., 0] = 124; arr[..., 1] = 58; arr[..., 2] = 237; arr[..., 3] = a.astype(np.uint8)
    return Image.alpha_composite(bg, Image.fromarray(arr, "RGBA"))

def legacy_icon(size, round_=False):
    S = size * SS
    img = background(S, S)
    draw_mark(img, S / 80, 14, 14)
    mask = Image.new("L", (S, S), 0)
    md = ImageDraw.Draw(mask)
    if round_: md.ellipse([0, 0, S - 1, S - 1], fill=255)
    else: md.rounded_rectangle([0, 0, S - 1, S - 1], radius=int(S * 0.22), fill=255)
    img.putalpha(mask)
    return img.resize((size, size), Image.LANCZOS)

for d, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
    legacy_icon(px).save(f"{RES}/mipmap-{d}/ic_launcher.png")
    legacy_icon(px, True).save(f"{RES}/mipmap-{d}/ic_launcher_round.png")
legacy_icon(512).save(f"{MAIN}/ic_launcher-playstore.png")

# Banner Android TV 320x180 (tamaño xhdpi) con el nombre
def banner_final(w=320, h=180):
    W, H = w * SS, h * SS
    img = background(W, H)
    k = H / 100
    # el centro del aro (54,54) debe caer en x=95px*SS, y=h/2
    ox = 54 - (78 * SS) / k
    oy = 54 - (H / 2) / k
    draw_mark(img, k, ox, oy)
    d = ImageDraw.Draw(img)
    for f in ("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
              "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf"):
        if os.path.exists(f):
            font = ImageFont.truetype(f, int(34 * SS)); break
    else:
        font = ImageFont.load_default()
    d.text((142 * SS, H / 2), "Lumora", fill=(255, 255, 255), font=font, anchor="lm")
    return img.resize((w, h), Image.LANCZOS).convert("RGB")
banner_final().save(f"{RES}/mipmap-xhdpi/ic_banner.png")
print("ok")
