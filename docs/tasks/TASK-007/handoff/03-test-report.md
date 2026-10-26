TASK: TASK-007
# Test raporu

## Kullanılan skill'ler
`backend-conventions` (bölüm 2, yeni yorum politikası). Yeni test yazılmadı (plan D9); mutation uygulanamaz.

## Yapılan iş
`backend/src/test` altındaki 59 dosyanın her birinin üst seviye tipine 1-2 satırlık Türkçe amaç Javadoc'u eklendi. Her metin, sınıfın test metot adları okunarak elle yazıldı (metinler `Test`/yardımcı sınıfın ne yaptığını söyler; test edilen sınıf adı düz metin + kesme ekiyle geçer, D3). Test kodunda yorum dışı hiçbir satır değişmedi. `backend/src/main`'e dokunulmadı.

## Plandan sapmalar
Yorumlar bir yerleştirme betiğiyle eklendi (backend-coder'ın yolu ve lead onayıyla aynı şartlar): betik yalnızca (1) elle yazılmış 59 metni, (2) 105 karakterde sözcük sınırından satıra bölme ve (3) tipin ilk annotation/tip satırının üstüne yerleştirmeyi yapar. Metin sınıf adından ya da şablondan üretilmedi. Dosyalar UTF-8 byte olarak yazıldı, satır sonları (LF) ve dosya sonu newline'ı korundu (V7: `\ No newline` yok). Başka sapma yok.

## Senaryo → test eşlemesi
Bu task'ta yeni test yok (D9); senaryolar V-kontrolleridir.

| # | Senaryo / Kural | Kontrol | Sonuç |
|---|-----------------|---------|-------|
| V1 | Her tipin üstünde Javadoc sonu (K1, K2) | planın V1 komutu (157 dosya) | Çıktı boş, geçti |
| V2 | Kapsam sayıları (K1, G1) | planın V2 komutları | main 98, test 59, `/**` içeren dosya 157, `package-info` boş; geçti |
| V3 | Dosya başına tek blok, sütun 0 | planın V3 komutları | İkisi de boş, geçti |
| V4 | 1-2 satır, biçim, ≤120 karakter | planın V4 komutları | İkisi de boş, geçti |
| V5 | Yorumda kendi adı yok | planın V5 komutu | Boş, geçti |
| V6 | Yasak karakter / İngilizce kelime | planın V6 komutları | İkisi de boş, geçti |
| V7 | Baseline'a göre sadece blok eklenmiş | `diff -r` baseline ↔ `backend/src` | `diff -r` 157; ekleme hunk'ı 157; değişiklik/silme, `<`, `Only in`, `\ No newline` boş; `>` satırlarının hepsi blok satırı; `>` sayısı 532 (471-628 aralığında). Test tarafı: 59 dosya, sadece ekleme; geçti |
| V8 | verify geçer, test sayısı aynı | `cd backend && ./mvnw verify` | `Tests run: 417, Failures: 0, Errors: 0, Skipped: 0`, "All coverage checks have been met", BUILD SUCCESS. Statik test annotation sayısı: baseline 329, şimdi 329; geçti |
| V9 | Javadoc aracı açıklamayı tipe bağlar | `dependency:build-classpath` + `javadoc -Xdoclint:none -encoding UTF-8` | javadoc çıkış kodu 0; `SourceService.html`'de "Çalışma alanındaki kaynakları sayfa sayfa listeler" 1 kez; geçti |

## Mutation
Uygulanamaz: bu task yalnız yorum ekler, davranış değişmez; PIT çalıştırılmadı.

## Komutlar ve sonuçları
- V1-V7: yukarıdaki tablo (repo kökünden, planın komutlarıyla; V7 çıktısı `scratch/TASK-007-diff.txt`).
- `cd backend && ./mvnw verify` → BUILD SUCCESS, 417 test, 0 başarısız/hata/atlanan, JaCoCo eşiği karşılandı (log: `scratch/TASK-007-verify.log`).
- V9 javadoc: çıkış kodu 0.

## Çalıştırılamayanlar
Yok (frontend ve e2e bu task'ın kapsamı dışı).

## Düzeltme
Yok. Main'de kural ihlali görülmedi (V1-V6 157 dosyanın tamamını kapsar ve temiz).

## Düzeltme turu 1
qa Nit'i (`ChunkIntegrationTest` Javadoc dilbilgisi). Testin doğruladığı 404 senaryoları: başka kaynağa ait parça (`CHUNK_NOT_FOUND`), başka çalışma alanındaki kaynak (`SOURCE_NOT_FOUND`), bilinmeyen çalışma alanı (`WORKSPACE_NOT_FOUND`), yenilenmiş parça (`CHUNK_NOT_FOUND`).

| Madde | Eski cümle | Yeni cümle |
|-------|-----------|-----------|
| ChunkIntegrationTest Javadoc | Parça getirme ucunu gerçek PostgreSQL ile doğrular; başka kaynağın, başka çalışma alanının ya da yenilenmiş parça için 404 döner. | Tek parça getirme ucunu gerçek PostgreSQL ile doğrular; başka kaynağa ait parça, başka çalışma alanındaki kaynak, bilinmeyen çalışma alanı ve yenilenmiş parça için 404 döner. |

Sadece bu dosyanın Javadoc satırları değişti (2 satır, ≤120 karakter, yasak karakter/İngilizce kelime yok). Kontroller: V4 satır uzunluğu komutu boş; `diff -r` baseline ↔ `backend/src` hâlâ 157 dosya. `cd backend && ./mvnw verify` → BUILD SUCCESS, `Tests run: 417, Failures: 0, Errors: 0, Skipped: 0`, JaCoCo eşiği karşılandı.

STATUS: DONE
