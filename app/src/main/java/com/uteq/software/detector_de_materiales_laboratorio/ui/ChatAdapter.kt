package com.uteq.software.detector_de_materiales_laboratorio.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.uteq.software.detector_de_materiales_laboratorio.databinding.ItemChatMessageBinding
import com.uteq.software.detector_de_materiales_laboratorio.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private val messages = ArrayList<ChatMessage>()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun setMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    inner class ChatViewHolder(private val binding: ItemChatMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(msg: ChatMessage) {
            val formattedTime = timeFormat.format(Date(msg.timestamp))

            if (msg.isBot) {
                binding.layoutBotMessage.visibility = View.VISIBLE
                binding.layoutUserMessage.visibility = View.GONE
                binding.tvBotMessage.text = msg.text
                binding.tvBotTime.text = formattedTime
            } else {
                binding.layoutBotMessage.visibility = View.GONE
                binding.layoutUserMessage.visibility = View.VISIBLE
                binding.tvUserMessage.text = msg.text
                binding.tvUserTime.text = formattedTime
            }
        }
    }
}
