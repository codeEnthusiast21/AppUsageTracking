package com.example.otherapptimedetection

import retrofit2.Call
import retrofit2.Response
import retrofit2.http.GET

interface QuotesApi {
    @GET("quotes")
    fun getRandomQuote():Call<List<QuotesModel>>
}