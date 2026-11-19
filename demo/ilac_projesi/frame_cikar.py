import cv2
import os

VIDEO_KLASORU = "videolar"
CIKTI_KLASORU = "frames"
FRAME_ARALIGI = 5

os.makedirs(CIKTI_KLASORU, exist_ok=True)

for video_adi in os.listdir(VIDEO_KLASORU):
    if not video_adi.lower().endswith((".mov", ".mp4", ".avi", ".mkv")):
        continue

    video_yolu = os.path.join(VIDEO_KLASORU, video_adi)
    ilac_etiketi = os.path.splitext(video_adi)[0]

    ilac_klasoru = os.path.join(CIKTI_KLASORU, ilac_etiketi)
    os.makedirs(ilac_klasoru, exist_ok=True)

    cap = cv2.VideoCapture(video_yolu)

    if not cap.isOpened():
        print(f"Acilamadi: {video_adi}")
        continue

    frame_sayaci = 0
    kayit_sayaci = 0

    while True:
        ret, frame = cap.read()

        if not ret:
            break

        if frame_sayaci % FRAME_ARALIGI == 0:
            dosya_adi = f"{ilac_etiketi}_{kayit_sayaci:04d}.jpg"
            cikti_yolu = os.path.join(ilac_klasoru, dosya_adi)
            cv2.imwrite(cikti_yolu, frame)
            kayit_sayaci += 1

        frame_sayaci += 1

    cap.release()

    print(f"{video_adi} tamamlandi. Cikan frame: {kayit_sayaci}")

print("Tum videolardan frame cikarma tamamlandi.")