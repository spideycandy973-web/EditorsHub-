package com.editorshub.core.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.editorshub.core.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

data class AiResult(
    val graph: EffectGraph,
    val effects: List<com.editorshub.core.domain.model.Effect>,
    val explanation: String,
    val raw: String
)

/** Real inference against a configured OpenAI-compatible endpoint. Never simulates. */
@Singleton
class AiEffectService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository
) {
    @Volatile private var lastCallMs = 0L

    companion object {
        private const val SYSTEM = """You convert a natural-language video effect description into a SAFE effect graph.
Reply with ONLY JSON: {"name":string,"explanation":string,"nodes":[{"type":string,"label":string,"params":object}],"animation":boolean}
Allowed node types: INPUT, OUTPUT, BLUR, COLOR_GRADE, DISTORT, GLOW, NOISE, DISPLACE, TRANSFORM, BLEND, MASK, COMPOSITE, GRADIENT, TEXTURE, ANIMATION.
Always start with INPUT and end with OUTPUT. Use numeric params only. Do not emit code, shaders or URLs."""
    }

    suspend fun status(): Result<String> = withContext(Dispatchers.IO) {
        val s = settings.settings.first()
        if (!s.aiConfigured) return@withContext Result.failure(
            IllegalStateException("AI service is not configured. Add an endpoint and API key in Settings → AI."))
        val key = AiKeyStore.decrypt(s.aiApiKey)
        if (key.isBlank()) return@withContext Result.failure(
            IllegalStateException("Stored API key could not be decrypted. Re-enter it in Settings → AI."))
        try {
            val conn = (URL(s.aiBaseUrl.trimEnd('/') + "/models").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 8000; readTimeout = 8000
                setRequestProperty("Authorization", "Bearer $key")
            }
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) Result.success("Connected · ${s.aiModel}")
            else Result.failure(IllegalStateException("Endpoint returned HTTP $code"))
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Connection failed: ${e.message}"))
        }
    }

    suspend fun generate(prompt: String, referencePath: String?): Result<AiResult> = withContext(Dispatchers.IO) {
        val s = settings.settings.first()
        if (!s.aiConfigured) return@withContext Result.failure(
            IllegalStateException("AI service is not configured. Open Settings → AI to add a real endpoint and key."))
        val key = AiKeyStore.decrypt(s.aiApiKey)
        if (key.isBlank()) return@withContext Result.failure(
            IllegalStateException("Stored API key could not be decrypted. Re-enter it."))
        val now = System.currentTimeMillis()
        if (now - lastCallMs < 1200) return@withContext Result.failure(
            IllegalStateException("Rate limit: wait a moment before generating again."))
        lastCallMs = now

        val reference = referencePath?.let { analyzeReference(it) }
        val userPrompt = buildString {
            append(prompt.trim())
            if (reference != null) append("\nReference image analysis: $reference")
        }
        val body = JSONObject().apply {
            put("model", s.aiModel)
            put("temperature", s.aiTemperature.toDouble())
            put("max_tokens", s.aiMaxTokens)
            put("response_format", JSONObject().put("type", "json_object"))
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", SYSTEM))
                put(JSONObject().put("role", "user").put("content", userPrompt))
            })
        }
        return@withContext try {
            val conn = (URL(s.aiBaseUrl.trimEnd('/') + "/chat/completions")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 15_000; readTimeout = 45_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $key")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = if (code in 200..299) conn.inputStream.bufferedReader().readText()
                else conn.errorStream?.bufferedReader()?.readText() ?: ""
            conn.disconnect()
            if (code !in 200..299) return@withContext Result.failure(
                IllegalStateException("AI service error (HTTP $code): ${text.take(300)}"))
            val content = JSONObject(text)
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
            parse(content, prompt)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("AI request failed: ${e.message}"))
        }
    }

    private fun parse(content: String, prompt: String): Result<AiResult> {
        val root = try { JSONObject(content) } catch (e: Exception) {
            return Result.failure(IllegalStateException("AI returned invalid JSON."))
        }
        val name = root.optString("name", "AI effect").take(48)
        val explanation = root.optString("explanation", "Generated effect graph.").take(400)
        val nodesJson = root.optJSONArray("nodes") ?: JSONArray()
        val nodes = mutableListOf<EffectNode>()
        for (i in 0 until nodesJson.length()) {
            val o = nodesJson.optJSONObject(i) ?: continue
            val type = o.optString("type").uppercase()
            if (!NodeTypes.valid(type)) continue
            val p = mutableMapOf<String, Float>()
            o.optJSONObject("params")?.let { po ->
                po.keys().forEach { k -> po.optDouble(k, Double.NaN).takeIf { !it.isNaN() }?.let { p[k] = it.toFloat() } }
            }
            nodes.add(EffectNode(type = type, label = o.optString("label", type),
                params = p, x = 60f + (i % 4) * 200f, y = 60f + (i / 4) * 130f))
        }
        if (nodes.none { it.type == "INPUT" }) nodes.add(0, EffectNode(type = "INPUT", label = "Input", x = 20f, y = 60f))
        if (nodes.none { it.type == "OUTPUT" }) nodes.add(EffectNode(type = "OUTPUT", label = "Output",
            x = 60f + nodes.size * 200f, y = 60f))
        if (root.optBoolean("animation", false) && nodes.none { it.type == "ANIMATION" })
            nodes.add(nodes.size - 1, EffectNode(type = "ANIMATION", label = "Pulse",
                params = mapOf("pulse" to 0.25f), x = 400f, y = 240f))
        val edges = mutableListOf<EffectEdge>()
        for (i in 0 until nodes.size - 1) edges.add(EffectEdge(nodes[i].id, nodes[i + 1].id))
        val graph = EffectGraph(name = name, nodes = nodes, edges = edges,
            outputId = nodes.last().id, sourcePrompt = prompt)
        val validation = NodeEvaluator.validate(graph)
        if (!validation.ok) return Result.failure(
            IllegalStateException("Generated graph was rejected: ${validation.errors.joinToString()}"))
        val effects = NodeEvaluator.toEffects(graph)
        if (effects.isEmpty()) return Result.failure(
            IllegalStateException("Generated graph contains no renderable effects. Try a more specific prompt."))
        return Result.success(AiResult(graph, effects, explanation, content))
    }

    /** Genuine pixel analysis of a reference image (no AI image upload simulation). */
    fun analyzeReference(path: String): String? = try {
        val file = File(path.removePrefix("file://"))
        val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
        val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        val step = (bmp.width / 32).coerceAtLeast(1)
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val c = bmp.getPixel(x, y)
                r += android.graphics.Color.red(c); g += android.graphics.Color.green(c)
                b += android.graphics.Color.blue(c); n++
                x += step
            }
            y += step
        }
        val rr = (r / n).toInt(); val gg = (g / n).toInt(); val bb = (b / n).toInt()
        val brightness = (rr + gg + bb) / 3
        val mx = maxOf(rr, gg, bb); val mn = minOf(rr, gg, bb)
        val sat = if (mx == 0) 0 else (mx - mn) * 100 / mx
        val hue = when {
            mx == mn -> "neutral"
            mx == rr && gg >= bb -> "warm red/orange"
            mx == rr -> "magenta"
            mx == gg -> "green/cyan"
            else -> "blue/violet"
        }
        "average RGB($rr,$gg,$bb), brightness $brightness/255, saturation $sat%, hue $hue"
    } catch (e: Exception) { null }
}
