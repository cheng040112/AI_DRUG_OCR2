package com.example.ai_drug_ocr.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

data class TextRequest(val text: String)
data class SummaryResponse(val summary: String)

interface GeminiApiService {
    @Headers("Content-Type: application/json")
    @POST("api/process_text")
    fun getSummary(@Body request: TextRequest): Call<SummaryResponse>
}
