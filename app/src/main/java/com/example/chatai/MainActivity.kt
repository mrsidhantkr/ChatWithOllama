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
import com.example.chatai.GeminiClient
import com.example.chatai.R

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerViewMessages: RecyclerView
    private lateinit var editTextMessage: EditText
    private lateinit var buttonSend: ImageButton
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var geminiClient: GeminiClient

    // Replace with your actual Gemini API key
    private val GEMINI_API_KEY = "AIzaSyACuEzK_2WIxfNCI3NVV9OcdFivJXsd1tI"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupRecyclerView()
        setupClickListeners()
        initializeGemini()
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

    private fun initializeGemini() {
        if (GEMINI_API_KEY == "YOUR_API_KEY_HERE") {
            showApiKeyDialog()
            return
        }

        geminiClient = GeminiClient(GEMINI_API_KEY)

        // Check connection to Gemini API
        geminiClient.checkConnection { isConnected, message ->
            runOnUiThread {
                if (isConnected) {
                    Toast.makeText(this, "✅ Connected to Gemini", Toast.LENGTH_SHORT).show()
                    // Add welcome message
                    val welcomeMessage = ChatMessage(
                        message = "Hello! I'm powered by Google's Gemini AI. How can I help you today?",
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

    private fun showApiKeyDialog() {
        AlertDialog.Builder(this)
            .setTitle("API Key Required")
            .setMessage("Please add your Gemini API key to the code.\n\n1. Go to Google AI Studio (aistudio.google.com)\n2. Create an API key\n3. Replace 'YOUR_API_KEY_HERE' in MainActivity.kt")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showConnectionError(error: String) {
        AlertDialog.Builder(this)
            .setTitle("Gemini Connection Error")
            .setMessage("$error\n\nMake sure:\n• You have a valid Gemini API key\n• You have internet connection\n• API key has proper permissions")
            .setPositiveButton("Retry") { _, _ -> initializeGemini() }
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

        // Send to Gemini
        sendToGemini(messageText)
    }

    private fun sendToGemini(message: String) {
        geminiClient.sendMessage(
            message = message,
            callback = object : GeminiClient.ChatCallback {
                override fun onResponse(response: String) {
                    runOnUiThread {
                        chatAdapter.removeTypingIndicator()
                        val aiMessage = ChatMessage(message = response, isUser = false)
                        chatAdapter.addMessage(aiMessage)
                    }
                }

                override fun onPartialResponse(partialResponse: String) {
                    // Gemini doesn't support streaming in this implementation
                    // But you could implement it for future use
                }

                override fun onError(error: String) {
                    runOnUiThread {
                        chatAdapter.removeTypingIndicator()
                        val errorMessage = ChatMessage(
                            message = "Sorry, I encountered an error: $error",
                            isUser = false
                        )
                        chatAdapter.addMessage(errorMessage)

                        // Show toast for network errors
                        Toast.makeText(this@MainActivity, error, Toast.LENGTH_LONG).show()
                    }
                }
            },
            useStreaming = false
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
            R.id.action_model_info -> {
                showModelInfo()
                true
            }
            R.id.action_connection -> {
                testConnection()
                true
            }
            R.id.action_api_usage -> {
                showApiUsageInfo()
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

    private fun showModelInfo() {
        geminiClient.getModelInfo { info ->
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("Model Information")
                    .setMessage(info)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun testConnection() {
        geminiClient.checkConnection { isConnected, message ->
            runOnUiThread {
                val icon = if (isConnected) "✅" else "❌"
                Toast.makeText(this, "$icon $message", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showApiUsageInfo() {
        AlertDialog.Builder(this)
            .setTitle("Gemini API - Free Tier")
            .setMessage("Free Tier Limits:\n• 1,500 requests per day\n• 15 requests per minute\n• 1 million tokens per minute\n\nUpgrade to paid plan for higher limits.")
            .setPositiveButton("OK", null)
            .show()
    }
}