# İlaç Kutusu Tanıma — Bitirme Tezi

Bu proje, ilaç kutusu tanımayı araştıran bir Android uygulaması ve Streamlit demosu içerir. Uygulama eğitim amaçlı bir prototiptir.

## Android

`android/IlacTanima` klasörünü Android Studio’da aç. Model, etiketler ve ilaç kataloğu uygulama varlıklarına dahildir. Kurulum bilgileri için [Android README](android/IlacTanima/README.md) dosyasına bak.

## Streamlit demosu

```powershell
cd demo/ilac_projesi
python -m pip install -r requirements.txt
python -m streamlit run app/app.py
```

Demo YOLO modelini ve SQLite kataloğunu yerel olarak kullanır. Tam eğitim veri seti ve deney grafikleri bu depoya dahil değildir.
