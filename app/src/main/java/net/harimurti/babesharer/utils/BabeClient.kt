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

    private fun getUserAgentApple() = "Mozilla/5.0 (Apple-iPhone7C2/1202.466; U; CPU like Mac OS X; en) " +
            "AppleWebKit/420+ (KHTML, like Gecko) Version/3.0 Mobile/1A543 Safari/419.3"

    private fun getUserAgentBabe() = "Dalvik/2.1.0 (Linux; U; Android 10; Redmi 3S Build/QQ3A.200905.001) NewsArticle/16.0.1"

    private fun getApiUrl(groupId: String, articleId: String) = "https://i16-tb.sgsnssdk.com/api/1201/article/content/1/$groupId/$articleId/0?" +
            "from_category=0&manifest_version_code=16010&current_region=ID&app_version=16.0.1&device_memory=2g&" +
            "iid=6953970413853542145&gaid=9f1d837b-fe93-4b20-b901-b0ec132ead96&original_channel=gp&channel=gp&" +
            "app_version_minor=16.0.1.02&device_type=Redmi+3S&language=id&resolution=1280*720&openudid=e89b0f2028693183&" +
            "enter_client_ab_vids=1664830&update_version_code=1601002&sys_language=in&login_platform=google&" +
            "cdid=6bc53f9c-fdb4-40bc-b3ec-e119930597a7&sys_region=US&os_api=29&tz_name=Asia%2FJakarta&tz_offset=25200&" +
            "tt_language=id&dpi=272&brand=Xiaomi&bind_platforms=google&carrier_region=ID&ac=WIFI&device_id=6879420167638599174&" +
            "os=android&mcc_mnc=51089&os_version=10&version_code=16010&hevc_supported=1&carrier_region_v2=510&cold_start=0&" +
            "release_build=v16.0.1.02+Build+gp_70c13f6_20210422&sim_oper=51089&device_brand=Xiaomi&device_platform=android&" +
            "sim_region=ID&region=id&aid=1124&is_helo_babe=true&ui_language=id"

    private fun getCookies() = "passport_csrf_token=915e2c98787592c4d6c35b50ef3b1f70; passport_csrf_token_default=915e2c98787592c4d6c35b50ef3b1f70; " +
            "store-idc=alisg; store-country-code=jp; install_id=6953970413853542145; ttreq=1\$8aecb1b55eb4d6e64cc819669d60ea4c623cd8e2; " +
            "multi_sids=6693595566808630273%3Ac62fcd27908a5c53a335ae8182b7f0ea; " +
            "odin_tt=37c90a65fc4af3945b2690e65cc4d9fb0b5c11ecc91c9462880bfdd58540c8467364386f21c2ccfaa69c1d9e83787c27ddb6b51b8fcebc85c8e0ce426a45637b; " +
            "d_ticket=c84d2e060f116cd3655315cd6a44e42942047; sid_guard=c62fcd27908a5c53a335ae8182b7f0ea%7C1619156363%7C5184000%7CTue%2C+22-Jun-2021+05%3A39%3A23+GMT; " +
            "uid_tt=e5f81f7e194cf7c2e44bd93c073d38e49b558e64ee2d93a26fe73f992e9d3fe9; uid_tt_ss=e5f81f7e194cf7c2e44bd93c073d38e49b558e64ee2d93a26fe73f992e9d3fe9; " +
            "sid_tt=c62fcd27908a5c53a335ae8182b7f0ea; sessionid=c62fcd27908a5c53a335ae8182b7f0ea; sessionid_ss=c62fcd27908a5c53a335ae8182b7f0ea"

    @UnstableDefault
    fun getArticle(url: String) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", getUserAgentApple())
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
            .header("User-Agent", getUserAgentBabe())
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