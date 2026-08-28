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
            
    return best_match if max_score > 0 else (equipos[0] if equipos else None)

def generate_rag_answer(user_msg: str, equipment: Dict[str, Any]) -> ChatResponse:
    gemini_key = os.getenv("GEMINI_API_KEY")
    openai_key = os.getenv("OPENAI_API_KEY")
    
    context_text = f"""
    EQUIPO: {equipment.get('nombre_oficial')} ({equipment.get('nombre_comun')})
    FABRICANTE/MODELO: {equipment.get('fabricante')} - {equipment.get('modelo')}
    UBICACIÓN: {equipment.get('ubicacion')}
    FUNCIÓN PRINCIPAL: {equipment.get('funcion_principal')}
    PRINCIPIO DE FUNCIONAMIENTO: {equipment.get('principio_funcionamiento')}
    COMPONENTES: {', '.join(equipment.get('componentes_principales', []))}
    GUÍAS DE PRÁCTICA UTEQ: {'; '.join(equipment.get('guias_practica_uteq', []))}
    PROCEDIMIENTO OPERATIVO PASO A PASO: {' | '.join(equipment.get('procedimiento_operativo_estandar', []))}
    EPP OBLIGATORIO: {', '.join(equipment.get('epp_requerido', []))}
    RIESGOS ASOCIADOS: {', '.join(equipment.get('riesgos_asociados', []))}
    NORMAS DE BIOSEGURIDAD: {'; '.join(equipment.get('normas_seguridad', []))}
    CITAS BIBLIOGRÁFICAS / NORMAS: {'; '.join(equipment.get('fuentes_referencias', []))}
    """
    
    citations = equipment.get("fuentes_referencias", [])
    epp = equipment.get("epp_requerido", [])
    risks = equipment.get("riesgos_asociados", [])
    
    # Si hay API Key de Gemini configurada
    if gemini_key:
        try:
            import google.generativeai as genai
            genai.configure(api_key=gemini_key)
            model = genai.GenerativeModel("gemini-1.5-flash")
            
            prompt = f"""
            Eres el Asistente Experto en Bromatología y Bioseguridad del Laboratorio de la Universidad Técnica Estatal de Quevedo (UTEQ).
            Responde la siguiente consulta del estudiante basándote EXCLUSIVAMENTE en la siguiente ficha técnica y manual oficial del equipo:
            
            {context_text}
            
            PREGUNTA DEL ESTUDIANTE: {user_msg}
            
            Instrucciones para responder:
            1. Sé claro, profesional y académicamente riguroso.
            2. Si preguntan por procedimiento, lista los pasos ordenados.
            3. Si preguntan por seguridad o riesgos, menciona el EPP y las precauciones críticas.
            4. Al final de tu respuesta, añade siempre una sección "📚 Fuentes Oficiales Consultadas:" citando las normas y manuales del contexto.
            """
            response = model.generate_content(prompt)
            return ChatResponse(
                response=response.text,
                equipment_id=equipment.get("id"),
                citations=citations,
                epp_required=epp,
                risks=risks
            )
        except Exception as e:
            print(f"Error llamando a Gemini: {e}")
            
    # Respuesta local estructurada (Fallback Offline para máxima robustez)
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
    if not eq:
        raise HTTPException(status_code=404, detail="No se encontró información técnica para la consulta")
    return generate_rag_answer(req.message, eq)

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)
