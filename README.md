# Pro SatFinder

Uydu kataloğu + 20 dil (19 istenen dil, Azerbaycan Türkçesi Latin ve Arap alfabesi ayrı).

## Derleme
Gradle wrapper dosyaları ppakette yok. Android Studio projeyi açınca oluşturur; ya da GitHub'a yükle:
`.github/workflows/build.yml` testleri çalıştırır ve debug APK üretir (Gradle 8.7, JDK 17).
Yerelde: `gradle testDebugUnitTest assembleDebug`. minSdk 26, targetSdk 34 (Android 11'de çalışır).

## Katalog (dilden bağımsız)
- Kaynak dosya: `filesDir/catalog/catalog.json`. İlk açılışta `assets/catalog_seed.json` kopyalanır; internet gerekmez.
- Ayarlar > "Kataloğu güncelle": HTTPS adresinden JSON indirir, **önce doğrular**, sonra eski dosyayı
  `catalog/backups/` altına yedekler (son 5), Room'a tek transaction'da yazar. Hata olursa eski katalog korunur.
- Favoriler ayrı tabloda; katalog güncellenince silinmez.
- Veri tarihi (`updatedAt`) ve kaynağı (`source`) ana ekranın altında görünür.

### JSON biçimi (schemaVersion 1)
```json
{
  "schemaVersion": 1, "dataVersion": "2026-11-11", "updatedAt": "2026-11-11",
  "source": "Kaynak adı", "sourceUrl": "https://…",
  "satellites": [{
    "id": "benzersiz-id", "name": "Ad", "operator": "Operatör",
    "position": 13.0,                 // doğu +, batı −
    "status": "active|inactive|unverified",
    "checkedDate": "2026-11-01",      // "active" için ZORUNLU
    "countries": ["TR"], "continents": ["150"],   // ISO 3166, UN M.49
    "transponders": [{ "band": "Ku", "freqMhz": 11727, "pol": "V", "symbolRate": 27500, "fec": "3/4" }]
  }]
}
```
Kurallar (`CatalogValidator`): band C/Ku/Ka ve downlink aralıkları, pol H/V/L/R, `active` için doğrulama tarihi şart.
Bilinmeyen alan boş bırakılır (uydurulmaz). Kıta kodları: 150 Avrupa, 142 Asya, 002 Afrika, 021 Kuzey Amerika, 419 Latin Amerika, 009 Okyanusya.

## Diller
- Metinler `res/values-*/strings.xml` içinde; varsayılan `values/` İngilizce, eksik çeviri İngilizceye düşer.
- Ülke ve kıta adları ICU'dan seçili dilde gelir. Uydu adları özel isimdir, çevrilmez. MHz/SR/FEC uluslararası semboller.
- RTL: ar, fa, ur, ckb, azb, az-Arab (`isRtl`).
- **Yeni dil**: `language_names.xml` + `locales_config.xml` + `values-<kod>/strings.xml`. `tools/gen_strings.py` örnek üretici.
- **Test**: `StringsCompletenessTest` eksik/fazla anahtar, boş değer, yer tutucu uyuşmazlığı ve kayıtsız dilleri yakalar.
- Çeviriler ilk taslaktır; özellikle azb, az-Arab, ckb, kmr ana dili konuşanlarca gözden geçirilmeli.
