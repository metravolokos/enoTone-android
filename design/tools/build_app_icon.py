# Сборка enoTone App Icon v2.0 из мастера Бандита.
# Запуск: python3 tools/build_app_icon.py assets/bandit/masters/bandit_pointing_master_1254.png assets/icons/v2.0
# Пиксели персонажа не меняются: альфа 240–254 -> 255 (исходник слегка просвечивал), альфа < 6 -> 0,
# далее только масштаб, размещение и маски. Зависимости: Pillow, numpy.
import sys, numpy as np
from PIL import Image, ImageDraw
SRC=sys.argv[1]; OUT=sys.argv[2]
import os; os.makedirs(OUT,exist_ok=True)
a=np.array(Image.open(SRC).convert('RGBA'))
al=a[...,3].astype(int)
al[al>=240]=255; al[al<6]=0
a[...,3]=al.astype(np.uint8)
fig=Image.fromarray(a)
BB=(345,223,892,1014)  # bbox фигуры в мастере
# параметры композиции (в долях стороны иконки)
TOP=float(os.environ.get('TOP',0.075)); W=float(os.environ.get('W',0.80)); DX=float(os.environ.get('DX',0.0))
def compose(size,bg,top=TOP,w=W,dx=DX):
    s=size*w/(BB[2]-BB[0])
    big=fig.resize((round(1254*s),round(1254*s)),Image.LANCZOS)
    ox=round(size/2 - ((BB[0]+BB[2])/2)*s + dx*size)
    oy=round(top*size - BB[1]*s)
    c=Image.new('RGBA',(size,size),bg if bg else (0,0,0,0))
    c.alpha_composite(big,(ox,oy)) if ox>=0 and oy>=0 else c.paste(big,(ox,oy),big)
    return c
def mask(size,kind,ss=4):
    m=Image.new('L',(size*ss,size*ss),0); d=ImageDraw.Draw(m)
    if kind=='circle': d.ellipse((0,0,size*ss-1,size*ss-1),fill=255)
    else: d.rounded_rectangle((0,0,size*ss-1,size*ss-1),radius=int(size*ss*0.225),fill=255)
    return m.resize((size,size),Image.LANCZOS)
themes={'graphite':'#1B1A19'}
for name,bg in themes.items():
    full=compose(1024,bg)
    full.save(f'{OUT}/enotone_icon_{name}_fullbleed_1024.png')
    for k in ['square','round']:
        c=full.copy(); c.putalpha(mask(1024,'circle' if k=='round' else 'rr'))
        c.save(f'{OUT}/enotone_icon_{name}_{k}_1024.png')

# --- круглая: фигура мельче, чтобы кулак и шарф не резались кругом
RW=0.74; RT=0.085
for name,bg in themes.items():
    c=compose(1024,bg,top=RT,w=RW); c.putalpha(mask(1024,'circle'))
    c.save(f'{OUT}/enotone_icon_{name}_round_1024.png')
# --- Android adaptive icon: слой 108dp, видимая область 72dp (18..90)
A=f'{OUT}/android'
dens={'mdpi':1,'hdpi':1.5,'xhdpi':2,'xxhdpi':3,'xxxhdpi':4}
for d,k in dens.items():
    S=round(108*k); inner=72*k
    s=inner*RW/(BB[2]-BB[0])
    big=fig.resize((round(1254*s),round(1254*s)),Image.LANCZOS)
    ox=round(S/2-((BB[0]+BB[2])/2)*s); oy=round(18*k+RT*inner-BB[1]*s)
    c=Image.new('RGBA',(S,S),(0,0,0,0)); c.paste(big,(ox,oy),big)
    os.makedirs(f'{A}/res/mipmap-{d}',exist_ok=True)
    c.save(f'{A}/res/mipmap-{d}/ic_launcher_foreground.png')
    # legacy (API<26): 48dp
    L=round(48*k)
    for name,bg,q in [('graphite','#1B1A19','')]:
        os.makedirs(f'{A}/res/mipmap{q}-{d}',exist_ok=True)
        sq=compose(L*4,bg).resize((L,L),Image.LANCZOS); sq.putalpha(mask(L,'rr'))
        sq.save(f'{A}/res/mipmap{q}-{d}/ic_launcher.png')
        rd=compose(L*4,bg,top=RT,w=RW).resize((L,L),Image.LANCZOS); rd.putalpha(mask(L,'circle'))
        rd.save(f'{A}/res/mipmap{q}-{d}/ic_launcher_round.png')
os.makedirs(f'{A}/res/mipmap-anydpi-v26',exist_ok=True)
xml='''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
'''
for n in ['ic_launcher','ic_launcher_round']: open(f'{A}/res/mipmap-anydpi-v26/{n}.xml','w').write(xml)
for q,col in [('values','#1B1A19')]:
    os.makedirs(f'{A}/res/{q}',exist_ok=True)
    open(f'{A}/res/{q}/ic_launcher_background.xml','w').write(f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <!-- enoTone tokens v1.3: Графит dark.background -->\n    <color name="ic_launcher_background">{col}</color>\n</resources>\n')
