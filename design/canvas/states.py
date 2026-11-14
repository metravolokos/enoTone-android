from common import *
from wm_inline import *
import re
B={'books':'9d39cc9c761563a47fc166e9bb4d3633','reading':'519b42f1928f8fd3f4e16c333c9f44f4','thinking':'1a96964982af163c8d690ab897bf3c4c','peeking':'b2989b45012b5d5f93195233e4132a03','happy':'415d143d62af7f518e514bfacd7c9170','relaxing':'f38c8cde1bc42aa5741934ce521a6c15','sleeping_books':'9edbf560e2c67a0ab928e35989aca906','armchair':'7c22a902bee15905abec4ab69b1f3b5e','sitting':'a4aa22d9dcbf03cdd2ed4860b1638bc2'}
LOCAL={'books':'01_books','reading':'02_reading','thinking':'03_thinking','peeking':'04_peeking','happy':'05_happy','relaxing':'06_relaxing','sleeping_books':'09_sleeping_books','armchair':'10_armchair_cool','sitting':'11_sitting'}
def img(k,h,extra=''): return f'<img src="/_blob/{B[k]}" alt="" style="height:{h}px;width:auto;{extra}">'
CSS=DOCK_CSS+'''
.st{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;text-align:center;padding:0 36px}
.st h2{margin:18px 0 0;font-family:'Unbounded',sans-serif;font-weight:600;font-size:24px;line-height:1.2}
.st p{margin:10px 0 0;font-size:16px;line-height:1.45;color:var(--tx2)}
.btn{height:52px;border-radius:26px;padding:0 28px;font-size:16px;font-weight:600;margin-top:24px;gap:8px}
.btn.p{background:var(--ac);color:var(--onac)}
.btn.s{background:var(--sf2);color:var(--tx)}
.fld{width:100%;height:56px;border-radius:18px;background:var(--sf);border:1.5px solid var(--ln);box-sizing:border-box;padding:0 18px;display:flex;flex-direction:column;justify-content:center;text-align:left;margin-top:12px}
.fld small{font-size:12px;color:var(--tx2)}.fld span{font-size:16px;margin-top:1px}
.bar2{width:100%;height:8px;border-radius:4px;background:var(--ln);margin-top:22px;position:relative;overflow:hidden}
.bar2 i{position:absolute;left:0;top:0;bottom:0;border-radius:4px;background:var(--ac)}
.srch{margin:56px 20px 0;height:52px;border-radius:26px;background:var(--sf2);display:flex;align-items:center;gap:10px;padding:0 16px;color:var(--tx2);font-size:16px}
.srch b{color:var(--tx);font-weight:500;flex:1;text-align:left}
.set{padding:0 20px}
.grp{background:var(--sf);border-radius:24px;margin-top:12px;overflow:hidden}
.si{display:flex;align-items:center;justify-content:space-between;height:58px;padding:0 20px;border-bottom:1px solid var(--ln);font-size:16px}
.si:last-child{border-bottom:0}
.si span{color:var(--tx2);font-size:14px}
.wm{height:40px;width:170px;border:1.5px dashed var(--ln);border-radius:12px;display:flex;align-items:center;justify-content:center;font-size:12px;color:var(--tx2)}'''
def phone(theme,label,inner):
    return f'''<div style="position:relative">
  <div style="position:absolute;top:-34px;left:0;font-size:15px;font-weight:600;color:#141210">{label}</div>
  <div class="ph" data-theme="{theme}">
{inner}
  </div>
</div>'''
def screens():
    return [
 ('Вход',f'''<div class="st" style="padding-top:96px">{img('sitting',200)}<h2>Привет! Я Бандит</h2><p>Подключите Облако Mail.ru — я найду музыку в папке AllMusic.</p>
  <div class="fld"><small>Почта</small><span>[name@mail.ru]</span></div>
  <div class="fld"><small>Пароль для внешнего приложения</small><span>••••••••••••</span></div>
  <button class="btn p" style="width:100%">Подключить</button></div>'''),
 ('Индексация',f'''<div class="st" style="padding-top:150px">{img('reading',240)}<h2>Читаю вашу музыку…</h2><p>1 240 из 3 590 песен</p>
  <div class="bar2"><i style="width:35%"></i></div><p style="font-size:14px;margin-top:12px">/AllMusic/[Исполнитель]/[Альбом]</p>
  <button class="btn s">Слушать, пока читаю</button></div>'''),
 ('Индексация на паузе',f'''<div class="st" style="padding-top:150px">{img('sleeping_books',230)}<h2>Задремал — нет сети</h2><p>Продолжу с того же места, как только появится интернет. Прочитано 1 240 из 3 590.</p>
  <button class="btn p">Повторить сейчас</button></div>'''),
 ('Индексация завершена',f'''<div class="st" style="padding-top:150px">{img('happy',240)}<h2>Всё готово!</h2><p>3 590 песен, [N] альбомов, [N] исполнителей</p>
  <button class="btn p">К музыке</button></div>'''),
 ('Поиск: ничего',f'''<div class="srch">{svg('search',22)}<b>[zzzz]</b>{svg('more',0)}</div>
  <div class="st" style="padding-top:200px">{img('thinking',210)}<h2>Ничего не нашлось</h2><p>Попробуйте другое написание — я ищу по названию, исполнителю и альбому.</p></div>
{dock('songs')}'''),
 ('Нет сети',f'''<div class="st" style="padding-top:150px">{img('peeking',230)}<h2>Облако не отвечает</h2><p>Проверьте интернет. Уже загруженные песни можно слушать.</p>
  <button class="btn p">Повторить</button></div>
{dock('home')}'''),
 ('Медиатека пуста',f'''<div class="st" style="padding-top:150px">{img('books',230)}<h2>Где же музыка?</h2><p>В папке /AllMusic нет файлов MP3 или FLAC.</p>
  <button class="btn s">Проверить подключение</button></div>
{dock('songs',mini=False)}'''),
 ('Ничего не играет',f'''<div class="st" style="padding-top:150px">{img('relaxing',220)}<h2>Тишина</h2><p>Очередь пуста. Включим что-нибудь?</p>
  <button class="btn p">{svg('shuffle',20)}Перемешать всё</button></div>'''),
 ('Настройки',f'''<div style="display:flex;align-items:flex-end;justify-content:space-between;padding:56px 28px 0">
    <div>{WM_SET}<div style="margin-top:8px;font-size:13px;color:var(--tx2)">Версия [0.x]</div></div>{img('armchair',150,'margin-right:-6px')}</div>
  <div class="set">
   <div class="grp"><div class="si">Облако<span>[name@mail.ru]</span></div><div class="si">Медиатека<span>3 590 песен</span></div><div class="si">Обновить медиатеку<span>{svg('more',0)}</span></div></div>
   <div class="grp"><div class="si">Тема<span>Системная</span></div><div class="si">Кэш<span>512 МБ</span></div></div>
   <div class="grp"><div class="si">Диагностика<span></span></div><div class="si">О приложении<span></span></div></div>
  </div>''')]
def board(theme,fname,title):
    sc=screens(); W=48+3*377+2*48+48; H=84+3*816+2*96+48
    ph=''.join(phone(theme,l,i) for l,i in sc)
    grid=f'<div style="display:grid;grid-template-columns:repeat(3,377px);column-gap:48px;row-gap:96px;padding:84px 48px 48px;box-sizing:border-box;width:{W}px;height:{H}px;background:#8E8A86">{ph}</div>'
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
{grid}
</x-dc>
<script type="text/x-dc" data-dc-script data-props='{{"$preview":{{"width":{W},"height":{H}}}}}'>
class Component extends DCLogic {{
  renderVals() {{ return {{}}; }}
}}
</script>
</body>
</html>
'''
    open('project/'+fname,'w').write(dc)
    F=str(FONTS)+'/'
    ff=''.join(f"@font-face{{font-family:'Onest';font-weight:{w};src:url('file://{F}onest/files/onest-{s}-{w}-normal.woff')}}" for w in (400,500,600) for s in ('cyrillic','latin'))
    ff+=''.join(f"@font-face{{font-family:'Unbounded';font-weight:600;src:url('file://{F}unbounded/files/unbounded-{s}-600-normal.woff')}}" for s in ('cyrillic','latin'))
    css=re.search(r'<style>(.*?)</style>',dc,re.S).group(1)
    g=grid
    for k,v in B.items(): g=g.replace(f'/_blob/{v}',f'{BANDIT.as_uri()}/bandit_{LOCAL[k]}.png')
    open('_s.html','w').write(f"<!doctype html><html><head><meta charset='utf-8'><style>{ff}{css}</style></head><body style='margin:0'>{g}</body></html>")
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        br=p.chromium.launch(executable_path='/usr/bin/google-chrome',args=['--allow-file-access-from-files'])
        pg=br.new_page(viewport={'width':W,'height':H},device_scale_factor=1)
        pg.goto((CANVAS/'_s.html').as_uri()); pg.wait_for_timeout(700)
        pg.query_selector('body > div').screenshot(path=fname.replace('.dc.html','.png')); br.close()
    return W,H
print(board('dark','Android2-Bandit-Dark.dc.html','Android v2: Бандит на экранах — Графит'))
print(board('light','Android2-Bandit-Light.dc.html','Android v2: Бандит на экранах — Фарфор'))
