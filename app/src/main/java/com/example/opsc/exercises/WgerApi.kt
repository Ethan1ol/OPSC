package com.example.opsc.exercises

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// The shapes below were checked against the live wger.de API (5 November 2026). Every field is
// nullable because the API may omit fields, and the app must never crash on a missing one.

/** One page of a wger list response. [next] is null on the last page. */
data class PageDto<T>(val count: Int?, val next: String?, val results: List<T>?)

/** Light exercise record: only ids. Names come from the translation list. */
data class ExerciseDto(
    val id: Int?,
    val category: Int?,
    val muscles: List<Int>?,
    @SerializedName("muscles_secondary") val musclesSecondary: List<Int>?,
    val equipment: List<Int>?
)

/** The name and description of an exercise in one language. [exercise] is the id of the exercise it belongs to. */
data class TranslationDto(val id: Int?, val exercise: Int?, val name: String?, val description: String?, val language: Int?)

data class CategoryDto(val id: Int?, val name: String?)

data class MuscleDto(val id: Int?, val name: String?, @SerializedName("name_en") val nameEn: String?)

data class EquipmentDto(val id: Int?, val name: String?)

/**
 * The public wger exercise API (no key needed). wger has no text-search endpoint, so the app
 * downloads the catalogue in pages and searches it locally.
 */
interface WgerApi {
    @GET("exercise/")
    suspend fun exercises(
        @Query("language") language: Int = ENGLISH,
        @Query("limit") limit: Int = PAGE_SIZE,
        @Query("offset") offset: Int = 0
    ): PageDto<ExerciseDto>

    @GET("exercise-translation/")
    suspend fun translations(
        @Query("language") language: Int = ENGLISH,
        @Query("limit") limit: Int = PAGE_SIZE,
        @Query("offset") offset: Int = 0
    ): PageDto<TranslationDto>

    @GET("exercisecategory/")
    suspend fun categories(): PageDto<CategoryDto>

    @GET("muscle/")
    suspend fun muscles(): PageDto<MuscleDto>

    @GET("equipment/")
    suspend fun equipment(): PageDto<EquipmentDto>

    companion object {
        const val BASE_URL = "https://wger.de/api/v2/"
        const val ENGLISH = 2
        const val PAGE_SIZE = 100

        // Adapted from: Square (2026) Retrofit: Introduction.
        // Available at: https://square.github.io/retrofit/
        /** Builds the Retrofit client with sensible timeouts and a JSON Accept header. */
        fun create(): WgerApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("Accept", "application/json").build())
                }
                .build()
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(WgerApi::class.java)
        }
    }
}
