from gen import *
CSS='''.top{display:flex;align-items:flex-end;justify-content:space-between;padding:56px 18px 0 28px}
.top h1{margin:0;font-family:'Unbounded',sans-serif;font-weight:600;font-size:30px;line-height:1.1}
.ib{width:48px;height:48px;border-radius:24px;color:var(--tx)}
.cnt{padding:6px 28px 0;font-size:14px;color:var(--tx2)}
.acts{display:flex;gap:12px;padding:18px 28px 10px}
.pill{flex:1;min-width:0;height:48px;border-radius:24px;gap:7px;font-size:15.5px;font-weight:600;white-space:nowrap}
.pill.p{background:var(--ac);color:var(--onac)}
.pill.s{background:var(--sf2);color:var(--tx)}
.list{padding:0 10px 0 28px}
.row{display:flex;align-items:center;gap:14px;height:64px}
.meta{flex:1;min-width:0;height:64px;display:flex;flex-direction:column;justify-content:center;border-bottom:1px solid var(--ln)}
.tt{font-size:16px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.ss{font-size:13.5px;color:var(--tx2);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin-top:3px}
.row.on .tt{color:var(--act)}
.dots{width:40px;height:64px;color:var(--tx2);flex:none}
.eq{position:absolute;inset:0;background:rgba(0,0,0,.3);display:flex;align-items:flex-end;justify-content:center;gap:2.5px;padding-bottom:12px}
.eq i{width:2.5px;height:14px;background:#fff;border-radius:2px;display:block;box-shadow:0 0 3px rgba(0,0,0,.35);transform-origin:bottom;animation:eqb 2s cubic-bezier(.45,0,.55,1) infinite alternate}
.eq i:nth-child(1){animation-duration:1.9s;animation-delay:-.4s}
.eq i:nth-child(2){animation-duration:2.6s;animation-delay:-1.5s}
.eq i:nth-child(3){animation-duration:1.6s;animation-delay:-.9s}
.eq i:nth-child(4){animation-duration:2.2s;animation-delay:-1.9s}
@keyframes eqb{0%{transform:scaleY(.3)}50%{transform:scaleY(.95)}100%{transform:scaleY(.5)}}
.eq.paused i{animation:none;transform:scaleY(.25)}
@media (prefers-reduced-motion:reduce){.eq i{animation:none;transform:scaleY(.55)}}
.fade{position:absolute;left:0;right:0;bottom:0;height:250px;background:linear-gradient(to bottom,transparent,var(--bg) 42%)}
.dock{position:absolute;left:12px;right:12px;bottom:22px;display:flex;flex-direction:column;gap:8px}
.mini{height:64px;border-radius:22px;background:var(--sf);box-shadow:0 8px 24px var(--sh);display:flex;align-items:center;gap:12px;padding:0 6px 0 10px;position:relative;overflow:hidden}
.mini .tt{font-size:15px}.mini .ss{margin-top:1px}
.mini .ctl{width:48px;height:48px;color:var(--tx)}
.mp{position:absolute;left:16px;right:16px;bottom:0;height:2px;background:var(--ln)}
.mp .b{position:absolute;left:0;top:0;bottom:0;background:var(--tx2);opacity:.38}
.mp .f{position:absolute;left:0;top:0;bottom:0;background:var(--tx)}
.nav{height:70px;border-radius:35px;background:var(--sf2);display:flex;align-items:center;justify-content:space-around;padding:0 6px}
.ni{flex:1;height:62px;flex-direction:column;gap:3px;font-size:11.5px;font-weight:500;color:var(--tx2)}
.ni.on{color:var(--act);font-weight:600}'''
C=['#EBAE9F','#8FB0C2','#B9AEA4','#4F7487','#D9C3AE','#3A3633','#A24F3E','#F4ECE4','#6E625A']
rows=[('[Название трека]','[Исполнитель] · [Альбом]',True),
      ('[Очень длинное название песни, которое не помещается в строку]','[Исполнитель feat. Гость] · [Альбом с длинным названием]',False),
      ('[Короткое]','[Исполнитель] · [Альбом]',False),
      ('[Название трека средней длины]','[Группа] · [Сборник]',False),
      ('[Название трека]','[Исполнитель] · [Альбом]',False),
      ('[Песня]','[Неизвестный исполнитель] · [Без альбома]',False),
      ('[Название трека средней длины]','[Исполнитель] · [Альбом]',False),
      ('[Название трека]','[Группа] · [Альбом]',False),
      ('[Короткое]','[Исполнитель] · [Альбом]',False),
      ('[Название трека]','[Исполнитель] · [Альбом]',False)]
lst=''
for i,(t,s,on) in enumerate(rows):
    eq='<span class="{{extraVals.eqCls}}" aria-hidden="true"><i></i><i></i><i></i><i></i></span>' if on else ''
    lst+=f'''      <div class="row{' on' if on else ''}">
        <div class="cv" style="width:44px;height:44px;background:{C[i%len(C)]}">{eq}</div>
        <div class="meta"><div class="tt">{t}</div><div class="ss">{s}</div></div>
        <button class="dots" aria-label="Действия с песней">{svg('more',20)}</button>
      </div>
'''
nav=''.join(f'<button class="ni{" on" if a else ""}" aria-label="{l}"{" aria-current=\"page\"" if a else ""}>{svg(n+("_fill" if a else ""),26)}<span>{l}</span></button>' for n,l,a in [('home','Домой',0),('songs','Песни',1),('albums','Альбомы',0),('artists','Артисты',0),('album_artists','Авторы',0)])
BODY=f'''  <div class="ph" data-theme="{{{{t.id}}}}">
    <div class="top"><h1>Песни</h1><button class="ib" aria-label="Поиск">{svg('search',26)}</button></div>
    <div class="cnt">3 590 песен</div>
    <div class="acts">
      <button class="pill p">{svg('play',20)}Играть</button>
      <button class="pill s">{svg('shuffle',20)}Перемешать</button>
    </div>
    <div class="list">
{lst}    </div>
    <div class="fade" aria-hidden="true"></div>
    <div class="dock">
      <div class="mini">
        <button aria-label="Открыть «Сейчас играет»" style="flex:1;min-width:0;justify-content:flex-start;gap:12px;text-align:left">
          <div class="cv" style="width:44px;height:44px;background:#EBAE9F"></div>
          <div style="min-width:0"><div class="tt">[Название трека]</div><div class="ss">[Исполнитель]</div></div>
        </button>
        <button class="ctl" aria-label="Пауза">{svg('pause',28)}</button>
        <button class="ctl" aria-label="Следующий">{svg('next',28)}</button>
        <div class="mp" aria-hidden="true"><div class="b" style="width:64%"></div><div class="f" style="width:38%"></div></div>
      </div>
      <nav class="nav" aria-label="Разделы">{nav}</nav>
    </div>
  </div>'''
dc=page('Android v2: Песни',CSS,BODY,props='{"playing":{"editor":"boolean","default":true},"$preview":{"width":898,"height":960}}',logic="    const extraVals = { eqCls: (this.props.playing ?? true) ? 'eq' : 'eq paused' };")
open('project/Android2-Songs.dc.html','w').write(dc)
shot(static(dc).replace('{{extraVals.eqCls}}','eq'),'android_v2_songs.png')
print('ok')
