import sqlite3
import pandas as pd
import os

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

CSV_PATH = os.path.join(BASE_DIR, "tum_ilaclar_bilgi_karti_model_etiketli.csv")
DB_PATH = os.path.join(BASE_DIR, "database", "ilaclar.db")

df = pd.read_csv(CSV_PATH)

conn = sqlite3.connect(DB_PATH)
cursor = conn.cursor()

cursor.execute("""
CREATE TABLE IF NOT EXISTS ilaclar (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    model_etiketi TEXT UNIQUE NOT NULL,
    ilac_adi TEXT NOT NULL,
    ne_ise_yarar TEXT,
    iyi_geldigi_1 TEXT,
    iyi_geldigi_2 TEXT,
    iyi_geldigi_3 TEXT
)
""")

cursor.execute("DELETE FROM ilaclar")

for _, row in df.iterrows():
    cursor.execute("""
    INSERT INTO ilaclar (
        model_etiketi,
        ilac_adi,
        ne_ise_yarar,
        iyi_geldigi_1,
        iyi_geldigi_2,
        iyi_geldigi_3
    )
    VALUES (?, ?, ?, ?, ?, ?)
    """, (
        row["model_etiketi"],
        row["ilac_adi"],
        row["ne_ise_yarar"],
        row["iyi_geldigi_1"],
        row["iyi_geldigi_2"],
        row["iyi_geldigi_3"]
    ))

conn.commit()
conn.close()

print("CSV verileri başarıyla SQLite veri tabanına aktarıldı.")
print("Veri tabanı yolu:", DB_PATH)