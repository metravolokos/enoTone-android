from common import *
from wm_inline import *
exec(open('home.py').read().split("rec=''")[0].split("from common import *")[1])  # CSS из home.py
import sys
HP=len(sys.argv)>1
BLOB='/_blob/6151178d8f0be6f5e287ec1bb1043f79' if HP else '/_blob/f13e432037237fec337d0068b63b205a'
CSS2=CSS+'''
.wm{height:44px;width:176px;border:1.5px dashed var(--ln);border-radius:12px;display:flex;align-items:center;justify-content:center;font-size:12px;color:var(--tx2);text-align:center;line-height:1.2}
.shuf2{margin:18px 20px 0;height:112px;border-radius:28px;background:var(--sf2);width:calc(100% - 40px);justify-content:flex-start;align-items:center;gap:14px;padding:0 20px;box-sizing:border-box;text-align:left;position:relative;overflow:hidden}
.shuf2 .tt{font-size:18px}.shuf2 .ss{margin-top:2px}
.shuf2 img{position:absolute;right:14px;bottom:-6px;height:118px;width:auto}\n.shuf2 img.hp{height:108px;bottom:-10px;right:6px}
.row{display:flex;align-items:center;gap:14px;height:64px;padding:0 10px 0 28px}
.row .meta{flex:1;min-width:0;height:64px;display:flex;flex-direction:column;justify-content:center;border-bottom:1px solid var(--ln)}
.dots{width:40px;height:64px;color:var(--tx2);flex:none}'''
rec=''.join(f'<button class="ac" aria-label="{t}"><div class="cv" style="background:{c}"></div><div class="tt">{t}</div><div class="ss">{a}</div></button>' for t,a,c in [('[Название альбома]','[Исполнитель]','#4F7487'),('[Альбом]','[Группа]','#D9C3AE'),('[Очень длинное название альбома]','[Исполнитель]','#EBAE9F')])
songs=''.join(f'<div class="row"><div class="cv" style="width:44px;height:44px;background:{c}"></div><div class="meta"><div class="tt">{t}</div><div class="ss">{s}</div></div><button class="dots" aria-label="Действия с песней">{svg("more",20)}</button></div>' for t,s,c in [('[Название трека]','[Исполнитель] · [Альбом]','#8FB0C2'),('[Короткое]','[Группа] · [Альбом]','#6E625A'),('[Название трека средней длины]','[Исполнитель] · [Альбом]','#B9AEA4')])
BODY=f'''  <div class="ph" data-theme="{{{{t.id}}}}">
    <div class="top"><h1>Домой</h1><button class="ib" aria-label="Настройки">{svg('settings',26)}</button></div>
    <button class="shuf2">{svg('shuffle',28)}<div><div class="tt">Перемешать всё</div><div class="ss">3 590 песен</div></div><img src="{BLOB}" alt=""{' class="hp"' if HP else ''}></button>
    <div class="sec"><h3>Недавно добавленные альбомы</h3><a href="#">Все</a></div>
    <div class="car">{rec}</div>
    <div class="sec"><h3>Недавно добавленные песни</h3><a href="#">Все</a></div>
    {songs}
{dock('home')}
  </div>'''
dc=page('Android v2: Домой',CSS2,BODY)
FN='project/Android2-Home-C2.dc.html' if HP else 'project/Android2-Home-C.dc.html'
if HP: dc=dc.replace('<title>Android v2: Домой</title>','<title>Android v2: Домой (Бандит в наушниках)</title>')
open(FN,'w').write(dc)
shot(static(dc).replace(BLOB,CANVAS.as_uri()+'/'+('hp_peek.png' if HP else 'bandit_peek.png')),'Android2-Home-C2.png' if HP else 'Android2-Home-C.png'); print('ok')
