package com.example.chatai
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.chatai.ChatAdapter
import com.example.chatai.ChatMessage
import com.example.chatai.OllamaClient
import com.example.chatai.R

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerViewMessages: RecyclerView
    private lateinit var editTextMessage: EditText
    private lateinit var buttonSend: ImageButton
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var ollamaClient: OllamaClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupRecyclerView()
        setupClickListeners()
        initializeOllama()
    }

    private fun initViews() {
        recyclerViewMessages = findViewById(R.id.recyclerViewMessages)
        editTextMessage = findViewById(R.id.editTextMessage)
        buttonSend = findViewById(R.id.buttonSend)
    }

    private fun setupRecyclerView() {
        val layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }

        recyclerViewMessages.layoutManager = layoutManager

        // Pass recyclerView reference to adapter for auto-scrolling
        chatAdapter = ChatAdapter(this, recyclerViewMessages)
        recyclerViewMessages.adapter = chatAdapter
    }

    private fun setupClickListeners() {
        buttonSend.setOnClickListener { sendMessage() }

        // Handle enter key press in EditText
        editTextMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else false
        }

        // Enable/disable send button based on text input
        editTextMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                buttonSend.isEnabled = !s.toString().trim().isEmpty()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun initializeOllama() {
        ollamaClient = OllamaClient()

        // Check connection to Ollama server
        ollamaClient.checkConnection { isConnected, message ->
            runOnUiThread {
                if (isConnected) {
                    Toast.makeText(this, "✅ Connected to Ollama", Toast.LENGTH_SHORT).show()
                    // Add welcome message
                    val welcomeMessage = ChatMessage(
                        message = "Hello! I'm running locally via Ollama. How can I help you today?",
                        isUser = false
                    )
                    chatAdapter.addMessage(welcomeMessage)
                } else {
                    Toast.makeText(this, "❌ $message", Toast.LENGTH_LONG).show()
                    showConnectionError(message)
                }
            }
        }
    }

    private fun showConnectionError(error: String) {
        AlertDialog.Builder(this)
            .setTitle("Ollama Connection Error")
            .setMessage("$error\n\nMake sure:\n• Ollama is installed and running\n• Server is accessible at the configured URL\n• Model is downloaded (run 'ollama pull llama2')")
            .setPositiveButton("Retry") { _, _ -> initializeOllama() }
            .setNegativeButton("Continue Offline", null)
            .show()
    }

    private fun sendMessage() {
        val messageText = editTextMessage.text.toString().trim()
        if (messageText.isEmpty()) return

        // Add user message to chat
        val userMessage = ChatMessage(message = messageText, isUser = true)
        chatAdapter.addMessage(userMessage)

        // Clear input
        editTextMessage.setText("")

        // Show typing indicator
        val typingMessage = ChatMessage(isTyping = true, isUser = false)
        chatAdapter.addMessage(typingMessage)

        // Hide keyboard
        hideKeyboard()

        // Send to Ollama
        sendToOllama(messageText)
    }

    private fun sendToOllama(message: String) {
        ollamaClient.sendMessage(
            message = message,
            callback = object : OllamaClient.ChatCallback {
                override fun onResponse(response: String) {
                    runOnUiThread {
                        // Final response after streaming ends
                    }
                }

                override fun onPartialResponse(partialResponse: String) {
                    runOnUiThread {
                        // Update chat UI with partial response
                    }
                }

                override fun onError(error: String) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                    }
                }
            },
            useStreaming = true,
            useChatApi = true  // 🔑 set to false if you want /api/generate
        )

    }

    private fun hideKeyboard() {
        currentFocus?.let { view ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear -> {
                clearChat()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun clearChat() {
        AlertDialog.Builder(this)
            .setTitle("Clear Chat")
            .setMessage("Are you sure you want to clear all messages?")
            .setPositiveButton("Clear") { _, _ ->
                chatAdapter.clearMessages()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAvailableModels() {
        ollamaClient.getAvailableModels { models ->
            runOnUiThread {
                if (models.isNotEmpty()) {
                    val modelArray = models.toTypedArray()
                    AlertDialog.Builder(this)
                        .setTitle("Available Models")
                        .setItems(modelArray) { _, which ->
                            val selectedModel = models[which]
                            Toast.makeText(this, "Selected: $selectedModel", Toast.LENGTH_SHORT).show()
                            // Here you could switch models if needed
                        }
                        .show()
                } else {
                    Toast.makeText(this, "No models found. Run 'ollama pull llama2'", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun testConnection() {
        ollamaClient.checkConnection { isConnected, message ->
            runOnUiThread {
                val icon = if (isConnected) "✅" else "❌"
                Toast.makeText(this, "$icon $message", Toast.LENGTH_SHORT).show()
            }
        }
    }
}