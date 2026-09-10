# -*- coding: utf-8 -*-
"""
Script maestro de generación de manuales técnicos y base de conocimiento RAG
Laboratorio de Bromatología - Universidad Técnica Estatal de Quevedo (UTEQ)
Genera:
  1. knowledge_base/manuales_bromatologia_uteq.md
  2. knowledge_base/Manual_Tecnico_Bromatologia_UTEQ_RAG.docx
"""

import os
import sys
import docx
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import OxmlElement, parse_xml
from docx.oxml.ns import nsdecls, qn

WORKSPACE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
KB_DIR = os.path.join(WORKSPACE_DIR, "knowledge_base")
IMAGES_DIR = os.path.join(WORKSPACE_DIR, "images_opt", "equipos_seleccionados")

EQUIPOS = [
    {
        "num": 1,
        "clase_yolo": "Destilador por Arrastre de Vapor tipo Kjeldahl",
        "nombre_oficial": "Unidad Automática de Destilación por Arrastre de Vapor Pro-Nitro",
        "fabricante": "J.P. SELECTA S.A. (España)",
        "modelo": "Pro-Nitro A / Pro-Nitro (Cat. 4002430)",
        "ubicacion": "Mesa analítica de digestión y destilación Kjeldahl",
        "alimentacion": "230 V ~ 50/60 Hz, 1800 W",
        "servicios": "Suministro continuo de agua desionizada para calderín y agua fría de red para refrigerante (1.5 - 2.0 L/min a 15-20 °C)",
        "imagen": "destilacion_arrastre_vapor.jpg",
        "principio": (
            "El principio del método Kjeldahl por arrastre de vapor consiste en la volatilización forzada de amoníaco libre a partir de una muestra orgánica previamente mineralizada y digerida por vía húmeda con ácido sulfúrico concentrado.\n\n"
            "En la muestra digerida, el nitrógeno orgánico fijado se encuentra en forma de sulfato de amonio [(NH4)2SO4]. La unidad Pro-Nitro inyecta vapor de agua sobrecalentado a 100-105 °C generado de forma autónoma en su caldera de acero inoxidable, mientras dosifica automáticamente hidróxido de sodio concentrado (NaOH al 40% p/v) en exceso estequiométrico.\n\n"
            "La brusca alcalinización del medio eleva el pH por encima de 11.5, desplazando el equilibrio iónico hacia la formación de amoníaco libre gaseoso (NH3), el cual es arrastrado por la corriente ascendente de vapor a través de la columna de desvolatilización hacia un condensador refrigerado de alta eficiencia. El condensado es recibido en un matraz Erlenmeyer con solución receptora de ácido bórico (H3BO3) al 4% con indicador mixto Tashiro (rojo de metilo y azul de metileno), formando borato de amonio. La cuantificación final se realiza mediante valoración volumétrica ácido-base con ácido clorhídrico estandarizado (HCl 0.1 N o 0.05 N)."
        ),
        "reacciones": [
            ("1. Mineralización / Digestión ácida previa", "Materia Orgánica (C, H, N, O) + H2SO4 (conc.) + Catalizador (CuSO4/K2SO4) --(380-420°C)--> (NH4)2SO4 + CO2 + SO2 + H2O"),
            ("2. Neutralización y Liberación de Amoníaco", "(NH4)2SO4 + 2 NaOH ----> Na2SO4 + 2 NH3 (gas) ^ + 2 H2O"),
            ("3. Arrastre por Vapor y Captura Receptora", "NH3 + H3BO3 (exceso) ----> NH4+ + H2BO3- (Viraje indicador: Violeta Rojizo -> Verde Esmeralda)"),
            ("4. Valoración Volumétrica con Ácido Fuerte", "NH4+ + H2BO3- + HCl ----> NH4Cl + H3BO3 (Viraje del punto final: Verde -> Violeta persistente)")
        ],
        "calculos": [
            ("Porcentaje de Nitrógeno Total (% N)", "% N = [ (V_muestra - V_blanco) * N_HCl * 1.4007 ] / Peso_muestra (g)"),
            ("Porcentaje de Proteína Cruda (% PB)", "% PB = % N * Factor_Conversión (F)\n  - Piensos, forrajes y carne: F = 6.25\n  - Lácteos y derivados: F = 6.38\n  - Trigo y harinas panificables: F = 5.70\n  - Soya y leguminosas: F = 5.71")
        ],
        "componentes": [
            "Generador de vapor autónomo de acero inoxidable AISI 304 con resistencia calefactora de 1800 W y control automático de nivel por electrodos.",
            "Bomba dosificadora de álcali de membrana químicamente resistente a NaOH 40% con calibración digital de volumen.",
            "Alojamiento universal de fijación rápida para tubos macro-Kjeldahl de 250 mL y 500 mL (diámetro exterior 42 mm).",
            "Refrigerante condensador de serpentín en vidrio borosilicato 3.3 con cánula de inmersión prolongada para matraz receptor.",
            "Pantalla LCD con microprocesador digital para programación de tiempos de adición de reactivo (0-30 s) y tiempo de destilación (1-60 min).",
            "Puerta de protección y seguridad en policarbonato transparente con microinterruptor magnético que inhabilita el generador si se encuentra abierta.",
            "Bandeja de goteo de polipropileno extraíble para contención de derrames accidentales."
        ],
        "sop": [
            ("Fase 1: Preparación e Inspección Previa", "Verificar que el bidón de agua destilada de alimentación del calderín tenga volumen suficiente (> 5 L). Comprobar que el depósito de reactivo NaOH 40% esté abastecido y libre de precipitados o cristalizaciones. Abrir la llave de paso del agua de refrigeración ajustando un flujo continuo de 1.5 a 2.0 L/min hacia el refrigerante."),
            ("Fase 2: Encendido y Calentamiento", "Accionar el interruptor general luminoso frontal (I/O). El equipo ejecuta un autodiagnóstico de electrodos. Esperar aproximadamente 5 a 8 minutos hasta que la resistencia interna genere vapor continuo y el indicador READY permanezca iluminado. Ejecutar un ciclo previo en blanco con un tubo con 50 mL de agua para purgar condensados residuales del sistema."),
            ("Fase 3: Ejecución de la Destilación", "Preparar el matraz receptor (Erlenmeyer de 250 mL) añadiendo 25 mL de solución de Ácido Bórico al 4% y 3 gotas de indicador mixto Tashiro (color violeta rojizo). Colocar el matraz en la plataforma inferior de salida del refrigerante, asegurando que la punta del tubo quede sumergida bajo el líquido receptor. Tomar el tubo macro-Kjeldahl con la muestra digerida (fría), encajarlo firmemente en el cono de neopreno/teflón y bajar la palanca de ajuste hermético. Cerrar la puerta de seguridad. Programar el volumen de NaOH (típicamente 50 a 60 mL para neutralizar 15-20 mL de H2SO4 de digestión) y el tiempo de destilación (4 minutos). Pulsar START. Observar el cambio de coloración inmediata del matraz de violeta a verde esmeralda en el primer minuto, indicando la captura de NH3."),
            ("Fase 4: Finalización y Valoración", "Al finalizar el temporizador (señal acústica), descender la plataforma del matraz receptor y permitir que el vapor gotee durante 30 segundos sobre la superficie, lavando la punta exterior con un chorro de piseta con agua destilada. Retirar el matraz y proceder de inmediato a la valoración volumétrica en bureta graduada digital o manual de 50 mL con solución de HCl 0.1000 N hasta observar el viraje exacto al violeta rojizo original. Registrar el volumen gastado."),
            ("Fase 5: Apagado y Limpieza", "Abrir la puerta de seguridad, desenganchar con pinzas de protección térmica el tubo de muestra caliente (¡Precaución! Temperatura > 90 °C) y descartar el efluente neutralizado según la normativa de residuos. Montar un tubo limpio con 100 mL de agua destilada y activar un ciclo de vapor de 2 minutos para deslavar el cabezal de arrastre. Desconectar el interruptor principal y cerrar el grifo de agua de refrigeración.")
        ],
        "epp": [
            ("Mandil de laboratorio antifluidos", "Obligatorio de manga larga con puño ajustado, 100% resistente a salpicaduras ácidas y alcalinas."),
            ("Gafas de seguridad química herméticas", "Con ventilación indirecta y protección lateral completa según norma ANSI Z87.1 contra proyecciones de hidróxido de sodio hirviendo."),
            ("Guantes de nitrilo / neopreno de puño largo", "Espesor mínimo 0.38 mm, resistentes a álcalis concentrados (NaOH 40%) y ácidos minerales."),
            ("Guantes de aislamiento térmico", "Para manipulación de tubos de digestión calientes al finalizar el ciclo de destilación."),
            ("Calzado cerrado de seguridad", "Suela antideslizante impermeable, sin partes textiles descubiertas.")
        ],
        "riesgos": [
            "Quemaduras químicas graves e irreversibles por contacto ocular o cutáneo con álcali cáustico (NaOH al 40% a temperatura > 80 °C).",
            "Quemaduras térmicas por contacto accidental con el cuerpo del generador de vapor, tuberías de ebullición o tubos de borosilicato calientes.",
            "Inhalación de vapores tóxicos de amoníaco (NH3) en caso de fallos de estanqueidad o falta de fluido refrigerante.",
            "Riesgo de sobrepresión en la caldera si las electroválvulas o el sensor de nivel sufren incrustaciones salinas."
        ],
        "guias_uteq": [
            "Práctica Bromatología N° 03: Determinación de Proteína Cruda en Harinas y Materias Primas para Nutrición Animal (Método Kjeldahl según AOAC 984.13).",
            "Práctica Bromatología N° 06: Control de Calidad de Harina de Pescado y Concentrados Proteicos según Norma Ecuatoriana NTE INEN 0516.",
            "Práctica Bromatología N° 09: Cuantificación de Proteína Verdadera y Nitrógeno No Proteico (NNP) en Forrajes Tropicales de la Cuenca del Río Quevedo."
        ],
        "normas": "AOAC Official Method 984.13, AOAC 2001.11, ISO 5983-2, NTE INEN 0516, NTE INEN 465.",
        "mantenimiento": [
            ("Diario / Post-uso", "Lavado del circuito de destilación mediante ciclo de vapor con agua destilada; limpieza de bandeja de derrames."),
            ("Semanal", "Inspección visual de mangueras de silicona y vitón en busca de agrietamientos; limpieza del cono adaptador de tubos con agua tibia."),
            ("Mensual / Descalcificación", "Descalcificación de la caldera de vapor mediante adición de 200 mL de solución de ácido acético al 10% o ácido cítrico al 5%, dejando actuar 30 minutos a 60 °C y enjuagando con 3 ciclos sucesivos de agua destilada."),
            ("Calibración Metrológica", "Verificación del porcentaje de recuperación de nitrógeno cada 15 días analizando una solución estándar de Sulfato de Amonio [(NH4)2SO4] analítico p.a. (pureza 99.9%). El porcentaje de recuperación debe ser obligatoriamente >= 99.5%.")
        ],
        "troubleshooting": [
            ("El destilado no cambia de color al verde esmeralda al destilar", "Volumen insuficiente de NaOH 40%; el pH del tubo no superó 11.0. Aumentar dosis de NaOH en 15-20 mL hasta que el líquido en el tubo tome coloración marrón-negra intensa."),
            ("El líquido receptor retorna por succión hacia el tubo de destilación (retrosucción)", "Fallo o apagado brusco del generador de vapor, generando vacío por contracción térmica. Bajar inmediatamente el matraz receptor para desacoplar la cánula del líquido."),
            ("Recuperación de nitrógeno < 98% en el estándar", "Fuga en la junta tórica del cabezal de borosilicato, temperatura del condensador excesiva (> 28 °C en destilado), o caudal de refrigeración insuficiente."),
            ("Alarma acústica continua de 'Falta de Agua'", "Depósito de alimentación de agua vacío, electrodos de nivel con acumulación de sarro, o presión de entrada insuficiente.")
        ]
    },
    {
        "num": 2,
        "clase_yolo": "Analizador de Fibra Cruda y Fracciones",
        "nombre_oficial": "Extractor y Analizador de Fibra DOSI-FIBER",
        "fabricante": "J.P. SELECTA S.A. (España)",
        "modelo": "DOSI-FIBER (Referencia Cat. 4000623)",
        "ubicacion": "Mesa analítica de análisis proximal de alimentos",
        "alimentacion": "230 V ~ 50/60 Hz, 1500 W",
        "servicios": "Conexión a bomba de vacío recirculante y circuito de refrigeración de agua (2 L/min)",
        "imagen": "analizador_fibra_cruda_fracciones.jpg",
        "principio": (
            "El analizador de fibra DOSI-FIBER permite la determinación cuantitativa gravimétrica de la fracción no digestible de los alimentos de origen vegetal mediante digestión ácida y alcalina sucesiva (Método oficial Weende) y el fraccionamiento de la pared celular vegetal por solubilidad diferencial en detergentes (Método de Van Soest).\n\n"
            "El principio se fundamenta en disolver los componentes intracelulares solubles (azúcares, almidones, pectinas, proteínas solubles y lípidos) mediante ebullición a reflujo con soluciones químicas estandarizadas, manteniendo intacta la pared celular dentro de crisoles filtrantes de cuarzo/vidrio sinterizado de porosidad controlada P2 (40 a 100 micrómetros).\n\n"
            "El equipo calienta simultáneamente hasta 6 posiciones independientes. La filtración de los reactivos calientes y los lavados sucesivos con agua destilada hirviendo y solventes orgánicos (acetona) se realizan directamente a través de la base porosa del crisol mediante vacío regulado, eliminando la necesidad de trasvases intermedios y previniendo pérdidas mecánicas de muestra."
        ),
        "reacciones": [
            ("1. Digestión Ácida (Método Weende)", "Muestra seca + H2SO4 (0.255 N / 1.25% p/v) --(100°C, 30 min)--> Hidrólisis de carbohidratos de reserva (almidón, azúcares) y proteínas solubles."),
            ("2. Digestión Alcalina (Método Weende)", "Residuo insoluble + NaOH (0.313 N / 1.25% p/v) --(100°C, 30 min)--> Saponificación de lípidos residuales y solubilización de proteínas insolubles y hemicelulosas lábiles."),
            ("3. Lavado con Solvente Orgánico", "Residuo celulósico + Acetona pura (CH3COCH3) ----> Extracción de pigmentos, ceras y restos lipídicos; deshidratación de la fibra."),
            ("4. Fraccionamiento Van Soest FDN", "Muestra + Detergente Neutro (Lauril Sulfato Sódico + EDTA + Borato) --(100°C, 60 min)--> Residuo FDN = Hemicelulosa + Celulosa + Lignina."),
            ("5. Fraccionamiento Van Soest FDA", "Muestra + Detergente Ácido (Bromuro de Cetiltrimetilamonio en H2SO4 1 N) --(100°C, 60 min)--> Residuo FDA = Celulosa + Lignina.")
        ],
        "calculos": [
            ("Porcentaje de Fibra Cruda (% FC - Weende)", "% FC = [ (P2 - P3) / P1 ] * 100\n  - P1 = Peso exacto de la muestra seca y desengrasada inicial (g)\n  - P2 = Peso del crisol con residuo fibroso seco a 105 °C (g)\n  - P3 = Peso del crisol con cenizas tras calcinación a 550 °C en mufla (g)"),
            ("Ecuaciones de Fraccionamiento de Van Soest", "Fibra Detergente Neutro (% FDN) = [ (P_FDN - P_cenizas_FDN) / P_muestra ] * 100\nFibra Detergente Ácido (% FDA) = [ (P_FDA - P_cenizas_FDA) / P_muestra ] * 100\nLignina Detergente Ácido (% LDA) = [ (P_LDA - P_cenizas_LDA) / P_muestra ] * 100\nContenido de Hemicelulosa (% Hemi) = % FDN - % FDA\nContenido de Celulosa (% Cel) = % FDA - % LDA")
        ],
        "componentes": [
            "Bloque calefactor de 6 puestos lineales con resistencias infrarrojas individuales de 250 W y reguladores de energía electrónicos independientes.",
            "6 columnas de digestión cilíndricas en vidrio de borosilicato 3.3 de alta resistencia al choque térmico y ataque químico.",
            "6 condensadores de reflujo tipo dedo frío metálicos/vidrio refrigerados por circuito cerrado de agua para evitar evaporación de reactivos.",
            "Juego de crisoles filtrantes de borosilicato con placa de cuarzo sinterizado porosidad P2 (40-100 µm) tarados y rotulados.",
            "Múltiple de vacío inferior de acero inoxidable AISI 316 con grifos de 3 posiciones para aspiración, descarga libre a colector o purga.",
            "Válvulas de control y manómetro de vacío analógico de glicerina integrado."
        ],
        "sop": [
            ("Fase 1: Preparación de Crisoles y Muestra", "Lavar los crisoles filtrantes P2 con solución de ácido sulfúrico diluido y acetona. Secar en estufa a 105 °C por 2 horas, atemperar en desecador de sílice por 45 minutos y registrar el peso tara exacto (P0) en balanza analítica (± 0.0001 g). Pesar 1.0000 g de muestra molida (tamaño de partícula <= 1 mm con molino ciclónico) desengrasada previamente si el contenido de extracto etéreo supera el 5%."),
            ("Fase 2: Montaje y Carga de Reactivo Ácido", "Insertar los crisoles con muestra en los alojamientos del DOSI-FIBER asegurando que las juntas cónicas de silicona sellen herméticamente. Conectar el flujo de agua de los refrigerantes superiores (2 L/min). Adicionar a cada posición 150 mL de solución precalentada de H2SO4 0.255 N y 3 a 5 gotas de agente antiespumante (1-octanol). Girar la perilla de control a potencia máxima hasta alcanzar ebullición franca y regular para mantener ebullición suave y constante durante exactamente 30 minutos."),
            ("Fase 3: Filtración y Lavado Ácido", "Conectar la bomba de recirculación de vacío al colector inferior. Abrir gradualmente las válvulas de vacío para drenar la solución ácida hacia el depósito de desecho. Lavar el residuo dentro de los crisoles 3 veces consecutivas con 30 mL de agua destilada hirviendo (cerca de 100 °C), aplicando succión entre cada lavado hasta eliminar cualquier traza de acidez."),
            ("Fase 4: Digestión Alcalina", "Cerrar las válvulas de drenaje. Adicionar 150 mL de solución precalentada de NaOH 0.313 N (o KOH) con 3 gotas de octanol. Mantener ebullición bajo reflujo durante 30 minutos exactos. Drenar la solución alcalina al vacío y lavar 3 veces con agua destilada hirviendo."),
            ("Fase 5: Deshidratación, Secado y Calcinación", "Lavar el residuo del crisol 3 veces con porciones de 25 mL de acetona pura al vacío para eliminar pigmentos y arrastrar el agua residual. Retirar los crisoles con pinzas metálicas y colocarlos en la estufa de secado a 105 °C ± 2 °C durante mínimo 4 horas. Transferir al desecador, enfriar a temperatura ambiente y registrar el peso P2. Colocar los crisoles en la mufla eléctrica e incinerar a 550 °C durante 3 horas hasta cenizas blancas o grisáceas libres de carbón. Enfriar a 200 °C dentro de la mufla, transferir al desecador hasta temperatura ambiente y registrar el peso final P3.")
        ],
        "epp": [
            ("Mandil de laboratorio antifluidos y delantal de hule/neopreno", "Para protección troncal completa contra ebullición ácida y alcalina."),
            ("Pantalla facial de policarbonato integral", "Obligatoria frente a los condensadores de reflujo en caso de sobrepresión o proyección de líquidos a ebullición."),
            ("Guantes térmicos de silicona o Kevlar", "Para manipulación y traslado de crisoles calientes desde la estufa y mufla."),
            ("Guantes de nitrilo puño largo para químicos", "Resistentes al manejo de H2SO4 0.255 N, NaOH 0.313 N y acetona."),
            ("Pinzas metálicas largas para crisoles", "En acero inoxidable con extremos protegidos para sujeción segura de crisoles de vidrio.")
        ],
        "riesgos": [
            "Quemaduras térmicas y químicas severas por proyección de soluciones corrosivas en ebullición a 100 °C.",
            "Ruptura de crisoles de borosilicato por choque térmico violento si se añade agua fría sobre crisoles sobrecalentados.",
            "Inhalación de vapores inflamables y tóxicos de acetona durante la etapa de lavado de deshidratación; realizar siempre bajo campana extractora o con ventilación adecuada.",
            "Atascamiento de la placa porosa sinterizada por muestras con alto contenido de mucílagos o pectinas solubles no desengrasadas."
        ],
        "guias_uteq": [
            "Práctica Bromatología N° 04: Determinación de Fibra Cruda en Forrajes, Pastos Tropicales y Subproductos Agroindustriales según Método Weende (AOAC 962.09).",
            "Práctica Bromatología N° 05: Fraccionamiento de la Fibra de la Pared Celular (FDN, FDA y Lignina) en Piensos para Rumiantes según Método Van Soest (ISO 6865 / ISO 13906).",
            "Práctica Bromatología N° 08: Evaluación del Contenido de Fibra Dietética Total e Insoluble en Harinas de Plátano y Yuca según NTE INEN 539."
        ],
        "normas": "AOAC Official Method 962.09, ISO 6865, ISO 13906, NTE INEN 539, AOCS Ba 6-84.",
        "mantenimiento": [
            ("Post-ensayo", "Limpieza de las columnas de vidrio con agua caliente y detergente neutro no abrasivo; enjuagar con agua desionizada."),
            ("Regeneración de Crisoles P2", "Cuando la placa porosa pierda velocidad de filtración por acumulación de sílice o lignina recalcitrante, calcinar a 500 °C en mufla y luego sumergir durante 2 horas en solución limpiadora sulfocrómica o mezcla de H2SO4 al 20% con peróxido de hidrógeno al 30%. Lavar abundantemente con agua destilada al vacío."),
            ("Juntas y Sellos", "Revisión mensual de las juntas tóricas de silicona de las columnas; lubricar ligeramente con grasa de silicona de alto vacío si presentan rigidez.")
        ],
        "troubleshooting": [
            ("Filtración sumamente lenta o crisol atascado", "La muestra contiene exceso de grasas (>5%) o almidón no gelatinizado. Desengrasar previamente la muestra en extractor Soxhlet con éter de petróleo; o aplicar pulsos breves de presión inversa positiva con jeringa de aire en el grifo inferior."),
            ("Formación excesiva de espuma que sube por la columna", "Ebullición violenta o presencia de saponinas. Reducir la potencia del regulador calefactor y añadir inmediatamente 3 gotas de 1-octanol o antiespumante de silicona."),
            ("Ruptura o fisura de la placa de vidrio poroso", "Choque térmico por lavar crisoles calientes con reactivo a temperatura ambiente. Utilizar siempre líquidos precalentados a > 80 °C."),
            ("Pérdida de masa en el blanco de crisol", "Ataque del álcali al cuarzo sinterizado. Verificar la normalidad exacta del NaOH (no debe superar 0.313 N) y limitar el tiempo de ebullición estrictamente a 30 minutos.")
        ]
    },
    {
        "num": 3,
        "clase_yolo": "Viscosimetro Brookfield Modelo DV-E",
        "nombre_oficial": "Viscosímetro Rotacional Digital Brookfield DV-E",
        "fabricante": "AMETEK Brookfield (Middleboro, MA, EE.UU.)",
        "modelo": "DV-E Viscometer (DVE-LV / DVE-RV)",
        "ubicacion": "Mesa de reología y análisis físico de alimentos",
        "alimentacion": "115 / 230 V ~ 50/60 Hz, 30 W",
        "servicios": "Mesa antivibratoria, baño termostático de circulación externa (precisión ± 0.1 °C)",
        "imagen": "viscosimetro_brookfield_dve.jpg",
        "principio": (
            "El viscosímetro rotacional digital Brookfield Modelo DV-E mide la resistencia al flujo y cizallamiento interno de fluidos newtonianos y no newtonianos mediante el método de inmersión rotacional.\n\n"
            "El principio físico opera haciendo girar un elemento geométrico sensor calibrado (husillo o spindle cilíndrico o discoidal) inmerso en el seno del fluido a una velocidad angular constante controlada (omega). El fluido ejerce un par o torque retardador por resistencia viscosa contra las caras del husillo.\n\n"
            "Este torque es transmitido y medido de forma ultraprecisa mediante la deflexión angular de un resorte de torsión de precisión fabricado en aleación bimetálica de berilio-cobre (Be-Cu). Un transductor rotativo diferencial convierte la deflexión angular mecánica en una señal eléctrica digital procesada por el microcontrolador del DV-E, expresándola directamente en unidades de viscosidad dinámica: centipoise (cP) o miliPascal-segundo (mPa·s), junto con el porcentaje de torque (% Torque) ejercido sobre el resorte.\n\n"
            "Para fluidos no newtonianos comunes en alimentos (pseudoplásticos, tixotrópicos, dilatantes), el instrumento permite determinar la viscosidad aparente dependiente de la tasa de cizalla modificando las RPM o el husillo seleccionado."
        ),
        "reacciones": [
            ("1. Ley de Viscosidad de Newton", "tau = eta * gamma_dot\n  - tau = Esfuerzo de corte o cizalladura (Pa o N/m^2)\n  - eta = Viscosidad dinámica o coeficiente de fricción interna (Pa*s)\n  - gamma_dot = Gradiente de velocidad o velocidad de cizalla (s^-1)"),
            ("2. Cálculo de Viscosidad Dinámica en el Instrumento", "eta (cP) = [ K * % Torque ] / RPM\n  - K = Constante geométrica de calibración del husillo en uso (Spindle Constant)\n  - % Torque = Porcentaje de deflexión del resorte (Rango válido estricto: 10.0% a 100.0%)\n  - RPM = Velocidad de rotación programada (0.3 a 100 RPM)\n  - Equivalencia estándar: 1 cP = 1 mPa*s = 10^-3 Pa*s")
        ],
        "calculos": [
            ("Rango de Fondo de Escala (Full Scale Range - FSR)", "FSR (cP) = TK * SMC * (10,000 / RPM)\n  - TK = Constante del resorte de torsión del modelo (LV = 0.09373; RV = 1.0)\n  - SMC = Constante multiplicadora del husillo (Spindle Multiplier Constant)"),
            ("Criterio Metrológico de Aceptación", "Precisión de medida: +/- 1.0% del rango de escala completa (FSR).\nRepetibilidad del ensayo: +/- 0.2% del fondo de escala.\nRegla de oro de validez: Si % Torque < 10.0%, la medida NO tiene validez estadística; se debe aumentar las RPM o seleccionar un husillo de mayor diámetro. Si % Torque > 100.0%, el resorte entra en sobrecarga ('EEEE'); se debe reducir la velocidad o usar un husillo más pequeño.")
        ],
        "componentes": [
            "Cabezal de medida rotacional con resorte espiral de torsión calibrado de berilio-cobre (Serie LV para bajas viscosidades, RV para medias/altas viscosidades).",
            "Pantalla digital LCD de alto contraste con lectura simultánea de Viscosidad (cP/mPa·s), % Torque (0.0 - 100.0%), Velocidad (RPM) y Código de Husillo (Spindle Code).",
            "Teclado frontal de membrana sellada con botones: SELECT DISPLAY, MOTOR ON/OFF, SPEED / SPINDLE up/down.",
            "Juego de husillos estándar en acero inoxidable electro-pulido AISI 316 (Spindles LV1 a LV4 para LV; RV1 a RV7 para RV).",
            "Pierna o guarda-husillo de protección hidrodinámica (Guardleg) para delimitación del campo de cizalla y protección contra choques con las paredes del recipiente.",
            "Estativo con trípode de hierro fundido, columna cromada, cremallera micrométrica de ascenso/descenso y nivel de burbuja de precisión.",
            "Varilla con sensor de temperatura RTD Pt100 (opcional en modelos avanzados)."
        ],
        "sop": [
            ("Fase 1: Nivelación y Preparación del Instrumento", "Comprobar que el viscosímetro esté firmemente anclado en su estativo. Ajustar los tornillos niveladores de la base del trípode hasta que la burbuja de aire esté perfectamente centrada en el círculo negro del nivel frontal. Encender el instrumento con el interruptor trasero; realizar el auto-cero automático sin husillo colocado si el menú lo solicita."),
            ("Fase 2: Acondicionamiento Térmico de la Muestra", "La viscosidad es extremadamente dependiente de la temperatura (variaciones de 1 °C pueden generar desviaciones de hasta 5-10% en viscosidad). Colocar la muestra del alimento (miel, pulpa, mayonesa, salsa) en un vaso de precipitados tipo Griffin de 600 mL sin pico (diámetro interior aproximado 82 mm). Introducir el vaso en un baño de agua termorregulado hasta alcanzar exactamente la temperatura normalizada (típicamente 20.0 °C o 25.0 °C +/- 0.1 °C). Homogeneizar suavemente sin inducir burbujas de aire."),
            ("Fase 3: Selección e Instalación del Husillo", "Seleccionar el husillo adecuado según la viscosidad esperada (ej. LV-1 / RV-2 para fluidos poco viscosos, LV-4 / RV-7 para fluidos pastosos). Enroscar la pierna de resguardo (guardleg) en la base roscada del cabezal. Sujetar firmemente el eje superior del viscosímetro levantándolo con una mano para descargar el peso y pivote del resorte, y enroscar con la otra mano el husillo hacia la IZQUIERDA (rosca izquierda de seguridad anti-desenrosque). ¡Nunca aplicar fuerza lateral ni forzar el eje sin sostenerlo!"),
            ("Fase 4: Inmersión y Medición Reológica", "Introducir el husillo en la muestra inclinando ligeramente el vaso para que el fluido moje la cara inferior del disco sin atrapar burbujas de aire bajo el plato. Bajar el cabezal mediante la perilla micrométrica hasta que el menisco del fluido coincida exactamente con la ranura de inmersión grabada en el vástago del husillo. Programar en pantalla el código del husillo instalado (código de 2 dígitos, ej. '01', '02', '62') y las RPM seleccionadas. Presionar MOTOR ON. Dejar girar durante 60 segundos continuos para permitir la estabilización hidrodinámica y reológica del fluido. Verificar que el valor de % Torque esté dentro del rango óptimo (40% a 80%, mínimo aceptable 10.0%). Registrar el valor de viscosidad en cP, el % Torque y las RPM."),
            ("Fase 5: Retiro, Apagado y Limpieza", "Detener el motor con MOTOR OFF. Elevar el cabezal fuera del vaso de muestra. Sostener el eje superior y desenroscar el husillo hacia la derecha con extremo cuidado. Lavar de inmediato el husillo y la pierna de resguardo con agua caliente y solvente apropiado según el alimento analizado; secar con paño suave sin rayar la superficie de acero pulido. Desconectar el instrumento y colocar la funda protectora de vinilo.")
        ],
        "epp": [
            ("Mandil blanco de laboratorio", "De manga larga, limpio para ensayos físicos y de reología."),
            ("Guantes desechables de nitrilo", "Para evitar la transferencia de calor corporal y grasa de los dedos hacia los husillos calibrados o las muestras alimenticias."),
            ("Gafas de seguridad de laboratorio", "Para protección ante eventuales salpicaduras en fluidos en movimiento.")
        ],
        "riesgos": [
            "Daño mecánico irreversible al resorte bimetálico de torsión o al zafiro del pivote si se ejerce fuerza axial o lateral sobre el eje sin sujetarlo durante el cambio de husillos.",
            "Lecturas analíticas completamente inválidas por presencia de burbujas de aire atrapadas bajo el disco del husillo o falta de nivelación en el trípode.",
            "Desviaciones severas de medida por fluctuaciones de temperatura durante el ensayo; todo ensayo reológico debe reportar obligatoriamente la temperatura exacta."
        ],
        "guias_uteq": [
            "Práctica Bromatología N° 08: Caracterización Reológica y Determinación de Viscosidad Aparente en Mieles de Abeja de la Provincia de Los Ríos según NTE INEN 1009.",
            "Práctica Bromatología N° 12: Evaluación del Comportamiento Reológico y Grado de Tixotropía en Salsas y Emulsiones Alimentarias según ASTM D2196.",
            "Práctica Bromatología N° 14: Control de Calidad de la Consistencia y Espesamiento por Pectinas en Pulpas de Frutas Tropicales (Mango, Maracuyá y Guayaba)."
        ],
        "normas": "NTE INEN 1009, ASTM D2196, ISO 2555, ISO 3219, AOAC 969.38.",
        "mantenimiento": [
            ("Inspección de Husillos", "Inspeccionar periódicamente la rectitud del vástago de los husillos haciéndolos rodar sobre una superficie de vidrio plano. Husillos deformados o golpeados introducen excentricidad y deben descartarse."),
            ("Calibración y Verificación con Fluidos Patrón", "Verificar la exactitud del instrumento cada 6 meses utilizando Aceites Patrón de Viscosidad de Silicona trazables al NIST (ej. Patrón Brookfield de 500 cP, 5000 cP o 12,500 cP a 25.0 °C). Si el error relativo supera el 1.0% del fondo de escala, enviar a servicio técnico autorizado para ajuste de resorte."),
            ("Limpieza del Eje", "Nunca permitir que se sequen restos de muestra sobre la rosca del eje o sobre el acople del husillo. Limpiar con etanol al 70%.")
        ],
        "troubleshooting": [
            ("La pantalla parpadea y muestra 'EEEE'", "Sobre-rango de torque (> 100%). El resorte está forzado. Detener el motor inmediatamente y reducir la velocidad (RPM) o cambiar a un husillo de menor tamaño (número más alto)."),
            ("El porcentaje de torque es inferior al 10.0%", "Medida no válida por sub-rango. La señal del resorte es demasiado débil para el ruido de fondo. Aumentar las RPM o cambiar a un husillo de mayor diámetro (LV-1 / RV-1)."),
            ("Lecturas oscilantes o inestables", "Burbujas de aire en el fluido, husillo rozando la pared del vaso Griffin, o vaso no concéntrico con el husillo. Verificar la distancia mínima de 1 pulgada (2.5 cm) entre el husillo y las paredes del vaso."),
            ("El instrumento no enciende o la pantalla no responde", "Verificar la conexión del cable de poder de 115/230V y revisar el fusible de protección localizado en el zócalo de entrada trasera.")
        ]
    },
    {
        "num": 4,
        "clase_yolo": "Microcospio Trinocular",
        "nombre_oficial": "Microscopio Óptico Trinocular de Laboratorio Primo Star",
        "fabricante": "Carl Zeiss Microscopy GmbH (Jena, Alemania)",
        "modelo": "Primo Star Trinocular (ICS Optics)",
        "ubicacion": "Mesa de microbiología y microscopía analítica bromatológica",
        "alimentacion": "100-240 V ~ 50/60 Hz, 30 W con fuente conmutada de bajo consumo",
        "servicios": "Mesa rígida antivibratoria protegida de radiación solar directa y humedad excesiva (< 70% HR)",
        "imagen": "microscopio_trinocular.jpg",
        "principio": (
            "El microscopio óptico trinocular Primo Star de Carl Zeiss utiliza el sistema óptico con corrección a infinito (ICS - Infinity Color-corrected System) para la observación microscópica de muestras biológicas y alimentarias en luz transmitida por campo claro.\n\n"
            "La luz blanca generada por un diodo emisor de luz (LED) de alto rendimiento (temperatura de color 3200 K) es condensada y colimada a través del sistema de iluminación Köhler, pasando por el diafragma de campo y el condensador de Abbe hacia el plano del portaobjetos. La muestra modula el haz luminoso por absorción, difracción y refracción diferencial.\n\n"
            "El objetivo plan-acromático correspondiente proyecta los rayos paralelamente hacia el infinito. Una lente de tubo secundaria enfoca la imagen intermedia en el plano focal de los oculares gran campo de 10x, corrigiendo aberraciones cromáticas y de esfericidad en todo el campo visual.\n\n"
            "El cabezal trinocular divide el haz óptico (50% visual / 50% fototubo o 100% fototubo) permitiendo la conexión simultánea de una cámara digital HD / microscópica mediante montura C-Mount para fotomicrografía, morfometría automatizada y registro digital en el RAG."
        ),
        "reacciones": [
            ("1. Poder de Resolución (Límite de Abbe)", "d = lambda / [ 2 * NA ]\n  - d = Distancia mínima resoluble entre dos puntos adyacentes (µm)\n  - lambda = Longitud de onda de la luz utilizada (aprox. 550 nm para luz blanca)\n  - NA = Apertura Numérica del objetivo (NA = n * sin(alfa))\n  - Resolución máxima teórica con objetivo 100x Oil (NA 1.25): d = 0.22 µm"),
            ("2. Aumento Óptico Total del Sistema", "Aumento Total = Aumento_Ocular * Aumento_Objetivo\n  - Objetivo 4x: Aumento total = 10 * 4 = 40x (Campo panorámico)\n  - Objetivo 10x: Aumento total = 10 * 10 = 100x (Estructura tisular y almidones)\n  - Objetivo 40x: Aumento total = 10 * 40 = 400x (Fibras y células vegetales)\n  - Objetivo 100x Oil: Aumento total = 10 * 100 = 1000x (Bacterias y levaduras en inmersión)")
        ],
        "calculos": [
            ("Enfoque e Iluminación de Köhler", "1. Enfocar la muestra con objetivo 10x.\n2. Cerrar el diafragma de campo hasta observar un polígono luminoso en el campo visual.\n3. Centrar el condensador con los tornillos de centrado y enfocar la imagen del borde del diafragma subiendo/bajando el condensador.\n4. Abrir el diafragma de campo hasta que apenas circunscriba el campo visual visible. Este protocolo optimiza el contraste y elimina luz difusa parásita.")
        ],
        "componentes": [
            "Cabezal trinocular Siedentopf inclinado a 30°, rotatorio en 360°, con distancia interpupilar regulable de 48 a 75 mm y puerto fotográfico superior vertical.",
            "Par de oculares de gran campo WF 10x / 20 mm con ajuste dióptrico independiente en ambos tubos portaoculares apto para usuarios con anteojos.",
            "Revólver cuádruple portaobjetos inclinado hacia atrás (para acceso libre a la platina) con 4 objetivos Plan-Acromáticos Zeiss:\n  - Plan-Achromat 4x / 0.10 (distancia de trabajo 6.50 mm)\n  - Plan-Achromat 10x / 0.25 (distancia de trabajo 4.39 mm)\n  - Plan-Achromat 40x / 0.65 retráctil con amortiguación de resorte (distancia de trabajo 0.60 mm)\n  - Plan-Achromat 100x / 1.25 Oil retráctil para inmersión en aceite mineral (distancia de trabajo 0.19 mm)",
            "Condensador Abbe centrado de apertura numérica NA 0.9 / 1.25 con diafragma iris graduado según aperturas de objetivos y ranura para corredera de contraste de fases.",
            "Platina mecánica de doble capa (140 x 135 mm) con pinza sujeta-portaobjetos ergonómica y mando coaxial X-Y bajo sin cremallera dentada saliente (rango 75 x 30 mm).",
            "Mecanismo de enfoque coaxial bilateral con perilla macrométrica (paso rápido) y perilla micrométrica graduada de alta sensibilidad (2 µm por división).",
            "Sistema de iluminación LED de luz blanca con temperatura de color 3200 K homogénea e indicador óptico de intensidad mediante barra de 5 luces LED azules en el estativo."
        ],
        "sop": [
            ("Fase 1: Conexión y Encendido", "Retirar la funda antipolvo de vinilo con cuidado. Comprobar que las lentes ópticas estén impecablemente limpias. Conectar el adaptador de corriente a 110-220 V. Encender el interruptor de encendido situado en el lateral inferior. Ajustar la intensidad luminosa con el mando giratorio hasta un nivel medio confortable (3 segmentos iluminados en la barra azul del estativo)."),
            ("Fase 2: Colocación de la Muestra y Ajuste Interpupilar", "Abrir la palanca de resorte de la pinza mecánica y colocar suavemente el portaobjetos con el cubreobjetos hacia arriba sobre la superficie de la platina, apoyándolo firmemente en la escuadra metálica. Ajustar la distancia interpupilar del cabezal binocular cerrando o abriendo los tubos oculares hasta observar un único círculo de luz uniforme con ambos ojos. Ajustar las dioptrías en los anillos de cada ocular."),
            ("Fase 3: Enfoque Secuencial", "Girar el revólver portaobjetos hasta encajar el objetivo de menor aumento (4x o 10x) en el eje óptico (se percibe un clic de retención). Subir la platina totalmente con el tornillo macrométrico observando lateralmente para no golpear el vidrio. Mirando por los oculares, bajar lentamente la platina con el macrométrico hasta que aparezca la imagen de la muestra; afinar la nitidez máxima con el tornillo micrométrico. Desplazar la preparación mediante los mandos coaxiales X-Y para explorar las zonas de interés."),
            ("Fase 4: Uso del Objetivo 100x con Aceite de Inmersión", "Para observar morfología bacteriana, frotis teñidos (Gram) o detalles finos de almidón a 1000x: enfocar la zona de interés con el objetivo 40x; rotar el revólver a una posición intermedia entre 40x y 100x; depositar una pequeña gota (sin burbujas) de Aceite de Inmersión de baja fluorescencia (Zeiss 518N, índice de refracción nD = 1.518) directamente sobre el cubreobjetos en el punto de luz; girar el objetivo 100x Oil hasta escuchar el clic, asegurando que la lente frontal quede inmersa en la gota de aceite. Ajustar finamente el enfoque ÚNICAMENTE con el tornillo micrométrico."),
            ("Fase 5: Protocolo de Limpieza Óptica Post-uso", "Al concluir la sesión, bajar la platina y retirar la preparación. LIMPIAR DE INMEDIATO el objetivo 100x Oil. Tomar una hoja de Papel Óptico especial para lentes (Lens Paper), doblarla y humedecerla ligeramente con una gota de solución limpiadora de lentes (mezcla de éter etílico y alcohol isopropílico 70:30 o limpiador óptico Zeiss). Limpiar suavemente la lente frontal con movimiento en espiral desde el centro hacia la periferia, sin frotar con fuerza. Secar con una porción limpia de papel óptico. ¡Nunca dejar aceite seco sobre la lente! Girar el revólver al objetivo 4x, descender la platina, apagar el interruptor y cubrir con la funda antipolvo.")
        ],
        "epp": [
            ("Mandil de laboratorio limpio", "Para trabajo en el área de microbiología y microscopía."),
            ("Guantes desechables de nitrilo", "Obligatorios al manipular preparaciones biológicas fijadas, tinciones de Gram, azul de metileno, Lugol o aceite de inmersión."),
            ("Papel óptico para lentes (Lens Cleaning Paper)", "Material de consumo obligatorio para el secado y limpieza sin rayaduras.")
        ],
        "riesgos": [
            "Contaminación o degradación química de los recubrimientos antirreflejo de las lentes si se limpian con papel tisú común, servilletas, ropa o solventes agresivos como acetona pura o xileno.",
            "Crecimiento de hongos filamentosos en los prismas internos si el microscopio se almacena en ambientes húmedos y cálidos sin fundas con sílica gel desecante.",
            "Ruptura de láminas portaobjetos y daño de la lente frontal de 40x o 100x por aproximación violenta con el tornillo macrométrico mirando a través de los oculares."
        ],
        "guias_uteq": [
            "Práctica Bromatología N° 07: Identificación Microscópica de Almidones y Detección de Adulteraciones en Harinas Comerciales mediante Tinción con Lugol según NTE INEN 0517.",
            "Práctica Bromatología N° 10: Detección Botánica y Microanálisis de Materias Extrañas en Alimentos Balanceados (Control de Harina de Carne y Hueso según Métodos Oficiales AOAC 970.44).",
            "Práctica Bromatología N° 15: Recuento en Cámara de Neubauer de Levaduras y Mohos en Procesos de Fermentación de Lácteos y Ensilajes Tropicales."
        ],
        "normas": "NTE INEN 1529, AOAC 970.44, ISO 8036 (Microscopía óptica - Aceites de inmersión), ISO 19012.",
        "mantenimiento": [
            ("Diario", "Inspección y limpieza de lentes oculares y objetivos con papel de óptica; retirar todo rastro de aceite de inmersión; colocación de funda protectora."),
            ("Semanal", "Limpieza de la superficie de la platina mecánica con un paño ligeramente humedecido en alcohol isopropílico para retirar polvo y manchas biológicas."),
            ("Semestral", "Revisión del alineamiento del eje óptico y lubricación de las guías de cremallera mecánica con grasa óptica de teflón sintética."),
            ("Control de Humedad", "Colocar bolsitas de gel de sílice activa reactivada dentro del gabinete de almacenamiento del microscopio para mantener la humedad relativa por debajo del 60%.")
        ],
        "troubleshooting": [
            ("Imagen borrosa, con velo lechoso o sin contraste", "Lente frontal del objetivo 40x o 100x sucia con aceite seco o huellas dactilares. Limpiar inmediatamente con papel óptico y solución limpiadora de lentes."),
            ("Puntos negros o partículas que giran al rotar el ocular", "Polvo en la lente externa o interna del ocular WF 10x. Desmontar el ocular y limpiar la superficie con soplador de perilla de aire y papel óptico."),
            ("La preparación choca contra la lente frontal al intentar enfocar", "El portaobjetos está colocado al revés (con el cubreobjetos hacia abajo), impidiendo que el objetivo de corta distancia de trabajo alcance el plano focal."),
            ("La luz LED parpadea o no enciende", "Verificar que el conector cilíndrico de la fuente de alimentación trasera esté firmemente insertado en la toma hembra del estativo.")
        ]
    },
    {
        "num": 5,
        "clase_yolo": "Destilacion de Nitrogeno y Proteinas",
        "nombre_oficial": "Unidad de Destilación Kjeldahl para Proteína Bruta Distillation Unit 100",
        "fabricante": "Fisher Scientific / Labconco Corporation (EE.UU.)",
        "modelo": "Distillation Unit 100 (Kjeldahl Rapid Distiller)",
        "ubicacion": "Mesa analítica de nitrógeno y proteína total",
        "alimentacion": "115 / 230 V ~ 50/60 Hz, 1600 W",
        "servicios": "Red de agua de refrigeración (flujo 1.5 - 2.5 L/min), desagüe resistente a químicos alcalinos y garrafa de reserva de NaOH 40%",
        "imagen": "destilador_proteina.jpg",
        "principio": (
            "La unidad Fisher Scientific Distillation Unit 100 es un equipo robusto de destilación Kjeldahl rápida por inyección directa de vapor diseñado para el análisis y cuantificación de nitrógeno total y proteína cruda en grandes lotes de muestras agrícolas, pecuarias y bromatológicas.\n\n"
            "El equipo opera mediante un sistema electromecánico de alta potencia donde el vapor generado en una caldera blindada es inyectado directamente en la base del tubo de digestión macro-Kjeldahl. La adición controlada de hidróxido de sodio concentrado (NaOH al 40% p/v) a través de un sistema dosificador por pulsador mecánico-asistido alcaliniza de inmediato el sulfato de amonio resultante de la digestión ácida.\n\n"
            "El vapor arrastra los vapores de amoníaco liberados (NH3) a través de un cabezal de borosilicato de diseño ciclónico con trampa de gotas anti-arrastre, que previene que salpicaduras de sosa cáustica pasen al condensador. El condensador tubular enfría el destilado hacia el matraz receptor de ácido bórico con indicador Tashiro para su titulación estequiométrica posterior."
        ),
        "reacciones": [
            ("1. Digestión Kjeldahl Catalítica (Bloque Digestor)", "Muestra en polvo + H2SO4 (98%) + CuSO4/Se --(420°C)--> (NH4)2SO4 + CO2 + SO2 + H2O"),
            ("2. Inyección de Reactivo Alcalino (Unit 100)", "(NH4)2SO4 + 2 NaOH ----> Na2SO4 + 2 NH3 (gas) ^ + 2 H2O"),
            ("3. Captura Cuantitativa en Ácido Bórico", "NH3 + H3BO3 ----> NH4+ + H2BO3- (Formación de borato amónico; viraje indicador a verde)"),
            ("4. Valoración Volumétrica con Ácido Patrón", "NH4+ + H2BO3- + HCl (0.1000 N) ----> NH4Cl + H3BO3 (Viraje exacto a violeta pálido)")
        ],
        "calculos": [
            ("Fórmula de Titulación para Proteína Cruda", "% Proteína Cruda = [ (V_HCl_muestra - V_HCl_blanco) * N_HCl * 1.4007 * F_proteína ] / Masa_muestra (g)\n  - V_HCl = Volumen gastado de ácido clorhídrico valorado en la bureta (mL)\n  - N_HCl = Normalidad exacta del HCl (aprox. 0.1000 N corregida con factor de corrección fc)\n  - 1.4007 = Miliequivalente del nitrógeno expresado en gramos/100 (14.007 mg/meq * 100 / 1000)\n  - F_proteína = Factor de Jones específico de la matriz alimenticia (6.25 para piensos y carnes)")
        ],
        "componentes": [
            "Caldera de vaporización rápida con resistencia eléctrica tubular de inmersión en aleación Incoloy de 1600 W.",
            "Panel de control frontal analógico-digital con selector de tiempo de destilación en minutos (0 a 10 min) y corte automático.",
            "Pulsador manual/automático de inyección suplementaria de vapor (Rapid Steam Boost) y llave selectora de dosificación de NaOH 40%.",
            "Cabezal de arrastre en vidrio borosilicato 3.3 con trampa de salpicaduras esférica incorporada.",
            "Condensador vertical de serpentín de vidrio con camisa de agua refrigerante de doble flujo.",
            "Adaptador elástico de silicona reforzada para acople hermético de tubos macro-Kjeldahl de 250 mL y 500 mL.",
            "Puerta frontal batiente de acrílico de alta resistencia al impacto para confinamiento de aerosoles calientes."
        ],
        "sop": [
            ("Fase 1: Verificación de Conexiones y Reactivos", "Revisar el bidón colector de NaOH al 40% p/v, asegurando que la manguera de aspiración de vitón esté sumergida en el fondo. Verificar que el suministro de agua para refrigeración esté abierto y que el tubo de descarga fluya libremente hacia el drenaje sin acodamientos."),
            ("Fase 2: Encendido y Purga Previa", "Accionar el interruptor de encendido POWER. La resistencia del calderín comienza a calentar el agua. Esperar 5 a 10 minutos para alcanzar producción de vapor estable. Colocar un tubo Kjeldahl vacío con 50 mL de agua destilada y un matraz receptor con agua en la salida del condensador. Ejecutar un ciclo de vapor de 3 minutos para estabilizar la temperatura del serpentín de condensación y eliminar residuos de ensayos previos."),
            ("Fase 3: Destilación de la Muestra Bromatológica", "Añadir 25 mL de solución de Ácido Bórico al 4% con 3 gotas de indicador mixto Tashiro en un matraz Erlenmeyer de 250 mL. Colocar el matraz bajo el tubo del condensador asegurando inmersión de la punta. Tomar el tubo Kjeldahl que contiene la muestra digerida y atemperada; acoplarlo en la boca del cabezal de destilación cerrando la palanca de fijación. Cerrar la pantalla protectora frontal. Presionar la perilla dosificadora de álcali para inyectar 50 mL de NaOH 40% (la mezcla en el tubo toma color negro azabache de inmediato por precipitación del hidróxido de cobre del catalizador). Ajustar el temporizador a 4 minutos y presionar START."),
            ("Fase 4: Recolección y Valoración", "El amoníaco arrastrado condensa y vira el indicador en el matraz a verde esmeralda brillante. Transcurridos los 4 minutos, la alarma sonora avisa el fin de la destilación. Bajar el matraz receptor lavando la cánula con piseta de agua destilada. Proceder de inmediato a la valoración con HCl 0.1 N estandarizado en la bureta hasta el viraje violeta."),
            ("Fase 5: Retiro Seguro y Purga de Cabezal", "Con guantes térmicos, desenganchar el tubo caliente y vaciar el líquido alcalino neutralizado en el bidón de residuos inorgánicos pesados. Colocar un tubo con agua destilada para un ciclo de enjuague rápido de 1 minuto. Apagar el equipo y cerrar el grifo de agua de refrigeración.")
        ],
        "epp": [
            ("Mandil de laboratorio antifluidos de manga larga", "Protección corporal completa frente a salpicaduras."),
            ("Gafas de seguridad química o pantalla facial completa", "Protección estricta contra álcalis cáusticos a temperatura de ebullición."),
            ("Guantes de nitrilo puño largo", "Para conexión y recarga de recipientes de hidróxido de sodio concentrado."),
            ("Guantes térmicos de protección contra calor", "Para desmonte de tubos macro Kjeldahl a 90 °C tras la destilación.")
        ],
        "riesgos": [
            "Quemaduras severas por salpicadura de solución cáustica alcalina (NaOH 40%) que destruye el tejido dérmico y ocular rápidamente.",
            "Explosión o proyección por taponamiento de las líneas de vapor si no se descalcifica la caldera periódicamente.",
            "Sobrecalentamiento del condensador por corte en el flujo de agua potable de refrigeración."
        ],
        "guias_uteq": [
            "Práctica Bromatología N° 03: Determinación de Proteína Cruda en Harinas y Materias Primas para Nutrición Animal (AOAC 984.13).",
            "Práctica Bromatología N° 06: Control de Calidad de Harina de Pescado y Concentrados Proteicos según Norma Ecuatoriana NTE INEN 0516.",
            "Práctica Bromatología N° 11: Contenido de Nitrógeno Total y Balance de Proteína en Ensilajes y Forrajes de Clima Cálido."
        ],
        "normas": "AOAC Official Method 984.13, AOAC 990.03, ISO 5983-1, NTE INEN 0516, NTE INEN 465.",
        "mantenimiento": [
            ("Diario", "Lavado del cabezal de condensación con agua destilada; enjuague de la manguera dosificadora de álcali; limpieza del acople de neopreno."),
            ("Quincenal", "Verificación del volumen real dosificado por el sistema de adición de NaOH utilizando agua y una probeta graduada de 100 mL."),
            ("Mensual", "Descalcificación química de la caldera de vapor mediante recirculación de solución de ácido cítrico al 10% durante 20 minutos; drenar y enjuagar 4 veces."),
            ("Control Analítico", "Ensayo en blanco de reactivos (sin muestra) y ensayo de recuperación de patrón de Sulfato de Amonio (>= 99.5%).")
        ],
        "troubleshooting": [
            ("El destilado sale caliente del refrigerante (> 30 °C)", "Caudal de agua de refrigeración insuficiente o temperatura del agua de red demasiado alta. Aumentar el flujo a 2.5 L/min o conectar un enfriador recirculante."),
            ("Salpicaduras de reactivo alcalino negro pasan al matraz receptor", "Ebullición violenta en el tubo o nivel excesivo de líquido. Verificar que el volumen dentro del tubo no supere los 150 mL y comprobar el estado de la trampa anti-arrastre."),
            ("El temporizador no activa el generador de vapor", "Microinterruptor de la compuerta de seguridad desalineado o compuerta mal cerrada."),
            ("Goteo constante por el acople superior del tubo Kjeldahl", "Junta elástica de silicona reseca o desgastada. Reemplazar la junta de goma cónica.")
        ]
    },
    {
        "num": 6,
        "clase_yolo": "Destilador de Agua Continuo Metalico",
        "nombre_oficial": "Destilador de Agua Metálico Continuo de Fijación Mural",
        "fabricante": "Boeco Germany / J.P. SELECTA S.A.",
        "modelo": "Destilador Mural Acero Inoxidable 4 L/h (Cat. Boeco WS-400 / Selecta Destil)",
        "ubicacion": "Pared húmeda de destilación y lavado químico",
        "alimentacion": "230 V ~ 50/60 Hz, 3000 W (Monofásico con tierra física de alta capacidad)",
        "servicios": "Red de agua potable o prefiltrada (presión 1.5 - 3.5 bar, caudal mínimo 60 L/h) y desagüe continuo",
        "imagen": "destilador_agua_continuo_metalico.jpg",
        "principio": (
            "El destilador de agua continuo metálico purifica agua potable de red mediante evaporación térmica y condensación forzada por contracorriente, generando agua destilada de alta pureza analítica (Tipo III / Tipo II según ISO 3696).\n\n"
            "El principio se basa en la gran diferencia de volatilidad entre el solvente agua (H2O, punto de ebullición 100 °C a 1 atm) y las impurezas disueltas (sales minerales de calcio, magnesio, sodio, cloruros, sulfatos, carbonatos, metales pesados y microorganismos pirogénicos), los cuales son no volátiles y quedan concentrados en el fondo de la caldera de ebullición.\n\n"
            "El agua de alimentación ingresa primero por el serpentín de condensación refrigerante, precalentándose mediante intercambio térmico pasivo con los vapores ascendentes antes de alimentar la caldera. Este diseño economizador de energía reduce el consumo eléctrico y optimiza la tasa de evaporación continua a 4.0 litros por hora."
        ),
        "reacciones": [
            ("1. Cambio de Fase Termodinámico (Caldera)", "H2O (líquido con solutos e iones) + Calor (Delta H_vap = 2257 kJ/kg) --(100°C)--> H2O (vapor purificado) ^"),
            ("2. Precipitación de Sales y Formación de Sarro en Caldera", "Ca(HCO3)2 (aq) --(Calor)--> CaCO3 (sólido incrustante / sarro) v + CO2 (gas) ^ + H2O"),
            ("3. Condensación por Enfriamiento en Serpentín", "H2O (vapor sobrecalentado) - Calor sensible y latente ----> H2O (líquido destilado, conductividad < 2.5 µS/cm)")
        ],
        "calculos": [
            ("Balance de Calidad del Agua Destilada Producida", "- Conductividad eléctrica a 25 °C: 1.5 a 2.5 µS/cm (Resistividad: 0.4 a 0.67 M-Ohm*cm)\n- pH a 25 °C: 5.5 a 6.8 (ligeramente ácido por disolución pasiva de CO2 atmosférico)\n- Residuo seco por evaporación a 105 °C: < 1.0 mg/kg (< 1 ppm)\n- Iones cloruro, sulfato y amonio: No detectables (< 0.1 mg/L)\n- Metales pesados (Pb, Cu, Fe, Zn): < 0.05 mg/L\n- Clasificación oficial: Cumple con la Norma ISO 3696 Grado 3 y ASTM D1193 Tipo IV")
        ],
        "componentes": [
            "Caldera cilíndrica de ebullición fabricada íntegramente en chapa embutida de acero inoxidable sanitario AISI 304 / 316 pulido espejo.",
            "Resistencia eléctrica calefactora blindada de inmersión en acero inoxidable de 3000 W (3.0 kW) de alta transferencia térmica.",
            "Refrigerante condensador de serpentín tubular continuo en acero inoxidable alojado en camisa refrigerante desmontable.",
            "Sistema de alimentación de nivel constante con rebose continuo y válvula flotante de equilibrio hidráulico.",
            "Dispositivo bimetálico de seguridad por sobretemperatura (corte automático si la caldera queda sin flujo de agua) con pulsador de rearme manual.",
            "Tubo de salida de agua destilada con cánula de aireación y tubo de rebose de agua de refrigeración hacia desagüe.",
            "Chasis de fijación mural reforzado en acero esmaltado epoxi resistente a la corrosión ambiental."
        ],
        "sop": [
            ("Fase 1: Verificación de Conexiones Hidráulicas", "Comprobar que la manguera de entrada de agua de red esté firmemente acoplada a la espiga metálica con abrazadera de presión. Verificar que la manguera de desagüe de refrigeración esté dirigida al sumidero sin estrangulamientos y con caída libre de gravedad. Comprobar que la manguera de salida de agua destilada descargue en un bidón de polietileno de alta densidad (HDPE) de 20 L perfectamente limpio y rotulado."),
            ("Fase 2: Alimentación Hidráulica Inicial", "Abrir la llave de paso de agua de la red general. Esperar a que el agua ingrese al condensador y empiece a llenar la caldera de ebullición. Cuando el agua comience a salir en flujo continuo por el tubo de rebose hacia el desagüe, significa que el nivel de seguridad de la caldera ha sido alcanzado."),
            ("Fase 3: Activación Eléctrica y Régimen de Ebullición", "Accionar el interruptor general eléctrico bipolar (con luz piloto). NUNCA encender el interruptor con la caldera vacía o sin flujo de rebose (riesgo inminente de quemado de la resistencia de 3000 W). En aproximadamente 10 a 15 minutos, se alcanzará la ebullición plena y comenzará el goteo constante de agua destilada por el pico colector. Descartar los primeros 500 mL producidos al inicio de la jornada para enjuague del condensador."),
            ("Fase 4: Monitoreo Durante el Funcionamiento", "Supervisar periódicamente que el flujo de agua de refrigeración hacia el desagüe permanezca templado (aproximadamente 40-50 °C). Si sale excesivamente caliente o emite vapor por el tubo de rebose, aumentar ligeramente el caudal de entrada de agua de red."),
            ("Fase 5: Parada y Enfriamiento Seguro", "Para detener la producción: desconectar en primer lugar el interruptor eléctrico de la resistencia. Dejar correr el agua de refrigeración durante 5 a 10 minutos adicionales para que la caldera y la resistencia se enfríen por debajo de 60 °C (previniendo incrustación térmica violenta de sales). Cerrar la llave de paso del agua de red. Tapar herméticamente el bidón de almacenamiento de agua destilada.")
        ],
        "epp": [
            ("Mandil de laboratorio", "De uso general en el laboratorio."),
            ("Gafas de seguridad", "Protección básica ocular."),
            ("Guantes de protección térmica o de goma gruesa", "Al realizar tareas de inspección, purga de agua caliente o descalcificación química con soluciones ácidas."),
            ("Calzado cerrado de seguridad con suela de goma", "Prevención de riesgo eléctrico en suelo húmedo.")
        ],
        "riesgos": [
            "Quemaduras térmicas graves por contacto directo con la carcasa metálica de la caldera (temperatura superficial > 95 °C) o por vapor de fuga.",
            "Riesgo eléctrico mayor: la potencia del equipo es de 3000 W en un entorno húmedo. Requiere obligatoriamente conexión a tierra física y disyuntor diferencial de 16 A / 30 mA.",
            "Destrucción de la resistencia eléctrica por fusión térmica inmediata si se enciende sin agua en la caldera o se corta el suministro de red."
        ],
        "guias_uteq": [
            "Operación Básica Bromatología: Suministro de Agua Destilada para Preparación de Reactivos Analíticos, Soluciones Patrón y Digestiones Kjeldahl.",
            "Práctica Bromatología N° 01: Enjuague Final de Material Volumétrico de Precisión (Matraces, Pipetas y Buretas) para Ensayos Proximales según ISO 3696.",
            "Norma Institucional de Aseguramiento de Calidad: Verificación de Conductividad y Ausencia de Iones Interferentes en Blancos Analíticos."
        ],
        "normas": "ISO 3696 (Grado 3), ASTM D1193 (Tipo IV), NTE INEN 1108, Farmacopea Europea (Agua Purificada).",
        "mantenimiento": [
            ("Semanal", "Inspección visual del fondo de la caldera a través de la tapa superior; drenar el fondo por el grifo de purga para desalojar sedimentos sueltos."),
            ("Mensual / Protocolo de Descalcificación", "Si se observa capa blanca de carbonato de calcio (sarro) sobre la resistencia: desconectar de la red eléctrica. Vaciar la caldera. Introducir 2 a 3 litros de solución de Ácido Cítrico al 10% o Ácido Acético al 10% (vinagre blanco analítico). Dejar actuar durante 4 a 6 horas (o calentar suavemente a 50 °C durante 30 min sin hervir). Enjuagar con 10 litros de agua corriente y descartar los primeros 2 litros de agua destilada generada."),
            ("Trimestral", "Verificación metrológica de la conductividad eléctrica del agua destilada producida (< 2.5 µS/cm a 25 °C) mediante conductímetro calibrado.")
        ],
        "troubleshooting": [
            ("El equipo se apaga solo de forma súbita", "Corte del flujo de agua de refrigeración. El termostato de seguridad actuó por sobretemperatura. Dejar enfriar 15 minutos, restablecer el caudal de agua y presionar el botón rojo de rearme térmico (Reset)."),
            ("El agua destilada tiene conductividad alta (> 5.0 µS/cm)", "Acumulación excesiva de sarro en la caldera que produce arrastre de espuma salina por ebullición tumultuosa. Ejecutar descalcificación química inmediata."),
            ("Sale vapor abundante por el tubo de agua destilada y no gotea líquido", "Flujo de agua de refrigeración cerrado o insuficiente. Aumentar inmediatamente el caudal de entrada de agua de red."),
            ("La resistencia no calienta a pesar de que el interruptor está encendido", "Resistencia eléctrica abierta/quemada o termostato de seguridad disparado. Revisar continuidad con multímetro.")
        ]
    },
    {
        "num": 7,
        "clase_yolo": "Sistema de Tratamiento y Desionizacion deAgua",
        "nombre_oficial": "Sistema de Tratamiento, Filtración y Desionización de Agua Multietapa",
        "fabricante": "Aquapro / Ensamblaje Industrial Especializado",
        "modelo": "Batería Desionizadora de 3 Etapas con Manómetros Hidráulicos",
        "ubicacion": "Área húmeda de suministro de agua desmineralizada y reactivos",
        "alimentacion": "No requiere alimentación eléctrica (operación por presión hidrostática de red, 2.0 a 4.5 bar)",
        "servicios": "Acometida hidráulica de agua potable de red y conducto a tanques de almacenamiento analítico",
        "imagen": "sistema_tratamiento_desionizacion_agua.jpg",
        "principio": (
            "El sistema de tratamiento y desionización de agua produce agua desmineralizada de alta pureza mediante un proceso secuencial de retención física de partículas, adsorción química sobre carbón activado e intercambio iónico heterogéneo en lecho mixto.\n\n"
            "En la primera etapa, un filtro de sedimentos de microfibras de polipropileno termosoldadas retiene partículas coloidales, turbidez, arenas y óxidos metálicos mayores a 5 micrómetros, protegiendo las etapas posteriores.\n\n"
            "En la segunda etapa, un bloque de carbón activado granular extrusionado (CTO) remueve por quimisorción el cloro residual libre, cloraminas, trihalometanos, olores, sabores y contaminantes orgánicos volátiles (COVs) que degradarían irreversiblemente las resinas sintéticas.\n\n"
            "En la tercera etapa, el agua atraviesa una columna de resinas de intercambio iónico de Lecho Mixto (Mixed Bed) compuestas por resina catiónica de ácido fuerte (R-SO3- H+) y resina aniónica de base fuerte (R-N+(CH3)3 OH-) en proporción estequiométrica equivalente. Todos los iones disueltos (cationes Ca2+, Mg2+, Na+, K+ y aniones Cl-, SO42-, HCO3-, NO3-) son intercambiados cuantitativamente por iones H+ y OH-, los cuales se recombinan instantáneamente formando moléculas de agua pura (H2O), alcanzando conductividades ultra-bajas (< 1.0 µS/cm)."
        ),
        "reacciones": [
            ("1. Retención Catiónica (Resina Catiónica Ácida)", "2 R-SO3^- H^+ + Ca^2+ (aq) ----> (R-SO3)2 Ca + 2 H^+ (aq)\nR-SO3^- H^+ + Na^+ (aq) ----> R-SO3 Na + H^+ (aq)"),
            ("2. Retención Aniónica (Resina Aniónica Básica)", "R-N^+(CH3)3 OH^- + Cl^- (aq) ----> R-N^+(CH3)3 Cl + OH^- (aq)\n2 R-N^+(CH3)3 OH^- + SO4^2- (aq) ----> [R-N^+(CH3)3]2 SO4 + 2 OH^- (aq)"),
            ("3. Recombinación de Iones Hidronio e Hidroxilo", "H^+ (aq) + OH^- (aq) ----> H2O (Pureza molecular neutra, pH 6.8 - 7.2)"),
            ("4. De-cloración Química en Carbón Activado", "C* (carbón activado) + HOCl (ácido hipocloroso) ----> C*O (óxido superficial) + H^+ + Cl^-")
        ],
        "calculos": [
            ("Parámetros de Calidad del Agua Desionizada Tipo II (ISO 3696)", "- Conductividad eléctrica a 25 °C: < 1.0 µS/cm (Resistividad: > 1.0 M-Ohm*cm)\n- Sólidos Totales Disueltos (TDS): < 0.5 ppm (mg/L)\n- Cloro libre residual: No detectable (< 0.02 ppm con ensayo DPD)\n- Caudal nominal de operación: 1.5 a 3.0 Litros/minuto a presión de red de 3 bar\n- Presión diferencial normal de trabajo (Delta P = P1 - P2): < 0.8 bar")
        ],
        "componentes": [
            "Portafiltro Etapa 1: Vaso de polipropileno reforzado azul de 10 pulgadas con cartucho de sedimentos de polipropileno spun de 5 micras (5 µm).",
            "Portafiltro Etapa 2: Vaso de 10 pulgadas con cartucho de Carbón Activado en Bloque compacto (CTO) de cáscara de coco de alta capacidad de adsorción.",
            "Portafiltro Etapa 3: Vaso transparente o azul de 10 pulgadas con cartucho recargable de Resina Desionizadora de Lecho Mixto (Mixed Bed MB400 / MB9L) con indicador colorimétrico de agotamiento.",
            "Manómetro de Entrada (P1): Manómetro analógico de esfera en baño de glicerina (0 - 10 bar / 0 - 150 psi) para monitoreo de la presión hidráulica de red.",
            "Manómetro Intermedio/Salida (P2): Manómetro de precisión en glicerina para cálculo directo de pérdida de carga y saturación de filtros (Delta P).",
            "Soporte bastidor metálico de montaje mural con pintura electrostática horneada anticorrosiva.",
            "Válvulas esféricas de corte de 1/2 pulgada y conexiones rápidas de polietileno tipo John Guest."
        ],
        "sop": [
            ("Fase 1: Inspección de Presión Hidráulica", "Observar los manómetros de glicerina P1 y P2. Abrir lentamente la válvula esférica de entrada de agua de red. Comprobar que la presión de entrada (P1) esté comprendida entre 2.0 y 4.0 bar (30 a 60 psi). Si la presión es inferior a 1.5 bar, el caudal será deficiente; si supera 5.0 bar, regular la llave para evitar sobrepresión en los vasos."),
            ("Fase 2: Monitoreo de Pérdida de Carga (Delta P)", "Verificar la lectura del manómetro P2 a la salida de las etapas de filtración. Calcular la presión diferencial: Delta P = P1 - P2. En condiciones normales con cartuchos limpios, Delta P debe ser menor a 0.5 bar. Si Delta P supera 1.2 bar, indica que el filtro de sedimentos de 5 µm está colmatado y debe ser reemplazado."),
            ("Fase 3: Purga Inicial y Verificación de Conductividad", "Abrir la válvula de toma de muestra y purgar los primeros 1 a 2 litros hacia la cubeta de desagüe para desplazar el frente iónico estancado en la columna de resinas. Tomar una alícuota en un vaso de precipitado limpio y medir la conductividad con conductímetro digital calibrado. El valor debe ser estrictamente menor a 1.5 µS/cm (idealmente < 0.8 µS/cm)."),
            ("Fase 4: Suministro a Equipos del Laboratorio", "Conectar la manguera de salida al tanque de abastecimiento del destilador Kjeldahl Pro-Nitro, al calderín del destilador metálico, al DOSI-FIBER o a los bidones de almacenamiento analítico de polietileno de alta pureza. Abrir la válvula de distribución continua."),
            ("Fase 5: Cierre al Finalizar la Jornada", "Cerrar la válvula esférica general de entrada de agua de red. Aliviar la presión del sistema abriendo brevemente la canilla de salida para no someter los portafiltros a fatiga mecánica estática continua durante la noche.")
        ],
        "epp": [
            ("Mandil de laboratorio", "De uso habitual."),
            ("Guantes de nitrilo", "Obligatorios durante las maniobras de apertura de vasos, cambio de cartuchos y manipulación de resinas de intercambio iónico vírgenes o regeneradas."),
            ("Gafas de seguridad de laboratorio", "Para protección ante proyecciones de agua a presión al abrir los vasos despresurizadores.")
        ],
        "riesgos": [
            "Inundación del laboratorio por rotura de mangueras de polietileno o desprendimiento de conexiones rápidas sometidas a golpes de ariete de la red.",
            "Contaminación química masiva de análisis bromatológicos si se utiliza agua desionizada con resina agotada (fuga de iones amonio, sodio y cloruros).",
            "Proliferación bacteriana o formación de biofilm en los vasos de resina si el sistema permanece inactivo durante semanas sin flujo ni desinfección."
        ],
        "guias_uteq": [
            "Protocolo de Aseguramiento de Calidad Analítica: Monitoreo Diario de la Pureza del Agua Desionizada (Conductividad y pH) en el Laboratorio de Bromatología.",
            "Práctica Bromatología N° 02: Preparación y Estandarización de Soluciones Valoradas de NaOH 0.1 N, HCl 0.1 N y H2SO4 0.255 N con Agua Grado Analítico.",
            "Mantenimiento Rutinario: Protocolo de Sustitución de Cartuchos y Reactivación de Resinas de Intercambio Iónico."
        ],
        "normas": "ISO 3696 (Grado 2), ASTM D1193 (Tipo II), NTE INEN 1108, Standard Methods for Examination of Water and Wastewater (Sección 1080).",
        "mantenimiento": [
            ("Filtro de Sedimentos (5 µm)", "Sustituir cada 2 a 3 meses, o inmediatamente cuando tome coloración marrón oscura y Delta P > 1.0 bar."),
            ("Filtro de Carbón Activado (CTO)", "Sustituir cada 4 a 6 meses para garantizar retención total de cloro libre y evitar la oxidación química de las resinas catiónicas."),
            ("Cartucho de Resina de Lecho Mixto", "Sustituir o regenerar cuando la conductividad del agua efluente supere los 2.5 µS/cm o cuando el 80% de la columna vire de color azul/verde a ámbar oscuro."),
            ("Desinfección de Portafiltros", "En cada cambio de cartuchos, lavar el interior de los vasos plásticos con solución de hipoclorito de sodio al 1% y enjuagar abundantemente con agua destilada.")
        ],
        "troubleshooting": [
            ("La conductividad sube súbitamente por encima de 10 µS/cm", "Agotamiento total de la resina de lecho mixto o canalización interna del lecho por exceso de caudal. Cambiar el cartucho de resina de inmediato."),
            ("El manómetro P2 marca cero mientras P1 marca 3 bar", "Filtro de sedimentos de 5 µm completamente taponado por barro o sedimentos de la red pública. Cambiar el cartucho de sedimentos."),
            ("Goteo por la rosca superior del portafiltro", "Junta tórica (O-ring) de goma negra desalineada, pellizcada o reseca. Cerrar el paso de agua, despresurizar, retirar el vaso, lubricar el O-ring con grasa de silicona grado alimentario y apretar con la llave de vaso."),
            ("El agua presenta pequeñas burbujas blancas microscópicas", "Aire atrapado tras el cambio de cartuchos. Mantener la purga abierta durante 5 minutos hasta que el flujo sea totalmente cristalino.")
        ]
    },
    {
        "num": 8,
        "clase_yolo": "Bomba de Vacio por Recirculacion de Agua",
        "nombre_oficial": "Bomba de Vacío por Recirculación de Agua de Doble Toma",
        "fabricante": "J.P. SELECTA S.A. (España)",
        "modelo": "Bomba Recirculante Ecológica de Vacío (Cat. 4001611)",
        "ubicacion": "Mesa de filtración analítica y secado al vacío",
        "alimentacion": "230 V ~ 50/60 Hz, 180 W",
        "servicios": "Depósito autónomo de 10 L de agua limpia; no consume agua corriente continua",
        "imagen": "bomba_vacio_recirculacion_agua.jpg",
        "principio": (
            "La bomba de vacío por recirculación de agua genera depresión neumática mediante el efecto hidrodinámico Venturi operando en circuito cerrado ecológico sin desperdicio de agua potable.\n\n"
            "El principio físico se basa en el teorema de Bernoulli aplicado a eyectores convergentes-divergentes (trompas de vacío hidrodinámicas). Un motor eléctrico impulsa una bomba centrífuga sumergida en el fondo de un depósito interno de polipropileno de 10 litros de capacidad.\n\n"
            "El agua es bombeada a alta presión a través de dos toberas Venturi independientes. En la sección estrecha de la tobera, la velocidad del fluido aumenta drásticamente produciendo una caída abrupta de la presión estática por debajo de la presión atmosférica. Este diferencial crea una potente succión que aspira el aire o los gases a través de las tomas de vacío, arrastrándolos y mezclándolos con la corriente de agua hacia el depósito.\n\n"
            "El vacío residual alcanzable está físicamente acotado por la tensión de vapor del agua a la temperatura del baño (aprox. 20 mbar a 20 °C), lo que garantiza una depresión estable y suave perfecta para filtraciones analíticas sin riesgo de rotura de crisoles o evaporación violenta de muestras."
        ),
        "reacciones": [
            ("1. Principio de Bernoulli y Efecto Venturi", "P_estatica + 0.5 * rho * v^2 = Constante\nAl aumentar la velocidad del agua (v) en la garganta de la tobera, la presión estática local (P_estatica) desciende a valores de vacío relativo de hasta -0.098 MPa (-735 mmHg)."),
            ("2. Tensión de Vapor del Agua y Límite de Vacío", "P_limite = P_vapor_agua (T)\n  - A 15 °C: Vacío límite = 17.0 mbar\n  - A 20 °C: Vacío límite = 23.4 mbar\n  - A 30 °C: Vacío límite = 42.4 mbar\nPor tanto, mantener el agua del depósito fría (< 20 °C) es crítico para maximizar la fuerza de succión.")
        ],
        "calculos": [
            ("Especificaciones Operativas del Sistema", "- Tomas de vacío independientes: 2 tomas con válvulas antirretorno de retención de teflón\n- Caudal nominal de aspiración de aire: 60 Litros/minuto (3.6 m^3/h combinadas)\n- Vacío límite absoluto alcanzable: 20 mbar (0.002 MPa / -0.098 MPa manométrico)\n- Capacidad del tanque de recirculación: 10 Litros en polipropileno resistente a vapores corrosivos\n- Consumo de agua de red: 0 Litros/minuto durante la operación normal (ahorro de hasta 300 L/h comparado con trompas de grifo)")
        ],
        "componentes": [
            "Depósito tanque de 10 litros fabricado en polipropileno (PP) de una sola pieza, químicamente inerte a vapores de solventes y ácidos débiles.",
            "Conjunto motor y bomba centrífuga sumergida de 180 W con bobinado protegido contra salpicaduras térmicas.",
            "Dos toberas eyectoras Venturi independientes fabricadas en material plástico técnico de alta resistencia al desgaste hidráulico.",
            "Dos tomas de succión con espigas de polipropileno equipadas con válvulas antirretorno de teflón para evitar el reflujo accidental de agua hacia las muestras.",
            "Vacuómetro analógico de esfera con escala dual de vacío: 0 a -0.1 MPa y 0 a -760 mmHg.",
            "Grifo inferior de vaciado y purga rápida del tanque de agua.",
            "Interruptor de encendido I/O estanco y asa superior de transporte ergonómica."
        ],
        "sop": [
            ("Fase 1: Inspección y Llenado del Tanque", "Comprobar que el depósito interior contenga agua limpia hasta la marca de nivel máximo (aproximadamente 8 a 10 litros). Se recomienda utilizar agua destilada o desionizada para prevenir la acumulación de sarro en las toberas Venturi. En jornadas de trabajo intenso, puede añadirse hielo en cubos al depósito para mantener el agua por debajo de 15 °C y aumentar el vacío final."),
            ("Fase 2: Conexión Neumática con Trampa de Seguridad", "Conectar mangueras de vacío de pared gruesa (goma de vacío o silicona reforzada de 8 mm) a una o ambas tomas de succión. Es MANDATORIO intercalar siempre un matraz Kitasato trampa con tapón de goma o un filtro de membrana hidrofóbico entre el equipo analítico (ej. Dosi-Fiber, embudo Büchner) y la bomba, para evitar que líquidos o ácidos concentrados sean aspirados hacia el depósito de agua."),
            ("Fase 3: Encendido y Verificación de Vacío", "Encender el interruptor luminoso frontal. El motor arranca suavemente haciendo recircular el agua interna. Observar la aguja del vacuómetro: al tapar la toma de succión con el dedo, la aguja debe descender rápidamente hacia -0.095 MPa a -0.098 MPa en menos de 5 segundos, confirmando la hermeticidad del sistema."),
            ("Fase 4: Ejecución de la Filtración", "Conectar la manguera al colector del analizador de fibra DOSI-FIBER o al embudo de filtración al vacío. Regular la velocidad de aspiración abriendo o cerrando la llave de la toma. Proceder a la filtración del reactivo caliente o de la suspensión bromatológica."),
            ("Fase 5: Desconexión y Parada Segura", "¡ATENCIÓN PROTOCOLO CRÍTICO!: Antes de apagar el motor, se debe romper SIEMPRE el vacío del circuito abriendo la llave de la trampa a la atmósfera o retirando la manguera. NUNCA apagar el motor con el circuito cerrado al vacío, ya que la contrapresión neumática podría forzar el retorno de agua hacia la muestra. Una vez aliviado el vacío, apagar el interruptor frontal.")
        ],
        "epp": [
            ("Mandil de laboratorio", "De uso habitual."),
            ("Gafas de seguridad de laboratorio", "Para protección ante posible implosión de material de vidrio de filtración al vacío (matraces Kitasato agrietados)."),
            ("Guantes de nitrilo", "Para manipulación de solventes, filtrados y recambio de agua del depósito.")
        ],
        "riesgos": [
            "Riesgo de implosión de matraces de vidrio de borosilicato si presentan rayas profundas, fisuras o no son de paredes gruesas específicas para vacío.",
            "Retorno de agua del depósito hacia los crisoles de muestra si se apaga la bomba sin romper el vacío previamente.",
            "Pérdida de poder de succión por calentamiento progresivo del agua recirculante durante jornadas prolongadas de más de 3 horas continuas.",
            "Contaminación del agua del depósito si se aspiran vapores ácidos concentrados sin matraz trampa neutralizante."
        ],
        "guias_uteq": [
            "Operación Asistida: Filtración al Vacío de Crisoles de Fibra Cruda en el Analizador DOSI-FIBER según AOAC 962.09.",
            "Práctica Bromatología N° 05: Filtración Rápida de Residuos Insolubles de Pared Celular en Fraccionamiento Van Soest (FDN y FDA).",
            "Práctica Bromatología N° 13: Desgasificación de Fases Móviles y Solventes Orgánicos para Cromatografía y Espectrofotometría."
        ],
        "normas": "ISO 6865, AOAC 962.09, DIN 12691 (Material de vidrio para filtración al vacío), UNE-EN 61010-1 (Seguridad eléctrica en laboratorio).",
        "mantenimiento": [
            ("Renovación de Agua", "Cambiar el agua del depósito semanalmente, o de forma inmediata si se han aspirado vapores ácidos o acetona (el agua toma olor o acidez)."),
            ("Limpieza de Toberas", "Si se observa pérdida de vacío, desmontar las toberas Venturi plásticas y descalcificar con ácido acético al 5% para eliminar depósitos salinos."),
            ("Válvulas Antirretorno", "Inspeccionar trimestralmente la membrana de teflón de las válvulas antirretorno en las espigas de succión; limpiar con alcohol isopropílico.")
        ],
        "troubleshooting": [
            ("El nivel de vacío manométrico no supera los -0.05 MPa", "Temperatura del agua del depósito demasiado alta (> 35 °C). Vaciar el tanque y rellenar con agua fría a 15 °C. Otra causa común es fuga en las mangueras de conexión."),
            ("El motor hace ruido excesivo o cavita", "Nivel de agua del depósito por debajo del mínimo. Rellenar inmediatamente con agua hasta cubrir la cámara de la bomba sumergida."),
            ("Retorno de gotas de agua por la manguera de succión", "Válvula antirretorno sucia con polvo o restos fibrosos que impiden el cierre hermético. Desmontar la espiga de conexión y limpiar la bolilla/membrana de retención."),
            ("La bomba no enciende al accionar el interruptor", "Comprobar el cable de alimentación y el fusible térmico del motor; dejar enfriar si actuó el protector térmico interno.")
        ]
    }
]

print(f"Total official equipments structured: {len(EQUIPOS)}")

# ==============================================================================
# 1. GENERACIÓN DEL MARKDOWN EXHAUSTIVO: manuales_bromatologia_uteq.md
# ==============================================================================

def generate_markdown():
    md_path = os.path.join(KB_DIR, "manuales_bromatologia_uteq.md")
    print(f"Generating exhaustive Markdown file at: {md_path}")
    
    lines = []
    lines.append("# Manual Técnico Oficial y Base de Conocimiento RAG")
    lines.append("## Laboratorio de Bromatología - Universidad Técnica Estatal de Quevedo (UTEQ)")
    lines.append("### Sistema Inteligente de Visión Artificial YOLO11 y Asistente RAG para Equipos de Laboratorio\n")
    lines.append("> **Documento Institucional de Referencia Académica y Metrológica**  ")
    lines.append("> **Facultad de Ciencias Pecuarias y Biológicas — Carreras de Ingeniería en Alimentos y Zootecnia**  ")
    lines.append("> *Versión Oficial 2.0.0 — Quevedo, Los Ríos, Ecuador (2026)*\n")
    lines.append("---\n")
    
    # Introducción
    lines.append("## 1. Introducción y Arquitectura del Sistema RAG\n")
    lines.append(
        "El presente manual técnico constituye la base de conocimiento especializada (Knowledge Base) del sistema de "
        "Generación Aumentada por Recuperación (RAG - *Retrieval-Augmented Generation*) integrado al Asistente Inteligente "
        "del Laboratorio de Bromatología de la UTEQ.\n\n"
        "La arquitectura del sistema vincula dos componentes tecnológicos de vanguardia:\n"
        "1. **Módulo de Visión Artificial (YOLO11)**: Detecta y clasifica en tiempo real sobre dispositivos móviles los equipos físicos "
        "presentes en el laboratorio a partir del feed de la cámara (30 FPS).\n"
        "2. **Pipeline RAG en Dispositivo (On-Device RAG)**: Al producirse una detección positiva con confianza suficiente, el sistema "
        "recupera los fragmentos documentales pertinentes de esta base de conocimiento indexada (procedimientos operativos, precauciones de "
        "bioseguridad, fórmulas de cálculo estequiométrico y guías de práctica de la UTEQ) y proporciona al estudiante o investigador "
        "asistencia técnica interactiva y precisa sin alucinaciones.\n\n"
        "A continuación se detallan exhaustivamente los **8 equipos oficiales** que conforman el catálogo instrumental del laboratorio."
    )
    lines.append("\n---\n")
    
    # Tabla resumen de equipos
    lines.append("## 2. Índice de Equipos Oficiales Documentados\n")
    lines.append("| N° | Clase YOLO11 | Nombre Oficial | Fabricante y Modelo | Función Primaria en Bromatología |")
    lines.append("|:--:|:---|:---|:---|:---|")
    for eq in EQUIPOS:
        lines.append(f"| **{eq['num']}** | `{eq['clase_yolo']}` | {eq['nombre_oficial']} | {eq['fabricante']} - {eq['modelo']} | {eq['ubicacion']} |")
    lines.append("\n---\n")
    
    # Capítulos por equipo
    for eq in EQUIPOS:
        lines.append(f"## {eq['num'] + 2}. Manual Técnico: {eq['nombre_oficial']}")
        lines.append(f"**Etiqueta de Clasificación YOLO11:** `{eq['clase_yolo']}`  ")
        lines.append(f"**Fabricante / Procedencia:** {eq['fabricante']}  ")
        lines.append(f"**Modelo Oficial:** {eq['modelo']}  ")
        lines.append(f"**Ubicación Física en Laboratorio:** {eq['ubicacion']}  ")
        lines.append(f"**Alimentación Eléctrica:** {eq['alimentacion']}  ")
        lines.append(f"**Requerimientos de Servicios:** {eq['servicios']}  ")
        lines.append(f"**Fotografía Oficial:** `images_opt/equipos_seleccionados/{eq['imagen']}`\n")
        lines.append(f"![{eq['nombre_oficial']}](../images_opt/equipos_seleccionados/{eq['imagen']})\n")
        
        # Principio
        lines.append(f"### {eq['num'] + 2}.1 Principio Físico-Químico y Fundamento Analítico\n")
        lines.append(eq['principio'] + "\n")
        
        # Reacciones
        lines.append(f"#### Ecuaciones Químicas y Fenómenos Involucrados\n")
        for r_tit, r_eq in eq['reacciones']:
            lines.append(f"- **{r_tit}**:\n  ```\n  {r_eq}\n  ```")
        lines.append("")
        
        # Cálculos
        lines.append(f"#### Fórmulas Matemáticas y Ecuaciones de Cálculo\n")
        for c_tit, c_eq in eq['calculos']:
            lines.append(f"**{c_tit}**:\n```\n{c_eq}\n```\n")
            
        # Componentes
        lines.append(f"### {eq['num'] + 2}.2 Arquitectura Instrumental y Componentes Críticos\n")
        for comp in eq['componentes']:
            lines.append(f"- **{comp.split(':')[0]}**: {comp.split(':')[1] if ':' in comp else comp}")
        lines.append("")
        
        # SOP
        lines.append(f"### {eq['num'] + 2}.3 Procedimiento Operativo Normalizado (PNO / SOP Paso a Paso)\n")
        for fase_tit, fase_desc in eq['sop']:
            lines.append(f"#### {fase_tit}")
            lines.append(f"{fase_desc}\n")
            
        # Matriz de EPP y Seguridad
        lines.append(f"### {eq['num'] + 2}.4 Matriz de Bioseguridad y EPP Obligatorio\n")
        lines.append("| Equipo de Protección (EPP) | Especificación Técnica y Función |")
        lines.append("|:---|:---|")
        for epp_nom, epp_esp in eq['epp']:
            lines.append(f"| **{epp_nom}** | {epp_esp} |")
        lines.append("")
        
        lines.append("#### Riesgos Críticos Identificados:\n")
        for r in eq['riesgos']:
            lines.append(f"- ⚠️ **Peligro:** {r}")
        lines.append("")
        
        # Guías UTEQ y Normas
        lines.append(f"### {eq['num'] + 2}.5 Guías de Práctica UTEQ y Normas Aplicables\n")
        lines.append("**Prácticas Oficiales de Laboratorio UTEQ:**")
        for g in eq['guias_uteq']:
            lines.append(f"- 📘 {g}")
        lines.append(f"\n**Normas Técnicas Internacionales y Nacionales:** `{eq['normas']}`\n")
        
        # Mantenimiento
        lines.append(f"### {eq['num'] + 2}.6 Plan de Mantenimiento Preventivo y Calibración\n")
        lines.append("| Frecuencia / Tipo | Protocolo de Inspección y Calibración |")
        lines.append("|:---|:---|")
        for m_frec, m_prot in eq['mantenimiento']:
            lines.append(f"| **{m_frec}** | {m_prot} |")
        lines.append("")
        
        # Troubleshooting
        lines.append(f"### {eq['num'] + 2}.7 Guía de Resolución de Fallos Comunes (Troubleshooting)\n")
        lines.append("| Síntoma / Problema Observado | Causa Raíz Probable | Acción Correctiva Inmediata |")
        lines.append("|:---|:---|:---|")
        for sint, acc in eq['troubleshooting']:
            lines.append(f"| ❌ {sint} | Desviación operativa o desgaste | ✔️ {acc} |")
        lines.append("\n---\n")
        
GLOSARIO = [
    ("Agua Grado Analítico", "Agua desionizada o destilada exenta de sales minerales, materia orgánica e iones interferentes, clasificada según norma ISO 3696 en Grados 1, 2 y 3."),
    ("Blanco de Reactivos", "Ensayo analítico que contiene todos los reactivos y sigue exactamente el mismo procedimiento pero sin muestra problema, empleado para corregir impurezas de reactivos."),
    ("Digestión Ácida", "Mineralización por vía húmeda de materia orgánica mediante calentamiento con ácido sulfúrico concentrado y catalizadores para transformar nitrógeno orgánico en sulfato amónico."),
    ("Efecto Venturi", "Fenómeno hidrodinámico por el cual un fluido en movimiento dentro de un conducto cerrado disminuye su presión estática al aumentar su velocidad al pasar por una zona de menor sección."),
    ("Fibra Detergente Neutro (FDN)", "Fracción de la pared celular vegetal insoluble en detergente neutro según Van Soest, compuesta por celulosa, hemicelulosa y lignina."),
    ("Fibra Detergente Ácido (FDA)", "Fracción de la pared celular vegetal insoluble en detergente ácido, compuesta principalmente por celulosa y lignina."),
    ("Microscopía ICS", "Infinity Color-corrected System; diseño óptico corregido al infinito donde los rayos emergen paralelos del objetivo, permitiendo insertar módulos accesorios sin aberraciones."),
    ("Proteína Cruda / Bruta (PB)", "Estimación indirecta del contenido proteico de un alimento a partir de su nitrógeno total (% N) multiplicado por un factor empírico convencional (usualmente 6.25)."),
    ("Viscosidad Dinámica", "Medida cuantitativa de la resistencia interna de un fluido a fluir o deformarse bajo una tensión cortante aplicada, expresada en centipoise (cP) o mPa·s.")
]

REFS = [
    "AOAC International. (2019). Official Methods of Analysis of AOAC INTERNATIONAL (21st ed.). Rockville, MD: AOAC International.",
    "AMETEK Brookfield. (2020). Operating Instructions: Model DV-E Viscometer with Digital Display (Manual No. M98-211-E0820). Middleboro, MA: AMETEK Brookfield.",
    "Carl Zeiss Microscopy GmbH. (2018). Operating Manual: Primo Star Laboratory Microscope (Order No. 415500-0001-000). Jena, Germany: Carl Zeiss.",
    "J.P. SELECTA S.A. (2021). Manual de Instrucciones: Unidad de Destilación Pro-Nitro A (Ref. 4002430 / Código 80164). Abrera, Barcelona, España.",
    "J.P. SELECTA S.A. (2019). Manual de Instrucciones: Analizador de Fibra DOSI-FIBER (Ref. 4000623 / Código 80145). Abrera, Barcelona, España.",
    "J.P. SELECTA S.A. (2020). Manual de Operación: Bomba de Vacío por Recirculación de Agua (Ref. 4001611). Abrera, Barcelona, España.",
    "Instituto Ecuatoriano de Normalización (INEN). (2013). NTE INEN 0516: Alimentos para animales. Determinación del contenido de nitrógeno y cálculo de proteína cruda. Quito, Ecuador.",
    "Instituto Ecuatoriano de Normalización (INEN). (2014). NTE INEN 0539: Alimentos para animales. Determinación de la fibra cruda. Quito, Ecuador.",
    "Instituto Ecuatoriano de Normalización (INEN). (2015). NTE INEN 1009: Miel de abeja. Determinación de la viscosidad mediante viscosímetro rotacional. Quito, Ecuador.",
    "International Organization for Standardization. (2005). ISO 6865: Animal feeding stuffs - Determination of crude fibre content - Method with intermediate filtration. Geneva, Switzerland.",
    "Van Soest, P. J., Robertson, J. B., & Lewis, B. A. (1991). Methods for dietary fiber, neutral detergent fiber, and nonstarch polysaccharides in relation to animal nutrition. Journal of Dairy Science, 74(10), 3583-3597."
]

# ==============================================================================
# 1. GENERACIÓN DEL MARKDOWN EXHAUSTIVO: manuales_bromatologia_uteq.md
# ==============================================================================

def generate_markdown():
    md_path = os.path.join(KB_DIR, "manuales_bromatologia_uteq.md")
    print(f"Generating exhaustive Markdown file at: {md_path}")
    
    lines = []
    lines.append("# Manual Técnico Oficial y Base de Conocimiento RAG")
    lines.append("## Laboratorio de Bromatología - Universidad Técnica Estatal de Quevedo (UTEQ)")
    lines.append("### Sistema Inteligente de Visión Artificial YOLO11 y Asistente RAG para Equipos de Laboratorio\n")
    lines.append("> **Documento Institucional de Referencia Académica y Metrológica**  ")
    lines.append("> **Facultad de Ciencias Pecuarias y Biológicas — Carreras de Ingeniería en Alimentos y Zootecnia**  ")
    lines.append("> *Versión Oficial 2.0.0 — Quevedo, Los Ríos, Ecuador (2026)*\n")
    lines.append("---\n")
    
    # Introducción
    lines.append("## 1. Introducción y Arquitectura del Sistema RAG\n")
    lines.append(
        "El presente manual técnico constituye la base de conocimiento especializada (Knowledge Base) del sistema de "
        "Generación Aumentada por Recuperación (RAG - *Retrieval-Augmented Generation*) integrado al Asistente Inteligente "
        "del Laboratorio de Bromatología de la UTEQ.\n\n"
        "La arquitectura del sistema vincula dos componentes tecnológicos de vanguardia:\n"
        "1. **Módulo de Visión Artificial (YOLO11)**: Detecta y clasifica en tiempo real sobre dispositivos móviles los equipos físicos "
        "presentes en el laboratorio a partir del feed de la cámara (30 FPS).\n"
        "2. **Pipeline RAG en Dispositivo (On-Device RAG)**: Al producirse una detección positiva con confianza suficiente, el sistema "
        "recupera los fragmentos documentales pertinentes de esta base de conocimiento indexada (procedimientos operativos, precauciones de "
        "bioseguridad, fórmulas de cálculo estequiométrico y guías de práctica de la UTEQ) y proporciona al estudiante o investigador "
        "asistencia técnica interactiva y precisa sin alucinaciones.\n\n"
        "A continuación se detallan exhaustivamente los **8 equipos oficiales** que conforman el catálogo instrumental del laboratorio."
    )
    lines.append("\n---\n")
    
    # Tabla resumen de equipos
    lines.append("## 2. Índice de Equipos Oficiales Documentados\n")
    lines.append("| N° | Clase YOLO11 | Nombre Oficial | Fabricante y Modelo | Función Primaria en Bromatología |")
    lines.append("|:--:|:---|:---|:---|:---|")
    for eq in EQUIPOS:
        lines.append(f"| **{eq['num']}** | `{eq['clase_yolo']}` | {eq['nombre_oficial']} | {eq['fabricante']} - {eq['modelo']} | {eq['ubicacion']} |")
    lines.append("\n---\n")
    
    # Capítulos por equipo
    for eq in EQUIPOS:
        lines.append(f"## {eq['num'] + 2}. Manual Técnico: {eq['nombre_oficial']}")
        lines.append(f"**Etiqueta de Clasificación YOLO11:** `{eq['clase_yolo']}`  ")
        lines.append(f"**Fabricante / Procedencia:** {eq['fabricante']}  ")
        lines.append(f"**Modelo Oficial:** {eq['modelo']}  ")
        lines.append(f"**Ubicación Física en Laboratorio:** {eq['ubicacion']}  ")
        lines.append(f"**Alimentación Eléctrica:** {eq['alimentacion']}  ")
        lines.append(f"**Requerimientos de Servicios:** {eq['servicios']}  ")
        lines.append(f"**Fotografía Oficial:** `images_opt/equipos_seleccionados/{eq['imagen']}`\n")
        lines.append(f"![{eq['nombre_oficial']}](../images_opt/equipos_seleccionados/{eq['imagen']})\n")
        
        # Principio
        lines.append(f"### {eq['num'] + 2}.1 Principio Físico-Químico y Fundamento Analítico\n")
        lines.append(eq['principio'] + "\n")
        
        # Reacciones
        lines.append(f"#### Ecuaciones Químicas y Fenómenos Involucrados\n")
        for r_tit, r_eq in eq['reacciones']:
            lines.append(f"- **{r_tit}**:\n  ```\n  {r_eq}\n  ```")
        lines.append("")
        
        # Cálculos
        lines.append(f"#### Fórmulas Matemáticas y Ecuaciones de Cálculo\n")
        for c_tit, c_eq in eq['calculos']:
            lines.append(f"**{c_tit}**:\n```\n{c_eq}\n```\n")
            
        # Componentes
        lines.append(f"### {eq['num'] + 2}.2 Arquitectura Instrumental y Componentes Críticos\n")
        for comp in eq['componentes']:
            lines.append(f"- **{comp.split(':')[0]}**: {comp.split(':')[1] if ':' in comp else comp}")
        lines.append("")
        
        # SOP
        lines.append(f"### {eq['num'] + 2}.3 Procedimiento Operativo Normalizado (PNO / SOP Paso a Paso)\n")
        for fase_tit, fase_desc in eq['sop']:
            lines.append(f"#### {fase_tit}")
            lines.append(f"{fase_desc}\n")
            
        # Matriz de EPP y Seguridad
        lines.append(f"### {eq['num'] + 2}.4 Matriz de Bioseguridad y EPP Obligatorio\n")
        lines.append("| Equipo de Protección (EPP) | Especificación Técnica y Función |")
        lines.append("|:---|:---|")
        for epp_nom, epp_esp in eq['epp']:
            lines.append(f"| **{epp_nom}** | {epp_esp} |")
        lines.append("")
        
        lines.append("#### Riesgos Críticos Identificados:\n")
        for r in eq['riesgos']:
            lines.append(f"- ⚠️ **Peligro:** {r}")
        lines.append("")
        
        # Guías UTEQ y Normas
        lines.append(f"### {eq['num'] + 2}.5 Guías de Práctica UTEQ y Normas Aplicables\n")
        lines.append("**Prácticas Oficiales de Laboratorio UTEQ:**")
        for g in eq['guias_uteq']:
            lines.append(f"- 📘 {g}")
        lines.append(f"\n**Normas Técnicas Internacionales y Nacionales:** `{eq['normas']}`\n")
        
        # Mantenimiento
        lines.append(f"### {eq['num'] + 2}.6 Plan de Mantenimiento Preventivo y Calibración\n")
        lines.append("| Frecuencia / Tipo | Protocolo de Inspección y Calibración |")
        lines.append("|:---|:---|")
        for m_frec, m_prot in eq['mantenimiento']:
            lines.append(f"| **{m_frec}** | {m_prot} |")
        lines.append("")
        
        # Troubleshooting
        lines.append(f"### {eq['num'] + 2}.7 Guía de Resolución de Fallos Comunes (Troubleshooting)\n")
        lines.append("| Síntoma / Problema Observado | Causa Raíz Probable | Acción Correctiva Inmediata |")
        lines.append("|:---|:---|:---|")
        for sint, acc in eq['troubleshooting']:
            lines.append(f"| ❌ {sint} | Desviación operativa o desgaste | ✔️ {acc} |")
        lines.append("\n---\n")
        
    # Glosario y referencias finales
    lines.append("## 11. Glosario de Términos Bromatológicos y Analíticos\n")
    for term, defn in GLOSARIO:
        lines.append(f"- **{term}**: {defn}")
        
    lines.append("\n---\n")
    lines.append("## 12. Referencias Bibliográficas y Normativas\n")
    for r in REFS:
        lines.append(f"- {r}")
        
    lines.append("\n\n*Catálogo oficial del proyecto **Asistente Inteligente de Laboratorio con Visión Artificial y RAG** — Universidad Técnica Estatal de Quevedo (UTEQ 2026).*")
    
    content = "\n".join(lines)
    with open(md_path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"Markdown successfully generated ({len(lines)} lines, {len(content)} bytes).")

# ==============================================================================
# 2. GENERACIÓN DEL DOCUMENTO WORD EXHAUSTIVO: .docx
# ==============================================================================

def set_cell_background(cell, fill_hex):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{fill_hex}"/>')
    tc_pr.append(shd)

def set_cell_margins(cell, top=100, bottom=100, left=150, right=150):
    tc_pr = cell._tc.get_or_add_tcPr()
    tcMar = parse_xml(f'<w:tcMar {nsdecls("w")}>'
                      f'<w:top w:w="{top}" w:type="dxa"/>'
                      f'<w:bottom w:w="{bottom}" w:type="dxa"/>'
                      f'<w:left w:w="{left}" w:type="dxa"/>'
                      f'<w:right w:w="{right}" w:type="dxa"/>'
                      f'</w:tcMar>')
    tc_pr.append(tcMar)

def add_callout(doc, title, text, bg_hex="EBF3FA", border_hex="1B365D"):
    tbl = doc.add_table(rows=1, cols=1)
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    tbl.autofit = False
    
    cell = tbl.cell(0, 0)
    cell.width = Inches(6.5)
    set_cell_background(cell, bg_hex)
    set_cell_margins(cell, top=140, bottom=140, left=200, right=200)
    
    tc_pr = cell._tc.get_or_add_tcPr()
    borders = parse_xml(f'<w:tcBorders {nsdecls("w")}>'
                        f'<w:top w:val="none"/>'
                        f'<w:left w:val="single" w:sz="24" w:space="0" w:color="{border_hex}"/>'
                        f'<w:bottom w:val="none"/>'
                        f'<w:right w:val="none"/>'
                        f'</w:tcBorders>')
    tc_pr.append(borders)
    
    p = cell.paragraphs[0]
    p.paragraph_format.space_before = Pt(3)
    p.paragraph_format.space_after = Pt(3)
    p.paragraph_format.line_spacing = 1.15
    run_t = p.add_run(f"📌 {title}: ")
    run_t.bold = True
    run_t.font.name = "Calibri"
    run_t.font.size = Pt(10)
    run_t.font.color.rgb = RGBColor(0x1B, 0x36, 0x5D) if border_hex == "1B365D" else RGBColor(0xA0, 0x1A, 0x1A)
    
    run_c = p.add_run(text)
    run_c.font.name = "Calibri"
    run_c.font.size = Pt(9.5)
    run_c.font.color.rgb = RGBColor(0x22, 0x22, 0x22)
    
    p_spacer = doc.add_paragraph()
    p_spacer.paragraph_format.space_before = Pt(0)
    p_spacer.paragraph_format.space_after = Pt(4)

def format_styled_table(table, col_widths, header_bg="1B365D"):
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False
    for i, row in enumerate(table.rows):
        is_header = (i == 0)
        for j, cell in enumerate(row.cells):
            cell.width = Inches(col_widths[j])
            cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
            set_cell_margins(cell, top=100, bottom=100, left=140, right=140)
            if is_header:
                set_cell_background(cell, header_bg)
                for p in cell.paragraphs:
                    p.paragraph_format.space_before = Pt(2)
                    p.paragraph_format.space_after = Pt(2)
                    for r in p.runs:
                        r.bold = True
                        r.font.name = "Calibri"
                        r.font.size = Pt(9.5)
                        r.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
            else:
                bg = "F7F9FB" if i % 2 == 1 else "FFFFFF"
                set_cell_background(cell, bg)
                for p in cell.paragraphs:
                    p.paragraph_format.space_before = Pt(2)
                    p.paragraph_format.space_after = Pt(2)
                    for r in p.runs:
                        r.font.name = "Calibri"
                        r.font.size = Pt(9.0)
                        r.font.color.rgb = RGBColor(0x22, 0x22, 0x22)
            # Bordes tenues
            tc_pr = cell._tc.get_or_add_tcPr()
            b = parse_xml(f'<w:tcBorders {nsdecls("w")}>'
                          f'<w:top w:val="single" w:sz="4" w:space="0" w:color="D0D7DE"/>'
                          f'<w:left w:val="single" w:sz="4" w:space="0" w:color="D0D7DE"/>'
                          f'<w:bottom w:val="single" w:sz="4" w:space="0" w:color="D0D7DE"/>'
                          f'<w:right w:val="single" w:sz="4" w:space="0" w:color="D0D7DE"/>'
                          f'</w:tcBorders>')
            tc_pr.append(b)

def generate_word_document():
    docx_path = os.path.join(KB_DIR, "Manual_Tecnico_Bromatologia_UTEQ_RAG.docx")
    print(f"Generating publication-quality Word document at: {docx_path}")
    
    doc = docx.Document()
    
    # Configurar márgenes estándar (1 pulgada = 2.54 cm)
    sections = doc.sections
    for section in sections:
        section.top_margin = Inches(1.0)
        section.bottom_margin = Inches(1.0)
        section.left_margin = Inches(1.0)
        section.right_margin = Inches(1.0)
        section.page_width = Inches(8.5)
        section.page_height = Inches(11.0)
        
    # Colores institucionales
    NAVY = RGBColor(0x1B, 0x36, 0x5D)
    TEAL = RGBColor(0x0E, 0x76, 0xA8)
    DARK = RGBColor(0x22, 0x22, 0x22)
    GRAY = RGBColor(0x55, 0x55, 0x55)
    
    # -------------------------------------------------------------
    # PORTADA UNIVERSITARIA INSTITUCIONAL
    # -------------------------------------------------------------
    p_uni = doc.add_paragraph()
    p_uni.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_uni.paragraph_format.space_before = Pt(36)
    p_uni.paragraph_format.space_after = Pt(4)
    run_u = p_uni.add_run("UNIVERSIDAD TÉCNICA ESTATAL DE QUEVEDO")
    run_u.bold = True
    run_u.font.name = "Calibri"
    run_u.font.size = Pt(17)
    run_u.font.color.rgb = NAVY
    
    p_fac = doc.add_paragraph()
    p_fac.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_fac.paragraph_format.space_before = Pt(2)
    p_fac.paragraph_format.space_after = Pt(20)
    run_f = p_fac.add_run("FACULTAD DE CIENCIAS PECUARIAS Y BIOLÓGICAS\nCARRERA DE INGENIERÍA EN ALIMENTOS / ZOOTECNIA\nLABORATORIO DE BROMATOLOGÍA Y NUTRICIÓN ANIMAL")
    run_f.font.name = "Calibri"
    run_f.font.size = Pt(11)
    run_f.font.color.rgb = GRAY
    
    # Línea decorativa
    p_line = doc.add_paragraph()
    p_line.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_line.paragraph_format.space_after = Pt(30)
    r_line = p_line.add_run("―" * 45)
    r_line.font.color.rgb = TEAL
    
    # Título principal
    p_tit = doc.add_paragraph()
    p_tit.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_tit.paragraph_format.space_before = Pt(10)
    p_tit.paragraph_format.space_after = Pt(10)
    run_t = p_tit.add_run("MANUAL TÉCNICO OFICIAL Y BASE DE CONOCIMIENTO RAG")
    run_t.bold = True
    run_t.font.name = "Calibri"
    run_t.font.size = Pt(22)
    run_t.font.color.rgb = NAVY
    
    p_sub = doc.add_paragraph()
    p_sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_sub.paragraph_format.space_before = Pt(4)
    p_sub.paragraph_format.space_after = Pt(28)
    run_s = p_sub.add_run("Sistema Inteligente de Visión Artificial YOLO11 y Asistente RAG Multimodal\npara el Reconocimiento, Operación y Bioseguridad de Equipos Analíticos")
    run_s.font.name = "Calibri"
    run_s.font.size = Pt(12.5)
    run_s.font.color.rgb = TEAL
    
    # Cuadro informativo de portada
    tbl_portada = doc.add_table(rows=4, cols=2)
    tbl_portada.alignment = WD_TABLE_ALIGNMENT.CENTER
    datos_portada = [
        ("Proyecto de Investigación / Grado:", "Asistente Inteligente de Laboratorio con Visión Artificial y RAG"),
        ("Laboratorio Asignado:", "Laboratorio de Bromatología - Campus Central UTEQ"),
        ("Tecnologías Empleadas:", "YOLO11 Object Detection, On-Device RAG, Android Native (Kotlin)"),
        ("Fecha de Edición y Versión:", "Septiembre 2026 | Versión Oficial 2.0.0")
    ]
    for idx, (k, v) in enumerate(datos_portada):
        cell_k = tbl_portada.cell(idx, 0)
        cell_v = tbl_portada.cell(idx, 1)
        cell_k.width = Inches(2.5)
        cell_v.width = Inches(4.0)
        p_k = cell_k.paragraphs[0]
        p_v = cell_v.paragraphs[0]
        p_k.paragraph_format.space_before = Pt(3)
        p_k.paragraph_format.space_after = Pt(3)
        p_v.paragraph_format.space_before = Pt(3)
        p_v.paragraph_format.space_after = Pt(3)
        rk = p_k.add_run(k)
        rk.bold = True
        rk.font.name = "Calibri"
        rk.font.size = Pt(9.5)
        rk.font.color.rgb = NAVY
        rv = p_v.add_run(v)
        rv.font.name = "Calibri"
        rv.font.size = Pt(9.5)
        rv.font.color.rgb = DARK
        set_cell_background(cell_k, "F0F4F8")
        set_cell_background(cell_v, "FFFFFF")
        set_cell_margins(cell_k, top=60, bottom=60, left=100, right=100)
        set_cell_margins(cell_v, top=60, bottom=60, left=100, right=100)
        
    p_loc = doc.add_paragraph()
    p_loc.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_loc.paragraph_format.space_before = Pt(50)
    run_loc = p_loc.add_run("Quevedo - Los Ríos - Ecuador\n2026")
    run_loc.font.name = "Calibri"
    run_loc.font.size = Pt(10)
    run_loc.font.color.rgb = GRAY
    
    doc.add_page_break()
    
    # -------------------------------------------------------------
    # CAPÍTULO 1: INTRODUCCIÓN Y ARQUITECTURA
    # -------------------------------------------------------------
    h1 = doc.add_heading(level=1)
    run_h1 = h1.add_run("1. Introducción y Arquitectura del Sistema RAG")
    run_h1.font.name = "Calibri"
    run_h1.font.size = Pt(16)
    run_h1.font.color.rgb = NAVY
    h1.paragraph_format.space_before = Pt(12)
    h1.paragraph_format.space_after = Pt(8)
    
    p_intro = doc.add_paragraph()
    p_intro.paragraph_format.line_spacing = 1.15
    p_intro.paragraph_format.space_after = Pt(8)
    p_intro.add_run(
        "El presente manual constituye el núcleo de la base de conocimiento especializada (Knowledge Base) integrada al "
        "asistente inteligente móvil desarrollado para el Laboratorio de Bromatología de la Universidad Técnica Estatal de Quevedo (UTEQ). "
        "En concordancia con los requerimientos académicos e institucionales, todos los proyectos del laboratorio comparten la misma "
        "arquitectura de inteligencia artificial, particularizándose en la colección de clases instrumentales, fotografías de entrenamiento "
        "y manuales operativos incorporados al sistema de Generación Aumentada por Recuperación (RAG).\n\n"
        "La solución tecnológica consta de dos subsistemas perfectamente integrados:"
    )
    
    doc.add_paragraph(
        "• Modelo de Visión Artificial (YOLO11): Inferencia embebida en dispositivo a través de TensorFlow Lite / PyTorch Mobile, "
        "capaz de detectar los equipos en tiempo real con alta velocidad (30 FPS), delimitando su caja contenedora (bounding box) "
        "y mitigando falsos positivos mediante algoritmos de supresión de solapamiento y filtrado de marco completo.\n"
        "• Pipeline RAG On-Device: Ante la detección de un equipo oficial, el motor RAG realiza una búsqueda vectorial y semántica en la "
        "base de datos estructurada, recuperando al instante los Procedimientos Operativos Normalizados (SOP), precauciones de bioseguridad, "
        "matrices de EPP, ecuaciones estequiométricas y guías de prácticas oficiales de la UTEQ.",
        style='List Bullet'
    )
    
    add_callout(
        doc,
        "Principio de Arquitectura RAG en Bromatología",
        "El sistema no genera respuestas aleatorias; cada indicación técnica, parámetro físico o advertencia de bioseguridad proviene "
        "de fragmentos verificados de los manuales del fabricante (J.P. Selecta, Carl Zeiss, AMETEK Brookfield, Fisher Scientific) "
        "y de las normas oficiales vigentes (AOAC, NTE INEN, ISO)."
    )
    
    # Tabla resumen de equipos
    doc.add_heading("Catálogo de los 8 Equipos Oficiales Seleccionados", level=2)
    tbl_resumen = doc.add_table(rows=9, cols=4)
    tbl_resumen.cell(0, 0).paragraphs[0].text = "N°"
    tbl_resumen.cell(0, 1).paragraphs[0].text = "Clase YOLO11"
    tbl_resumen.cell(0, 2).paragraphs[0].text = "Nombre Oficial"
    tbl_resumen.cell(0, 3).paragraphs[0].text = "Fabricante y Modelo"
    
    for idx, eq in enumerate(EQUIPOS):
        tbl_resumen.cell(idx + 1, 0).paragraphs[0].text = str(eq['num'])
        tbl_resumen.cell(idx + 1, 1).paragraphs[0].text = eq['clase_yolo']
        tbl_resumen.cell(idx + 1, 2).paragraphs[0].text = eq['nombre_oficial']
        tbl_resumen.cell(idx + 1, 3).paragraphs[0].text = f"{eq['fabricante']} - {eq['modelo']}"
        
    format_styled_table(tbl_resumen, [0.5, 2.0, 2.3, 1.7])
    
    doc.add_page_break()
    
    # -------------------------------------------------------------
    # CAPÍTULOS DETALLADOS POR EQUIPO (2 al 9)
    # -------------------------------------------------------------
    for eq in EQUIPOS:
        chap_num = eq['num'] + 1
        h_eq = doc.add_heading(level=1)
        r_heq = h_eq.add_run(f"{chap_num}. {eq['nombre_oficial']}")
        r_heq.font.name = "Calibri"
        r_heq.font.size = Pt(16)
        r_heq.font.color.rgb = NAVY
        h_eq.paragraph_format.space_before = Pt(14)
        h_eq.paragraph_format.space_after = Pt(6)
        
        # Ficha de identificación técnica
        tbl_id = doc.add_table(rows=6, cols=2)
        fichas_data = [
            ("Clase de Detección YOLO11:", eq['clase_yolo']),
            ("Fabricante y Procedencia:", eq['fabricante']),
            ("Modelo y Código de Catálogo:", eq['modelo']),
            ("Ubicación en Laboratorio:", eq['ubicacion']),
            ("Alimentación Eléctrica:", eq['alimentacion']),
            ("Requerimientos de Servicios:", eq['servicios'])
        ]
        for idx_f, (lbl, val) in enumerate(fichas_data):
            c0 = tbl_id.cell(idx_f, 0)
            c1 = tbl_id.cell(idx_f, 1)
            c0.width = Inches(2.2)
            c1.width = Inches(4.3)
            p0 = c0.paragraphs[0]
            p1 = c1.paragraphs[0]
            p0.paragraph_format.space_before = Pt(2)
            p0.paragraph_format.space_after = Pt(2)
            p1.paragraph_format.space_before = Pt(2)
            p1.paragraph_format.space_after = Pt(2)
            r0 = p0.add_run(lbl)
            r0.bold = True
            r0.font.name = "Calibri"
            r0.font.size = Pt(9.0)
            r0.font.color.rgb = NAVY
            r1 = p1.add_run(val)
            r1.font.name = "Calibri"
            r1.font.size = Pt(9.0)
            r1.font.color.rgb = DARK
            set_cell_background(c0, "F0F4F8")
            set_cell_background(c1, "FFFFFF")
            set_cell_margins(c0, top=50, bottom=50, left=90, right=90)
            set_cell_margins(c1, top=50, bottom=50, left=90, right=90)
            
        p_sp = doc.add_paragraph()
        p_sp.paragraph_format.space_before = Pt(2)
        p_sp.paragraph_format.space_after = Pt(4)
        
        # Insertar imagen si existe
        img_file = os.path.join(IMAGES_DIR, eq['imagen'])
        if os.path.exists(img_file):
            p_img = doc.add_paragraph()
            p_img.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p_img.paragraph_format.space_before = Pt(4)
            p_img.paragraph_format.space_after = Pt(2)
            run_pic = p_img.add_run()
            run_pic.add_picture(img_file, width=Inches(3.6))
            
            p_cap = doc.add_paragraph()
            p_cap.alignment = WD_ALIGN_PARAGRAPH.CENTER
            p_cap.paragraph_format.space_after = Pt(8)
            r_cap = p_cap.add_run(f"Figura {eq['num']}: Fotografía oficial verificada — {eq['nombre_oficial']} ({eq['modelo']})")
            r_cap.font.name = "Calibri"
            r_cap.font.size = Pt(8.5)
            r_cap.font.italic = True
            r_cap.font.color.rgb = GRAY
        
        # 2.1 Principio Físico-Químico
        h2_princ = doc.add_heading(f"{chap_num}.1 Principio Físico-Químico y Fundamento Analítico", level=2)
        h2_princ.paragraph_format.space_before = Pt(8)
        h2_princ.paragraph_format.space_after = Pt(4)
        
        p_pr = doc.add_paragraph()
        p_pr.paragraph_format.line_spacing = 1.15
        p_pr.paragraph_format.space_after = Pt(6)
        p_pr.add_run(eq['principio'])
        
        # Reacciones
        p_rx_tit = doc.add_paragraph()
        p_rx_tit.paragraph_format.space_before = Pt(4)
        p_rx_tit.paragraph_format.space_after = Pt(2)
        r_rx = p_rx_tit.add_run("Mecanismo y Reacciones Químicas Involucradas:")
        r_rx.bold = True
        r_rx.font.name = "Calibri"
        r_rx.font.color.rgb = TEAL
        
        for r_tit, r_eq in eq['reacciones']:
            p_r = doc.add_paragraph(style='List Bullet')
            p_r.paragraph_format.space_before = Pt(1)
            p_r.paragraph_format.space_after = Pt(2)
            p_r.paragraph_format.line_spacing = 1.1
            rr_tit = p_r.add_run(f"{r_tit}: ")
            rr_tit.bold = True
            rr_tit.font.size = Pt(9.0)
            rr_eq = p_r.add_run(r_eq)
            rr_eq.font.name = "Consolas"
            rr_eq.font.size = Pt(8.5)
            rr_eq.font.color.rgb = RGBColor(0x1B, 0x36, 0x5D)
            
        # Cálculos en callout
        for c_tit, c_eq in eq['calculos']:
            add_callout(doc, f"Formulación Analítica: {c_tit}", c_eq, bg_hex="F0F7FA", border_hex="0E76A8")
            
        # 2.2 Arquitectura y Componentes
        h2_comp = doc.add_heading(f"{chap_num}.2 Arquitectura del Equipo y Componentes Críticos", level=2)
        h2_comp.paragraph_format.space_before = Pt(8)
        h2_comp.paragraph_format.space_after = Pt(4)
        
        for c in eq['componentes']:
            p_c = doc.add_paragraph(style='List Bullet')
            p_c.paragraph_format.space_before = Pt(1)
            p_c.paragraph_format.space_after = Pt(2)
            p_c.paragraph_format.line_spacing = 1.1
            r_c = p_c.add_run(c)
            r_c.font.name = "Calibri"
            r_c.font.size = Pt(9.5)
            
        # 2.3 Procedimiento Operativo Normalizado (PNO / SOP)
        h2_sop = doc.add_heading(f"{chap_num}.3 Procedimiento Operativo Normalizado (PNO / SOP Paso a Paso)", level=2)
        h2_sop.paragraph_format.space_before = Pt(8)
        h2_sop.paragraph_format.space_after = Pt(4)
        
        for f_tit, f_desc in eq['sop']:
            p_ft = doc.add_paragraph()
            p_ft.paragraph_format.space_before = Pt(4)
            p_ft.paragraph_format.space_after = Pt(1)
            r_ft = p_ft.add_run(f_tit)
            r_ft.bold = True
            r_ft.font.name = "Calibri"
            r_ft.font.size = Pt(10)
            r_ft.font.color.rgb = TEAL
            
            p_fd = doc.add_paragraph()
            p_fd.paragraph_format.space_before = Pt(1)
            p_fd.paragraph_format.space_after = Pt(4)
            p_fd.paragraph_format.line_spacing = 1.15
            r_fd = p_fd.add_run(f_desc)
            r_fd.font.name = "Calibri"
            r_fd.font.size = Pt(9.5)
            r_fd.font.color.rgb = DARK
            
        # 2.4 Bioseguridad y EPP
        h2_bio = doc.add_heading(f"{chap_num}.4 Matriz de Bioseguridad, EPP y Prevención de Riesgos", level=2)
        h2_bio.paragraph_format.space_before = Pt(8)
        h2_bio.paragraph_format.space_after = Pt(4)
        
        tbl_epp = doc.add_table(rows=len(eq['epp']) + 1, cols=2)
        tbl_epp.cell(0, 0).paragraphs[0].text = "Equipo de Protección Requerido (EPP)"
        tbl_epp.cell(0, 1).paragraphs[0].text = "Especificación Técnica y Justificación"
        for idx_e, (e_nom, e_esp) in enumerate(eq['epp']):
            tbl_epp.cell(idx_e + 1, 0).paragraphs[0].text = e_nom
            tbl_epp.cell(idx_e + 1, 1).paragraphs[0].text = e_esp
        format_styled_table(tbl_epp, [2.5, 4.0], header_bg="9E2A2B")
        
        p_sp2 = doc.add_paragraph()
        p_sp2.paragraph_format.space_before = Pt(2)
        p_sp2.paragraph_format.space_after = Pt(2)
        
        for rk in eq['riesgos']:
            add_callout(doc, "ALERTA CRÍTICA DE SEGURIDAD", rk, bg_hex="FDF2F2", border_hex="9E2A2B")
            
        # 2.5 Guías UTEQ y Normas
        h2_guias = doc.add_heading(f"{chap_num}.5 Guías de Práctica UTEQ y Métodos Normalizados", level=2)
        h2_guias.paragraph_format.space_before = Pt(8)
        h2_guias.paragraph_format.space_after = Pt(4)
        
        for g in eq['guias_uteq']:
            p_g = doc.add_paragraph(style='List Bullet')
            p_g.paragraph_format.space_before = Pt(1)
            p_g.paragraph_format.space_after = Pt(2)
            rg = p_g.add_run(g)
            rg.font.name = "Calibri"
            rg.font.size = Pt(9.5)
            
        p_norm = doc.add_paragraph()
        p_norm.paragraph_format.space_before = Pt(4)
        p_norm.paragraph_format.space_after = Pt(6)
        rn_lbl = p_norm.add_run("Normativa de Referencia Aplicable: ")
        rn_lbl.bold = True
        rn_lbl.font.color.rgb = NAVY
        rn_val = p_norm.add_run(eq['normas'])
        rn_val.font.italic = True
        
        # 2.6 Mantenimiento
        h2_mant = doc.add_heading(f"{chap_num}.6 Plan de Mantenimiento Preventivo y Calibración Metrológica", level=2)
        h2_mant.paragraph_format.space_before = Pt(8)
        h2_mant.paragraph_format.space_after = Pt(4)
        
        tbl_mant = doc.add_table(rows=len(eq['mantenimiento']) + 1, cols=2)
        tbl_mant.cell(0, 0).paragraphs[0].text = "Frecuencia / Nivel"
        tbl_mant.cell(0, 1).paragraphs[0].text = "Procedimiento de Inspección y Calibración"
        for idx_m, (m_frec, m_prot) in enumerate(eq['mantenimiento']):
            tbl_mant.cell(idx_m + 1, 0).paragraphs[0].text = m_frec
            tbl_mant.cell(idx_m + 1, 1).paragraphs[0].text = m_prot
        format_styled_table(tbl_mant, [2.0, 4.5], header_bg="1B365D")
        
        p_sp3 = doc.add_paragraph()
        p_sp3.paragraph_format.space_before = Pt(2)
        p_sp3.paragraph_format.space_after = Pt(2)
        
        # 2.7 Troubleshooting
        h2_trouble = doc.add_heading(f"{chap_num}.7 Guía de Resolución de Fallos Comunes (Troubleshooting)", level=2)
        h2_trouble.paragraph_format.space_before = Pt(8)
        h2_trouble.paragraph_format.space_after = Pt(4)
        
        tbl_tr = doc.add_table(rows=len(eq['troubleshooting']) + 1, cols=2)
        tbl_tr.cell(0, 0).paragraphs[0].text = "Problema / Síntoma Observado"
        tbl_tr.cell(0, 1).paragraphs[0].text = "Causa Probable y Acción Correctiva"
        for idx_t, (sint, acc) in enumerate(eq['troubleshooting']):
            tbl_tr.cell(idx_t + 1, 0).paragraphs[0].text = sint
            tbl_tr.cell(idx_t + 1, 1).paragraphs[0].text = acc
        format_styled_table(tbl_tr, [2.8, 3.7], header_bg="4A5568")
        
        doc.add_page_break()
        
    # -------------------------------------------------------------
    # CAPÍTULO 10: GLOSARIO Y REFERENCIAS
    # -------------------------------------------------------------
    doc.add_heading("10. Glosario de Términos Analíticos y Bromatológicos", level=1)
    for term, defn in GLOSARIO:
        p_glo = doc.add_paragraph()
        p_glo.paragraph_format.space_before = Pt(2)
        p_glo.paragraph_format.space_after = Pt(2)
        p_glo.paragraph_format.line_spacing = 1.15
        rg_t = p_glo.add_run(f"• {term}: ")
        rg_t.bold = True
        rg_t.font.name = "Calibri"
        rg_t.font.color.rgb = NAVY
        rg_d = p_glo.add_run(defn)
        rg_d.font.name = "Calibri"
        rg_d.font.size = Pt(9.5)
        
    doc.add_heading("11. Referencias Bibliográficas y Documentales", level=1)
    for r in REFS:
        p_ref = doc.add_paragraph()
        p_ref.paragraph_format.space_before = Pt(2)
        p_ref.paragraph_format.space_after = Pt(4)
        p_ref.paragraph_format.left_indent = Inches(0.4)
        p_ref.paragraph_format.first_line_indent = Inches(-0.4)
        r_run = p_ref.add_run(r)
        r_run.font.name = "Calibri"
        r_run.font.size = Pt(9.0)
        r_run.font.color.rgb = DARK
        
    doc.save(docx_path)
    file_size = os.path.getsize(docx_path)
    print(f"Word document successfully saved ({file_size} bytes).")

if __name__ == "__main__":
    generate_markdown()
    generate_word_document()
    print("ALL DOCUMENTATION GENERATED SUCCESSFULLY!")
