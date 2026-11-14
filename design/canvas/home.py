from common import *
CSS=DOCK_CSS+'''
.cont{margin:20px 20px 0;border-radius:30px;background:var(--sf);padding:16px;display:flex;gap:16px;align-items:center}
.cont .cv{width:96px;height:96px;border-radius:18px}
.lab{font-size:12.5px;font-weight:600;color:var(--tx2);letter-spacing:.04em;text-transform:uppercase}
.cont .tt{font-size:17px;margin-top:4px}
.cont .ss{margin-top:2px}
.bigp{width:56px;height:56px;border-radius:28px;background:var(--ac);color:var(--onac);flex:none;box-shadow:0 8px 20px rgba(235,174,159,.3)}
.shuf{margin:12px 20px 0;height:64px;border-radius:24px;background:var(--sf2);width:calc(100% - 40px);justify-content:flex-start;gap:14px;padding:0 20px;box-sizing:border-box;text-align:left}
.shuf .tt{font-size:16px}.shuf .ss{margin-top:1px}
.sec{display:flex;align-items:baseline;justify-content:space-between;padding:26px 28px 12px}
.sec h3{margin:0;font-size:17px;font-weight:600;white-space:nowrap}
.sec a{font-size:14px;color:var(--act);text-decoration:none;font-weight:500}
.car{display:flex;gap:12px;padding:0 0 0 28px;overflow:hidden}
.ac{width:150px;flex:none;text-align:left;display:flex;flex-direction:column;align-items:stretch}
.ac .cv{width:150px;height:150px;border-radius:14px}
.ac .tt{font-size:14.5px;margin-top:8px}.ac .ss{margin-top:1px;font-size:13px}'''
rec=''.join(f'<button class="ac" aria-label="{t}"><div class="cv" style="background:{c}"></div><div class="tt">{t}</div><div class="ss">{a}</div></button>' for t,a,c in [('[Название альбома]','[Исполнитель]','#4F7487'),('[Альбом]','[Группа]','#D9C3AE'),('[Очень длинное название альбома]','[Исполнитель]','#EBAE9F')])
BODY=f'''  <div class="ph" data-theme="{{{{t.id}}}}">
    <div class="top"><h1>Домой</h1><button class="ib" aria-label="Настройки">{svg('settings',26)}</button></div>
    <div class="cont">
      <div class="cv" style="background:#8FB0C2"></div>
      <div style="flex:1;min-width:0">
        <div class="lab">Продолжить</div>
        <div class="tt">[Название трека]</div>
        <div class="ss">[Альбом] · 7 из 12</div>
      </div>
      <button class="bigp" aria-label="Продолжить воспроизведение">{svg('play',26,' style="margin-left:3px"')}</button>
    </div>
    <button class="shuf">{svg('shuffle',26)}<div><div class="tt">Перемешать всё</div><div class="ss">3 590 песен</div></div></button>
    <div class="sec"><h3>Недавно добавленные</h3><a href="#">Все</a></div>
    <div class="car">{rec}</div>
{dock('home')}
  </div>'''
dc=page('Android v2: Домой',CSS,BODY)
played=''.join(f'<button class="ac" aria-label="{t}"><div class="cv" style="background:{c}"></div><div class="tt">{t}</div><div class="ss">{a}</div></button>' for t,a,c in [('[Альбом]','[Исполнитель]','#8FB0C2'),('[Сборник]','Разные исполнители','#6E625A'),('[Название альбома]','[Группа]','#B9AEA4')])
BODY2=f'''  <div class="ph" data-theme="{{{{t.id}}}}">
    <div class="top"><h1>Домой</h1><button class="ib" aria-label="Настройки">{svg('settings',26)}</button></div>
    <button class="shuf" style="margin-top:20px">{svg('shuffle',26)}<div><div class="tt">Перемешать всё</div><div class="ss">3 590 песен</div></div></button>
    <div class="sec"><h3>Недавно играли</h3><a href="#">Все</a></div>
    <div class="car">{played}</div>
    <div class="sec"><h3>Недавно добавленные</h3><a href="#">Все</a></div>
    <div class="car">{rec}</div>
{dock('home')}
  </div>'''
dc2=page('Android v2: Домой (вариант без «Продолжить»)',CSS,BODY2)
open('project/Android2-Home-B.dc.html','w').write(dc2); shot(static(dc2),'Android2-Home-B.png')
open('project/Android2-Home.dc.html','w').write(dc); shot(static(dc),'Android2-Home.png'); print('ok')
