from common import *
CSS=DOCK_CSS+'''
.scroll{position:absolute;inset:0;overflow:hidden}
.hero{display:flex;flex-direction:column;align-items:center;padding:64px 28px 0;text-align:center}
.hero .cv{width:240px;height:240px;border-radius:22px;box-shadow:0 18px 40px var(--sh),inset 0 0 0 1px rgba(128,118,110,.18)}
.hero h2{margin:22px 0 0;font-size:23px;font-weight:600;line-height:1.25;max-width:100%}
.hero .ar{margin:4px 0 0;font-size:18px;font-weight:500;color:var(--act)}
.hero .mt{margin:4px 0 0;font-size:14px;color:var(--tx2)}
.acts{display:flex;gap:12px;align-items:center;padding:20px 28px 6px}
.more{width:48px;height:48px;border-radius:24px;background:var(--sf2);color:var(--tx);flex:none}
.disc{padding:14px 28px 4px;font-size:13px;font-weight:600;color:var(--tx2);letter-spacing:.02em}
.tr{display:flex;align-items:center;height:52px;padding:0 10px 0 28px;gap:14px}
.tr .n{width:22px;text-align:right;font-size:15px;color:var(--tx2);font-variant-numeric:tabular-nums;flex:none}
.tr .m{flex:1;min-width:0;height:52px;display:flex;align-items:center;gap:10px;border-bottom:1px solid var(--ln)}
.tr .m .tt{flex:1;font-weight:500}
.tr .d{font-size:13.5px;color:var(--tx2);font-variant-numeric:tabular-nums}
.tr.on .tt,.tr.on .n{color:var(--act)}
.dots{width:40px;height:52px;color:var(--tx2);flex:none}
.meq{display:flex;align-items:flex-end;justify-content:flex-end;gap:2px;height:16px}
.meq i{width:2.5px;height:14px;background:var(--act);border-radius:2px;display:block;transform-origin:bottom;animation:eqb 2s cubic-bezier(.45,0,.55,1) infinite alternate}
.meq i:nth-child(1){animation-duration:1.9s;animation-delay:-.4s}
.meq i:nth-child(2){animation-duration:2.6s;animation-delay:-1.5s}
.meq i:nth-child(3){animation-duration:1.6s;animation-delay:-.9s}
.meq i:nth-child(4){animation-duration:2.2s;animation-delay:-1.9s}
@keyframes eqb{0%{transform:scaleY(.3)}50%{transform:scaleY(.95)}100%{transform:scaleY(.5)}}
@media (prefers-reduced-motion:reduce){.meq i{animation:none;transform:scaleY(.55)}}'''
# два диска: нумерация начинается заново, заголовка диска нет
tracks=[(1,'[Вступление]','1:12',False),(2,'[Название трека]','3:48',True),(3,'[Очень длинное название трека, которое не помещается]','5:03',False),(1,'[Название трека]','4:12',False),(2,'[Короткое]','2:57',False),(3,'[Название трека]','3:36',False)]
T=''.join(f'''      <div class="tr{' on' if on else ''}">{'<span class="n meq" aria-label="Играет"><i></i><i></i><i></i><i></i></span>' if on else f'<span class="n">{n}</span>'}<div class="m"><span class="tt">{t}</span><span class="d">{d}</span></div><button class="dots" aria-label="Действия с песней">{svg('more',20)}</button></div>
''' for n,t,d,on in tracks)
BODY=f'''  <div class="ph" data-theme="{{{{t.id}}}}">
    <div class="scroll">
    <div class="hero">
      <div class="cv" style="background:#8FB0C2"></div>
      <h2>[Название альбома]</h2>
      <p class="ar">[Исполнитель]</p>
      <p class="mt">2019 · [Жанр] · 12 песен, 48 мин</p>
    </div>
    <div class="acts">
      <button class="pill p">{svg('play',20)}Играть</button>
      <button class="pill s">{svg('shuffle',20)}Перемешать</button>
      <button class="more" aria-label="Ещё: в очередь, к исполнителю, обновить теги">{svg('more',22)}</button>
    </div>
{T}    </div>
{dock('albums')}
  </div>'''
dc=page('Android v2: Альбом',CSS,BODY)
open('project/Android2-Album.dc.html','w').write(dc)
shot(static(dc),'android_v2_album.png')
