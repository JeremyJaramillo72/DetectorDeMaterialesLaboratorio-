import os
import sys
import zipfile
from pathlib import Path

if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

BASE_DIR = Path(r"c:\Users\ASUS\Documents\DetectorDeMaterialesLaboratorio")
DATASET_DIR = BASE_DIR / "dataset"
DATA_YAML = BASE_DIR / "data.yaml"
OUTPUT_ZIP = BASE_DIR / "dataset_bromatologia_uteq.zip"

def create_dataset_zip():
    print(f"[*] Empaquetando dataset en {OUTPUT_ZIP.name}...")
    with zipfile.ZipFile(OUTPUT_ZIP, "w", zipfile.ZIP_DEFLATED) as zipf:
        # Añadir data.yaml
        if DATA_YAML.exists():
            zipf.write(DATA_YAML, arcname="data.yaml")
            print("  + data.yaml")
        
        # Añadir todas las imágenes y labels
        count = 0
        for root, _, files in os.walk(DATASET_DIR):
            for file in files:
                file_path = Path(root) / file
                arcname = file_path.relative_to(BASE_DIR)
                zipf.write(file_path, arcname=arcname.as_posix())
                count += 1
        
        print(f"  + {count} archivos empaquetados.")
    
    zip_size_mb = OUTPUT_ZIP.stat().st_size / (1024 * 1024)
    print(f"[OK] Paquete de Dataset generado con éxito: {OUTPUT_ZIP.name} ({zip_size_mb:.2f} MB)")

if __name__ == "__main__":
    create_dataset_zip()
