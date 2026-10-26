TASK: TASK-006
# Deploy raporu

## Değişen dosyalar
- `deployment/docker-compose.yml`: `SEED_ENABLED` satırı kaldırıldı (`docker-compose.dev.yml`'de seed ayarı yoktu).
- `README.md`: "Örnek belgeyle dene", "Önceki sürümden güncelleme" bölümleri; uçtan uca testlerin veriyi sildiği uyarısı; seed anlatımı kaldırıldı; veri sıfırlamak için `down -v` (backend açıkken tablo boşaltma önerilmedi).
- `ollama.md`: bölüm 5.2-5.6 örnek belgeyle yeniden yazıldı (yükleme, 3 modda arama, psql, site adımları; çıktılar gerçek sistemden); E2E veri silme uyarısı; seed'e ait hata satırı ve `SEED_ENABLED` satırı kaldırıldı.
- `deployment/CLAUDE.md`: gerçek duruma güncellendi (boş açılış, V9 otomatik silme, E2E ve `TRUNCATE` tuzağı).
- `grep -rniE "poyraz|dalga|seed" deployment README.md ollama.md` → 0 sonuç.

## Servisler
Temiz başlangıç: `down -v` + `up --build -d` (eski `SEED_ENABLED` ile), sonra seed'siz compose ile `up -d` (backend yeniden oluşturuldu).

| Servis | İmaj / etiket | Port | Durum (ps çıktısı) |
|--------|---------------|------|--------------------|
| postgres | pgvector/pgvector:0.8.1-pg18 | 127.0.0.1:5432 | Up (healthy) |
| backend | embedding-pgvector-backend | 8080 | Up (healthy) |
| frontend | embedding-pgvector-frontend | 3000 | Up (healthy) |
| pgadmin | dpage/pgadmin4:9.8 | 127.0.0.1:5050 | Up (healthy) |

## Smoke test
- `docker info` → çalışıyor. `curl localhost:11434/api/tags` → `bge-m3` var.
- `curl -s localhost:8080/api/workspaces` (temiz sistem) → 200, tek alan 42 "Belgelerim", `sourceCount 0`, `chunkCount 0`. Backend container'ında `SEED_*` ortam değişkeni yok.
- `curl -sf localhost:8080/actuator/health` → `{"status":"UP",...}`
- `curl -s -o /dev/null -w "%{http_code}" localhost:3000` → 200
- `curl -s localhost:3000/api/workspaces/42/sources` (nginx proxy) → 200, `{"content":[{"sourceId":10001,...`
- `curl -s localhost:5050/misc/ping` → `PING`
- Örnek PDF yükleme (`POST /api/workspaces/42/sources`, name + file) → 202, iş 1 `DONE`, 24/24 parça, kaynak 10001 `READY`, `bge-m3@1024`.
- Aramalar (HYBRID/VECTOR): "Yıllık izin kaç gün?" → s. 7 (izin bölümü) ilk; "Masraf beyanı kaç gün içinde yapılır?" → s. 8 (masraf, "30 gün") ilk; "parolamı unuttum hesabım kilitlendi" → s. 16 (BT destek) ilk, KEYWORD+SEMANTIC. "kuantum fiziği" mesafe ≈ 0,68 ve `popular-queries` listesine girmedi.
- `search-events/popular-queries` → 3 sorgu (izin 3, parola 3, masraf 2). `explain:true` planı `Index Scan using idx_chunks_embedding`.
- Yeni sürüm yükleme (`.../sources/10001/versions`, version=2.5) → 202.
- WebSocket yok (kapsam dışı).
- Dokümandaki komutlar bu sistemde çalıştırıldı; ollama.md'de çıktısı gösterilen komutların hepsi gerçek çıktıdan alındı (uzun başlıklar kısaltıldı). `POST_FILTER` kısa liste örneği tek alanlı veritabanında yeniden üretilemez; dokümanda bu belirtildi.

## Nasıl çalıştırılır
```bash
docker compose -f deployment/docker-compose.yml up --build -d   # başlat
docker compose -f deployment/docker-compose.yml ps              # 4 servis healthy
docker compose -f deployment/docker-compose.yml logs --tail=100 backend
docker compose -f deployment/docker-compose.yml down            # durdur, veri kalır
docker compose -f deployment/docker-compose.yml down -v         # durdur ve veriyi sıfırla
```
Site http://localhost:3000. Örnek belge: README "Örnek belgeyle dene".

## Sistem durumu
Tam sistem **açık** (localhost:3000). İçinde örnek PDF (kaynak 10001, sürüm 2.5, iş 1 ve 2) ve 12 civarı arama kaydı var; dev compose kapalı (volume'lar duruyor). Uçtan uca testler (qa) veriyi kendisi sıfırlar.

## Not
Uçtan uca testler çalışma alanı 42'nin verisini siler; README ve ollama.md'de uyarı var.

## Düzeltme turu 1
QA maddesi (`ollama.md` bölüm 4 eski demo cümleleri) uygulandı.

- `ollama.md:52` "Şifremi unuttum, online işlemlere giremiyorum" → "Parolamı unuttum, hesabım kilitlendi". Gerçek Ollama'dan çıktı alındı; örnek yanıttaki vektör başı, `total_duration` (32280083) ve `load_duration` (4315708) güncellendi, `prompt_eval_count` 14 aynı kaldı.
- `ollama.md:70` "Şifremi unuttum" → "Yıllık izin kaç gün?" (çıktı 1024, değişmedi).
- Taramada çıkan ek eski örnekler: `ollama.md:79` toplu embed örneği ("modem ışığı kırmızı yanıyor", "internet bağlantım koptu") → "Yıllık izin kaç gün?", "Masraf beyanı kaç gün içinde yapılır?" (çıktı "2 vektör, 1024 boyut", gerçek Ollama'da doğrulandı). `ollama.md:422` `/api/embeddings` örneği "Şifremi unuttum" → "Yıllık izin kaç gün?"; çalışan backend'den gerçek çıktı "bge-m3 1024 1024 18 ms" alınıp yazıldı (eski: 42 ms).
- Kontrol: `grep -niE "poyraz|dalga|şifremi unuttum|online işlem|TS-999|modem|taşın" ollama.md README.md` ve `grep -rniE ... deployment` → eşleşme yok (boş). Dokümandaki "şifre" geçen satırlar (veritabanı/pgAdmin parolası) demo içeriği değil, bilerek kaldı.
- Sistem: tam sistem açık bırakıldı, dokunulmadı.

STATUS: DONE
