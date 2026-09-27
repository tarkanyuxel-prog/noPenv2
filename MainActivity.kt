package com.nopen.nopen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private var recognizer: SpeechRecognizer? = null
    private var sink: EventChannel.EventSink? = null
    private var listening = false
    private var locale = "tr-TR"
    private var pending: MethodChannel.Result? = null
    private val micRequest = 7101

    override fun configureFlutterEngine(engine: FlutterEngine) {
        super.configureFlutterEngine(engine)
        EventChannel(engine.dartExecutor.binaryMessenger, "nopen/speech_events")
            .setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) { sink = events }
                override fun onCancel(arguments: Any?) { sink = null }
            })
        MethodChannel(engine.dartExecutor.binaryMessenger, "nopen/speech")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "isAvailable" -> result.success(SpeechRecognizer.isOnDeviceRecognitionAvailable(this))
                    "start" -> {
                        locale = call.argument<String>("locale") ?: "tr-TR"
                        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                            result.error("UNAVAILABLE", "On-device speech recognition kullanılamıyor.", null)
                        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            pending = result
                            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), micRequest)
                        } else {
                            startRecognizer()
                            result.success(true)
                        }
                    }
                    "stop" -> {
                        listening = false
                        recognizer?.cancel()
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == micRequest) {
            val result = pending
            pending = null
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecognizer()
                result?.success(true)
            } else {
                result?.error("MIC_PERMISSION", "Mikrofon izni verilmedi.", null)
            }
        }
    }

    private fun startRecognizer() {
        listening = true
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    if (listening) window.decorView.postDelayed({ listenOnce() }, 500)
                }
                override fun onResults(results: Bundle?) {
                    emit(results, true)
                    if (listening) window.decorView.postDelayed({ listenOnce() }, 350)
                }
                override fun onPartialResults(partialResults: Bundle?) { emit(partialResults, false) }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        listenOnce()
    }

    private fun emit(bundle: Bundle?, finalResult: Boolean) {
        val text = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
        sink?.success(mapOf("text" to text, "final" to finalResult))
    }

    private fun listenOnce() {
        if (!listening) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        recognizer?.startListening(intent)
    }

    override fun onDestroy() {
        listening = false
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }
}
