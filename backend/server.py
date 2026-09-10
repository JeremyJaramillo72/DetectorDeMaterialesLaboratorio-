import os
import json
import re
from typing import List, Optional, Dict, Any
from pathlib import Path
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import uvicorn
from dotenv import load_dotenv

load_dotenv()

app = FastAPI(
    title="UTEQ Bromatología RAG API",
    description="Servidor RAG para asistencia inteligente sobre equipos del Laboratorio de Bromatología de la UTEQ",
    version="1.0.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Cargar Base de Conocimiento
KB_PATH = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "manuales_bromatologia_uteq.json"
if not KB_PATH.exists():
    KB_PATH = Path(__file__).resolve().parent.parent / "knowledge_base" / "manuales_bromatologia_uteq.json"

knowledge_db: Dict[str, Any] = {}
if KB_PATH.exists():
    with open(KB_PATH, "r", encoding="utf-8") as f:
        knowledge_db = json.load(f)
    print(f"✅ Base de conocimiento cargada con {len(knowledge_db.get('equipos', []))} equipos.")
else:
    print(f"⚠️ No se encontró la base de conocimiento en {KB_PATH}")

# Modelos Pydantic para Requests / Responses
class ChatRequest(BaseModel):
    message: str
    equipment_id: Optional[str] = None
    conversation_history: Optional[List[Dict[str, str]]] = []

class Citation(BaseModel):
    title: str
    section: Optional[str] = None

class ChatResponse(BaseModel):
    response: str
    equipment_id: Optional[str] = None
    citations: List[str] = []
    epp_required: List[str] = []
    risks: List[str] = []

def number_to_spanish(n: int) -> str:
    if n == 0:
        return "cero"
    units = ["", "un", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
             "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete",
             "dieciocho", "diecinueve", "veinte", "veintiún", "veintidós", "veintitrés",
             "veinticuatro", "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve"]
    tens = ["", "", "", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa"]
    hundreds = ["", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos",
                "seiscientos", "setecientos", "ochocientos", "novecientos"]
                
    def convert_under_thousand(num: int) -> str:
        if num == 0:
            return ""
        if num == 100:
            return "cien"
        res = []
        if num >= 100:
            res.append(hundreds[num // 100])
            num %= 100
        if num < 30:
            if num > 0:
                res.append(units[num])
        else:
            t = tens[num // 10]
            u = num % 10
            if u > 0:
                res.append(f"{t} y {units[u]}")
            else:
                res.append(t)
        return " ".join(res).strip()

    if n < 1000:
        return convert_under_thousand(n)
    if n < 1000000:
        thousands = n // 1000
        remainder = n % 1000
        t_str = "mil" if thousands == 1 else f"{convert_under_thousand(thousands)} mil"
        if remainder > 0:
            return f"{t_str} {convert_under_thousand(remainder)}".strip()
        return t_str
    if n < 2000000:
        remainder = n % 1000000
        return f"un millón {number_to_spanish(remainder)}".strip() if remainder > 0 else "un millón"
    millions = n // 1000000
    remainder = n % 1000000
    m_str = f"{convert_under_thousand(millions)} millones"
    return f"{m_str} {number_to_spanish(remainder)}".strip() if remainder > 0 else m_str

def parse_price_value(s: str):
    clean = s.strip().replace("$", "").replace("USD", "").replace("usd", "").strip()
    if "," in clean and "." in clean:
        comma_idx = clean.find(",")
        dot_idx = clean.find(".")
        if comma_idx < dot_idx:
            parts = clean.split(".")
            integ = int(parts[0].replace(",", ""))
            cents = int(parts[1][:2].ljust(2, "0")) if len(parts) > 1 and parts[1] else 0
            return integ, cents
        else:
            parts = clean.split(",")
            integ = int(parts[0].replace(".", ""))
            cents = int(parts[1][:2].ljust(2, "0")) if len(parts) > 1 and parts[1] else 0
            return integ, cents
    if "," in clean:
        parts = clean.split(",")
        if len(parts) == 2:
            if len(parts[1]) == 3:
                return int(parts[0] + parts[1]), 0
            elif len(parts[1]) in (1, 2):
                return int(parts[0]), int(parts[1][:2].ljust(2, "0"))
        return int(clean.replace(",", "")), 0
    if "." in clean:
        parts = clean.split(".")
        if len(parts) == 2:
            if len(parts[1]) == 3 and len(parts[0]) in (1, 2, 3):
                return int(parts[0] + parts[1]), 0
            elif len(parts[1]) in (1, 2):
                return int(parts[0]), int(parts[1][:2].ljust(2, "0"))
        return int(clean.replace(".", "")), 0
    digits = ''.join(c for c in clean if c.isdigit())
    return int(digits) if digits else 0, 0

def format_price_phrase(s: str) -> str:
    integ, cents = parse_price_value(s)
    words = number_to_spanish(integ)
    curr = "dólar" if integ == 1 else "dólares"
    if cents > 0:
        cents_w = number_to_spanish(cents)
        c_label = "centavo" if cents == 1 else "centavos"
        return f"{words} {curr} con {cents_w} {c_label}"
    return f"{words} {curr}"

def format_price_for_speech(raw_price: str) -> str:
    if not raw_price or "no especificado" in raw_price.lower():
        return "precio no especificado en la ficha técnica"
    range_regex = re.compile(r'(?i)\$?\s*([\d,.]+)\s*USD?\s*\(rango\s*estimado:?\s*\$?\s*([\d,.]+)\s*[-–—]\s*\$?\s*([\d,.]+)\s*USD?\)')
    m = range_regex.search(raw_price.strip())
    if m:
        base_p = format_price_phrase(m.group(1))
        min_integ, min_c = parse_price_value(m.group(2))
        max_integ, max_c = parse_price_value(m.group(3))
        if min_c == 0 and max_c == 0:
            range_p = f"con un rango estimado de {number_to_spanish(min_integ)} a {number_to_spanish(max_integ)} dólares"
        else:
            range_p = f"con un rango estimado de {format_price_phrase(m.group(2))} a {format_price_phrase(m.group(3))}"
        return f"cuesta {base_p}, {range_p}"
    simple_regex = re.compile(r'(?i)\$?\s*([\d,.]+)\s*USD?')
    ms = simple_regex.search(raw_price.strip())
    if ms:
        return f"cuesta {format_price_phrase(ms.group(1))}"
    return f"cuesta {format_price_phrase(raw_price)}"

# Indexación y Búsqueda Semántica / BM25
def find_relevant_equipment(query: str, target_id: Optional[str] = None) -> Optional[Dict[str, Any]]:
    equipos = knowledge_db.get("equipos", [])
    if not equipos:
        return None
    
    if target_id:
        for eq in equipos:
            if eq.get("id") == target_id or eq.get("clase_yolo") == target_id:
                return eq
    
    # Búsqueda por palabras clave en la consulta
    query_lower = query.lower()
    best_match = None
    max_score = 0
    
    for eq in equipos:
        score = 0
        name = eq.get("nombre_comun", "").lower()
        official = eq.get("nombre_oficial", "").lower()
        model = eq.get("modelo", "").lower()
        brand = eq.get("fabricante", "").lower()
        yolo_id = eq.get("clase_yolo", "").lower()
        
        words = re.findall(r"\w+", query_lower)
        for w in words:
            if len(w) < 3:
                continue
            if w in name:
                score += 3
            if w in official:
                score += 3
            if w in model or w in brand:
                score += 2
            if w in yolo_id:
                score += 4
            if w in eq.get("funcion_principal", "").lower():
                score += 1
                
        if score > max_score:
            max_score = score
            best_match = eq
            
    return best_match if max_score >= 3 else None

def generate_rag_answer(user_msg: str, equipment: Optional[Dict[str, Any]]) -> ChatResponse:
    gemini_key = os.getenv("GEMINI_API_KEY")
    
    citations = equipment.get("fuentes_referencias", []) if equipment else []
    epp = equipment.get("epp_requerido", []) if equipment else []
    risks = equipment.get("riesgos_asociados", []) if equipment else []
    
    context_text = ""
    if equipment:
        context_text = f"""
        EQUIPO IDENTIFICADO EN EL CATÁLOGO UTEQ:
        Nombre: {equipment.get('nombre_oficial')} ({equipment.get('nombre_comun')})
        Fabricante/Modelo: {equipment.get('fabricante')} - {equipment.get('modelo')}
        Precio Comercial Estimado: {equipment.get('precio_aproximado', 'No especificado')}
        Ubicación: {equipment.get('ubicacion')}
        Función Principal: {equipment.get('funcion_principal')}
        Principio de Funcionamiento: {equipment.get('principio_funcionamiento')}
        Componentes: {', '.join(equipment.get('componentes_principales', []))}
        Guías de Práctica UTEQ: {'; '.join(equipment.get('guias_practica_uteq', []))}
        Procedimiento Operativo: {' | '.join(equipment.get('procedimiento_operativo_estandar', []))}
        EPP Requerido: {', '.join(equipment.get('epp_requerido', []))}
        Riesgos Asociados: {', '.join(equipment.get('riesgos_asociados', []))}
        Normas de Bioseguridad: {'; '.join(equipment.get('normas_seguridad', []))}
        Fuentes / Normas: {'; '.join(equipment.get('fuentes_referencias', []))}
        """

    # Si hay API Key de Gemini configurada
    if gemini_key:
        try:
            import google.generativeai as genai
            genai.configure(api_key=gemini_key)
            
            try:
                # Intentar habilitar herramienta de búsqueda web en Google
                model = genai.GenerativeModel("gemini-1.5-flash", tools='google_search_retrieval')
            except Exception:
                model = genai.GenerativeModel("gemini-1.5-flash")
            
            context_block = context_text if equipment else "No se identificó un equipo particular del inventario local de Bromatología UTEQ."
            prompt = f"""
            Eres el Asistente Experto en Bromatología y Ciencias de Laboratorio de la Universidad Técnica Estatal de Quevedo (UTEQ).
            
            {context_block}
            
            CONSULTA DEL USUARIO: {user_msg}
            
            INSTRUCCIONES:
            1. Si la consulta corresponde a un equipo del inventario local UTEQ provisto arriba, prioriza la información institucional oficial (función, EPP, riesgos, procedimiento).
            2. REGLA ESTRICTA DE PRECIO: Si preguntan por el precio o costo, toma exactamente el valor de la ficha técnica diciendo 'cuesta [monto] dólares...', sin usar emojis (como 💰) ni signos como $, asteriscos o paréntesis para que la pronunciación por voz sea limpia y exacta.
            3. Si la consulta involucra reactivos, cálculos de concentración, normas internacionales (AOAC, Codex, ISO), o equipos externos no presentes en la base local, utiliza búsqueda en internet y tu conocimiento científico para responder con exactitud. Si es un equipo no disponible físicamente en la sede, explícalo con naturalidad técnica.
            4. Sé claro, riguroso, profesional y conciso (aprox. 80-140 palabras).
            5. Si aplica, menciona las fuentes o normas técnicas correspondientes.
            """
            response = model.generate_content(prompt)
            return ChatResponse(
                response=response.text,
                equipment_id=equipment.get("id") if equipment else None,
                citations=citations,
                epp_required=epp,
                risks=risks
            )
        except Exception as e:
            print(f"Error llamando a Gemini: {e}")
            
    # Respuesta local estructurada (Fallback Offline)
    if not equipment:
        return ChatResponse(
            response="Servidor en modo local sin clave de IA: La consulta no coincide con ningún equipo del catálogo local de Bromatología UTEQ. Para habilitar respuestas abiertas y búsqueda en internet, añade GEMINI_API_KEY en el archivo backend/.env.",
            equipment_id=None,
            citations=[],
            epp_required=[],
            risks=[]
        )

    q = user_msg.lower()
    resp_lines = []
    
    resp_lines.append(f"🔬 **{equipment.get('nombre_oficial')}** ({equipment.get('fabricante')} {equipment.get('modelo')})\n")
    
    if any(k in q for k in ["epp", "proteccion", "seguridad", "peligro", "riesgo", "cuidado"]):
        resp_lines.append("🦺 **Elementos de Protección Personal (EPP) Obligatorios:**")
        for item in epp:
            resp_lines.append(f"- {item}")
        resp_lines.append("\n⚠️ **Riesgos Asociados y Normas de Bioseguridad:**")
        for r in risks:
            resp_lines.append(f"- {r}")
        for n in equipment.get("normas_seguridad", []):
            resp_lines.append(f"  • *Norma*: {n}")
            
    elif any(k in q for k in ["paso", "procedimiento", "como usar", "como funciona", "operar", "practica", "ensayo"]):
        resp_lines.append("📋 **Procedimiento Operativo Estándar:**")
        for step in equipment.get("procedimiento_operativo_estandar", []):
            resp_lines.append(f"{step}")
        resp_lines.append("\n🧪 **Guías de Práctica UTEQ Relacionadas:**")
        for g in equipment.get("guias_practica_uteq", []):
            resp_lines.append(f"- {g}")
            
    elif any(k in q for k in ["componente", "parte", "pieza", "estructura"]):
        resp_lines.append("⚙️ **Componentes Principales:**")
        for comp in equipment.get("componentes_principales", []):
            resp_lines.append(f"- {comp}")

    elif any(k in q for k in ["precio", "costo", "cuesta", "vale", "valor", "cotizacion", "cuanto"]):
        raw_price = equipment.get('precio_aproximado')
        if raw_price:
            resp_lines.append(f"El equipo {format_price_for_speech(raw_price)} según la ficha técnica oficial.\n")
        else:
            resp_lines.append("El precio no está especificado en la ficha técnica oficial.\n")
            
    else:
        resp_lines.append(f"**Función en Bromatología:**\n{equipment.get('funcion_principal')}\n")
        resp_lines.append(f"**Principio de Funcionamiento:**\n{equipment.get('principio_funcionamiento')}\n")
        resp_lines.append("📋 **Guías Académicas UTEQ:**")
        for g in equipment.get("guias_practica_uteq", []):
            resp_lines.append(f"- {g}")
            
    resp_lines.append("\n📚 **Fuentes y Normas Oficiales:**")
    for src in citations:
        resp_lines.append(f"- {src}")
        
    return ChatResponse(
        response="\n".join(resp_lines),
        equipment_id=equipment.get("id"),
        citations=citations,
        epp_required=epp,
        risks=risks
    )

# Endpoints de la API REST
@app.get("/")
def read_root():
    return {"message": "UTEQ Bromatología RAG API Activa", "status": "online", "equipos_total": len(knowledge_db.get("equipos", []))}

@app.get("/api/equipment")
def get_all_equipment():
    return knowledge_db.get("equipos", [])

@app.get("/api/equipment/{equipment_id}")
def get_equipment_detail(equipment_id: str):
    equipos = knowledge_db.get("equipos", [])
    for eq in equipos:
        if eq.get("id") == equipment_id or eq.get("clase_yolo") == equipment_id:
            return eq
    raise HTTPException(status_code=404, detail="Equipo no encontrado")

@app.post("/api/chat", response_model=ChatResponse)
def chat_endpoint(req: ChatRequest):
    eq = find_relevant_equipment(req.message, req.equipment_id)
    return generate_rag_answer(req.message, eq)

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
