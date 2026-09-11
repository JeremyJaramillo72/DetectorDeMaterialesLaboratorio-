# -*- coding: utf-8 -*-
"""
Generador del Banco de Preguntas y Simulador de Examen Técnico (.docx)
Laboratorio de Bromatología - Universidad Técnica Estatal de Quevedo (UTEQ)
"""

import os
import sys
import docx
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import parse_xml
from docx.oxml.ns import nsdecls

WORKSPACE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
KB_DIR = os.path.join(WORKSPACE_DIR, "knowledge_base")
OUTPUT_DOCX = os.path.join(KB_DIR, "Cuestionario_Examen_Detector_Bromatologia_UTEQ.docx")

COLOR_BLACK = RGBColor(0x00, 0x00, 0x00)
COLOR_DARK_GRAY = RGBColor(0x33, 0x33, 0x33)
COLOR_GRAY = RGBColor(0x66, 0x66, 0x66)
COLOR_WHITE = RGBColor(0xFF, 0xFF, 0xFF)

def set_cell_background(cell, fill_hex):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{fill_hex}"/>')
    tc_pr.append(shd)

def set_cell_margins(cell, top=100, bottom=100, left=140, right=140):
    tc_pr = cell._tc.get_or_add_tcPr()
    tcMar = parse_xml(f'<w:tcMar {nsdecls("w")}>'
                      f'<w:top w:w="{top}" w:type="dxa"/>'
                      f'<w:bottom w:w="{bottom}" w:type="dxa"/>'
                      f'<w:left w:w="{left}" w:type="dxa"/>'
                      f'<w:right w:w="{right}" w:type="dxa"/>'
                      f'</w:tcMar>')
    tc_pr.append(tcMar)

def add_answer_box(doc, correct_opt, explanation, code_ref):
    """Crea una caja destacada con la respuesta correcta, justificación y referencia de código."""
    tbl = doc.add_table(rows=1, cols=1)
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    tbl.autofit = False
    
    cell = tbl.cell(0, 0)
    cell.width = Inches(6.5)
    set_cell_background(cell, "F4F4F4")
    set_cell_margins(cell, top=120, bottom=120, left=180, right=180)
    
    tc_pr = cell._tc.get_or_add_tcPr()
    borders = parse_xml(f'<w:tcBorders {nsdecls("w")}>'
                        f'<w:top w:val="none"/>'
                        f'<w:left w:val="single" w:sz="36" w:space="0" w:color="000000"/>'
                        f'<w:bottom w:val="none"/>'
                        f'<w:right w:val="none"/>'
                        f'</w:tcBorders>')
    tc_pr.append(borders)
    
    p = cell.paragraphs[0]
    p.paragraph_format.space_before = Pt(3)
    p.paragraph_format.space_after = Pt(2)
    p.paragraph_format.line_spacing = 1.15
    
    r_ans = p.add_run(f"✔ RESPUESTA CORRECTA: {correct_opt}\n")
    r_ans.bold = True
    r_ans.font.name = "Calibri"
    r_ans.font.size = Pt(10)
    r_ans.font.color.rgb = COLOR_BLACK
    
    r_exp_lbl = p.add_run("Justificación Técnica: ")
    r_exp_lbl.bold = True
    r_exp_lbl.font.name = "Calibri"
    r_exp_lbl.font.size = Pt(9.5)
    r_exp_lbl.font.color.rgb = COLOR_DARK_GRAY
    
    r_exp = p.add_run(f"{explanation}\n")
    r_exp.font.name = "Calibri"
    r_exp.font.size = Pt(9.0)
    r_exp.font.color.rgb = COLOR_DARK_GRAY
    
    r_ref_lbl = p.add_run("Ubicación en Código: ")
    r_ref_lbl.bold = True
    r_ref_lbl.font.name = "Calibri"
    r_ref_lbl.font.size = Pt(9.0)
    r_ref_lbl.font.color.rgb = COLOR_BLACK
    
    r_ref = p.add_run(code_ref)
    r_ref.font.name = "Consolas"
    r_ref.font.size = Pt(8.5)
    r_ref.font.color.rgb = COLOR_DARK_GRAY
    
    p_sp = doc.add_paragraph()
    p_sp.paragraph_format.space_before = Pt(0)
    p_sp.paragraph_format.space_after = Pt(6)

PREGUNTAS = [
    # -------------------------------------------------------------
    # MÓDULO 1: ARQUITECTURA ANDROID Y PIPELINE DE CÁMARA
    # -------------------------------------------------------------
    {
        "num": 1,
        "modulo": "Módulo 1: Arquitectura Android y Pipeline de Cámara (MainActivity.kt)",
        "pregunta": "¿Qué API de cámara implementa la aplicación para la captura en vivo y cuál es la ventaja principal sobre las APIs anteriores?",
        "opciones": [
            "A) Usa Camera1 (android.hardware.Camera) porque es más liviana y no requiere permisos en runtime.",
            "B) Usa CameraX (androidx.camera.core / lifecycle) porque gestiona automáticamente el ciclo de vida de la Activity y es compatible con el 98% de dispositivos Android.",
            "C) Usa el NDK con libjpeg-turbo para capturar directamente desde el driver de Linux.",
            "D) Usa la API de MediaPlayer leyendo un stream RTSP local."
        ],
        "correcta": "B",
        "justificacion": "CameraX abstrae la complejidad de la fragmentación de dispositivos Android y se acopla al ciclo de vida mediante ProcessCameraProvider.getInstance(this).bindToLifecycle(this, cameraSelector, preview, imageAnalysis).",
        "codigo": "MainActivity.kt -> fun startCamera() [Líneas 140-190]"
    },
    {
        "num": 2,
        "modulo": "Módulo 1: Arquitectura Android y Pipeline de Cámara (MainActivity.kt)",
        "pregunta": "¿Por qué el análisis de cada frame no se ejecuta dentro del hilo principal (UI Thread)?",
        "opciones": [
            "A) Porque Android arrojaría NetworkOnMainThreadException.",
            "B) Porque la inferencia de YOLO toma entre 25 ms y 80 ms por frame; ejecutarla en el UI Thread causaría bloqueo de interfaz (lag) y caídas de frames (ANR - Application Not Responding).",
            "C) Porque CameraX prohíbe explícitamente el acceso al hilo de la interfaz.",
            "D) Porque el modelo TFLite solo puede correr en hilos de background por limitaciones de hardware."
        ],
        "correcta": "B",
        "justificacion": "El renderizado a 60 FPS requiere que cada frame de la UI se dibuje en menos de 16 ms. Para no bloquear la interfaz, el análisis de imagen se delega a cameraExecutor = Executors.newSingleThreadExecutor().",
        "codigo": "MainActivity.kt -> imageAnalysis.setAnalyzer(cameraExecutor, Analyzer { ... })"
    },
    {
        "num": 3,
        "modulo": "Módulo 1: Arquitectura Android y Pipeline de Cámara (MainActivity.kt)",
        "pregunta": "¿Qué ocurre en la función rotateBitmapIfNeeded() y por qué es obligatoria?",
        "opciones": [
            "A) Rota la imagen 180° si el usuario tiene el celular boca abajo.",
            "B) Aplica una matriz de rotación Matrix().postRotate(rotationDegrees) al Bitmap obtenido del ImageProxy porque el sensor físico de la cámara está orientado a 90° o 270° respecto a la pantalla.",
            "C) Rota el modelo de IA para que pueda detectar equipos inclinados.",
            "D) Invierte la imagen horizontalmente para crear un efecto espejo en la cámara trasera."
        ],
        "correcta": "B",
        "justificacion": "Los sensores de cámara de Android entregan las imágenes en formato horizontal nativo (landscape). Si no se rotara el Bitmap con imageProxy.imageInfo.rotationDegrees, el modelo YOLO recibiría la imagen girada de costado y no detectaría ningún equipo.",
        "codigo": "MainActivity.kt -> fun rotateBitmapIfNeeded(bitmap: Bitmap, degrees: Int): Bitmap"
    },
    {
        "num": 4,
        "modulo": "Módulo 1: Arquitectura Android y Pipeline de Cámara (MainActivity.kt)",
        "pregunta": "Al terminar de procesar un frame en el analizador de CameraX, ¿qué llamada es estrictamente requerida sobre el objeto ImageProxy?",
        "opciones": [
            "A) imageProxy.recycle()",
            "B) imageProxy.close()",
            "C) imageProxy.flush()",
            "D) imageProxy.destroy()"
        ],
        "correcta": "B",
        "justificacion": "CameraX mantiene un búfer circular limitado de imágenes (por defecto 1 o 2 frames). Si no se invoca imageProxy.close() en un bloque try-finally, el búfer se llena y la cámara se congela permanentemente esperando que se liberen los frames.",
        "codigo": "MainActivity.kt -> fun processImageProxy(imageProxy: ImageProxy) en bloque finally { imageProxy.close() }"
    },
    {
        "num": 5,
        "modulo": "Módulo 1: Arquitectura Android y Pipeline de Cámara (MainActivity.kt)",
        "pregunta": "¿Cómo se envían los resultados del detector calculados en el hilo secundario hacia la vista gráfica OverlayView?",
        "opciones": [
            "A) Usando un Intent con sendBroadcast().",
            "B) Guardando los datos en SharedPreferences y haciendo polling.",
            "C) Mediante runOnUiThread { overlayView.setResults(detections, ...) } o Handler(Looper.getMainLooper()).post { }.",
            "D) Modificando directamente las variables de OverlayView desde el hilo secundario."
        ],
        "correcta": "C",
        "justificacion": "En Android, solo el hilo principal que creó la jerarquía de vistas puede modificar la UI. Intentar llamar a overlayView.setResults() o invalidate() desde el hilo secundario causaría un CalledFromWrongThreadException.",
        "codigo": "MainActivity.kt -> runOnUiThread { overlayView.setResults(results, ...) }"
    },
    {
        "num": 6,
        "modulo": "Módulo 1: Arquitectura Android y Pipeline de Cámara (MainActivity.kt)",
        "pregunta": "¿Qué función cumple el método warmEquipmentInfoCache() llamado en el onCreate() de MainActivity?",
        "opciones": [
            "A) Calienta el procesador del celular para que el modelo corra más rápido.",
            "B) Pre-carga en memoria en un hilo secundario la base de conocimiento JSON y las imágenes para que al tocar un equipo la apertura del BottomSheet sea instantánea y sin tirones.",
            "C) Elimina los archivos temporales de la memoria caché.",
            "D) Descarga actualizaciones de los manuales desde internet."
        ],
        "correcta": "B",
        "justificacion": "Parsea de forma asíncrona la base de datos de manuales en segundo plano al arrancar la app (warm-up), evitando un retraso de 300 ms cuando el usuario interactúa por primera vez con un equipo.",
        "codigo": "MainActivity.kt -> fun warmEquipmentInfoCache() [Líneas 70-95]"
    },

    # -------------------------------------------------------------
    # MÓDULO 2: MOTOR DE VISIÓN ARTIFICIAL YOLO11 / TFLITE
    # -------------------------------------------------------------
    {
        "num": 7,
        "modulo": "Módulo 2: Motor de Visión Artificial YOLO11 / TFLite (YoloDetector.kt)",
        "pregunta": "¿Cómo carga en memoria el archivo del modelo yolo11_bromatologia.tflite la clase YoloDetector?",
        "opciones": [
            "A) Lee el archivo con FileInputStream, abre un FileChannel y obtiene un MappedByteBuffer con map(MapMode.READ_ONLY).",
            "B) Lee todo el archivo en un ByteArrayOutputStream y lo pasa como String Base64.",
            "C) Carga una URL remota desde un servidor en la nube con Retrofit.",
            "D) Usa la librería SQLite para leer los pesos almacenados en una tabla."
        ],
        "correcta": "A",
        "justificacion": "TensorFlow Lite requiere un búfer de memoria directa mapeada en memoria virtual (Memory Mapped File) mediante FileChannel para permitir que la GPU o NNAPI acceda a los pesos sin duplicar memoria RAM.",
        "codigo": "YoloDetector.kt -> fun loadModelFile(modelName: String): MappedByteBuffer"
    },
    {
        "num": 8,
        "modulo": "Módulo 2: Motor de Visión Artificial YOLO11 / TFLite (YoloDetector.kt)",
        "pregunta": "¿Qué dimensiones de entrada exactas y qué formato de datos espera el modelo en inputBuffer?",
        "opciones": [
            "A) 224 x 224 x 1 (escala de grises, enteros de 8 bits).",
            "B) 640 x 640 x 3 canales RGB, con valores flotantes normalizados dividiendo cada componente (0..255) entre 255.0f.",
            "C) 1920 x 1080 x 4 canales RGBA sin normalizar.",
            "D) 416 x 416 x 3 con estandarización restando la media ImageNet."
        ],
        "correcta": "B",
        "justificacion": "YOLOv8 y YOLO11 entrenados en Roboflow usan por defecto resolución 640x640. Los píxeles se empaquetan en un ByteBuffer con orden nativo ByteOrder.nativeOrder(), donde cada byte de color R, G, B se convierte a Float: (val and 0xFF) / 255.0f.",
        "codigo": "YoloDetector.kt -> inputSize = 640, buffer.putFloat(r / 255.0f)"
    },
    {
        "num": 9,
        "modulo": "Módulo 2: Motor de Visión Artificial YOLO11 / TFLite (YoloDetector.kt)",
        "pregunta": "En la salida de YOLO11 [1, 12, 8400], ¿qué representan los índices 0, 1, 2 y 3 del primer eje?",
        "opciones": [
            "A) Las probabilidades de las primeras 4 clases del laboratorio.",
            "B) Las coordenadas del bounding box en formato central y dimensiones relativas: cx (centro X), cy (centro Y), w (ancho) y h (alto).",
            "C) Los valores de color R, G, B y transparencia Alfa del objeto.",
            "D) La matriz de rotación en cuaterniones del objeto en 3D."
        ],
        "correcta": "B",
        "justificacion": "YOLO predice la caja mediante coordenadas normalizadas o absolutas referenciadas al centro del objeto. Para convertir a coordenadas cartesianas se calcula: left = cx - w/2, top = cy - h/2, right = cx + w/2, bottom = cy + h/2.",
        "codigo": "YoloDetector.kt -> fun mapBox(cx, cy, w, h) -> RectF(left, top, right, bottom)"
    },
    {
        "num": 10,
        "modulo": "Módulo 2: Motor de Visión Artificial YOLO11 / TFLite (YoloDetector.kt)",
        "pregunta": "¿Por qué el código de YoloDetector cuenta con dos métodos de parseo: parseTransposed() y parseRows()?",
        "opciones": [
            "A) Uno es para celulares rápidos y el otro para lentos.",
            "B) Para soportar dos formatos posibles de exportación TFLite de Ultralytics: [1, 12, 8400] (transpuesto) o [1, 8400, 12] (por filas), adaptándose dinámicamente según la forma detectada en el intérprete.",
            "C) Uno analiza la imagen verticalmente y el otro horizontalmente.",
            "D) Uno se usa para fotos estáticas y el otro para video continuo."
        ],
        "correcta": "B",
        "justificacion": "Dependiendo de los argumentos usados en ultralytics 'yolo export format=tflite', el tensor de salida puede quedar en formato NCHW [1, 4+C, 8400] o NHWC [1, 8400, 4+C]. YoloDetector verifica la forma en initInterpreter() y activa automáticamente el parser correspondiente.",
        "codigo": "YoloDetector.kt -> fun parseTransposed() vs fun parseRows() [Líneas 145-210]"
    },
    {
        "num": 11,
        "modulo": "Módulo 2: Motor de Visión Artificial YOLO11 / TFLite (YoloDetector.kt)",
        "pregunta": "¿Cuál es la función matemática del método asProb(score: Float)?",
        "opciones": [
            "A) Eleva el score al cuadrado.",
            "B) Si el score del modelo viene en logits crudos (sin función de activación) o en rango no acotado, aplica la función sigmoide: 1.0f / (1.0f + exp(-score)) para acotarlo estrictamente entre 0.0 y 1.0.",
            "C) Calcula la raíz cuadrada de la confianza.",
            "D) Convierte el score a porcentaje multiplicando por 100."
        ],
        "correcta": "B",
        "justificacion": "Algunas exportaciones de TFLite omiten la capa Sigmoid final por optimización matemática. Si el score es mayor a 1.0f o menor a 0.0f, asProb() detecta que son logits y aplica la sigmoide para garantizar probabilidades válidas.",
        "codigo": "YoloDetector.kt -> fun asProb(score: Float): Float = 1.0f / (1.0f + exp(-score))"
    },
    {
        "num": 12,
        "modulo": "Módulo 2: Motor de Visión Artificial YOLO11 / TFLite (YoloDetector.kt)",
        "pregunta": "¿Cómo calcula YoloDetector el IoU (Intersection over Union) entre dos cajas RectF?",
        "opciones": [
            "A) Suma las áreas de ambas cajas y las divide para dos.",
            "B) Calcula el área de la intersección (ancho * alto solapado) y la divide entre el área de la unión: Area(A) + Area(B) - Area(Intersección).",
            "C) Mide la distancia euclidiana entre los centros de las dos cajas.",
            "D) Compara los perímetros de las dos figuras."
        ],
        "correcta": "B",
        "justificacion": "El IoU es la métrica reina en detección de objetos: IoU = Area_Overlap / Area_Union. Si es 0.0, no hay solapamiento; si es 1.0, las cajas son idénticas.",
        "codigo": "YoloDetector.kt -> fun calculateIoU(box1: RectF, box2: RectF): Float"
    },
    {
        "num": 13,
        "modulo": "Módulo 2: Motor de Visión Artificial YOLO11 / TFLite (YoloDetector.kt)",
        "pregunta": "¿Qué hace el algoritmo applyGlobalNms() implementado en YoloDetector?",
        "opciones": [
            "A) Guarda las mejores 10 fotos en la galería de fotos.",
            "B) Ordena todas las detecciones de forma descendente por confianza, toma la de mayor score y descarta todas las demás que tengan un IoU superior al umbral (0.50f), evitando cajas repetidas sobre el mismo equipo.",
            "C) Aplica un filtro de enfoque sobre la imagen antes de pasarla a la red.",
            "D) Cambia el tamaño de las cajas para que todas midan lo mismo."
        ],
        "correcta": "B",
        "justificacion": "Non-Maximum Suppression (NMS) es fundamental porque YOLO predice múltiples cajas candidatas en diferentes anclas para un solo objeto físico. NMS suprime todas las redundantes quedándose con la más confiable.",
        "codigo": "YoloDetector.kt -> fun applyGlobalNms(detections: List<DetectionResult>): List<DetectionResult>"
    },
    {
        "num": 14,
        "modulo": "Módulo 2: Motor de Visión Artificial YOLO11 / TFLite (YoloDetector.kt)",
        "pregunta": "¿Qué condición evalúa el método isDuplicateOrContained(boxA, boxB)?",
        "opciones": [
            "A) Si las cajas tienen el mismo color.",
            "B) Si una caja pequeña está contenida geométricamente dentro de una caja más grande (solapamiento de contención > 75%), eliminando detecciones anidadas falsas de partes del mismo equipo.",
            "C) Si los nombres de los equipos empiezan con la misma letra.",
            "D) Si ambas cajas tocan los bordes de la pantalla."
        ],
        "correcta": "B",
        "justificacion": "En equipos complejos (como el analizador de fibra o el destilador Kjeldahl), el modelo a veces detecta un condensador interno como un equipo separado dentro del equipo grande. isDuplicateOrContained detecta contención espacial y suprime la caja hija espuria.",
        "codigo": "YoloDetector.kt -> fun isDuplicateOrContained(a: RectF, b: RectF): Boolean"
    },
    {
        "num": 15,
        "modulo": "Módulo 2: Motor de Visión Artificial YOLO11 / TFLite (YoloDetector.kt)",
        "pregunta": "¿Qué valor tienen las constantes de filtrado confidenceThreshold y classMargin y por qué son tan estrictas?",
        "opciones": [
            "A) 0.10f y 0.02f para que detecte cualquier objeto que aparezca.",
            "B) confidenceThreshold = 0.65f (65%) y classMargin = 0.18f (18%) para evitar falsos positivos graves en el laboratorio y asegurar que la clase ganadora sea claramente distinguible de las demás.",
            "C) 0.99f y 0.50f para detectar solo en condiciones de laboratorio estériles.",
            "D) Son números aleatorios generados en cada ejecución."
        ],
        "correcta": "B",
        "justificacion": "En aplicaciones de laboratorio con equipos industriales metálicos muy parecidos entre sí, un umbral del 65% y un margen del 18% evitan confusiones críticas entre equipos similares (como la unidad Fisher y el Pro-Nitro Selecta).",
        "codigo": "YoloDetector.kt -> class YoloDetector(confidenceThreshold = 0.65f, classMargin = 0.18f)"
    },

    # -------------------------------------------------------------
    # MÓDULO 3: BASE DE CONOCIMIENTO Y SISTEMA RAG LOCAL
    # -------------------------------------------------------------
    {
        "num": 16,
        "modulo": "Módulo 3: Base de Conocimiento y Sistema RAG Local (KnowledgeBaseRepository.kt)",
        "pregunta": "¿Qué patrón de diseño de software implementa KnowledgeBaseRepository para su instanciación?",
        "opciones": [
            "A) Factory Method",
            "B) Singleton (instancia única thread-safe con synchronized)",
            "C) Builder Pattern",
            "D) Prototype Pattern"
        ],
        "correcta": "B",
        "justificacion": "KnowledgeBaseRepository utiliza un constructor privado y un método getInstance(context) sincronizado con @Volatile para garantizar que la base de datos JSON se cargue una sola vez en toda la vida de la app, ahorrando memoria RAM.",
        "codigo": "KnowledgeBaseRepository.kt -> companion object { fun getInstance(context: Context): KnowledgeBaseRepository }"
    },
    {
        "num": 17,
        "modulo": "Módulo 3: Base de Conocimiento y Sistema RAG Local (KnowledgeBaseRepository.kt)",
        "pregunta": "¿Cómo asocia el repositorio las etiquetas que entrega YOLO con los datos del JSON?",
        "opciones": [
            "A) Mediante el índice numérico de la fila en labels.txt.",
            "B) Mediante un diccionario de normalización roboflowToId que convierte cadenas en minúsculas y sin acentos hacia el identificador único oficial del JSON.",
            "C) Hace una llamada a un endpoint REST para cada consulta.",
            "D) Los nombres del modelo y del JSON son exactamente idénticos carácter por carácter."
        ],
        "correcta": "B",
        "justificacion": "Permite desacoplar el modelo entrenado (cuyas etiquetas pueden tener guiones, faltas tipográficas o espacios) del esquema de datos JSON interno (ej. 'microcospio trinocular' -> 'microscopio_trinocular').",
        "codigo": "KnowledgeBaseRepository.kt -> private val roboflowToId = mapOf(...)"
    },
    {
        "num": 18,
        "modulo": "Módulo 3: Base de Conocimiento y Sistema RAG Local (KnowledgeBaseRepository.kt)",
        "pregunta": "¿Qué hace el método isOffTopicEquipmentQuery(query: String)?",
        "opciones": [
            "A) Verifica si el usuario escribió con faltas de ortografía.",
            "B) Evalúa si la consulta del usuario intenta preguntar sobre equipos que NO pertenecen al Laboratorio de Bromatología (ej. telescopio, autoclave médica, centrifugadora clínica), respondiendo cortésmente que solo atiende equipos de Bromatología UTEQ.",
            "C) Bloquea preguntas que contengan palabras ofensivas.",
            "D) Traduce la pregunta a otro idioma."
        ],
        "correcta": "B",
        "justificacion": "Previene alucinaciones del asistente virtual y delimita el alcance analítico del proyecto al dominio de Bromatología de la UTEQ, cumpliendo con las directrices del docente evaluador.",
        "codigo": "KnowledgeBaseRepository.kt -> fun isOffTopicEquipmentQuery(query: String): Boolean"
    },
    {
        "num": 19,
        "modulo": "Módulo 3: Base de Conocimiento y Sistema RAG Local (KnowledgeBaseRepository.kt)",
        "pregunta": "Cuando se llama a buildDirectEquipmentAnswer(eq: EquipmentData, query: String), ¿cómo decide qué información entregar?",
        "opciones": [
            "A) Siempre devuelve el texto completo de 500 líneas del manual.",
            "B) Analiza palabras clave en la consulta: si detecta 'epp' o 'seguridad' extrae la matriz de protección; si detecta 'como funciona' o 'pasos' extrae el SOP; si detecta 'formula' o 'calculo' extrae el principio químico.",
            "C) Llama a la API de OpenAI para redactar un resumen.",
            "D) Devuelve únicamente la foto del equipo."
        ],
        "correcta": "B",
        "justificacion": "Es la implementación canónica de un sistema RAG determinista y explicable: clasifica la intención del usuario y recupera quirúrgicamente los vectores/campos exactos de la ficha técnica almacenada.",
        "codigo": "KnowledgeBaseRepository.kt -> fun buildDirectEquipmentAnswer(eq: EquipmentData, query: String): String"
    },
    {
        "num": 20,
        "modulo": "Módulo 3: Base de Conocimiento y Sistema RAG Local (KnowledgeBaseRepository.kt)",
        "pregunta": "¿Qué función cumple normalizeText(text: String) en las búsquedas del repositorio?",
        "opciones": [
            "A) Convierte el texto a formato HTML.",
            "B) Pasa el texto a minúsculas, reemplaza vocales con tilde (á->a, é->e), elimina caracteres especiales y signos de puntuación para permitir coincidencias difusas confiables.",
            "C) Encripta el texto usando SHA-256.",
            "D) Cuenta el número de palabras en la oración."
        ],
        "correcta": "B",
        "justificacion": "Garantiza que búsquedas como '¿Cuál es el SOP del viscosímetro?' y 'viscosimetro' coincidan con la misma entidad sin importar mayúsculas o tildes.",
        "codigo": "KnowledgeBaseRepository.kt -> fun normalizeText(text: String): String"
    },
    {
        "num": 21,
        "modulo": "Módulo 3: Base de Conocimiento y Sistema RAG Local (RagApiClient.kt)",
        "pregunta": "¿Cómo maneja RagApiClient la pérdida de conexión a internet o fallos en el servidor remoto?",
        "opciones": [
            "A) Muestra un diálogo de error y cierra la aplicación forzosamente.",
            "B) Implementa un mecanismo de degradación elegante (Graceful Degradation / Offline Fallback): si la petición HTTP falla o excede el timeout, invoca automáticamente a KnowledgeBaseRepository.generateOfflineRagResponse().",
            "C) Intenta reconectarse en un bucle infinito bloqueando la pantalla.",
            "D) Borra la memoria caché de la aplicación."
        ],
        "correcta": "B",
        "justificacion": "Garantiza alta disponibilidad analítica dentro del laboratorio físico de la UTEQ donde la señal Wi-Fi o móvil puede ser inestable o nula.",
        "codigo": "RagApiClient.kt -> catch (e: Exception) { return repository.generateOfflineRagResponse(...) }"
    },
    {
        "num": 22,
        "modulo": "Módulo 3: Base de Conocimiento y Sistema RAG Local (KnowledgeBaseRepository.kt)",
        "pregunta": "¿Cuántos equipos contiene oficialmente la base de conocimiento estructurada para el modelo entrenado?",
        "opciones": [
            "A) 1 solo equipo genérico.",
            "B) 8 equipos oficiales del Laboratorio de Bromatología con compatibilidad para 26 clases de catálogo general.",
            "C) 100 equipos de toda la universidad.",
            "D) Ninguno, se descargan en tiempo real."
        ],
        "correcta": "B",
        "justificacion": "El archivo JSON contiene las fichas completas de los 8 equipos oficiales entrenados en el modelo YOLO actual, además del registro histórico ampliado.",
        "codigo": "labels.txt y manuales_bromatologia_uteq.json"
    },

    # -------------------------------------------------------------
    # MÓDULO 4: RENDERIZADO GRÁFICO, CANVAS Y UI INTERACTIVA
    # -------------------------------------------------------------
    {
        "num": 23,
        "modulo": "Módulo 4: Renderizado Gráfico, Canvas y UI Interactiva (OverlayView.kt)",
        "pregunta": "¿Qué tipo de componente de Android es OverlayView y qué método sobreescribe para dibujar las cajas?",
        "opciones": [
            "A) Es un Fragment y sobreescribe onCreateView().",
            "B) Es una subclase personalizada de android.view.View y sobreescribe fun onDraw(canvas: Canvas).",
            "C) Es un ViewGroup que contiene botones transparentes.",
            "D) Es un SurfaceView que decodifica video OpenGL."
        ],
        "correcta": "B",
        "justificacion": "OverlayView extiende de View y se coloca sobre el PreviewView de la cámara en un FrameLayout. En onDraw(), utiliza objetos Paint para renderizar con aceleración gráfica por hardware.",
        "codigo": "OverlayView.kt -> class OverlayView : View, override fun onDraw(canvas: Canvas)"
    },
    {
        "num": 24,
        "modulo": "Módulo 4: Renderizado Gráfico, Canvas y UI Interactiva (OverlayView.kt)",
        "pregunta": "¿Por qué es necesario calcular scaleFactorX y scaleFactorY en OverlayView?",
        "opciones": [
            "A) Para hacer zoom digital en la cámara.",
            "B) Porque las coordenadas de YOLO vienen relativas al marco analizado (640x640) y deben proyectarse matemáticamente al tamaño real en píxeles de la pantalla del celular (ej. 1080x2400) respetando márgenes y aspecto.",
            "C) Para cambiar el grosor de las líneas según la batería.",
            "D) Para calcular la distancia en metros entre el celular y el equipo."
        ],
        "correcta": "B",
        "justificacion": "Sin esta transformación de coordenadas proyectivas afines, las cajas se dibujarían diminutas en una esquina de 640x640 en pantallas modernas de alta densidad (FHD+).",
        "codigo": "OverlayView.kt -> fun onDraw() -> val scale = max(width / imageWidth, height / imageHeight)"
    },
    {
        "num": 25,
        "modulo": "Módulo 4: Renderizado Gráfico, Canvas y UI Interactiva (OverlayView.kt)",
        "pregunta": "¿Cómo gestiona OverlayView la interacción del usuario al tocar una caja en la pantalla?",
        "opciones": [
            "A) No tiene interacción táctil.",
            "B) Sobreescribe onTouchEvent(event: MotionEvent): cuando detecta ACTION_UP, verifica si las coordenadas (event.x, event.y) caen dentro del RectF de alguna caja mediante box.contains(x, y) y dispara el callback de selección.",
            "C) Usa un sensor de proximidad.",
            "D) Toma una captura de pantalla y busca el color del toque."
        ],
        "correcta": "B",
        "justificacion": "Permite que el estudiante toque directamente el equipo en la pantalla para seleccionarlo y abrir su ficha técnica o iniciar el chat interactivo.",
        "codigo": "OverlayView.kt -> override fun onTouchEvent(event: MotionEvent): Boolean"
    },
    {
        "num": 26,
        "modulo": "Módulo 4: Renderizado Gráfico, Canvas y UI Interactiva (OverlayView.kt)",
        "pregunta": "¿Qué técnica gráfica utiliza drawLabel() para que el texto de la etiqueta nunca se desborde fuera de los bordes de la pantalla?",
        "opciones": [
            "A) Trunca el texto a 3 letras.",
            "B) Mide el ancho del texto con Paint.measureText() y si la posición derecha calculada (x + textWidth) supera el ancho de la pantalla, desplaza el origen hacia la izquierda (clamping).",
            "C) Reduce el tamaño de la fuente a 1 pixel.",
            "D) Oculta la etiqueta si no entra completa."
        ],
        "correcta": "B",
        "justificacion": "Nombres largos como 'Destilador por Arrastre de Vapor tipo Kjeldahl' se saldrían de la pantalla si se dibujan pegados al borde derecho. El clamping asegura legibilidad profesional.",
        "codigo": "OverlayView.kt -> fun drawLabel(canvas: Canvas, text: String, box: RectF)"
    },
    {
        "num": 27,
        "modulo": "Módulo 4: Renderizado Gráfico, Canvas y UI Interactiva (EquipmentBottomSheetDialog.kt)",
        "pregunta": "¿Qué componente de UI se despliega desde la parte inferior cuando el usuario toca el botón de información de un equipo?",
        "opciones": [
            "A) Un Toast simple.",
            "B) Un AlertDialog tradicional centrado.",
            "C) Un BottomSheetDialogFragment (EquipmentBottomSheetDialog) con diseño moderno, pestañas y scroll interactivo.",
            "D) Una nueva Activity completa."
        ],
        "correcta": "C",
        "justificacion": "El BottomSheet permite consultar especificaciones sin perder la vista en vivo de la cámara, facilitando una experiencia de Realidad Aumentada fluida.",
        "codigo": "EquipmentBottomSheetDialog.kt -> class EquipmentBottomSheetDialog : BottomSheetDialogFragment"
    },
    {
        "num": 28,
        "modulo": "Módulo 4: Renderizado Gráfico, Canvas y UI Interactiva (MarkdownText.kt)",
        "pregunta": "¿Qué librería o función permite formatear textos con negritas, listas y cursivas en las respuestas del asistente en ChatActivity?",
        "opciones": [
            "A) Transforma el texto plano a Spanned utilizando expresiones regulares o la librería Markwon/HtmlCompat para soportar formato Markdown.",
            "B) Reemplaza cada palabra manualmente con un ImageView.",
            "C) Convierte el texto a una imagen PNG y la muestra.",
            "D) No soporta formato, solo muestra texto plano."
        ],
        "correcta": "A",
        "justificacion": "Las respuestas del RAG incluyen viñetas, fórmulas y pasos numerados en Markdown que son renderizados dinámicamente como texto enriquecido en los TextView.",
        "codigo": "MarkdownText.kt o ChatAdapter.kt"
    },

    # -------------------------------------------------------------
    # MÓDULO 5: ESTABILIDAD TEMPORAL, CACHÉ Y RENDIMIENTO
    # -------------------------------------------------------------
    {
        "num": 29,
        "modulo": "Módulo 5: Estabilidad Temporal, Caché y Rendimiento (DetectionTracker.kt)",
        "pregunta": "¿Por qué es crucial el uso de DetectionTracker en una aplicación móvil de visión en tiempo real?",
        "opciones": [
            "A) Para enviar estadísticas a Google Analytics.",
            "B) Para eliminar el parpadeo de las cajas (flickering): si la red neuronal no detecta el equipo en 1 o 2 frames por desenfoque o movimiento, el tracker mantiene la caja en pantalla y suaviza su posición mediante interpolación.",
            "C) Para acelerar el reloj de la CPU.",
            "D) Para calcular la velocidad del objeto en km/h."
        ],
        "correcta": "B",
        "justificacion": "Proporciona persistencia temporal (temporal smoothing). Sin el tracker, las cajas predecidas en vivo parpadearían violentamente en pantalla molestando al usuario.",
        "codigo": "DetectionTracker.kt -> fun update(newDetections: List<DetectionResult>): List<DetectionResult>"
    },
    {
        "num": 30,
        "modulo": "Módulo 5: Estabilidad Temporal, Caché y Rendimiento (DetectionTracker.kt)",
        "pregunta": "¿Cómo funciona el algoritmo de suavizado smoothBox(oldBox, newBox) dentro de DetectionTracker?",
        "opciones": [
            "A) Reemplaza inmediatamente la caja vieja con la nueva.",
            "B) Aplica un filtro de media móvil exponencial: smoothed = alpha * new + (1 - alpha) * old, haciendo que los bordes de la caja se muevan suavemente sin saltos bruscos.",
            "C) Hace vibrar el celular cuando la caja se mueve.",
            "D) Redondea las coordenadas al múltiplo de 100 más cercano."
        ],
        "correcta": "B",
        "justificacion": "El filtrado exponencial amortigua las pequeñas fluctuaciones numéricas de píxeles entre fotogramas consecutivos generadas por el ruido de la cámara.",
        "codigo": "DetectionTracker.kt -> fun smoothBox(old: RectF, new: RectF, alpha: Float = 0.7f): RectF"
    },
    {
        "num": 31,
        "modulo": "Módulo 5: Estabilidad Temporal, Caché y Rendimiento (EquipmentDetectionCache.kt)",
        "pregunta": "¿Qué ventaja aporta EquipmentDetectionCache al sistema de detección?",
        "opciones": [
            "A) Guarda fotos en la tarjeta SD para compartirlas en WhatsApp.",
            "B) Registra los equipos que ya han sido confirmados con alta certeza en la sesión; para esos equipos reduce dinámicamente el umbral de confianza en frames subsiguientes (boostIfCached), logrando un seguimiento ultrasensible y continuo.",
            "C) Aumenta el contraste de los colores de la pantalla.",
            "D) Evita que el celular se sobrecaliente apagando el Bluetooth."
        ],
        "correcta": "B",
        "justificacion": "Es una técnica avanzada de visión: una vez que el sistema 'sabe' qué equipo está enfrente con 80% de certeza, puede tolerar que en frames siguientes baje a 50% sin perder la detección ni cortar la experiencia.",
        "codigo": "EquipmentDetectionCache.kt -> fun confidenceThresholdFor(classIndex: Int): Float"
    },
    {
        "num": 32,
        "modulo": "Módulo 5: Estabilidad Temporal, Caché y Rendimiento (EquipmentDetectionCache.kt)",
        "pregunta": "¿Cómo persiste EquipmentDetectionCache los equipos detectados para que no se pierdan al cerrar la app?",
        "opciones": [
            "A) Enviando los datos por correo electrónico al docente.",
            "B) Serializando el historial a un archivo JSON local en el almacenamiento interno privado del dispositivo (context.filesDir) con persistToDisk().",
            "C) Guardándolos en la memoria RAM volátil.",
            "D) Modificando el código fuente de la app."
        ],
        "correcta": "B",
        "justificacion": "Permite recuperar el historial de equipos identificados en la última práctica de laboratorio incluso si la app fue destruida por el sistema operativo para liberar memoria.",
        "codigo": "EquipmentDetectionCache.kt -> fun persistToDisk() y fun loadFromDisk()"
    },

    # -------------------------------------------------------------
    # MÓDULO 6: DOMINIO DEL LABORATORIO DE BROMATOLOGÍA Y QUÍMICA
    # -------------------------------------------------------------
    {
        "num": 33,
        "modulo": "Módulo 6: Dominio del Laboratorio de Bromatología y Fundamento Químico",
        "pregunta": "¿Cuáles son las 4 reacciones químicas secuenciales del Método Kjeldahl implementado en los destiladores del laboratorio?",
        "opciones": [
            "A) Oxidación, reducción, precipitación y secado.",
            "B) 1. Digestión ácida de muestra con H2SO4 conc. -> (NH4)2SO4; 2. Alcalinización con NaOH 40% liberando NH3 gas; 3. Arrastre por vapor y captura en H3BO3 formando borato amónico; 4. Titulación volumétrica con HCl 0.1 N hasta viraje del indicador Tashiro.",
            "C) Fermentación láctica, pasteurización, centrifugación y evaporación.",
            "D) Disolución en agua regia y titulación con permanganato de potasio."
        ],
        "correcta": "B",
        "justificacion": "Es el método analítico oficial por excelencia para determinar Nitrógeno Total y Proteína Cruda (%PB = %N x 6.25) según la norma AOAC 984.13 y NTE INEN 0516.",
        "codigo": "knowledge_base/manuales_bromatologia_uteq.md -> Capítulo Kjeldahl"
    },
    {
        "num": 34,
        "modulo": "Módulo 6: Dominio del Laboratorio de Bromatología y Fundamento Químico",
        "pregunta": "En el Analizador de Fibra DOSI-FIBER, ¿cuál es la diferencia entre el Método Weende y el Método Van Soest?",
        "opciones": [
            "A) Weende mide humedad y Van Soest mide cenizas.",
            "B) Weende utiliza digestión secuencial con ácido diluido (H2SO4 0.255 N) y base (NaOH 0.313 N) para aislar Fibra Cruda total; Van Soest utiliza detergente neutro (FDN) y detergente ácido (FDA) para fraccionar hemicelulosa, celulosa y lignina.",
            "C) Weende es para carnes y Van Soest es para agua potable.",
            "D) Son nombres distintos para exactamente la misma fórmula matemática."
        ],
        "correcta": "B",
        "justificacion": "El fraccionamiento de Van Soest permite a los nutricionistas animales conocer exactamente la fracción digestible de la pared celular vegetal (hemicelulosa = FDN - FDA; celulosa = FDA - LDA).",
        "codigo": "knowledge_base/manuales_bromatologia_uteq.md -> Capítulo DOSI-FIBER"
    },
    {
        "num": 35,
        "modulo": "Módulo 6: Dominio del Laboratorio de Bromatología y Fundamento Químico",
        "pregunta": "¿Cuál es la 'Regla de Oro' metrológica al medir viscosidad con el Viscosímetro Brookfield DV-E?",
        "opciones": [
            "A) El ensayo debe realizarse siempre a 100 RPM sin importar la muestra.",
            "B) El porcentaje de torsión del resorte (% Torque) DEBE ubicarse obligatoriamente entre 10.0% y 100.0% (óptimo entre 40% y 80%); si es menor al 10%, la lectura carece de significancia estadística; si supera el 100%, hay sobrecarga del resorte ('EEEE').",
            "C) El husillo debe tocar el fondo del vaso de vidrio.",
            "D) No es necesario controlar la temperatura del fluido."
        ],
        "correcta": "B",
        "justificacion": "La precisión del viscosímetro rotacional es de +/- 1.0% del fondo de escala (FSR). Por debajo del 10% de torque, el error relativo instrumental se dispara exponencialmente invalidando el ensayo según ASTM D2196 y NTE INEN 1009.",
        "codigo": "knowledge_base/manuales_bromatologia_uteq.md -> Capítulo Viscosímetro Brookfield"
    }
]

def generate_questionnaire_docx():
    print("Creando documento Word del Cuestionario de Examen...")
    doc = docx.Document()
    
    # Márgenes de 1 pulgada
    for sec in doc.sections:
        sec.top_margin = Inches(0.8)
        sec.bottom_margin = Inches(0.8)
        sec.left_margin = Inches(1.0)
        sec.right_margin = Inches(1.0)
        sec.page_width = Inches(8.5)
        sec.page_height = Inches(11.0)
        
    # Encabezado universitario
    p_u = doc.add_paragraph()
    p_u.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_u.paragraph_format.space_before = Pt(4)
    p_u.paragraph_format.space_after = Pt(2)
    r_u = p_u.add_run("UNIVERSIDAD TÉCNICA ESTATAL DE QUEVEDO\n")
    r_u.bold = True
    r_u.font.name = "Calibri"
    r_u.font.size = Pt(14)
    r_u.font.color.rgb = COLOR_BLACK
    
    r_f = p_u.add_run("FACULTAD DE CIENCIAS PECUARIAS Y BIOLÓGICAS — CARRERAS DE ALIMENTOS Y ZOOTECNIA\n")
    r_f.font.name = "Calibri"
    r_f.font.size = Pt(10)
    r_f.font.color.rgb = COLOR_DARK_GRAY
    
    r_lab = p_u.add_run("PROYECTO: ASISTENTE INTELIGENTE CON VISIÓN ARTIFICIAL (YOLO11) Y RAG")
    r_lab.bold = True
    r_lab.font.name = "Calibri"
    r_lab.font.size = Pt(9.5)
    r_lab.font.color.rgb = COLOR_GRAY
    
    # Divisor
    p_div = doc.add_paragraph()
    p_div.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_div.paragraph_format.space_before = Pt(2)
    p_div.paragraph_format.space_after = Pt(10)
    r_div = p_div.add_run("―" * 55)
    r_div.font.color.rgb = COLOR_BLACK
    
    # Título principal
    p_tit = doc.add_paragraph()
    p_tit.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_tit.paragraph_format.space_before = Pt(4)
    p_tit.paragraph_format.space_after = Pt(2)
    r_tit = p_tit.add_run("SIMULADOR DE EXAMEN Y BANCO TÉCNICO DE PREGUNTAS")
    r_tit.bold = True
    r_tit.font.name = "Calibri"
    r_tit.font.size = Pt(16)
    r_tit.font.color.rgb = COLOR_BLACK
    
    p_sub = doc.add_paragraph()
    p_sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_sub.paragraph_format.space_after = Pt(14)
    r_sub = p_sub.add_run("Evaluación Exhaustiva de Código Fuente, Algoritmos de IA, Pipeline CameraX y Arquitectura RAG\n(35 Preguntas Maestras con Justificación y Referencia de Archivos)")
    r_sub.font.name = "Calibri"
    r_sub.font.size = Pt(10.5)
    r_sub.font.color.rgb = COLOR_DARK_GRAY
    
    # Cuadro informativo de estudio
    tbl_info = doc.add_table(rows=1, cols=1)
    tbl_info.alignment = WD_TABLE_ALIGNMENT.CENTER
    tbl_info.autofit = False
    c_info = tbl_info.cell(0, 0)
    c_info.width = Inches(6.5)
    set_cell_background(c_info, "EAEAEA")
    set_cell_margins(c_info, top=100, bottom=100, left=140, right=140)
    p_inf = c_info.paragraphs[0]
    p_inf.paragraph_format.space_before = Pt(2)
    p_inf.paragraph_format.space_after = Pt(2)
    p_inf.paragraph_format.line_spacing = 1.15
    r_itit = p_inf.add_run("📖 GUÍA METODOLÓGICA PARA EL ESTUDIANTE:\n")
    r_itit.bold = True
    r_itit.font.name = "Calibri"
    r_itit.font.size = Pt(10)
    r_itit.font.color.rgb = COLOR_BLACK
    r_ibody = p_inf.add_run(
        "Este cuestionario fue elaborado analizando línea por línea los 8 archivos principales del repositorio. "
        "Cada pregunta evalúa un concepto crítico de ingeniería de software, procesamiento de tensores, visión artificial o "
        "química analítica bromatológica. Para estudiar con la máxima retención, lee la pregunta, intenta responder mentalmente "
        "o en una hoja de papel, y luego verifica tu razonamiento con la caja de respuesta y la ubicación en el código fuente."
    )
    r_ibody.font.name = "Calibri"
    r_ibody.font.size = Pt(9.0)
    r_ibody.font.color.rgb = COLOR_DARK_GRAY
    
    doc.add_paragraph().paragraph_format.space_after = Pt(8)
    
    # Generación de preguntas
    current_modulo = ""
    for q in PREGUNTAS:
        if q['modulo'] != current_modulo:
            current_modulo = q['modulo']
            p_mod = doc.add_paragraph()
            p_mod.paragraph_format.space_before = Pt(14)
            p_mod.paragraph_format.space_after = Pt(6)
            r_mod = p_mod.add_run(f"■ {current_modulo.upper()}")
            r_mod.bold = True
            r_mod.font.name = "Calibri"
            r_mod.font.size = Pt(12)
            r_mod.font.color.rgb = COLOR_BLACK
            
        p_q = doc.add_paragraph()
        p_q.paragraph_format.space_before = Pt(6)
        p_q.paragraph_format.space_after = Pt(3)
        r_num = p_q.add_run(f"Pregunta {q['num']}. ")
        r_num.bold = True
        r_num.font.name = "Calibri"
        r_num.font.size = Pt(10.5)
        r_num.font.color.rgb = COLOR_BLACK
        
        r_enun = p_q.add_run(q['pregunta'])
        r_enun.bold = True
        r_enun.font.name = "Calibri"
        r_enun.font.size = Pt(10.5)
        r_enun.font.color.rgb = COLOR_BLACK
        
        for op in q['opciones']:
            p_op = doc.add_paragraph()
            p_op.paragraph_format.space_before = Pt(1)
            p_op.paragraph_format.space_after = Pt(2)
            p_op.paragraph_format.left_indent = Inches(0.3)
            r_op = p_op.add_run(op)
            r_op.font.name = "Calibri"
            r_op.font.size = Pt(9.5)
            r_op.font.color.rgb = COLOR_DARK_GRAY
            
        p_sp = doc.add_paragraph()
        p_sp.paragraph_format.space_before = Pt(1)
        p_sp.paragraph_format.space_after = Pt(2)
        
        add_answer_box(doc, q['correcta'], q['justificacion'], q['codigo'])
        
    # Pie de página institucional
    p_end = doc.add_paragraph()
    p_end.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_end.paragraph_format.space_before = Pt(20)
    r_end = p_end.add_run("Fin del Banco de Preguntas — Universidad Técnica Estatal de Quevedo (UTEQ 2026)\n¡Mucho éxito en tu evaluación!")
    r_end.font.name = "Calibri"
    r_end.font.size = Pt(9.0)
    r_end.font.italic = True
    r_end.font.color.rgb = COLOR_GRAY
    
    doc.save(OUTPUT_DOCX)
    file_size = os.path.getsize(OUTPUT_DOCX)
    print(f"Cuestionario guardado con éxito en: {OUTPUT_DOCX} ({file_size} bytes)")

if __name__ == "__main__":
    generate_questionnaire_docx()
