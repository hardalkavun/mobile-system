package com.example.maps_with_infos

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

data class WikiResponse(val query: WikiQuery?)
data class WikiQuery(val pages: Map<String, WikiPage>?)
data class WikiPage(val pageid: Int?, val title: String?, val extract: String?)

interface WikipediaApi {
    @GET("api.php")
    fun getCityInfo(
        @Query("format") format: String = "json",
        @Query("action") action: String = "query",
        @Query("prop") prop: String = "extracts",
        @Query("exintro") exintro: Boolean = true,
        @Query("explaintext") explaintext: Boolean = true,
        @Query("redirects") redirects: Int = 1,
        @Query("titles") titles: String
    ): Call<WikiResponse>
}