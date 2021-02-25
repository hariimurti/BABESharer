package net.harimurti.babesharer.utils

import android.content.Context
import android.os.Build
import android.text.Html
import android.util.Log
import androidx.core.text.HtmlCompat
import kotlinx.serialization.UnstableDefault
import okhttp3.*
import java.io.IOException
import kotlinx.serialization.json.*
import net.harimurti.babesharer.BuildConfig
import net.harimurti.babesharer.R
import net.harimurti.babesharer.json.Babe

class BabeClient(private val context: Context) {
    interface OnCallback {
        fun onStart()
        fun onError(msg: String)
        fun onFoundTitle(text: String)
        fun onFoundSource(link: String)
        fun onFinish()
    }

    private var listener: OnCallback? = null
    private var title = ""

    fun setCallback(listener: OnCallback): BabeClient {
        this.listener = listener
        return this
    }

    private fun getApiUrl(groupId: String, articleId: String) = "https://i16-tb.sgsnssdk.com/api/1111/article/content/1/$groupId/$articleId/0?" +
            "youtube=1&manifest_version_code=13610&app_version=13.6.1&iid=6901292288317392641&gaid=030281ff-a361-4fe4-afbd-59baae0f8f34&babe_id&" +
            "original_channel=gp&channel=gp&app_version_minor=13.6.1.04&device_type=Pixel&language=id&resolution=1280*720&" +
            "openudid=23d24cbe03814174&update_version_code=1361004&sys_language=en&cdid=6bc53f9c-fdb4-40bc-b3ec-e119930597a7&" +
            "sys_region=us&os_api=29&tz_name=Asia%2FJakarta&tz_offset=25200&dpi=272&brand=Google&ac=WIFI&device_id=6879420167638599174&" +
            "os=android&os_version=10&version_code=13610&hevc_supported=1&babe_logged_in=0&cold_start=0&" +
            "release_build=v13.6.1.04+Build+gp_01c5d96_20201127&sim_oper=51089&device_brand=Google&device_platform=android&" +
            "sim_region=id&region=id&aid=1124&ui_language=id"

    private fun getCookies() = "passport_csrf_token=7c0a34d24008c0027060968b067cecb3; " +
            "passport_csrf_token_default=7c0a34d24008c0027060968b067cecb3; " +
            "odin_tt=62c74e15d2c7fc0f64e5bd5738e30995be55cbd6d8660ee08f0a0f8aafd6a67e0b38be15f67f269f1ffd9c7c313db263f269fc0b95c96cc3349b5775c69bf08a; " +
            "install_id=6901292288317392641; ttreq=1\$d2910d39bbcbb52320c6c0a661967242ecc44ee2"

    @UnstableDefault
    fun getArticle(url: String) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UserAgent.apple())
            .build()

        listener?.onStart()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("OkHttp", "Request Failure", e)
                listener?.onError(context.getString(R.string.request_failure))
                listener?.onFinish()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        // website content
                        // val currentUrl = response.networkResponse?.request?.url.toString()
                        val content = response.body?.string().toString()
                            .replace("\\\"", "\"")
                            .replace("\\\\\"", "\\\"")

                        // save to cache : babe.html
                        if (BuildConfig.DEBUG) {
                            Content.toFile(context, "babe.html", content)
                        }

                        // regex json
                        val match = Regex("INITIAL_STATE.+JSON\\.parse\\(\"(.+?)\"\\);").find(content)
                        if (match == null) {
                            // fail get json
                            listener?.onError(context.getString(R.string.original_article_not_found))
                            listener?.onFinish()
                            return
                        }

                        val json = Json(JsonConfiguration(ignoreUnknownKeys = true))
                            .parse(Babe.serializer(), match.groups[1]?.value.toString())

                        title = json.article.title
                        //if (!Regex("[\\.|\\?|!]\$").matches(title)) title += "."

                        listener?.onFoundTitle(title)

                        if (json.article.articleType == "article") {
                            getSourceArticle(getApiUrl(json.article.groupId, json.article.articleId))
                        } else {
                            listener?.onFinish()
                        }
                    }
                    else {
                        listener?.onError("HTTP Response : ${response.code}")
                        listener?.onFinish()
                    }
                }
            }
        })
    }

    private fun getSourceArticle(url: String) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UserAgent.babe())
            .addHeader("Cookie", getCookies())
            .addHeader("Accept", "application/json")
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("OkHttp", "Request Failure", e)
                listener?.onError(context.getString(R.string.request_failure))
                listener?.onFinish()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        // get content & replace \" to "
                        val content = response.body?.string().toString()
                            .replace("\\\"", "\"")

                        // save to cache : article.json
                        if (BuildConfig.DEBUG) {
                            Content.toFile(context, "article.json", content)
                        }

                        // regex title & http url
                        val match = Regex("class=\"title\">(.+?)</div.+href=\"(https?://.+?)\"").find(content)
                        if (match != null) {
                            // html decode special char
                            if (title.isEmpty()) title = decodeHtml(match.groups[1]?.value.toString())

                            listener?.onFoundTitle(title)
                            listener?.onFoundSource(linkNormalizer(match.groups[2]?.value.toString()))
                            listener?.onFinish()
                        }
                        else {
                            listener?.onError(context.getString(R.string.original_article_not_found))
                            listener?.onFinish()
                        }
                    }
                    else {
                        listener?.onError("HTTP Response : ${response.code}")
                        listener?.onFinish()
                    }
                }
            }
        })
    }

    private fun decodeHtml(text: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        } else {
            @Suppress("deprecation")
            Html.fromHtml(text).toString()
        }
    }

    private fun linkNormalizer(text: String): String {
        var link = Regex("(\\?.+)").replace(decodeHtml(text), "")
        for (value in context.resources.getStringArray(R.array.pageall)) {
            val match = Regex("(.+)\\|(.+)").find(value) ?: continue
            if (link.contains(match.groups[1]?.value.toString())) {
                link += match.groups[2]?.value.toString()
            }
        }
        return link
    }
}