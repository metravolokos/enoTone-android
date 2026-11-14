from gen import *
DOCK_CSS='''.top{display:flex;align-items:flex-end;justify-content:space-between;padding:56px 18px 0 28px}
.top h1{margin:0;font-family:'Unbounded',sans-serif;font-weight:600;font-size:30px;line-height:1.1}
.ib{width:48px;height:48px;border-radius:24px;color:var(--tx)}
.cnt{padding:6px 28px 0;font-size:14px;color:var(--tx2)}
.tt{font-size:16px;font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.ss{font-size:13.5px;color:var(--tx2);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin-top:3px}
.pill{flex:1;min-width:0;height:48px;border-radius:24px;gap:7px;font-size:15.5px;font-weight:600;white-space:nowrap}
.pill.p{background:var(--ac);color:var(--onac)}
.pill.s{background:var(--sf2);color:var(--tx)}
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
def dock(active,mini=True):
    items=[('home','Домой'),('songs','Песни'),('albums','Альбомы'),('artists','Артисты'),('album_artists','Авторы')]
    nav=''.join(f'<button class="ni{" on" if n==active else ""}" aria-label="{l}"{" aria-current=\"page\"" if n==active else ""}>{svg(n+("_fill" if n==active else ""),26)}<span>{l}</span></button>' for n,l in items)
    MINI=f'''      <div class="mini">
        <button aria-label="Открыть «Сейчас играет»" style="flex:1;min-width:0;justify-content:flex-start;gap:12px;text-align:left">
          <div class="cv" style="width:44px;height:44px;background:#EBAE9F"></div>
          <div style="min-width:0"><div class="tt">[Название трека]</div><div class="ss">[Исполнитель]</div></div>
        </button>
        <button class="ctl" aria-label="Пауза">{svg('pause',28)}</button>
        <button class="ctl" aria-label="Следующий">{svg('next',28)}</button>
        <div class="mp" aria-hidden="true"><div class="b" style="width:64%"></div><div class="f" style="width:38%"></div></div>
      </div>
'''
    return f'''    <div class="fade" aria-hidden="true"></div>
    <div class="dock">
{MINI if mini else ''}      <nav class="nav" aria-label="Разделы">{nav}</nav>
    </div>'''
C=['#EBAE9F','#8FB0C2','#B9AEA4','#4F7487','#D9C3AE','#3A3633','#A24F3E','#F4ECE4','#6E625A']
