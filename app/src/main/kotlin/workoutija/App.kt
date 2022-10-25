package workoutija

import com.fasterxml.jackson.databind.JsonNode
import org.http4k.client.JavaHttpClient
import org.http4k.core.Credentials
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.UriTemplate
import org.http4k.filter.ClientFilters.CustomBasicAuth.withBasicAuth
import org.http4k.format.Jackson.asJsonArray
import org.http4k.format.Jackson.asJsonObject
import org.http4k.format.JacksonYaml
import java.nio.file.Paths
import java.time.LocalDate
import kotlin.io.path.inputStream

val client = JavaHttpClient()

data class Config(val username: String, val password: String, val athleteId: String)

fun main() {
    val cfg = Paths.get(System.getenv("HOME"), ".intervals_icu/cfg.yaml").inputStream().use {
        JacksonYaml.asA(it, Config::class)
    }

    val uri = UriTemplate.from("https://intervals.icu/api/v1/athlete/{athleteId}/activities")
        .generate(mapOf("athleteId" to cfg.athleteId))

    val from = LocalDate.now().minusDays(1).toString()
    val to = LocalDate.now().plusDays(1).toString()
    val credentials = Credentials(cfg.username, cfg.password)
    val request = Request(Method.GET, uri)
        .withBasicAuth(credentials)
        .query("oldest", from)
        .query("newest", to)

    val response = client(request)
    val summaries = response.bodyString().asJsonObject().asJsonArray().map {
        val o = it.asJsonObject()
        val id = o.get("id").textValue()
        summarize(o, comments(id, credentials))
    }
    println(summaries.joinToString("\n\n=====\n\n"))
}

private fun summarize(o: JsonNode, comments: List<String>): String = with(o) {
    """
        ${comments.joinToString("\n")}
        ---
        ⏳${get("elapsed_time").fmtTime()} ( 🚚${get("moving_time").fmtTime()} 🛑${get("coasting_time").fmtTime()} )
        ❤️⌀${get("average_heartrate")} 📈${get("max_heartrate")}
        💪🏼⌀${get("icu_average_watts")}W 📈${get("icu_pm_p_max")}W 🧑🏻‍⚖️${get("icu_weighted_avg_watts")}W
        RPE: ${get("icu_rpe")}/10 ; Feel: ${get("feel")}/5
        💿⌀${get("average_cadence")}rpm 🌡⌀${get("average_temp")}°C
        ${get("name")} ${optionalStravaLink(o)} ... ${get("start_date_local")}
        ${"https://intervals.icu/activities/${get("id").textValue()}"}
    """.trimIndent()
}

fun JsonNode.fmtTime(): String {
    val inSecs = this.intValue()
    val hours = inSecs / 60 / 60
    val minutes = inSecs / 60 % 60
    val seconds = inSecs % 60
    return "$hours:$minutes:$seconds"
}

fun optionalStravaLink(o: JsonNode): String? =
    o.get("strava_id").takeIf { !it.isNull }?.let { "https://www.strava.com/activities/${it.textValue()}" }

fun comments(activityId: String, credentials: Credentials, _client: HttpHandler = client): List<String> {
    val uri = UriTemplate.from("https://intervals.icu/api/v1/activity/{id}/messages")
        .generate(mapOf("id" to activityId))
    val request = Request(Method.GET, uri)
        .withBasicAuth(credentials)
    val response = _client(request)
    return response.bodyString().asJsonObject().map {
        it.get("content").textValue()
    }
}
