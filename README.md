# 🎬 CloudStream Turkish Providers Repository

CloudStream 3 için özel olarak hazırlanmış, reklamsız Türkçe dizi ve film sağlayıcıları deposudur.

---

## 📦 İçerilen Sağlayıcılar (Providers)

| Sağlayıcı | Tür | Desteklenen İçerikler |
| :--- | :--- | :--- |
| **Dizipal** | Dizi, Film, Anime | Popüler Diziler, Son Eklenen Filmler, Trendler |
| **HdFilmCehennemi** | Film, Dizi | Full HD Türkçe Dublaj & Altyazılı Filmler, Yabancı Diziler |
| **Yabancı Dizi** | Dizi, Film | Güncel Yabancı Diziler, Sezon & Bölüm Listeleri |

---

## 🚀 1. Adım: Projeyi GitHub'a Yükleme

GitHub'da yeni bir **Public (Herkese Açık)** depo oluşturun (örneğin: `CloudStreamRepo`) ve terminalde şu komutları çalıştırarak kodları gönderin:

```bash
git remote add origin https://github.com/vlknsozeri/CloudStreamRepo.git
git branch -M main
git push -u origin main
```

---

## ⚙️ 2. Adım: GitHub Actions İzinlerini Açma (Çok Önemli)

Eklentilerin otomatik derlenip CloudStream'e hazır hale getirilmesi için GitHub'da:

1. Deponuzun **Settings** sekmesine gidin.
2. Sol menüden **Actions -> General** bölümüne tıklayın.
3. Aşağı kaydırıp **Workflow permissions** kısmında **"Read and write permissions"** seçeneğini işaretleyip **Save** butonuna tıklayın.

> [!NOTE]
> Kodları `main` branch'ine her gönderdiğinizde GitHub Actions otomatik olarak çalışır; eklentileri derleyip `.cs3` dosyalarını ve `repo.json` manifestini `builds` branch'ine yükler.

---

## 🔗 3. Adım: Kısa Kod (Shortcode) veya URL Alma

Derleme tamamlandıktan sonra deponuzun bağlantısı şu formatta hazır olur:

```text
https://raw.githubusercontent.com/vlknsozeri/CloudStreamRepo/builds/repo.json
```

### ⚡ Kısa Kod Oluşturma (TV ve Kolay Giriş İçin):
Uzun URL'yi tek tek yazmak yerine:
1. Yukarıdaki bağlantıyı kopyalayın.
2. [TinyURL.com](https://tinyurl.com) veya [is.gd](https://is.gd) gibi bir servise yapıştırın.
3. Kendinize özel bir kısaltma belirleyin (Örn: `tinyurl.com/tr-dizi`).
4. CloudStream uygulamasına sadece bu kısa bağlantıyı yazmanız yeterlidir! CloudStream yönlendirmeyi otomatik takip eder.

---

## 📱 4. Adım: CloudStream APK'sına Ekleme

1. Android cihazınızda veya Android TV'nizde **CloudStream** uygulamasını açın.
2. **Ayarlar (Settings)** ➔ **Eklentiler (Extensions)** bölümüne gidin.
3. **Depo Ekle (Add Repository)** butonuna tıklayın.
4. **Depo Adı:** `Türkçe Dizi & Film`
5. **Depo URL veya Kısa Kod:** Yukarıdaki `repo.json` URL'nizi veya oluşturduğunuz kısa bağlantıyı yapıştırın.
6. **Depo Ekle (Add Repository)** deyin.
7. Listelenen eklentileri görüp **İndir / Yükle** butonuna basarak reklamsız izlemenin keyfini çıkarın!
