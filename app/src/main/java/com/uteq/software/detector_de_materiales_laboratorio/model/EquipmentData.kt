package com.uteq.software.detector_de_materiales_laboratorio.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class KnowledgeBaseRoot(
    @SerializedName("laboratorio") val laboratorio: String,
    @SerializedName("universidad") val universidad: String,
    @SerializedName("facultad") val facultad: String,
    @SerializedName("version") val version: String,
    @SerializedName("equipos") val equipos: List<EquipmentData>
) : Serializable

data class EquipmentData(
    @SerializedName("id") val id: String,
    @SerializedName("clase_yolo") val claseYolo: String,
    @SerializedName("nombre_comun") val nombreComun: String,
    @SerializedName("nombre_oficial") val nombreOficial: String,
    @SerializedName("fabricante") val fabricante: String,
    @SerializedName("modelo") val modelo: String,
    @SerializedName("ubicacion") val ubicacion: String,
    @SerializedName("funcion_principal") val funcionPrincipal: String,
    @SerializedName("principio_funcionamiento") val principioFuncionamiento: String,
    @SerializedName("componentes_principales") val componentesPrincipales: List<String> = emptyList(),
    @SerializedName("guias_practica_uteq") val guiasPracticaUteq: List<String> = emptyList(),
    @SerializedName("procedimiento_operativo_estandar") val procedimientoOperativoEstandar: List<String> = emptyList(),
    @SerializedName("epp_requerido") val eppRequerido: List<String> = emptyList(),
    @SerializedName("riesgos_asociados") val riesgosAsociados: List<String> = emptyList(),
    @SerializedName("normas_seguridad") val normasSeguridad: List<String> = emptyList(),
    @SerializedName("fuentes_referencias") val fuentesReferencias: List<String> = emptyList()
) : Serializable
