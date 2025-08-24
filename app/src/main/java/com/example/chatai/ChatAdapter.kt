package com.example.chatai

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(private val context: Context, recyclerViewMessages: RecyclerView) : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]

        when {
            message.isTyping -> {
                // Show typing indicator
                holder.layoutUserMessage.visibility = View.GONE
                holder.layoutAiMessage.visibility = View.GONE
                holder.layoutTyping.visibility = View.VISIBLE
            }
            message.isUser -> {
                // Show user message
                holder.layoutUserMessage.visibility = View.VISIBLE
                holder.layoutAiMessage.visibility = View.GONE
                holder.layoutTyping.visibility = View.GONE
                holder.textUserMessage.text = message.message
            }
            else -> {
                // Show AI message
                holder.layoutUserMessage.visibility = View.GONE
                holder.layoutAiMessage.visibility = View.VISIBLE
                holder.layoutTyping.visibility = View.GONE
                holder.textAiMessage.text = message.message
            }
        }
    }

    override fun getItemCount(): Int = messages.size

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun updateLastMessage(newMessage: String) {
        if (messages.isNotEmpty() && !messages.last().isUser && !messages.last().isTyping) {
            val lastIndex = messages.size - 1
            messages[lastIndex] = messages[lastIndex].copy(message = newMessage)
            notifyItemChanged(lastIndex)
        }
    }

    fun removeTypingIndicator() {
        for (i in messages.indices.reversed()) {
            if (messages[i].isTyping) {
                messages.removeAt(i)
                notifyItemRemoved(i)
                break
            }
        }
    }

    fun clearMessages() {
        val size = messages.size
        messages.clear()
        notifyItemRangeRemoved(0, size)
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val layoutUserMessage: LinearLayout = itemView.findViewById(R.id.layoutUserMessage)
        val layoutAiMessage: LinearLayout = itemView.findViewById(R.id.layoutAiMessage)
        val layoutTyping: LinearLayout = itemView.findViewById(R.id.layoutTyping)
        val textUserMessage: TextView = itemView.findViewById(R.id.textUserMessage)
        val textAiMessage: TextView = itemView.findViewById(R.id.textAiMessage)
    }
}