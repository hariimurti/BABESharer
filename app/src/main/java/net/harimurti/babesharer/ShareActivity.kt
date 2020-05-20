package net.harimurti.babesharer

import android.content.*
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import okhttp3.Response
import java.io.IOException

class ShareActivity : AppCompatActivity() {
    private var uiMode = true
    private var article = ""
    private var address = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        uiMode = getSharedPreferences("preferences", Context.MODE_PRIVATE)
            .getBoolean(getString(R.string.pref_ui_mode), true)

        if (uiMode) {
            setContentView(R.layout.activity_share_full)
            // disable input
            findViewById<EditText>(R.id.txt_address).inputType = InputType.TYPE_NULL
        }
        else {
            setContentView(R.layout.activity_share_loading)
        }

        if (intent.action.equals(Intent.ACTION_SEND) && intent.type.equals("text/plain")) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (text == null || text.isEmpty()) return

            // find (title)(http://share.babe.news/…)
            val match = Regex("([\\s\\S]+?)(https?://share\\.babe\\.news/.+)")
                .findAll(text).firstOrNull()
            if (match != null) {
                setArticle(match.groups[1]?.value.toString())
                setAddress(match.groups[2]?.value.toString())
            } else {
                shareText(text)
                this.finish()
                return
            }
        }

        // set window as dialog fullscreen
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        // make statusbar transparent
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        // process the link
        BabeClient(this)
            .setCallback(object : BabeClient.OnCallback {
                override fun onStart() {
                    setProgress(true)
                }

                override fun onClientError(e: IOException) {
                    setProgress(false)
                    toastMessage(getString(R.string.toast_request_failure))
                    if (!uiMode) shareText()
                }

                override fun onResponseError(response: Response) {
                    setProgress(false)
                    toastMessage("HTTP Response : ${response.code}")
                    if (!uiMode) shareText()
                }

                override fun onSetArticle(url: String, text: String) {
                    setAddress(url)
                    setArticle(text)
                }

                override fun onFinish(isError: Boolean) {
                    setProgress(false)
                    if (isError) toastMessage(getString(R.string.toast_original_article_not_found))
                    if (!uiMode) shareText()
                }
            }).getArticle(address)
    }

    fun onLayoutClick(view: View) {
        this.finish()
    }

    fun onFabAction(view: View) {
        if (article.isNotEmpty()) {
            shareText()
        } else {
            toastMessage(getString(R.string.toast_article_is_empty))
        }
    }

    private fun toastMessage(message: String, vararg strings: String) {
        runOnUiThread {
            Toast.makeText(this, String.format(message, strings), Toast.LENGTH_SHORT).show()
            Log.e("Toast", String.format(message, strings))
        }
    }

    private fun setProgress(state: Boolean) {
        if (!uiMode) return
        runOnUiThread {
            findViewById<View>(R.id.layout_progress)
                .visibility = if (state) View.VISIBLE else View.GONE
            findViewById<View>(R.id.fab_action).isEnabled = !state
            findViewById<EditText>(R.id.txt_article).isEnabled = !state
            findViewById<EditText>(R.id.txt_address).isEnabled = !state
        }
    }

    private fun setArticle(text: String) {
        article = text.trim()
        if (!uiMode) return

        runOnUiThread {
            val editText = findViewById<EditText>(R.id.txt_article)
            editText.setText(article)
            editText.setSelection(article.length)
            editText.clearFocus()
        }
    }

    private fun setAddress(text: String) {
        address = text.trim()
        if (!uiMode) return

        runOnUiThread {
            findViewById<EditText>(R.id.txt_address).setText(address)
        }
    }

    private fun shareText(text: String? = null) {
        val extra = if (text.isNullOrEmpty()) "$article\n\nLink » $address".trim() else text.trim()
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, extra)
        }

        val shareIntent = Intent.createChooser(sendIntent, getString(R.string.title_share))
        runOnUiThread { startActivityForResult(shareIntent, 0) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        this.finish()
    }
}
