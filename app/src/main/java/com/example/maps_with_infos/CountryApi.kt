package com.example.maps_with_infos

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

// Veri sınıfları sadece burada
data class Country(
    val name: Name,
    val capital: List<String>?,
    val population: Long?,
    val region: String?,
    val subregion: String?
)

data class Name(val common: String)

// API interface
interface RestCountryApi {
    @GET("v3.1/alpha/{code}")
    fun getCountry(@Path("code") code: String): Call<List<Country>>
}
