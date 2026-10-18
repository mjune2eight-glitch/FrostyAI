package com.frostyai.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.util.Locale

class MainActivity : Activity() {

    private val bg = Color.rgb(10, 13, 20)
    private val blue = Color.rgb(105, 205, 255)
    private val white = Color.rgb(235, 242, 250)

    private lateinit var messages: LinearLayout
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView

    private val history = mutableListOf<JSONObject>()

    private var apiKey = ""
    private var endpoint =
        "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
    private var model = "gemini-2.5-flash"

    private val voiceRequest = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(18, 14, 18, 12)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "❄ FROSTY AI"
            textSize = 24f
            setTextColor(blue)
            typeface = Typeface.DEFAULT_BOLD
        }

        val settings = button("⚙", blue)
        settings.setOnClickListener { showSettings() }

        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(settings)
        root.addView(header)

        val subtitle = TextView(this).apply {
            text = "YOUR PERSONAL AI  •  READY TO HELP"
            textSize = 10f
            setTextColor(Color.GRAY)
            setPadding(2, 5, 0, 12)
        }
        root.addView(subtitle)

        val line = View(this).apply {
            setBackgroundColor(Color.rgb(40, 65, 85))
        }
        root.addView(line, LinearLayout.LayoutParams(-1, 1))

        scroll = ScrollView(this)
        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 12)
        }

        scroll.addView(messages)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(0, 0, 1f)
        )

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        input = EditText(this).apply {
            hint = "Message Frosty..."
            setHintTextColor(Color.GRAY)
            setTextColor(white)
            textSize = 15f
            setSingleLine(false)
            maxLines = 4
            setPadding(12, 10, 12, 10)
            setBackgroundColor(Color.rgb(25, 31, 42))
        }

        val mic = button("🎙", blue)
        mic.setOnClickListener { startVoice() }

        val send = button("➤", blue)
        send.setOnClickListener { sendMessage() }

        inputRow.addView(
            input,
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        inputRow.addView(mic)
        inputRow.addView(send)

        root.addView(inputRow)

        setContentView(root)

        addMessage(
            "Frosty",
            "Hey! I'm Frosty AI. ❄\n\n" +
                "I'm ready. Ask me anything, or set up your AI key in ⚙."
        )
    }

    private fun button(
        text: String,
        color: Int
    ): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(color)
            textSize = 17f
            isAllCaps = false
            setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun addMessage(
        sender: String,
        message: String
    ) {
        val label = TextView(this).apply {
            text = sender
            textSize = 12f
            setTextColor(blue)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 10, 0, 3)
        }

        val body = TextView(this).apply {
            text = message
            textSize = 15f
            setTextColor(white)
            setPadding(12, 10, 12, 10)
            setBackgroundColor(Color.rgb(23, 29, 40))
        }

        messages.addView(label)
        messages.addView(
            body,
            LinearLayout.LayoutParams(-1, -2)
        )

        scroll.post {
            scroll.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun sendMessage() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return

        input.setText("")

        val manager =
            getSystemService(Context.INPUT_METHOD_SERVICE)
                as InputMethodManager
        manager.hideSoftInputFromWindow(input.windowToken, 0)

        addMessage("You", text)

        if (text.startsWith("/search ", ignoreCase = true)) {
            searchWeb(text.removePrefix("/search ").trim())
            return
        }

        if (apiKey.isBlank()) {
            addMessage(
                "Frosty",
                "First, open ⚙ and add your API key. " +
                    "Without it, AI replies won't work yet."
            )
            return
        }

        history.add(
            JSONObject()
                .put("role", "user")
                .put("content", text)
        )

        addMessage("Frosty", "Thinking...")

        Thread {
            try {
                val url = URL(endpoint)
                val connection =
                    url.openConnection() as HttpURLConnection

                connection.requestMethod = "POST"
                connection.connectTimeout = 20000
                connection.readTimeout = 60000
                connection.doOutput = true
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json"
                )
                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $apiKey"
                )

                val messagesJson = JSONArray()

                messagesJson.put(
                    JSONObject()
                        .put("role", "system")
                        .put(
                            "content",
                            "You are Frosty AI, a helpful, honest, " +
                                "professional but friendly personal assistant. " +
                                "Be direct, thoughtful and practical. " +
                                "Adapt to the user's language."
                        )
                )

                for (item in history) {
                    messagesJson.put(item)
                }

                val payload = JSONObject()
                    .put("model", model)
                    .put("messages", messagesJson)

                OutputStreamWriter(
                    connection.outputStream,
                    Charsets.UTF_8
                ).use {
                    it.write(payload.toString())
                }

                val stream =
                    if (connection.responseCode in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }

                val response = stream.bufferedReader().use {
                    it.readText()
                }

                if (connection.responseCode !in 200..299) {
                    throw Exception("HTTP ${connection.responseCode}: $response")
                }

                val json = JSONObject(response)
                val reply = json
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                history.add(
                    JSONObject()
                        .put("role", "assistant")
                        .put("content", reply)
                )

                runOnUiThread {
                    if (messages.childCount > 0) {
                        messages.removeViewAt(
                            messages.childCount - 1
                        )
                    }
                    addMessage("Frosty", reply)
                }

                connection.disconnect()

            } catch (e: Exception) {
                runOnUiThread {
                    if (messages.childCount > 0) {
                        messages.removeViewAt(
                            messages.childCount - 1
                        )
                    }
                    addMessage(
                        "Frosty",
                        "Couldn't get a reply.\n${e.message ?: "Unknown error"}"
                    )
                }
            }
        }.start()
    }

    private fun startVoice() {
        try {
            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    Locale.getDefault()
                )
                putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    "Speak to Frosty"
                )
            }

            startActivityForResult(intent, voiceRequest)

        } catch (e: Exception) {
            addMessage(
                "Frosty",
                "Voice input isn't available on this device."
            )
        }
    }

    @Deprecated("Deprecated by Android, retained for compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (
            requestCode == voiceRequest &&
            resultCode == RESULT_OK &&
            data != null
        ) {
            val results = data.getStringArrayListExtra(
                RecognizerIntent.EXTRA_RESULTS
            )

            if (!results.isNullOrEmpty()) {
                input.setText(results[0])
                input.setSelection(input.text.length)
            }
        }
    }

    private fun searchWeb(query: String) {
        if (query.isBlank()) {
            addMessage("Frosty", "Write what you want to search for.")
            return
        }

        val searchUrl =
            "https://www.google.com/search?q=" +
                java.net.URLEncoder.encode(query, "UTF-8")

        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    android.net.Uri.parse(searchUrl)
                )
            )
        } catch (e: Exception) {
            addMessage("Frosty", "Couldn't open web search.")
        }
    }

    private fun showSettings() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 12, 24, 4)
        }

        val keyInput = EditText(this).apply {
            hint = "API key"
            setText(apiKey)
            setSingleLine(true)
        }

        val endpointInput = EditText(this).apply {
            hint = "API endpoint URL"
            setText(endpoint)
            setSingleLine(true)
        }

        val modelInput = EditText(this).apply {
            hint = "Model name"
            setText(model)
            setSingleLine(true)
        }

        layout.addView(TextView(this).apply {
            text = "API key"
        })
        layout.addView(keyInput)

        layout.addView(TextView(this).apply {
            text = "Endpoint"
        })
        layout.addView(endpointInput)

        layout.addView(TextView(this).apply {
            text = "Model"
        })
        layout.addView(modelInput)

        AlertDialog.Builder(this)
            .setTitle("Frosty AI Settings")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                apiKey = keyInput.text.toString().trim()
                endpoint = endpointInput.text.toString().trim()
                model = modelInput.text.toString().trim()

                addMessage(
                    "Frosty",
                    "Settings updated for this session."
                )
            }
            .setNeutralButton("Clear Chat") { _, _ ->
                history.clear()
                messages.removeAllViews()
                addMessage("Frosty", "Chat cleared. ❄")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
