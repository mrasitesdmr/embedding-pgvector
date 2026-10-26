# Agent Ekibi Kullanım Kılavuzu

Bu kılavuz, bir task'ı agent ekibiyle (planner → coder'lar → tester → devops → qa) baştan sona nasıl yürüteceğini anlatır. Kuralların tamamı kök `CLAUDE.md` dosyasındadır; burada sadece kullanım adımları var.

## Kurulum (bir kez)

```bash
brew install tmux
```

Kurulu olup olmadığını `tmux -V` ile kontrol edebilirsin.

Proje ayarları hazır (`.claude/settings.json`):
- `CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1` ile agent ekibi açık.
- `"teammateMode": "tmux"` ile her agent kendi tmux panelinde çalışır.

## Senin rolün

Akışta dört yerde sen devreye girersin:

1. `TASK.md` dosyasını yazarsın.
2. Onay kartını onaylarsın.
3. qa bittikten sonra sonucu kabul edersin.
4. Commit için onay verirsin.

Geri kalanını lead (ana Claude oturumu) ve ekip yürütür.

## 1. Task'ı hazırla

`docs/tasks/_TEMPLATE.md` dosyasını `docs/tasks/TASK-XXX/TASK.md` olarak kopyala ve doldur. Teknik detay yazma, iş kurallarını yaz.

## 2. Başlat

1. macOS Terminal'i ya da iTerm2'yi aç. **VS Code'un dahili terminalini ya da Ghostty'yi kullanma**, tmux panelleri orada açılmaz.
2. Şunları çalıştır:
   ```bash
   cd ~/Documents/embedding-pgvector
   tmux new -s pgvector
   claude
   ```
3. Açılan Claude oturumu **lead**'dir. Ona şunu yaz:
   > `docs/tasks/TASK-XXX/TASK.md için akışı başlat`

## 3. Onay kartını onayla

Planner `handoff/00-onay-karti.md` dosyasını yazar, lead kartı sana gösterir.

- Uygunsa açıkça **"onaylıyorum"** yaz.
- Değişiklik istiyorsan ne istediğini yaz. Lead bunu planner'a iletir ve yeni kart gelir.

Senin açık onayın olmadan kod yazılmaz.

## 4. Ekibi izle

Onaydan sonra akış kendiliğinden ilerler. Her agent başladığında pencere bölünür ve agent kendi panelinde çalışır. Panelleri Claude açar, senin elle bir şey açman gerekmez.

İşini bitiren agent kapanmaz, boşta bekler. Paneller task kapanana kadar birikir:

| Aşama | Ekranda görünenler |
|-------|--------------------|
| Başlangıç | lead |
| Planlama | lead + planner |
| Kodlama | + backend-coder + frontend-coder (paralel) |
| Test | + tester |
| Deploy | + devops (Docker etkileniyorsa) |
| Kontrol | + qa |

Henüz başlamamış roller görünmez. Planner'ın bu task'ta çalışmayacak dediği roller hiç açılmaz.

**Talimatları lead üzerinden ver.** Bir agent'ın paneline doğrudan da yazabilirsin, ama talimatlar lead'den geçince akışın kaydı tek yerde kalır.

**Düzeltme turu:** Bir adım `NEEDS_FIX` dönerse düzeltme turu kendiliğinden başlar. Aynı adım üst üste 2 kez `NEEDS_FIX` dönerse lead durur ve sana sorar.

**İzin istemleri:** Komutların çoğu önceden izinli. `npm install` ve `git commit` her seferinde onay ister. `git push` kapalıdır.

### tmux kısayolları

Hepsinde önce `Ctrl+b`'ye bas, bırak, sonra ikinci tuşa bas.

| Kısayol | Ne yapar |
|---------|----------|
| `Ctrl+b` → ok tuşları | Paneller arasında geçiş |
| `Ctrl+b` → `z` | Seçili paneli tam ekran yapar / geri alır |
| `Ctrl+b` → `Space` | Panel düzenini değiştirir (çok panel varken "tiled" en okunaklısı) |
| `Ctrl+b` → `[` | Geriye kaydırma modu (`q` ile çıkılır) |
| `Ctrl+b` → `d` | tmux'tan ayrılır, ekip arka planda çalışmaya devam eder |

## 5. Task bitince çıkış

1. qa bitince lead bir özet sunar. Uygunsa **"kabul ediyorum"** yaz.
2. Lead ekibi kapatır, Docker'ı durdurur ve handoff dosyalarını `docs/tasks/TASK-XXX/handoff/` altına taşır.
3. Lead değişiklikleri gösterir. Uygunsa **commit için onay ver**.
4. Claude'dan çık: `/exit`
5. tmux'tan çık: `exit`

Sırada başka task varsa 4. ve 5. adımları atla, lead'e bir sonraki task'ı ver.

## Yarıda bırakma

**Ekip çalışmaya devam etsin, sen ayrıl:**
- `Ctrl+b` → `d`. Terminal penceresini kapatırsan da aynı şey olur.
- Geri dönmek için: `tmux attach -t pgvector`

**Her şeyi durdur:**
1. Lead'e yaz: `ekibi durdur`
2. `/exit`
3. `exit`

## Kaldığın yerden devam etme

```bash
cd ~/Documents/embedding-pgvector
tmux new -s pgvector
claude --resume
```

Listeden önceki oturumu seç ve lead'e **"devam et"** yaz. Ekip üyeleri otomatik geri gelmez. Lead `handoff/` dosyalarındaki `STATUS` satırlarına bakar ve sadece bitmemiş rolleri yeniden başlatır.

## Durumu kontrol etme

Hangi adımın bittiğini görmek için:

```bash
grep -H "STATUS\|ONAY" handoff/*.md
```

| Değer | Anlamı |
|-------|--------|
| `ONAY: BEKLİYOR` | Onay kartı senin onayını bekliyor |
| `ONAY: VERİLDİ` | Onayladın, kodlama başlayabilir |
| `STATUS: DONE` | Adım tamamlandı |
| `STATUS: NEEDS_FIX` | Adımda düzeltilecek bir şey çıktı |
| `STATUS: STALE` | Düzeltme turu nedeniyle adım yeniden çalışacak |

## Sorun giderme

| Sorun | Çözüm |
|-------|-------|
| Paneller açılmıyor, ekip üyeleri lead'in ekranının içinde görünüyor | Claude tmux dışında başlatılmış. `/exit` ile çık, `tmux new -s pgvector` ile oturumu aç, `claude --resume` ile devam et. Bu sırada Shift+↓ ile ekip üyeleri arasında geçebilirsin. |
| `sessions should be nested with care` hatası | Zaten bir tmux oturumunun içindesin. `tmux new` yazmana gerek yok, doğrudan `claude` çalıştır. |
| `duplicate session: pgvector` hatası | Bu isimde bir oturum zaten var. `tmux attach -t pgvector` ile ona bağlan. |
| Paneller çok küçük | Pencereyi tam ekran yap, `Ctrl+b` → `Space` ile düzeni değiştir ya da `Ctrl+b` → `z` ile tek paneli büyüt. |
