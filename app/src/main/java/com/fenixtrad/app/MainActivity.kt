package com.fenixtrad.app

import okhttp3.MediaType

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fenixtrad.app.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private var isActive = false
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener
    private var lastOriginal: String? = null
    private var lastTranslation: String? = null
    private lateinit var tts: TextToSpeech
    private val httpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tts = TextToSpeech(this, this)

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
            if (isActive) {
                val clip: ClipData? = clipboardManager.primaryClip
                val item = clip?.getItemAt(0)
                val text = item?.coerceToText(this)?.toString()
                text?.let { handleNewClipboardText(it) }
            }
        }

        binding.toggleButton.setOnClickListener { toggleDetection() }
        binding.repeatButton.setOnClickListener { repeatLastTranslation() }

        updateToggleUi()
    }

    private fun toggleDetection() {
        isActive = !isActive
        if (isActive) {
            clipboardManager.addPrimaryClipChangedListener(clipboardListener)
        } else {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        }
        updateToggleUi()
    }

    private fun updateToggleUi() {
        if (isActive) {
            binding.toggleButton.text = getString(R.string.toggle_pause)
            binding.toggleButton.setBackgroundColor(Color.parseColor("#4CAF50")) // verde
        } else {
            binding.toggleButton.text = getString(R.string.toggle_activate)
            binding.toggleButton.setBackgroundColor(Color.parseColor("#F44336")) // rojo
        }
    }

    private fun handleNewClipboardText(text: String) {
        if (text.length > 500) {
            showMessage(getString(R.string.error_long_text))
            return
        }
        lifecycleScope.launch {
            val translation = translateText(text)
            if (translation == null) {
                showMessage(getString(R.string.error_translation_failed))
                return@launch
            }
            lastOriginal = text
            lastTranslation = translation
            binding.originalTextView.text = text
            binding.translatedTextView.text = translation
            speak(translation)
        }
    }

    private suspend fun translateText(text: String): String? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("q", text)
                put("source", "fr")
                put("target", "es")
                put("format", "text")
            }
            val mediaType = "application/json".toMediaTypeOrNull()
            val body = RequestBody.create(mediaType, json.toString())
            val request = Request.Builder()
                .url("https://libretranslate.de/translate")
                .post(body)
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val respBody = response.body?.string() ?: return@withContext null
            val respJson = JSONObject(respBody)
            return@withContext respJson.getString("translatedText")
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts1")
    }

    private fun repeatLastTranslation() {
        val translation = lastTranslation
        if (translation != null) {
            speak(translation)
        } else {
            showMessage(getString(R.string.error_no_translation))
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("es", "ES")
        } else {
            showMessage(getString(R.string.error_tts_init))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isActive) {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener)
        }
        tts.shutdown()
    }
}