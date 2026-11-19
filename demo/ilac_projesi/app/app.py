import os
import sqlite3

import streamlit as st
from PIL import Image
from ultralytics import YOLO


# Ana proje klasörü
BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Dosya yolları
MODEL_PATH = os.path.join(BASE_DIR, "model", "yolov11n_best.pt")
DB_PATH = os.path.join(BASE_DIR, "database", "ilaclar.db")


# Sayfa ayarları
st.set_page_config(
    page_title="İlaç Kutusu Tanıma Sistemi",
    page_icon="💊",
    layout="centered"
)

st.title("💊 İlaç Kutusu Tanıma Sistemi")
st.write(
    "İlaç kutusu fotoğrafı yükleyin. "
    "Sistem ilacı tespit edip SQLite veri tabanından bilgi kartını gösterecektir."
)


# Model yükleme
@st.cache_resource
def load_model():
    return YOLO(MODEL_PATH)


# SQLite veri tabanından ilaç bilgisi çekme
def get_medicine_info(model_etiketi):
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()

    cursor.execute("""
        SELECT 
            model_etiketi,
            ilac_adi,
            ne_ise_yarar,
            iyi_geldigi_1,
            iyi_geldigi_2,
            iyi_geldigi_3
        FROM ilaclar
        WHERE model_etiketi = ?
    """, (model_etiketi,))

    row = cursor.fetchone()
    conn.close()

    return row


# Dosya kontrolleri
if not os.path.exists(MODEL_PATH):
    st.error(f"Model dosyası bulunamadı: {MODEL_PATH}")
    st.stop()

if not os.path.exists(DB_PATH):
    st.error(f"SQLite veri tabanı bulunamadı: {DB_PATH}")
    st.stop()


model = load_model()


uploaded_file = st.file_uploader(
    "Fotoğraf yükleyin",
    type=["jpg", "jpeg", "png"]
)


if uploaded_file is not None:
    image = Image.open(uploaded_file).convert("RGB")

    st.subheader("Yüklenen Görsel")
    st.image(image, use_container_width=True)

    if st.button("İlacı Tanı"):
        results = model.predict(
            source=image,
            imgsz=640,
            conf=0.25
        )

        result = results[0]

        if len(result.boxes) == 0:
            st.warning(
                "İlaç kutusu tespit edilemedi. "
                "Daha net veya yakından çekilmiş bir fotoğraf deneyin."
            )

        else:
            st.subheader("Model Tahmini")

            annotated_image = result.plot()
            st.image(
                annotated_image,
                caption="Tespit Edilen İlaç Kutuları",
                use_container_width=True
            )

            st.caption(f"Veri kaynağı: SQLite veri tabanı → {DB_PATH}")

            boxes = result.boxes
            detected_items = []

            for i in range(len(boxes)):
                class_id = int(boxes.cls[i].item())
                confidence = float(boxes.conf[i].item())
                predicted_class = model.names[class_id]

                detected_items.append({
                    "model_etiketi": predicted_class,
                    "confidence": confidence
                })

            # Güven skoruna göre büyükten küçüğe sırala
            detected_items = sorted(
                detected_items,
                key=lambda x: x["confidence"],
                reverse=True
            )

            # Aynı ilaç birden fazla tespit edilirse tek kez göster
            unique_items = []
            seen_classes = set()

            for item in detected_items:
                if item["model_etiketi"] not in seen_classes:
                    unique_items.append(item)
                    seen_classes.add(item["model_etiketi"])

            st.write(f"Tespit edilen farklı ilaç sayısı: `{len(unique_items)}`")

            for item in unique_items:
                predicted_class = item["model_etiketi"]
                confidence = item["confidence"]

                medicine = get_medicine_info(predicted_class)

                st.divider()

                st.write(f"Tahmin edilen etiket: `{predicted_class}`")
                st.write(f"Güven skoru: `{confidence:.2f}`")

                if medicine is None:
                    st.error("Bu model etiketi SQLite veri tabanında bulunamadı.")
                    st.write("Veri tabanına eklenmesi gereken model etiketi:")
                    st.code(predicted_class)

                else:
                    st.success("İlaç bilgisi SQLite veri tabanından başarıyla çekildi.")
                    st.write(
                        f"Veri tabanı eşleşen kayıt etiketi: "
                        f"`{medicine['model_etiketi']}`"
                    )

                    st.subheader(f"İlaç Bilgi Kartı: {medicine['ilac_adi']}")

                    st.write(f"Ne işe yarar: {medicine['ne_ise_yarar']}")

                    st.write("İyi geldiği durumlar:")
                    st.write(f"1. {medicine['iyi_geldigi_1']}")
                    st.write(f"2. {medicine['iyi_geldigi_2']}")
                    st.write(f"3. {medicine['iyi_geldigi_3']}")

            st.info(
                "Bu bilgiler genel bilgilendirme amaçlıdır. "
                "Tanı veya tedavi önerisi değildir."
            )