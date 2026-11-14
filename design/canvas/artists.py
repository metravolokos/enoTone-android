from common import *
AV=[('#EBAE9F','#1B1A19'),('#8FB0C2','#1B1A19'),('#4F7487','#F4ECE4'),('#D9C3AE','#1B1A19'),('#6E625A','#F4ECE4'),('#B9AEA4','#1B1A19')]
LIST_CSS=DOCK_CSS+'''
.list{padding:14px 10px 0 28px}
.ar{display:flex;align-items:center;gap:14px;height:68px;width:100%;text-align:left}
.av{width:48px;height:48px;border-radius:24px;flex:none;display:flex;align-items:center;justify-content:center;font-family:'Unbounded',sans-serif;font-weight:600;font-size:18px}
.ar .meta{flex:1;min-width:0;height:68px;display:flex;flex-direction:column;justify-content:center;border-bottom:1px solid var(--ln)}'''
def avatar(name,i,size=48,fs=18,icon=None):
    bg,fg=AV[i%len(AV)]
    inner=svg(icon,size//2) if icon else name.strip('[')[0]
    return f'<div class="av" aria-hidden="true" style="width:{size}px;height:{size}px;border-radius:{size//2}px;background:{bg};color:{fg};font-size:{fs}px">{inner}</div>'
def listscreen(title,count,items,active,fname,ptitle):
    rows=''.join(f'''      <button class="ar" aria-label="{n}">{avatar(n,i,icon=ic)}<div class="meta"><div class="tt">{n}</div><div class="ss">{s}</div></div></button>
''' for i,(n,s,ic) in enumerate(items))
    BODY=f'''  <div class="ph" data-theme="{{{{t.id}}}}">
    <div class="top"><h1{' style="font-size:25px"' if len(title)>12 else ''}>{title}</h1><button class="ib" aria-label="Поиск">{svg('search',26)}</button></div>
    <div class="cnt">{count}</div>
    <div class="list">
{rows}    </div>
{dock(active)}
  </div>'''
    dc=page(ptitle,LIST_CSS,BODY)
    open('project/'+fname,'w').write(dc); shot(static(dc),fname.replace('.dc.html','.png'))
listscreen('Артисты','[N] артистов',[
  ('[Алиса Имя]','3 альбома · 41 песня',None),('[Бэнд]','1 альбом · 12 песен',None),('[Очень длинное имя исполнителя с продолжением]','2 альбома · 19 песен',None),
  ('[Вера Фамилия]','Песни в сборниках · 4 песни',None),('[Группа]','5 альбомов · 63 песни',None),('[Даниил]','1 альбом · 9 песен',None),
  ('[Ёлка]','2 альбома · 22 песни',None),('[Исполнитель]','1 альбом · 11 песен',None),('[Квартет]','3 альбома · 30 песен',None)],
  'artists','Android2-Artists.dc.html','Android v2: Артисты')
listscreen('Авторы альбомов','[N] авторов',[
  ('Разные исполнители','7 сборников','album_artists'),('[Алиса Имя]','3 альбома',None),('[Бэнд]','1 альбом',None),
  ('[Очень длинное имя исполнителя с продолжением]','2 альбома',None),('[Группа]','5 альбомов',None),('[Даниил]','1 альбом',None),
  ('[Ёлка]','2 альбома',None),('[Исполнитель]','1 альбом',None),('[Квартет]','3 альбома',None)],
  'album_artists','Android2-AlbumArtists.dc.html','Android v2: Авторы альбомов')
# экран исполнителя
CSS=DOCK_CSS+'''
.hero{display:flex;flex-direction:column;align-items:center;padding:72px 28px 0;text-align:center}
.av{border-radius:999px;display:flex;align-items:center;justify-content:center;font-family:'Unbounded',sans-serif;font-weight:600}
.hero h2{margin:18px 0 0;font-size:24px;font-weight:600}
.hero .mt{margin:4px 0 0;font-size:14px;color:var(--tx2)}
.acts{display:flex;gap:12px;align-items:center;padding:20px 28px 0}
.more{width:48px;height:48px;border-radius:24px;background:var(--sf2);color:var(--tx);flex:none}
.sec{padding:24px 28px 10px;font-size:18px;font-weight:600}
.car{display:flex;gap:12px;padding:0 0 0 28px;overflow:hidden}
.ac{width:150px;flex:none;text-align:left;display:flex;flex-direction:column;align-items:stretch}
.ac .cv{width:150px;height:150px;border-radius:14px}
.ac .tt{font-size:14.5px;margin-top:8px}.ac .ss{margin-top:1px;font-size:13px}
.row{display:flex;align-items:center;gap:14px;height:60px;padding:0 10px 0 28px}
.row .meta{flex:1;min-width:0;height:60px;display:flex;flex-direction:column;justify-content:center;border-bottom:1px solid var(--ln)}
.dots{width:40px;height:60px;color:var(--tx2);flex:none}'''
albums=''.join(f'<button class="ac" aria-label="{t}"><div class="cv" style="background:{c}"></div><div class="tt">{t}</div><div class="ss">{y}</div></button>' for t,y,c in [('[Название альбома]','2021','#8FB0C2'),('[Альбом]','2018','#EBAE9F'),('[Очень длинное название альбома]','2015','#4F7487')])
songs=''.join(f'<div class="row"><div class="cv" style="width:44px;height:44px;background:{c}"></div><div class="meta"><div class="tt">{t}</div><div class="ss">{s}</div></div><button class="dots" aria-label="Действия с песней">{svg("more",20)}</button></div>' for t,s,c in [('[Название трека]','[Название альбома] · 2021','#8FB0C2'),('[Короткое]','[Альбом] · 2018','#EBAE9F')])
BODY=f'''  <div class="ph" data-theme="{{{{t.id}}}}">
    <div class="hero">
      {avatar('[Алиса Имя]',0,132,48)}
      <h2>[Алиса Имя]</h2>
      <p class="mt">3 альбома · 41 песня</p>
    </div>
    <div class="acts">
      <button class="pill p">{svg('play',20)}Играть</button>
      <button class="pill s">{svg('shuffle',20)}Перемешать</button>
      <button class="more" aria-label="Ещё: в очередь">{svg('more',22)}</button>
    </div>
    <div class="sec">Альбомы</div>
    <div class="car">{albums}</div>
    <div class="sec">Песни</div>
    {songs}
{dock('artists')}
  </div>'''
dc=page('Android v2: Исполнитель',CSS,BODY)
open('project/Android2-Artist.dc.html','w').write(dc); shot(static(dc),'Android2-Artist.png')
print('ok')
