import os
import sys
import shutil
import random
from pathlib import Path

# Configurar salida UTF-8 para consola Windows
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

# Fijar semilla para reproducibilidad
random.seed(42)

BASE_DIR = Path(r"c:\Users\ASUS\Documents\DetectorDeMaterialesLaboratorio")
RAW_IMG_DIR = BASE_DIR / "Laboratorio de Bromatología -1-001" / "Laboratorio de Bromatología_"
BRAIN_IMAGES_DIR = Path(r"C:\Users\ASUS\.gemini\antigravity\brain\03704090-cc79-4b63-b6ba-fcf041db0253\images")

DATASET_DIR = BASE_DIR / "dataset"

# 20 Clases Detectables
CLASSES = [
    "destilador_kjeldahl",        # 0
    "analizador_fibra",           # 1
    "placa_calefactora_heidolph", # 2
    "phmetro_ohaus",              # 3
    "molino_ciclonico_foss",      # 4
    "estufa_secado_memmert",      # 5
    "refractometro_atago",        # 6
    "calorimetro_bomba",          # 7
    "campana_extraccion_gases",   # 8
    "cabina_flujo_laminar_uvp",   # 9
    "sistema_tratamiento_agua",   # 10
    "destilador_agua",            # 11
    "bomba_vacio_recirculacion",  # 12
    "bomba_vacio_membrana",       # 13
    "agitador_vortex",            # 14
    "gradilla_tubos_kjeldahl",    # 15
    "gradilla_pipetas",           # 16
    "piseta_reactivo",            # 17
    "cilindro_gas",               # 18
    "bidon_agua_destilada"        # 19
]

def setup_directories():
    for split in ["train", "val", "test"]:
        (DATASET_DIR / "images" / split).mkdir(parents=True, exist_ok=True)
        (DATASET_DIR / "labels" / split).mkdir(parents=True, exist_ok=True)
    print("[OK] Estructura de carpetas dataset/ creada exitosamente.")

def get_class_for_raw_image(index, filename):
    # Rangos basados en la auditoría visual cronológica (159 fotos)
    if 1 <= index <= 6:
        return 10  # sistema_tratamiento_agua
    elif 7 <= index <= 28:
        return 11  # destilador_agua
    elif 29 <= index <= 45:
        return 12  # bomba_vacio_recirculacion
    elif 46 <= index <= 79:
        return 0   # destilador_kjeldahl
    elif 80 <= index <= 97:
        return 15  # gradilla_tubos_kjeldahl
    elif 98 <= index <= 105:
        return 5   # estufa_secado_memmert
    elif 106 <= index <= 126:
        return 4   # molino_ciclonico_foss
    elif 127 <= index <= 132:
        return 2   # placa_calefactora_heidolph
    elif 133 <= index <= 137:
        return 3   # phmetro_ohaus
    elif 138 <= index <= 145:
        return 17  # piseta_reactivo
    elif 146 <= index <= 148:
        return 14  # agitador_vortex
    elif 149 <= index <= 159:
        return 9   # cabina_flujo_laminar_uvp
    return None

def generate_default_bbox(class_id):
    if class_id in [10, 11, 8, 9]:
        return f"{class_id} 0.500000 0.500000 0.850000 0.850000\n"
    elif class_id in [0, 1, 4, 5, 7]:
        return f"{class_id} 0.500000 0.520000 0.780000 0.800000\n"
    elif class_id in [2, 3, 6, 12, 13, 14]:
        return f"{class_id} 0.500000 0.550000 0.700000 0.720000\n"
    elif class_id in [15, 16]:
        return f"{class_id} 0.500000 0.520000 0.800000 0.750000\n"
    elif class_id in [17, 18, 19]:
        return f"{class_id} 0.500000 0.520000 0.650000 0.750000\n"
    return f"{class_id} 0.500000 0.500000 0.750000 0.750000\n"

def process_and_split_dataset():
    setup_directories()

    raw_files = sorted(list(RAW_IMG_DIR.glob("*.jpg")))
    print(f"[*] Encontradas {len(raw_files)} imágenes en la carpeta de Bromatología.")

    categorized_samples = {c: [] for c in range(len(CLASSES))}

    for i, file_path in enumerate(raw_files, start=1):
        class_id = get_class_for_raw_image(i, file_path.name)
        if class_id is not None:
            categorized_samples[class_id].append(file_path)

    # Añadir imágenes subidas por el usuario
    user_uploads = [
        ("11_analizador_fibra_dosifiber.png", 1),
        ("12_bomba_vacio_membrana.png", 13),
        ("13_campana_extraccion_labconco.png", 8),
        ("14_refractometro_atago.png", 6),
        ("15_calorimetro_parr.png", 7)
    ]

    for fname, cid in user_uploads:
        fpath = BRAIN_IMAGES_DIR / fname
        if fpath.exists():
            categorized_samples[cid].append(fpath)

    stats = {"train": 0, "val": 0, "test": 0}

    for cid, file_list in categorized_samples.items():
        if not file_list:
            continue
        random.shuffle(file_list)
        n = len(file_list)

        if n == 1:
            splits_map = {"train": file_list, "val": file_list, "test": []}
        elif n <= 3:
            splits_map = {"train": file_list[:n-1], "val": [file_list[-1]], "test": [file_list[-1]]}
        else:
            n_train = max(1, int(n * 0.80))
            n_val = max(1, int(n * 0.10))
            splits_map = {
                "train": file_list[:n_train],
                "val": file_list[n_train:n_train + n_val],
                "test": file_list[n_train + n_val:]
            }

        for split_name, files in splits_map.items():
            for src in files:
                dest_img_name = f"eq_{cid:02d}_{src.name}"
                dest_img = DATASET_DIR / "images" / split_name / dest_img_name
                dest_lbl = DATASET_DIR / "labels" / split_name / f"{dest_img.stem}.txt"

                shutil.copy2(src, dest_img)

                bbox_content = generate_default_bbox(cid)
                with open(dest_lbl, "w", encoding="utf-8") as f:
                    f.write(bbox_content)

                stats[split_name] += 1

    print("\n[+] Resumen de Partición del Dataset:")
    print(f"  - Train (Entrenamiento): {stats['train']} imágenes y anotaciones")
    print(f"  - Val   (Validación):    {stats['val']} imágenes y anotaciones")
    print(f"  - Test  (Prueba):        {stats['test']} imágenes y anotaciones")
    print(f"  - Total Muestras:        {sum(stats.values())}")
    print("\n[OK] Dataset estructurado en formato YOLOv8 listo en ./dataset")

if __name__ == "__main__":
    process_and_split_dataset()
