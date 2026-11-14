from common import *
CSS=DOCK_CSS+'''
.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));column-gap:12px;row-gap:20px;padding:18px 28px 0}
.al{display:flex;flex-direction:column;align-items:stretch;text-align:left;min-width:0}
.al .cv{width:100%;aspect-ratio:1;border-radius:16px}
.al .tt{font-size:15px;margin-top:8px}.al .ss{margin-top:2px}'''
alb=[('[Название альбома]','[Исполнитель]'),('[Очень длинное название альбома с подзаголовком]','[Исполнитель feat. Гость]'),
     ('[Альбом]','[Группа]'),('[Сборник]','Разные исполнители'),('[Название альбома]','[Исполнитель]'),('[Альбом]','[Неизвестный исполнитель]')]
G=''.join(f'''      <button class="al" aria-label="{t}, {a}"><div class="cv" style="background:{C[(i*2+1)%len(C)]}"></div><div class="tt">{t}</div><div class="ss">{a}</div></button>
''' for i,(t,a) in enumerate(alb))
BODY=f'''  <div class="ph" data-theme="{{{{t.id}}}}">
    <div class="top"><h1>Альбомы</h1><button class="ib" aria-label="Поиск">{svg('search',26)}</button></div>
    <div class="cnt">[N] альбомов</div>
    <div class="grid">
{G}    </div>
{dock('albums')}
  </div>'''
dc=page('Android v2: Альбомы',CSS,BODY)
open('project/Android2-Albums.dc.html','w').write(dc)
shot(static(dc),'android_v2_albums.png')
