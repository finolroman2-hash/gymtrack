package com.example.gymtrack.nutrition

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object OpenFoodFactsApi {

    internal const val USER_AGENT = "MealTrack-GymTrack/1.2 (Android)"

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun search(query: String): Result<List<OffFood>> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext Result.success(emptyList())
        NetworkRetry.untilTimeout { searchOnce(q) }
    }

    private fun searchOnce(q: String): Result<List<OffFood>> {
        return try {
            val encoded = URLEncoder.encode(q, "UTF-8")
            val url =
                "https://world.openfoodfacts.org/cgi/search.pl?search_terms=$encoded" +
                    "&search_simple=1&action=process&json=1&page_size=50" +
                    "&fields=code,product_name,product_name_es,brands,nutriments," +
                    "serving_size,serving_quantity,quantity,categories_tags"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return Result.failure(IllegalStateException("Open Food Facts ${resp.code}"))
                }
                val json = JSONObject(resp.body?.string().orEmpty())
                val products = json.optJSONArray("products") ?: JSONArray()
                val out = ArrayList<OffFood>()
                for (i in 0 until products.length()) {
                    val entity = OffProductMapper.fromSearchJson(products.getJSONObject(i)) ?: continue
                    out.add(entity.toOffFood())
                }
                Result.success(FoodSearchRanker.rank(q, out))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
