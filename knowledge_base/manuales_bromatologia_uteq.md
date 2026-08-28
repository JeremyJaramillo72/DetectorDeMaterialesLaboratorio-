# Manuales Técnicos y Base de Conocimiento RAG: Laboratorio de Bromatología UTEQ

Este documento recopila la documentación técnica, científica y de bioseguridad para el sistema RAG del Laboratorio de Bromatología de la Universidad Técnica Estatal de Quevedo (UTEQ).

---

## 1. Unidad de Destilación Kjeldahl (Selecta Pro-Nitro A)
- **Clase YOLO**: `destilador_kjeldahl`
- **Fabricante / Modelo**: J.P. SELECTA S.A., Pro-Nitro A (Cat. 4002430)
- **Función**: Destilación por arrastre de vapor de amoníaco liberado tras digestión ácida para cuantificar nitrógeno total y proteína bruta.
- **Principio**: La muestra digerida con \(H_2SO_4\) se neutraliza con \(NaOH\) al 40%, liberando gas \(NH_3\), que es arrastrado por vapor de agua y recolectado en solución receptora de ácido bórico (\(H_3BO_3\)) con indicador mixto Tashiro, para su valoración con \(HCl\) 0.1 N.
- **Cálculo**:
  \[ \% N = \frac{(V_{\text{muestra}} - V_{\text{blanco}}) \times N_{\text{ácido}} \times 1.4007}{\text{Peso}_{\text{muestra}} \text{ (g)}} \]
  \[ \% \text{Proteína Cruda} = \% N \times 6.25 \]
- **Guía de Práctica UTEQ**: Práctica N° 03: Determinación de Proteína Cruda en Alimentos Balanceados (AOAC 984.13 / ISO 5983-2).
- **EPP Requerido**: Mandil antifluidos, gafas de seguridad con protección lateral, guantes de nitrilo/neopreno, calzado cerrado.
- **Riesgos y Bioseguridad**: Riesgo de quemaduras por vapor caliente (>100 °C) y contacto con \(NaOH\) al 40%. No operar con la puerta de seguridad (Safety Door) abierta.

---

## 2. Analizador de Fibra Dosi-Fiber (Selecta DOSI-FIBER)
- **Clase YOLO**: `analizador_fibra`
- **Fabricante / Modelo**: J.P. SELECTA S.A., DOSI-FIBER (Cat. 4000623)
- **Función**: Determinación cuantitativa de Fibra Cruda (Método Weende) y fracciones de fibra detergente (FND, FAD, Lignina) según Van Soest.
- **Principio**: Digestión secuencial en crisoles filtrantes con soluciones ácidas (\(H_2SO_4\) 0.255 N) y alcalinas (\(NaOH\) 0.313 N) a ebullición bajo reflujo con condensadores superiores, lavado con agua caliente y acetona, secado a 105 °C y calcinación a 550 °C.
- **Guía de Práctica UTEQ**: Práctica N° 05: Determinación de Fibra Cruda en Forrajes y Piensos (AOAC 962.09).
- **EPP Requerido**: Mandil de laboratorio, gafas protectoras, guantes resistentes a altas temperaturas y químicos, pinzas para crisoles.
- **Riesgos**: Quemaduras por contacto con columnas calientes a ebullición, vapores de acetona y soluciones calientes.

---

## 3. Campana de Extracción de Gases y Humos (Labconco)
- **Clase YOLO**: `campana_extraccion_gases`
- **Fabricante / Modelo**: Labconco Corporation, Protector Premier Fume Hood
- **Función**: Captura, contención y evacuación de vapores tóxicos, aerosoles de ácidos minerales (\(H_2SO_4\), \(HNO_3\), \(HCl\)) y disolventes volátiles.
- **Principio**: Extractor centrífugo que genera velocidad frontal de captación (0.5 m/s) conduciendo los humos fuera de la zona de respiración del analista.
- **Normas de Seguridad**: Trabajar a mínimo 15 cm dentro de la campana, mantener la guillotina en la marca de seguridad (máx. 30 cm) y no usar como almacén de reactivos.

---

## 4. Estufa / Horno de Secado (Memmert)
- **Clase YOLO**: `estufa_secado_memmert`
- **Fabricante / Modelo**: Memmert GmbH, Universal Oven UN55 / UN110
- **Función**: Determinación gravimétrica de humedad y materia seca (MS) a 105 °C ± 2 °C y secado de material de vidrio.
- **Guía de Práctica UTEQ**: Práctica N° 01: Determinación de Humedad y Materia Seca Total (AOAC 930.15 / NTE INEN 461).
- **Cálculo**:
  \[ \% \text{Humedad} = \frac{P_1 - (P_2 - P_0)}{P_1} \times 100 \]
  \[ \% \text{Materia Seca (MS)} = 100 - \% \text{Humedad} \]
- **EPP y Riesgos**: Guantes térmicos aislantes, pinzas metálicas. Peligro de quemaduras térmicas. Prohibido meter solventes inflamables (éter).

---

## 5. Molino Ciclónico de Muestras (FOSS Cyclotec 1093)
- **Clase YOLO**: `molino_ciclonico_foss`
- **Fabricante / Modelo**: FOSS Analytical A/S, Cyclotec 1093 Sample Mill
- **Función**: Molienda analítica rápida y homogénea de granos, semillas y forrajes secos a través de cribas calibradas (0.5 mm o 1.0 mm) sin pérdida de humedad.
- **Componentes**: Rotor de alta velocidad (10,000 rpm), cámara de separación ciclónica por flujo de aire, frasco colector acoplable.
- **EPP y Riesgos**: Gafas de seguridad contra polvo, mascarilla N95 contra partículas, protección auditiva. No introducir muestras con humedad > 15% ni metales/piedras.

---

## 6. Refractómetro Abbe Digital (ATAGO)
- **Clase YOLO**: `refractometro_atago`
- **Fabricante / Modelo**: ATAGO CO., LTD., Abbe Refractometer con Termómetro Digital
- **Función**: Medición del Índice de Refracción (\(n_D\)) y sólidos solubles totales (°Brix) en alimentos líquidos (miel, jugos, néctares, aceites, lácteos).
- **Guía de Práctica UTEQ**: Práctica N° 08: Sólidos Solubles en Frutas y Conservas (AOAC 932.12 / NTE INEN 380).
- **Cuidado del Equipo**: Calibrar con agua destilada a 20 °C (0.0 °Brix). Limpiar siempre con papel para lentes (lens paper) y etanol al 70%. Nunca usar espátulas metálicas sobre el prisma.

---

## 7. Bomba Calorimétrica / Calorímetro de Oxígeno (Parr)
- **Clase YOLO**: `calorimetro_bomba`
- **Fabricante / Modelo**: Parr Instrument Company, Parr 1341 Plain Jacket / 6775 Digital Thermometer
- **Función**: Determinación del Valor Energético Bruto (EB) / poder calorífico superior en alimentos balanceados y materias primas mediante combustión en bomba sellada con oxígeno puro a 30 atm.
- **Guía de Práctica UTEQ**: Práctica N° 10: Determinación de Energía Bruta (ISO 9831 / ASTM D5865).
- **EPP y Seguridad Crítica**: Pantalla facial, gafas de alto impacto. Prohibido el uso de grasas o lubricantes en conexiones de oxígeno. Presión máxima de carga: 30 atm.

---

## 8. Cabina de Flujo Laminar / UV PCR Workstation (UVP)
- **Clase YOLO**: `cabina_flujo_laminar_uvp`
- **Fabricante / Modelo**: Analytik Jena US LLC (UVP LLC), UV PCR Workstation
- **Función**: Recinto estéril libre de contaminación microbiológica y nucleasas mediante luz UV-C germicida (254 nm) y recirculación de aire filtrado.
- **Guía de Práctica UTEQ**: Práctica N° 11: Siembra y Recuento Microbiológico en Alimentos (NTE INEN 1529).
- **Seguridad**: No mirar directamente a la lámpara UV encendida ni trabajar con la luz UV activa (riesgo de fotoqueratitis y quemaduras dérmicas). Utilizar temporizador para ciclo previo de 20-30 min.

---

## 9. Sistema de Tratamiento y Desionización de Agua
- **Clase YOLO**: `sistema_tratamiento_agua`
- **Función**: Purificación de agua de red mediante prefiltración de sedimentos, carbón activado y resinas de intercambio iónico de lecho mixto para obtener agua desionizada Tipo II / Tipo III (\(>1.0\text{ M}\Omega\cdot\text{cm}\)).
- **Uso**: Preparación de reactivos, soluciones valoradas y blancos analíticos.

---

## 10. Destilador de Agua Metálico
- **Clase YOLO**: `destilador_agua`
- **Función**: Producción continua de agua destilada de alta pureza libre de sales e impurezas no volátiles mediante ebullición y condensación en serpentín de acero inoxidable.
- **Seguridad**: Verificar flujo de agua de refrigeración antes de encender las resistencias calefactoras.

---

## 11. Bomba de Recirculación de Vacío (Selecta)
- **Clase YOLO**: `bomba_vacio_recirculacion`
- **Fabricante / Modelo**: J.P. SELECTA S.A., Bomba 4001611
- **Función**: Generación de vacío continuo ecológico mediante eyectores Venturi y recirculación interna de agua para filtraciones al vacío (Büchner, Kitasato, Gooch).

---

## 12. Bomba de Vacío de Membrana Portátil
- **Clase YOLO**: `bomba_vacio_membrana`
- **Función**: Suministro de vacío seco y libre de aceite para microfiltración de reactivos, HPLC y desecadores al vacío.

---

## 13. Gradilla con Tubos de Digestión Kjeldahl
- **Clase YOLO**: `gradilla_tubos_kjeldahl`
- **Función**: Soporte, ordenamiento y transporte seguro de tubos macro Kjeldahl de vidrio de borosilicato 3.3 (250 ml) durante el pesado y digestión con ácido concentrado.

---

## 14. Piseta / Frasco Lavador de Reactivo (NaOH 0.1 N)
- **Clase YOLO**: `piseta_reactivo`
- **Función**: Dosificación controlada de solución alcalina valorada (\(NaOH\) 0.1 N) para valoraciones volumétricas y lavado de material en análisis de acidez.

---

## 15. Cilindro de Gas con Manómetro
- **Clase YOLO**: `cilindro_gas`
- **Función**: Almacenamiento seguro y regulación de gases presurizados a alta presión (150-200 bar) mediante manoreductor de dos etapas hacia equipos analíticos.
