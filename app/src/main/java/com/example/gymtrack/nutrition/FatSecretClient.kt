package com.example.gymtrack.nutrition

import android.util.Base64
import com.example.gymtrack.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

enum class FoodQtyUnit(val label: String, val code: String) {
    GRAMS("Gramos", "g"),
    MILLILITERS("Mililitros", "ml"),
    SERVING("Unidad / ración", "serving"),
}

data class OffFood(
    val code: String,
    val name: String,
    val brand: String,
    val kcal100g: Float,
    val protein100g: Float,
    val carbs100g: Float,
    val fat100g: Float,
    val fiber100g: Float,
    val kcal100ml: Float?,
    val servingGrams: Float?,
    val servingLabel: String?,
    val quantityHint: String,
    val isGeneric: Boolean = true,
    val needsDetails: Boolean = false,
    val cacheable: Boolean = true,
    val isCustom: Boolean = false,
    val servingKcal: Float? = null,
    val servingProtein: Float? = null,
    val servingCarbs: Float? = null,
    val servingFat: Float? = null,
    val servingFiber: Float? = null,
) {
    val units: List<FoodQtyUnit>
        get() = buildList {
            if (kcal100g > 0f || protein100g > 0f || carbs100g > 0f || fat100g > 0f) {
                add(FoodQtyUnit.GRAMS)
            }
            if (kcal100ml != null || looksLikeLiquid(quantityHint)) add(FoodQtyUnit.MILLILITERS)
            if (servingKcal != null || (servingGrams != null && servingGrams > 0f)) {
                add(FoodQtyUnit.SERVING)
            }
            if (isEmpty()) add(FoodQtyUnit.GRAMS)
        }.distinct()

    fun macrosFor(quantity: Float, unit: FoodQtyUnit): MacroPortion {
        return when (unit) {
            FoodQtyUnit.SERVING -> {
                val kcal = servingKcal
                if (kcal != null) {
                    MacroPortion(
                        grams = (servingGrams ?: 0f) * quantity,
                        kcal = kcal * quantity,
                        protein = (servingProtein ?: 0f) * quantity,
                        carbs = (servingCarbs ?: 0f) * quantity,
                        fat = (servingFat ?: 0f) * quantity,
                        fiber = (servingFiber ?: 0f) * quantity,
                    )
                } else {
                    per100(gramsFor(quantity, unit) / 100f, useMl = false)
                }
            }
            FoodQtyUnit.MILLILITERS -> {
                val useMl = kcal100ml != null
                per100(quantity / 100f, useMl)
            }
            FoodQtyUnit.GRAMS -> per100(quantity / 100f, useMl = false)
        }
    }

    fun gramsFor(quantity: Float, unit: FoodQtyUnit): Float = when (unit) {
        FoodQtyUnit.GRAMS -> quantity
        FoodQtyUnit.MILLILITERS -> quantity
        FoodQtyUnit.SERVING -> quantity * (servingGrams ?: 0f)
    }

    private fun per100(factor: Float, useMl: Boolean): MacroPortion {
        val kcalBase = if (useMl) kcal100ml ?: kcal100g else kcal100g
        return MacroPortion(
            grams = factor * 100f,
            kcal = kcalBase * factor,
            protein = protein100g * factor,
            carbs = carbs100g * factor,
            fat = fat100g * factor,
            fiber = fiber100g * factor,
        )
    }
}

data class MacroPortion(
    val grams: Float,
    val kcal: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val fiber: Float,
)

internal fun looksLikeLiquid(hint: String): Boolean {
    val h = hint.lowercase()
    return h.contains("ml") || h.contains("litro") || h.contains("beverage") ||
        h.contains("drink") || h.contains("bebida") || h.contains("en:beverages")
}

object FatSecretClient {

    private const val API_URL = "https://platform.fatsecret.com/rest/server.api"
    private val searchMethods = listOf("foods.search.v3", "foods.search.v2", "foods.search")

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun hasCredentials(): Boolean =
        BuildConfig.FATSECRET_CONSUMER_KEY.isNotBlank() &&
            BuildConfig.FATSECRET_CONSUMER_SECRET.isNotBlank()

    suspend fun search(query: String): Result<List<OffFood>> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext Result.success(emptyList())
        if (!hasCredentials()) {
            return@withContext Result.failure(IllegalStateException("missing_keys"))
        }
        NetworkRetry.untilTimeout(
            retryIf = { it is java.io.IOException },
        ) { searchOnce(q) }
    }

    private fun searchOnce(q: String): Result<List<OffFood>> {
        var lastError: Exception? = null
        for (method in searchMethods) {
            val json = request(
                mapOf(
                    "method" to method,
                    "search_expression" to q,
                    "max_results" to "50",
                    "page_number" to "0",
                    "format" to "json",
                    "region" to "ES",
                    "language" to "es",
                    "flag_default_serving" to "true",
                ),
            )
            json.fold(
                onSuccess = { root ->
                    val apiError = root.apiError()
                    if (apiError != null) {
                        lastError = IllegalStateException(apiError)
                        val retryable = listOf("method", "scope", "invalid parameter", "not available", "not permitted", "upgrade")
                            .any { apiError.contains(it, true) }
                        if (!retryable) {
                            return Result.failure(lastError!!)
                        }
                        return@fold
                    }
                    val parsed = parseSearch(root)
                    if (parsed.isNotEmpty() || method == searchMethods.last()) {
                        return Result.success(FoodSearchRanker.rank(q, parsed))
                    }
                },
                onFailure = { lastError = it as? Exception ?: Exception(it) },
            )
        }
        return Result.failure(lastError ?: IllegalStateException("FatSecret search failed"))
    }

    suspend fun details(food: OffFood): Result<OffFood> = withContext(Dispatchers.IO) {
        if (!food.needsDetails && food.servingKcal != null) return@withContext Result.success(food)
        if (!hasCredentials()) return@withContext Result.failure(IllegalStateException("missing_keys"))
        val json = request(
            mapOf(
                "method" to "food.get.v2",
                "food_id" to food.code,
                "format" to "json",
                "region" to "ES",
                "language" to "es",
            ),
        )
        json.mapCatching { root ->
            root.apiError()?.let { throw IllegalStateException(it) }
            val obj = root.optJSONObject("food") ?: throw IllegalStateException("Sin detalle de alimento")
            parseFood(obj) ?: food
        }
    }

    private fun request(params: Map<String, String>): Result<JSONObject> {
        return try {
            val all = oauthParams() + params
            val signature = sign("GET", API_URL, all)
            val query = (all + ("oauth_signature" to signature))
                .entries
                .joinToString("&") { (k, v) -> "${rfc3986(k)}=${rfc3986(v)}" }
            val req = Request.Builder()
                .url("$API_URL?$query")
                .header("User-Agent", "MealTrack-GymTrack/1.2 (Android)")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) {
                    return Result.failure(IllegalStateException("FatSecret ${resp.code}"))
                }
                Result.success(JSONObject(body))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun oauthParams(): Map<String, String> = mapOf(
        "oauth_consumer_key" to BuildConfig.FATSECRET_CONSUMER_KEY.trim(),
        "oauth_signature_method" to "HMAC-SHA1",
        "oauth_timestamp" to (System.currentTimeMillis() / 1000L).toString(),
        "oauth_nonce" to UUID.randomUUID().toString().replace("-", ""),
        "oauth_version" to "1.0",
    )

    private fun sign(method: String, url: String, params: Map<String, String>): String {
        val paramString = params.entries
            .sortedBy { it.key }
            .joinToString("&") { (k, v) -> "${rfc3986(k)}=${rfc3986(v)}" }
        val base = listOf(method.uppercase(), url, paramString).joinToString("&") { rfc3986(it) }
        val key = "${rfc3986(BuildConfig.FATSECRET_CONSUMER_SECRET.trim())}&"
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        val raw = mac.doFinal(base.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(raw, Base64.NO_WRAP)
    }

    private fun rfc3986(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")

    private fun parseSearch(root: JSONObject): List<OffFood> {
        val foodsNode = root.optJSONObject("foods_search")?.optJSONObject("results")
            ?: root.optJSONObject("foods_search")
            ?: root.optJSONObject("foods")
            ?: return emptyList()
        val out = ArrayList<OffFood>()
        foodsNode.jsonArray("food").forEach { obj ->
            parseFood(obj)?.let { out.add(it) }
        }
        return out
    }

    private fun parseFood(obj: JSONObject): OffFood? {
        val id = obj.optString("food_id").ifBlank { return null }
        val name = obj.optString("food_name").ifBlank { return null }
        val brand = obj.optString("brand_name")
        val generic = !obj.optString("food_type").equals("Brand", ignoreCase = true)
        val servings = obj.optJSONObject("servings")?.jsonArray("serving").orEmpty()
        if (servings.isEmpty()) {
            val per100 = parsePer100Description(obj.optString("food_description")) ?: return null
            return OffFood(
                code = id,
                name = name,
                brand = brand,
                kcal100g = per100.kcal,
                protein100g = per100.protein,
                carbs100g = per100.carbs,
                fat100g = per100.fat,
                fiber100g = per100.fiber,
                kcal100ml = null,
                servingGrams = null,
                servingLabel = null,
                quantityHint = obj.optString("food_description"),
                isGeneric = generic,
                needsDetails = true,
                cacheable = false,
            )
        }
        return fromServings(id, name, brand, generic, servings)
    }

    private fun fromServings(
        id: String,
        name: String,
        brand: String,
        generic: Boolean,
        servings: List<JSONObject>,
    ): OffFood? {
        val parsed = servings.mapNotNull { ServingNutrients.from(it) }
        if (parsed.isEmpty()) return null
        val per100g = parsed.firstNotNullOfOrNull { it.per100g() }
        val per100ml = parsed.firstNotNullOfOrNull { it.per100ml() }
        val defaultServing = parsed.firstOrNull { it.isDefault }
            ?: parsed.firstOrNull { !it.isMetricOnly }
            ?: parsed.first()
        val kcal100 = per100g?.kcal
            ?: per100ml?.kcal
            ?: defaultServing.scaledTo(100f).kcal
        val protein100 = per100g?.protein
            ?: per100ml?.protein
            ?: defaultServing.scaledTo(100f).protein
        val carbs100 = per100g?.carbs
            ?: per100ml?.carbs
            ?: defaultServing.scaledTo(100f).carbs
        val fat100 = per100g?.fat
            ?: per100ml?.fat
            ?: defaultServing.scaledTo(100f).fat
        val fiber100 = per100g?.fiber
            ?: per100ml?.fiber
            ?: defaultServing.scaledTo(100f).fiber
        if (kcal100 <= 0f && protein100 <= 0f && carbs100 <= 0f && fat100 <= 0f) return null
        val hint = parsed.joinToString(" ") { "${it.description} ${it.metricUnit}" }
        return OffFood(
            code = id,
            name = name,
            brand = brand,
            kcal100g = kcal100,
            protein100g = protein100,
            carbs100g = carbs100,
            fat100g = fat100,
            fiber100g = fiber100,
            kcal100ml = per100ml?.kcal,
            servingGrams = defaultServing.grams.takeIf { it > 0f },
            servingLabel = defaultServing.description.ifBlank { null },
            quantityHint = hint,
            isGeneric = generic,
            needsDetails = false,
            cacheable = false,
            servingKcal = defaultServing.kcal,
            servingProtein = defaultServing.protein,
            servingCarbs = defaultServing.carbs,
            servingFat = defaultServing.fat,
            servingFiber = defaultServing.fiber,
        )
    }
}

private data class ServingNutrients(
    val description: String,
    val metricAmount: Float,
    val metricUnit: String,
    val grams: Float,
    val kcal: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val fiber: Float,
    val isDefault: Boolean,
    val isMetricOnly: Boolean,
) {
    fun per100g(): MacroPortion? {
        if (grams <= 0f) return null
        val f = 100f / grams
        return MacroPortion(100f, kcal * f, protein * f, carbs * f, fat * f, fiber * f)
    }

    fun per100ml(): MacroPortion? {
        if (!metricUnit.equals("ml", true) || metricAmount <= 0f) return null
        val f = 100f / metricAmount
        return MacroPortion(100f, kcal * f, protein * f, carbs * f, fat * f, fiber * f)
    }

    fun scaledTo(gramsTarget: Float): MacroPortion {
        val src = grams.takeIf { it > 0f } ?: 100f
        val f = gramsTarget / src
        return MacroPortion(gramsTarget, kcal * f, protein * f, carbs * f, fat * f, fiber * f)
    }

    companion object {
        fun from(obj: JSONObject): ServingNutrients? {
            val kcal = obj.num("calories") ?: return null
            val amount = obj.num("metric_serving_amount") ?: 0f
            val unit = obj.optString("metric_serving_unit")
            val grams = when {
                unit.equals("g", true) -> amount
                unit.equals("oz", true) -> amount * 28.3495f
                unit.equals("ml", true) -> amount
                else -> 0f
            }
            val measure = obj.optString("measurement_description")
            val description = obj.optString("serving_description").ifBlank { measure }
            val metricOnly = measure.equals("g", true) || measure.equals("ml", true) ||
                measure.equals("oz", true) || description.matches(Regex("""\s*\d+([.,]\d+)?\s*(g|ml|oz)\s*""", RegexOption.IGNORE_CASE))
            return ServingNutrients(
                description = description,
                metricAmount = amount,
                metricUnit = unit,
                grams = grams,
                kcal = kcal,
                protein = obj.num("protein") ?: 0f,
                carbs = obj.num("carbohydrate") ?: 0f,
                fat = obj.num("fat") ?: 0f,
                fiber = obj.num("fiber") ?: 0f,
                isDefault = obj.optString("is_default") == "1" || obj.optInt("is_default", 0) == 1,
                isMetricOnly = metricOnly,
            )
        }
    }
}

private fun JSONObject.apiError(): String? {
    val err = optJSONObject("error") ?: return null
    val code = err.optString("code")
    val message = err.optString("message")
    return listOf(code, message).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "FatSecret error" }
}

private fun JSONObject.jsonArray(key: String): List<JSONObject> {
    val arr: JSONArray = when (val raw = opt(key)) {
        is JSONArray -> raw
        is JSONObject -> JSONArray().put(raw)
        else -> return emptyList()
    }
    return buildList {
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            add(item)
        }
    }
}

private fun JSONObject.num(key: String): Float? {
    if (!has(key) || isNull(key)) return null
    return when (val raw = opt(key)) {
        is Number -> raw.toFloat()
        else -> optString(key).replace(',', '.').toFloatOrNull()
    }?.takeIf { it >= 0f }
}
