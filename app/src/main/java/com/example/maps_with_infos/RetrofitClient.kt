package com.example.maps_with_infos

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    val wikiApi: WikipediaApi by lazy {
        // Create OkHttpClient with User-Agent header (required by Wikipedia API)
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithUserAgent = originalRequest.newBuilder()
                    .header("User-Agent", "MapsWithInfos/1.0 (https://github.com/yourusername/maps_with_infos; contact@example.com)")
                    .build()
                chain.proceed(requestWithUserAgent)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://en.wikipedia.org/w/") // Wikipedia API base URL
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WikipediaApi::class.java)
    }
}
