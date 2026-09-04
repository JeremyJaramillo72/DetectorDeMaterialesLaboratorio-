package com.uteq.software.detector_de_materiales_laboratorio.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.uteq.software.detector_de_materiales_laboratorio.ChatActivity
import com.uteq.software.detector_de_materiales_laboratorio.databinding.BottomSheetEquipmentDetailBinding
import com.uteq.software.detector_de_materiales_laboratorio.model.EquipmentData

class EquipmentBottomSheetDialog : BottomSheetDialogFragment() {

    private var _binding: BottomSheetEquipmentDetailBinding? = null
    private val binding get() = _binding!!

    private var equipment: EquipmentData? = null

    companion object {
        private const val ARG_EQUIPMENT = "arg_equipment"

        fun newInstance(equipment: EquipmentData): EquipmentBottomSheetDialog {
            val fragment = EquipmentBottomSheetDialog()
            val args = Bundle().apply {
                putSerializable(ARG_EQUIPMENT, equipment)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            @Suppress("DEPRECATION")
            equipment = it.getSerializable(ARG_EQUIPMENT) as? EquipmentData
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetEquipmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val eq = equipment ?: return

        binding.tvEquipmentName.text = eq.nombreComun
        binding.tvEquipmentOfficial.text = "${eq.fabricante} • ${eq.modelo}"
        binding.tvFunction.text = eq.funcionPrincipal

        // EPP
        if (eq.eppRequerido.isNotEmpty()) {
            binding.tvEPP.text = eq.eppRequerido.joinToString("\n") { "• $it" }
        } else {
            binding.tvEPP.text = "• Mandil de laboratorio reglamentario"
        }

        // Riesgos
        if (eq.riesgosAsociados.isNotEmpty()) {
            binding.tvRisks.text = eq.riesgosAsociados.joinToString("\n") { "• $it" }
        } else {
            binding.tvRisks.text = "• Precaución general de laboratorio"
        }

        // Guías
        if (eq.guiasPracticaUteq.isNotEmpty()) {
            binding.tvPractices.text = eq.guiasPracticaUteq.joinToString("\n") { "• $it" }
        } else {
            binding.tvPractices.text = "• Guía de Prácticas de Bromatología UTEQ"
        }

        binding.btnConsultRAG.setOnClickListener {
            dismiss()
            val intent = Intent(requireContext(), ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_EQUIPMENT_ID, eq.id)
                putExtra(ChatActivity.EXTRA_EQUIPMENT_CLASS, eq.claseYolo)
                putExtra(ChatActivity.EXTRA_EQUIPMENT_NAME, eq.nombreComun)
                putExtra(ChatActivity.EXTRA_SCOPED_TO_EQUIPMENT, true)
            }
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
