package com.example.chatai

data class ChatMessage (
    val message: String = "",
    val isUser: Boolean = false,
    val isTyping: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()

){
    companion object{
        fun createUserMessage(message: String) = ChatMessage(
            message = message,
            isUser = true
        )

        fun createAiMessage(message: String) = ChatMessage(
            message = message,
            isUser = false
        )


        fun createTypingMessage() = ChatMessage(


            isTyping = true,
            isUser = false
        )
    }
}