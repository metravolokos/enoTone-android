import re, sys
from paths import CANVAS, DESIGN, ICONS, BANDIT, FONTS
D=str(ICONS)+'/'
def inner(n): return re.search(r'<svg[^>]*>(.*)</svg>',open(D+n+'.svg').read(),re.S).group(1)
EXTRA={ # служебные значки в том же стиле (линия 2)
 'search':'<circle cx="10.5" cy="10.5" r="6.3" fill="none" stroke="currentColor" stroke-width="2"></circle><path d="M15.3 15.3 20 20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"></path>',
 'play_next':'<path d="M4 6.5h10M4 11.5h7M4 16.5h7" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"></path><path d="M15.5 11.6 20.5 15l-5 3.4z" fill="currentColor" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"></path>',
 'settings':'<path d="M4 7h8.5M17.5 7H20M4 17h2.5M11.5 17H20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"></path><circle cx="15" cy="7" r="2.5" fill="none" stroke="currentColor" stroke-width="2"></circle><circle cx="9" cy="17" r="2.5" fill="none" stroke="currentColor" stroke-width="2"></circle>',
 'add_end':'<path d="M4 6.5h12M4 11.5h12M4 16.5h6M17.5 13.5v7M14 17h7" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"></path>',
}
def svg(n,size,extra=''):
    body=EXTRA.get(n) or inner(n)
    body=re.sub(r'<(path|circle|rect)([^<>]*?)\s*/>',r'<\1\2></\1>',body)
    return f'<svg viewBox="0 0 24 24" width="{size}" height="{size}" aria-hidden="true"{extra}>{body}</svg>'
THEMES='''[data-theme=dark]{--bg:#1B1A19;--sf:#252321;--sf2:#2F2C2A;--ln:#3A3633;--tx:#F4ECE4;--tx2:#B9AEA4;--ac:#EBAE9F;--onac:#1B1A19;--act:#EBAE9F;--sh:rgba(0,0,0,.5);--scrim:rgba(0,0,0,.55)}
[data-theme=light]{--bg:#EDE9E5;--sf:#F8F5F1;--sf2:#E2DCD6;--ln:#CFC6BE;--tx:#211E1C;--tx2:#5C544E;--ac:#EBAE9F;--onac:#211E1C;--act:#A24F3E;--sh:rgba(60,40,30,.22);--scrim:rgba(33,30,28,.38)}'''
BASE='''body{margin:0;font-family:'Onest',system-ui,sans-serif}
button{background:none;border:0;font:inherit;color:inherit;padding:0;cursor:pointer;display:flex;align-items:center;justify-content:center}
.ph{width:377px;height:816px;border-radius:56px;overflow:hidden;background:var(--bg);color:var(--tx);box-shadow:0 24px 60px var(--sh);position:relative}
.lbl{font-size:15px;font-weight:600;color:#141210;margin-bottom:12px}
.cv{flex:none;border-radius:10px;position:relative;overflow:hidden;box-shadow:inset 0 0 0 1px rgba(128,118,110,.18)}'''
def page(title,css,body,props='{"$preview":{"width":898,"height":960}}',logic=''):
    return f'''<!doctype html>
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
{css}
</style>
</helmet>
<div style="display:flex;gap:48px;padding:48px;box-sizing:border-box;width:898px;height:960px;background:#8E8A86">
<sc-for list="{{{{themes}}}}" as="t" hint-placeholder-count="2">
<div>
  <div class="lbl">{{{{t.label}}}}</div>
{body}
</div>
</sc-for>
</div>
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{props}'>
class Component extends DCLogic {{
  renderVals() {{
{logic}
    return {{ themes: [{{ id: 'dark', label: 'Графит' }}, {{ id: 'light', label: 'Фарфор' }}]{', '+'extraVals' if logic else ''} }};
  }}
}}
</script>
</body>
</html>
'''
def static(dc):
    """Развернуть sc-for тем для локального рендера."""
    F=str(FONTS)+'/'
    ff=''.join(f"@font-face{{font-family:'Onest';font-weight:{w};src:url('file://{F}onest/files/onest-{s}-{w}-normal.woff')}}" for w in (400,500,600) for s in ('cyrillic','latin'))
    ff+=''.join(f"@font-face{{font-family:'Unbounded';font-weight:600;src:url('file://{F}unbounded/files/unbounded-{s}-600-normal.woff')}}" for s in ('cyrillic','latin'))
    css=re.search(r'<style>(.*?)</style>',dc,re.S).group(1)
    body=re.search(r'<sc-for list="\{\{themes\}\}"[^>]*>(.*?)</sc-for>\s*</div>\s*</x-dc>',dc,re.S).group(1)
    cols=''.join(body.replace('{{t.id}}',i).replace('{{t.label}}',l) for i,l in (('dark','Графит'),('light','Фарфор')))
    return f"<!doctype html><html><head><meta charset='utf-8'><style>{ff}{css}</style></head><body><div style='display:flex;gap:48px;padding:48px;width:898px;box-sizing:border-box;background:#8E8A86'>{cols}</div></body></html>"
def shot(html,out):
    open(CANVAS/'_s.html','w').write(html)
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        br=p.chromium.launch(executable_path='/usr/bin/google-chrome',args=['--allow-file-access-from-files'])
        pg=br.new_page(viewport={'width':898,'height':960},device_scale_factor=2)
        pg.goto((CANVAS/'_s.html').as_uri()); pg.wait_for_timeout(500)
        pg.query_selector('body > div').screenshot(path=out); br.close()
