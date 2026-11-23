# Ezan Vakti (Android)

Diyanet yöntemiyle (İmsak 18°, Yatsı 17°, Diyanet ihtiyat payları) vakit hesaplayan, vakit girince ezanı kendisi çalan Android uygulaması.

## Neler yapar

- 26 şehir (Türkiye, Hollanda, Avrupa, Mekke) veya elle enlem/boylam/saat dilimi girişi.
- Her vakit için ayrı ses dosyası seçimi. Her vakit kartında geleneksel makam önerisi yazar (Sabah Saba, Öğle Uşşak, İkindi Rast, Akşam Segâh, Yatsı Hicaz). Bu eşleşme bağlayıcı değil, istediğiniz kaydı seçebilirsiniz.
- Vakit girince ezan arka planda ve uygulama kapalıyken çalar (kesin alarm + ön plan servisi).
- Bildirim panelinde (Samsung dahil) kalıcı kart: şehir, altı vaktin saati ve sıradaki vakte geri sayım.
- Telefon yeniden başlayınca, saat veya saat dilimi değişince alarmlar kendiliğinden yeniden kurulur.

## Ses dosyaları

Uygulamada ezan kaydı yoktur. Her vakit için kendi mp3/m4a/ogg dosyanızı "Ses seç" ile ekleyin. Dosya uygulamanın içine kopyalanır, sonradan silmeniz sorun olmaz. Seçmediğiniz vakitte telefonun varsayılan bildirim sesi çalar. Güneş vakti varsayılan olarak kapalıdır.

## APK nasıl alınır

### Yol 1: GitHub ile (bilgisayarda Android Studio gerekmez)

1. github.com'da yeni bir repo açın.
2. Bu klasörün tamamını (gizli `.github` klasörü dahil) repoya yükleyin. Web arayüzünde gizli klasör kaçabilir; `git push` daha güvenlidir.
3. Repo > Actions > "APK derle" > Run workflow.
4. İş bitince sayfanın altındaki Artifacts bölümünden `ezan-vakti-apk` dosyasını indirin, zip'in içinden `app-debug.apk` çıkar.

### Yol 2: Android Studio

Klasörü Android Studio ile açın, Gradle senkronizasyonu bitince Build > Build APK(s).

## Telefona kurulum

1. APK'yı telefona aktarın ve açın. "Bilinmeyen kaynaklardan yükleme" izni isteyebilir.
2. İlk açılışta bildirim iznini verin.
3. Uygulamanın en altındaki durum satırlarında üç tikin de (✓) olmasını sağlayın: bildirim izni, kesin alarm izni, pil kısıtlaması.
4. Samsung: Ayarlar > Pil > Arka plan kullanım sınırları. "Uyku modundaki uygulamalar" listesinde Ezan Vakti olmasın, "Hiç uyumayan uygulamalar" listesine ekleyin. Bu yapılmazsa One UI uygulamayı kısıtlayıp ezanı geciktirebilir.
5. Her vakit için "Ses seç", ardından "Dene" ile çalıp kontrol edin.

## Bilinen sınırlar

- Bu proje hazırlanırken derlenip telefonda denenmedi; hesaplama motoru ayrıca doğrulandı, Android kısımları (alarm, servis, bildirim) ilk kurulumda test edilmeli.
- Diyanet tablolarıyla 1-2 dakika fark olabilir. İbadet için resmî takvimi esas alın.
- Amsterdam gibi yüksek enlemlerde yazın İmsak ve Yatsı yaklaşık hesaplanır ve uygulamada "yaklaşık" diye işaretlenir.
- Bildirim panelindeki kartı elle kaydırıp kapatırsanız bir sonraki vakitte kendiliğinden geri gelir.
