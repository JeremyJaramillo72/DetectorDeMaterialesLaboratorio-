# 🔬 Asistente Inteligente de Bromatología UTEQ
### Detección en Tiempo Real con YOLO11 + TensorFlow Lite + Sistema RAG

[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-blue.svg?logo=kotlin)](https://kotlinlang.org/)
[![YOLO11](https://img.shields.io/badge/YOLO11-Ultralytics-00ffff.svg)](https://ultralytics.com)
[![TensorFlow Lite](https://img.shields.io/badge/TensorFlow_Lite-2.16.1-orange.svg?logo=tensorflow)](https://tensorflow.org/lite)
[![FastAPI](https://img.shields.io/badge/FastAPI-RAG_Backend-009688.svg?logo=fastapi)](https://fastapi.tiangolo.com)
[![UTEQ](https://img.shields.io/badge/Universidad-UTEQ_Ecuador-0D5C3A.svg)](https://www.uteq.edu.ec/)

Aplicación móvil Android nativa desarrollada para la **Universidad Técnica Estatal de Quevedo (UTEQ)** que detecta, localiza e identifica en tiempo real **20 equipos, instrumentos y materiales del Laboratorio de Bromatología** utilizando visión artificial de última generación (**YOLO11 Nano**) y responde consultas técnicas mediante un asistente conversacional inteligente con **RAG** (Retrieval-Augmented Generation) basado en protocolos, normas oficiales (AOAC, INEN, ISO) y bioseguridad.

---

## 📱 Características Principales

1. **Detección en Tiempo Real a 30+ FPS**:
   - Procesamiento de cámara en vivo con **CameraX**.
   - Inferencia en dispositivo con **TensorFlow Lite (`yolo11_bromatologia.tflite`)**.
   - Bounding boxes con indicador visual verde institucional UTEQ (`#0D5C3A`), nombre del equipo y porcentaje de certeza.

2. **Fichas Técnicas Interactivas**:
   - Despliegue de BottomSheet interactivo al tocar el equipo detectado.
   - Especificaciones técnicas: Principio de funcionamiento, componentes, procedimientos operativos estándar (SOPs), normas oficiales aplicables (AOAC, INEN, ISO), EPP obligatorio y riesgos de bioseguridad.

3. **Asistente Conversacional RAG (Online & Offline)**:
   - **Modo Online**: Servidor FastAPI conectado a Google Gemini API para razonamiento avanzado.
   - **Modo Offline Autónomo**: Motor RAG local integrado en la app que funciona en el laboratorio sin conexión a internet.

---

## 🧪 20 Equipos y Materiales Detectables

| N° | ID de Clase | Equipo / Material | Marca y Modelo |
|---|---|---|---|
| 1 | `destilador_kjeldahl` | Unidad de Destilación Kjeldahl | J.P. Selecta Pro-Nitro A |
| 2 | `analizador_fibra` | Analizador de Fibra Dosi-Fiber | J.P. Selecta DOSI-FIBER |
| 3 | `placa_calefactora_heidolph` | Placa con Agitador Magnético | Heidolph MR Hei-Standard |
| 4 | `phmetro_ohaus` | pH-metro Digital de Mesa | OHAUS STARTER 3100 |
| 5 | `molino_ciclonico_foss` | Molino Ciclónico de Muestras | FOSS Cyclotec 1093 |
| 6 | `estufa_secado_memmert` | Estufa de Secado Universal | Memmert UN55 / UN110 |
| 7 | `refractometro_atago` | Refractómetro Abbe Digital | ATAGO DR-A1 |
| 8 | `calorimetro_bomba` | Bomba Calorimétrica de Oxígeno | Parr 1341 / Termómetro 6775 |
| 9 | `campana_extraccion_gases` | Campana de Extracción de Gases | Labconco Protector Premier |
| 10 | `cabina_flujo_laminar_uvp` | Cabina de Flujo Laminar / UV PCR | Analytik Jena UVP Workstation |
| 11 | `sistema_tratamiento_agua` | Sistema de Desionización | Batería Desionizadora + Filtro |
| 12 | `destilador_agua` | Destilador de Agua Metálico | Destilador Mural Acero Inox |
| 13 | `bomba_vacio_recirculacion` | Bomba de Vacío por Recirculación | J.P. Selecta Cat. 4001611 |
| 14 | `bomba_vacio_membrana` | Bomba de Vacío de Membrana | Portátil con Doble Manómetro |
| 15 | `agitador_vortex` | Agitador Vortex de Tubos | Boeco / Bioevopeak V-1 Plus |
| 16 | `gradilla_tubos_kjeldahl` | Tubos Digestión Kjeldahl y Balones | Borosilicato 3.3 en Gradilla |
| 17 | `gradilla_pipetas` | Soporte Giratorio para Pipetas | Gradilla Circular 94 Posiciones |
| 18 | `piseta_reactivo` | Piseta Reactivo NaOH 0.1 N | LDPE con Rotulado SGA/GHS |
| 19 | `cilindro_gas` | Cilindro de Gas con Manómetros | Cilindro de Acero 200 bar |
| 20 | `bidon_agua_destilada` | Bidón de Agua Destilada con Embudo | PEAD con Embudo de Trasvase |

---

## 📊 Métricas del Modelo YOLO11

- **$mAP_{50}$:** `91.70%` (0.9170)
- **$mAP_{50-95}$:** `78.34%` (0.7834)
- **Velocidad de Inferencia:** `11.7 ms` por imagen (~85 FPS)
- **Tamaño del Modelo TFLite:** `10.1 MB`

---

## 📁 Estructura del Repositorio

```
├── app/                                # Código fuente de la App Android
│   ├── src/main/java/com/uteq/software/...
│   │   ├── data/                       # Repositorio RAG y carga JSON
│   │   ├── ml/                         # Inferencia YOLO11 con TFLite y NMS
│   │   ├── model/                      # Modelos de datos
│   │   ├── network/                    # Cliente HTTP para RAG Backend
│   │   └── ui/                         # Activities, OverlayView y BottomSheet
│   └── src/main/assets/
│       ├── yolo11_bromatologia.tflite  # Red neuronal entrenada
│       ├── labels.txt                  # 20 etiquetas de clase
│       └── manuales_bromatologia_uteq.json # Base de conocimiento RAG
├── backend/                            # Servidor RAG FastAPI con Gemini
│   ├── server.py
│   └── requirements.txt
├── dataset/                            # Dataset anotado en formato YOLO
├── entrenamiento_yolo11_bromatologia.ipynb # Cuaderno de Google Colab
├── catalogo_equipos_bromatologia.md    # Catálogo con especificaciones y normas
├── galeria_equipos.html                # Galería interactiva visual
└── data.yaml                           # Configuración YOLOv8/YOLO11
```

---

## 🚀 Instalación y Ejecución

### 1. Compilar y Ejecutar la App Android
Abre el proyecto en **Android Studio** o ejecuta desde terminal:
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. Servidor RAG Backend (Opcional)
```bash
cd backend
pip install -r requirements.txt
export GEMINI_API_KEY="tu_api_key"
uvicorn server:app --host 0.0.0.0 --port 8000 --reload
```

---

## 👨‍💻 Autor y Créditos
- **Universidad Técnica Estatal de Quevedo (UTEQ)**
- **Facultad de Ciencias Pecuarias y Biológicas - Laboratorio de Bromatología**
