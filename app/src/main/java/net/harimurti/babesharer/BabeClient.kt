package net.harimurti.babesharer

import android.os.Build
import android.text.Html
import android.util.Log
import androidx.core.text.HtmlCompat
import okhttp3.*
import java.io.IOException

class BabeClient {
    interface OnCallback {
        fun onStart()
        fun onClientError(e: IOException)
        fun onResponseError(response: Response)
        fun onSetArticle(url: String, text: String)
        fun onFinish(isError: Boolean)
    }

    private var listener: OnCallback? = null

    fun setCallback(listener: OnCallback): BabeClient {
        this.listener = listener
        return this
    }

    private fun getApiUrl(groupId: String, articleId: String) = "https://i16-tb.sgsnssdk.com/api/1062/article/content/1/$groupId/$articleId/0?" +
            "youtube=1&manifest_version_code=13120&app_version=13.1.2&iid=6816441765136484097&" +
            "gaid=b6434742-5905-4e01-aefd-18b2c8f79756&babe_id=&original_channel=gp&channel=gp&" +
            "app_version_minor=13.1.2.02&device_type=Redmi%203X&language=id&resolution=1280*720&" +
            "openudid=cb94ee8ca8aca223&update_version_code=1312002&sys_language=en&" +
            "cdid=dcbfcb44-f88f-4316-8329-b71559b650fd&sys_region=us&os_api=29&tz_name=Asia%2FJakarta&" +
            "tz_offset=25200&dpi=272&brand=Xiaomi&ac=WIFI&device_id=6775525397713061378&os=android&" +
            "os_version=10&version_code=13120&hevc_supported=1&babe_logged_in=0&cold_start=0&" +
            "release_build=v13.1.2.02%20Build%20gp_4ccd318_20200414&device_brand=Xiaomi&" +
            "device_platform=android&sim_region=id&region=id&aid=1124&ui_language=id"

    private fun getCookies() = "store-idc=alisg; " +
            "odin_tt=3ea15986627310f0aa6cd837e20adb4c37a898295cb55ceae973bb746047c5767e0438a2d4caeb6ec680e2aebf78c77ca6dad00e5934467ac1b2e3443d8c86ea; " +
            "store-country-code=id; install_id=6816441765136484097; " +
            "ttreq=1\$25fa2921db20dcf6ad36cda3586de6ad47ffc9d3; " +
            "passport_csrf_token=b7074271dea7adc9dc61f70d3b4f2fca"

    fun getArticle(url: String) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UserAgent.apple())
            .build()

        listener?.onStart()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("OkHttp", "Request Failure", e)
                listener?.onClientError(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        // get current url & website content
                        val currentUrl = response.networkResponse?.request?.url.toString()
                        val content = response.body?.string().toString()
                            .replace("\\\"", "\"")

                        // save to cache : babe.html
                        // Content.toFile(applicationContext, "babe.html", content)

                        // regex1 : find groupId, articleId, title
                        var match = Regex("\"groupId\":\"(\\d+)\",\"articleId\":\"(\\d+)\",\"title\":\"(.+?)\"").find(content)
                        if (match != null) {
                            listener?.onSetArticle(url, match.groups[3]?.value.toString())
                            val apiLink = getApiUrl(
                                match.groups[1]?.value.toString(),
                                match.groups[2]?.value.toString()
                            )

                            // next step
                            getSourceArticle(apiLink)
                            return
                        }

                        // regex1 is fail
                        // regex2 : find groupId, articleId
                        match = Regex("/[\\w+]?(\\d+)\\?.+gid=(\\d+)&").find(currentUrl)
                        if (match != null) {
                            val apiLink = getApiUrl(
                                match.groups[2]?.value.toString(),
                                match.groups[1]?.value.toString()
                            )

                            // next step
                            getSourceArticle(apiLink)
                            return
                        }

                        // fail get articleid
                        listener?.onFinish(true)
                    }
                    else {
                        listener?.onResponseError(response)
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
                listener?.onClientError(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        // get content & replace \" to "
                        val content = response.body?.string().toString()
                            .replace("\\\"", "\"")

                        // save to cache : article.json
                        // Content.toFile(applicationContext, "article.json", content)

                        // regex title & http url
                        val match = Regex("class=\"title\">(.+?)</div.+href=\"(https?://.+?)\"").find(content)
                        if (match != null) {
                            // html decode special char
                            val title = decodeHtml(match.groups[1]?.value.toString())
                            val link = decodeHtml(match.groups[2]?.value.toString())

                            listener?.onSetArticle(link, title)
                            listener?.onFinish(false)
                        }
                        else listener?.onFinish(true)
                    }
                    else {
                        listener?.onResponseError(response)
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
}