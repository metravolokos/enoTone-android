from common import *
import re
CSS=DOCK_CSS+'''
.listwrap{position:absolute;inset:0;overflow:hidden}
.row{display:flex;align-items:center;gap:14px;height:64px;padding:0 10px 0 28px}
.meta{flex:1;min-width:0;height:64px;display:flex;flex-direction:column;justify-content:center;border-bottom:1px solid var(--ln)}
.dots{width:40px;height:64px;color:var(--tx2);flex:none}
.row.on .tt{color:var(--act)}
.tr{display:flex;align-items:center;height:52px;padding:0 10px 0 28px;gap:14px}
.tr .n{width:22px;text-align:right;font-size:15px;color:var(--tx2);font-variant-numeric:tabular-nums;flex:none}
.tr .m{flex:1;min-width:0;height:52px;display:flex;align-items:center;gap:10px;border-bottom:1px solid var(--ln)}
.tr .m .tt{flex:1;font-weight:500}
.tr .d{font-size:13.5px;color:var(--tx2);font-variant-numeric:tabular-nums}
.tr.on .tt,.tr.on .n{color:var(--act)}
.tr .dots{height:52px}
.strip{position:absolute;left:0;right:0;top:0;padding-top:40px;background:color-mix(in srgb,var(--bg) 84%,transparent);backdrop-filter:blur(18px);-webkit-backdrop-filter:blur(18px);border-bottom:1px solid color-mix(in srgb,var(--ln) 70%,transparent)}
.bar{height:56px;display:flex;align-items:center;gap:6px;padding:0 14px 0 28px}
.bar .t{flex:1;min-width:0;font-size:17px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.cb{width:40px;height:40px;border-radius:20px;flex:none}
.cb.s{background:var(--sf2);color:var(--tx)}
.cb.p{background:var(--ac);color:var(--onac)}
.ib2{width:44px;height:44px;color:var(--tx);flex:none}
.pins{display:flex;gap:12px;padding:2px 28px 12px}
.pins .pill{height:44px}
.tag{position:absolute;top:-34px;left:0;font-size:15px;font-weight:600;color:#141210}'''
C9=C
def songs_rows(start):
    names=[('[Название трека средней длины]','[Группа] · [Сборник]'),('[Название трека]','[Исполнитель] · [Альбом]'),('[Песня]','[Неизвестный исполнитель] · [Без альбома]'),
           ('[Очень длинное название песни, которое не помещается в строку]','[Исполнитель] · [Альбом]'),('[Короткое]','[Исполнитель] · [Альбом]'),('[Название трека]','[Группа] · [Альбом]'),
           ('[Название трека средней длины]','[Исполнитель] · [Альбом]'),('[Название трека]','[Исполнитель] · [Альбом]'),('[Короткое]','[Группа] · [Альбом]'),('[Название трека]','[Исполнитель] · [Альбом]'),
           ('[Название трека]','[Исполнитель] · [Альбом]'),('[Песня]','[Группа] · [Альбом]'),('[Название трека]','[Исполнитель] · [Альбом]')]
    return ''.join(f'<div class="row"><div class="cv" style="width:44px;height:44px;background:{C9[(i+3)%len(C9)]}"></div><div class="meta"><div class="tt">{t}</div><div class="ss">{s}</div></div><button class="dots" aria-label="Действия с песней">{svg("more",20)}</button></div>' for i,(t,s) in enumerate(names))
def album_rows():
    tr=[(4,'[Название трека]','4:01'),(5,'[Короткое]','2:44'),(6,'[Название трека средней длины]','3:58'),(1,'[Название трека]','4:12'),(2,'[Очень длинное название трека, которое не помещается]','5:31'),(3,'[Название трека]','3:36'),(4,'[Короткое]','2:57'),(5,'[Название трека]','4:20'),(6,'[Название трека]','3:15'),(7,'[Финал]','6:02'),(8,'[Название трека]','3:40'),(9,'[Бонус]','2:10'),(10,'[Название трека]','3:33'),(11,'[Название трека]','3:01'),(12,'[Короткое]','2:22')]
    return ''.join(f'<div class="tr"><span class="n">{n}</span><div class="m"><span class="tt">{t}</span><span class="d">{d}</span></div><button class="dots" aria-label="Действия с песней">{svg("more",20)}</button></div>' for n,t,d in tr)
def phone(theme,variant,screen):
    title='Песни' if screen=='songs' else '[Название альбома]'
    rows=songs_rows(0) if screen=='songs' else album_rows()
    top_pad=(96 if variant=='B' else 154)
    right=''
    if variant=='B':
        right=f'<button class="cb s" aria-label="Перемешать">{svg("shuffle",20)}</button><button class="cb p" aria-label="Играть">{svg("play",20)}</button>'
    search=f'<button class="ib2" aria-label="Поиск">{svg("search",24)}</button>' if screen=='songs' else ''
    pins=f'<div class="pins"><button class="pill p">{svg("play",20)}Играть</button><button class="pill s">{svg("shuffle",20)}Перемешать</button></div>' if variant=='A' else ''
    lab={'A':'A — крупные кнопки закреплены','B':'B — компактные кнопки в полоске'}[variant]
    return f'''<div style="position:relative">
  <div class="tag">{lab} · {"Графит" if theme=="dark" else "Фарфор"}</div>
  <div class="ph" data-theme="{theme}">
    <div class="listwrap"><div style="padding-top:{top_pad-30}px">{rows}</div></div>
    <div class="strip"><div class="bar"><div class="t">{title}</div>{right}{search}</div>{pins}</div>
{dock('songs' if screen=='songs' else 'albums')}
  </div>
</div>'''
def board(screen,fname,title):
    phones=''.join(phone(t,v,screen) for t in ('dark','light') for v in ('A','B'))
    W=48+4*377+3*48+48
    dc=f'''<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<title>{title}</title>
<script src="./support.js"></script>
</head>
<body>
<x-dc>
<helmet>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Onest:wght@400;500;600;700&amp;family=Unbounded:wght@600&amp;display=swap">
<style>
{BASE}
{THEMES}
{CSS}
</style>
</helmet>
<div style="display:flex;gap:48px;padding:84px 48px 48px;box-sizing:border-box;width:{W}px;height:960px;background:#8E8A86">
{phones}
</div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{{"$preview":{{"width":{W},"height":960}}}}'>
class Component extends DCLogic {{
  renderVals() {{ return {{}}; }}
}}
</script>
</body>
</html>
'''
    open(f'project/{fname}','w').write(dc)
    F=str(FONTS)+'/'
    ff=''.join(f"@font-face{{font-family:'Onest';font-weight:{w};src:url('file://{F}onest/files/onest-{s}-{w}-normal.woff')}}" for w in (400,500,600) for s in ('cyrillic','latin'))
    css=re.search(r'<style>(.*?)</style>',dc,re.S).group(1)
    st=f"<!doctype html><html><head><meta charset='utf-8'><style>{ff}{css}</style></head><body style='margin:0'><div style='display:flex;gap:48px;padding:84px 48px 48px;width:{W}px;box-sizing:border-box;background:#8E8A86'>{phones}</div></body></html>"
    open('_s.html','w').write(st)
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        br=p.chromium.launch(executable_path='/usr/bin/google-chrome',args=['--allow-file-access-from-files'])
        pg=br.new_page(viewport={'width':W,'height':960},device_scale_factor=1.5)
        pg.goto((CANVAS/'_s.html').as_uri()); pg.wait_for_timeout(500)
        pg.query_selector('body > div').screenshot(path=fname.replace('.dc.html','.png')); br.close()
    return W
print(board('songs','Android2-Songs-Scrolled.dc.html','Android v2: Песни — прокрутка'))
print(board('album','Android2-Album-Scrolled.dc.html','Android v2: Альбом — прокрутка'))
