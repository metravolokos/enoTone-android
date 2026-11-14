from gen import *
import re
songs=open('project/Android2-Songs.dc.html').read()
songs_css=re.search(r'<style>(.*?)</style>',songs,re.S).group(1)
songs_css=songs_css.split('[data-theme=light]',1)[1].split('\n',1)[1]  # без базовых правил и тем
ph=re.search(r'(  <div class="ph" data-theme="\{\{t.id\}\}">.*?)\n</div>\n</sc-for>',songs,re.S).group(1)
ph=ph.rsplit('</div>',1)[0]  # открыть .ph, чтобы вставить поверх затемнение и лист
CSS=songs_css+'''
.scrim{position:absolute;inset:0;background:var(--scrim)}
.sheet{position:absolute;left:8px;right:8px;bottom:8px;border-radius:48px;background:var(--sf);box-shadow:0 -10px 40px var(--sh);padding:10px 0 26px}
.grab2{width:88px;height:28px;margin:0 auto}
.grab2 span{display:block;width:40px;height:5px;border-radius:3px;background:var(--ln)}
.sh-h{display:flex;align-items:center;gap:14px;padding:4px 28px 16px;border-bottom:1px solid var(--ln)}
.sh-h .tt{font-size:17px}
.act{width:100%;height:58px;justify-content:flex-start;gap:18px;padding:0 28px;font-size:16.5px;font-weight:500;box-sizing:border-box}
.act svg{color:var(--tx2)}
.sep{height:1px;background:var(--ln);margin:6px 28px}'''
acts=[('play_next','Играть следующей'),('add_end','В конец очереди'),None,('albums','Перейти к альбому'),('artists','Перейти к исполнителю')]
A=''.join('<div class="sep"></div>' if a is None else f'<button class="act">{svg(a[0],24)}{a[1]}</button>' for a in acts)
BODY=ph+f'''    <div class="scrim" aria-hidden="true"></div>
    <div class="sheet" role="dialog" aria-label="Действия с песней">
      <button class="grab2" aria-label="Закрыть"><span></span></button>
      <div class="sh-h">
        <div class="cv" style="width:56px;height:56px;border-radius:12px;background:#8FB0C2"></div>
        <div style="min-width:0;flex:1"><div class="tt">[Очень длинное название песни, которое не помещается в строку]</div><div class="ss">[Исполнитель feat. Гость] · [Альбом с длинным названием]</div></div>
      </div>
      <div style="padding-top:6px">{A}</div>
    </div>
  </div>'''
dc=page('Android v2: Действия с песней',CSS,BODY)
open('project/Android2-SongActions.dc.html','w').write(dc)
shot(static(dc),'android_v2_song_actions.png')
print('ok')
