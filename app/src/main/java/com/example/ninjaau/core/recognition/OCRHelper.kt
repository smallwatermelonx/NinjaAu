//package com.example.ninjaau.core.recognition
//
//import android.graphics.Bitmap
//import com.google.mlkit.vision.common.InputImage
//import com.google.mlkit.vision.text.TextRecognition
//import com.google.mlkit.vision.text.latin.TextRecognizerOptions
//
//class OCRHelper {
//
//    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
//
//    fun recognizeText(bitmap: Bitmap, callback: (String) -> Unit) {
//        val image = InputImage.fromBitmap(bitmap, 0)
//        recognizer.process(image)
//            .addOnSuccessListener { visionText ->
//                callback(visionText.text)
//            }
//            .addOnFailureListener {
//                callback("")
//            }
//    }
//}
