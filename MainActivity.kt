package com.nopen.nopen
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
 private var recognizer: SpeechRecognizer?=null
 private var sink:EventChannel.EventSink?=null
 private var listening=false
 private var locale="tr-TR"
 private var pending:MethodChannel.Result?=null
 private val micRequest=7101
 override fun configureFlutterEngine(engine:FlutterEngine){
  super.configureFlutterEngine(engine)
  EventChannel(engine.dartExecutor.binaryMessenger,"nopen/speech_events").setStreamHandler(object:EventChannel.StreamHandler{
   override fun onListen(a:Any?,e:EventChannel.EventSink?){sink=e}
   override fun onCancel(a:Any?){sink=null}
  })
  MethodChannel(engine.dartExecutor.binaryMessenger,"nopen/speech").setMethodCallHandler{call,result->
   when(call.method){
    "isAvailable"->result.success(SpeechRecognizer.isOnDeviceRecognitionAvailable(this))
    "start"->{
     locale=call.argument<String>("locale")?:"tr-TR"
     if(!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) result.error("UNAVAILABLE","On-device speech recognition kullanılamıyor.",null)
     else if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){pending=result;ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.RECORD_AUDIO),micRequest)}
     else{startRecognizer();result.success(true)}
    }
    "stop"->{listening=false;recognizer?.cancel();result.success(true)}
    else->result.notImplemented()
   }
  }
 }
 override fun onRequestPermissionsResult(code:Int,p:Array<out String>,g:IntArray){
  super.onRequestPermissionsResult(code,p,g)
  if(code==micRequest){val r=pending;pending=null;if(g.isNotEmpty()&&g[0]==PackageManager.PERMISSION_GRANTED){startRecognizer();r?.success(true)}else r?.error("MIC_PERMISSION","Mikrofon izni verilmedi.",null)}
 }
 private fun startRecognizer(){
  listening=true
  if(recognizer==null){
   recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
   recognizer?.setRecognitionListener(object:RecognitionListener{
    override fun onReadyForSpeech(p:Bundle?){}; override fun onBeginningOfSpeech(){}; override fun onRmsChanged(v:Float){}
    override fun onBufferReceived(b:ByteArray?){}; override fun onEndOfSpeech(){}
    override fun onError(e:Int){if(listening)window.decorView.postDelayed({listenOnce()},500)}
    override fun onResults(b:Bundle?){emit(b,true);if(listening)window.decorView.postDelayed({listenOnce()},350)}
    override fun onPartialResults(b:Bundle?){emit(b,false)}; override fun onEvent(t:Int,p:Bundle?){}
   })
  };listenOnce()
 }
 private fun emit(b:Bundle?,f:Boolean){val t=b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?:return;sink?.success(mapOf("text" to t,"final" to f))}
 private fun listenOnce(){if(!listening)return;recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_LANGUAGE,locale);putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)})}
 override fun onDestroy(){listening=false;recognizer?.destroy();super.onDestroy()}
}