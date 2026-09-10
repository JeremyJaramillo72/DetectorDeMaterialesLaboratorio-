package com.uteq.software.detector_de_materiales_laboratorio.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.uteq.software.detector_de_materiales_laboratorio.databinding.BottomSheetEquipmentSelectorBinding
import com.uteq.software.detector_de_materiales_laboratorio.databinding.ItemEquipmentSelectorBinding
import java.io.Serializable
import java.util.Locale

/**
 * Panel emergente que lista los equipos detectados simultáneamente en pantalla
 * para permitir al usuario seleccionar cuál desea consultar.
 */
class EquipmentSelectorBottomSheet : BottomSheetDialogFragment() {

    data class SelectorEntry(
        val label: String,
        val displayName: String,
        val subtitle: String,
        val confidence: Float
    ) : Serializable

    private var _binding: BottomSheetEquipmentSelectorBinding? = null
    private val binding get() = _binding!!

    private var entries: List<SelectorEntry> = emptyList()

    var onEquipmentSelected: ((label: String) -> Unit)? = null

    companion object {
        private const val ARG_ENTRIES = "arg_entries"

        fun newInstance(entries: List<SelectorEntry>): EquipmentSelectorBottomSheet {
            val fragment = EquipmentSelectorBottomSheet()
            val args = Bundle().apply {
                putSerializable(ARG_ENTRIES, ArrayList(entries))
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            @Suppress("DEPRECATION", "UNCHECKED_CAST")
            val list = it.getSerializable(ARG_ENTRIES) as? ArrayList<SelectorEntry>
            if (list != null) {
                entries = list
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetEquipmentSelectorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvEquipmentList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvEquipmentList.adapter = EquipmentSelectorAdapter(entries) { selectedEntry ->
            dismiss()
            onEquipmentSelected?.invoke(selectedEntry.label)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class EquipmentSelectorAdapter(
        private val items: List<SelectorEntry>,
        private val onItemClick: (SelectorEntry) -> Unit
    ) : RecyclerView.Adapter<EquipmentSelectorAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemEquipmentSelectorBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(
            private val binding: ItemEquipmentSelectorBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: SelectorEntry) {
                binding.tvSelectorItemName.text = item.displayName
                val percentText = String.format(Locale.getDefault(), "%.1f %%", item.confidence * 100f)
                val detailText = if (item.subtitle.isNotBlank()) {
                    "${item.subtitle} · $percentText"
                } else {
                    "Confianza: $percentText"
                }
                binding.tvSelectorItemDetails.text = detailText

                binding.root.setOnClickListener {
                    onItemClick(item)
                }
            }
        }
    }
}
