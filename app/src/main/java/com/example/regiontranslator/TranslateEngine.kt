package com.example.regiontranslator

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class TranslateEngine {
    private val translator: Translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.KOREAN)
            .build()
    )

    suspend fun ensureModel() = suspendCancellableCoroutine<Unit> { cont ->
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resume(Unit) } // 실패해도 앱은 계속 돌아가게
    }

    suspend fun translate(text: String): String = suspendCancellableCoroutine { cont ->
        if (text.isBlank()) { cont.resume(""); return@suspendCancellableCoroutine }
        translator.translate(text)
            .addOnSuccessListener { cont.resume(it ?: "") }
            .addOnFailureListener { cont.resume("") }
    }
}
