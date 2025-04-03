package com.example.otherapptimedetection

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create


object RetrofitClient {
private const val BASE_URL="https://zenquotes.io/api/"
    private fun getInstance(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    val qouteApi: QuotesApi = getInstance().create(QuotesApi::class.java)
}