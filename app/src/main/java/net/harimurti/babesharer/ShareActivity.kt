package net.harimurti.babesharer

import android.content.*
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import kotlinx.serialization.UnstableDefault
import net.harimurti.babesharer.utils.BabeClient
import net.harimurti.babesharer.utils.PackageFinder
import net.harimurti.babesharer.utils.ViewMode

class ShareActivity : AppCompatActivity() {
    private val sharecode = 2020
    private var telegram: String? = null
    private var whatsapp: String? = null

    @UnstableDefault
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share)

        // switch view -> none
        switchView(ViewMode.NONE)

        // get send intent
        if (resources.getStringArray(R.array.babeapps).find{ p -> p == callingPackage}.isNullOrEmpty()) {
            shareChooser(intent)
            return
        }

        /*if (!(intent.action.equals(Intent.ACTION_SEND) && intent.type.equals("text/plain"))) {
            this.finish()
            return
        }*/

        // get text from intent
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (text.isNullOrEmpty()) {
            this.finish()
            return
        }
        // find (title)(http://share.babe.news/…)
        val match = Regex("([\\s\\S]+?)(https?://share\\.babe\\.news/.+)")
            .findAll(text.toString())
            .firstOrNull()
        if (match == null) {
            shareChooser(intent)
            return
        }

        // update article text
        val link = match.groups[2]?.value.toString()
        setArticle(link, match.groups[1]?.value.toString())

        // process the link
        BabeClient(this)
            .setCallback(object : BabeClient.OnCallback {
                override fun onStart() {
                    switchView(ViewMode.LOADING)
                    initButtonShare()
                }

                override fun onError(msg: String) {
                    toastMessage(msg, Gravity.CENTER)
                }

                override fun onUpdateArticle(url: String, text: String) {
                    setArticle(url, text)
                }

                override fun onFinish() {
                    switchView(ViewMode.SHARE)
                }
            })
            .getArticle(link)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == sharecode)
            this.finish()
    }

    fun onRootLayoutClick(view: View) {
        this.finish()
    }

    fun onShareClick(view: View) {
        if (view.tag == getString(R.string.tag_copy)) {
            shareToClipboard()
        }
        if (view.tag == getString(R.string.tag_send)) {
            shareDefault()
        }
        if (view.tag == getString(R.string.tag_share)) {
            shareChooser()
        }
        if (view.tag == getString(R.string.tag_telegram)) {
            shareToPackage(telegram)
        }
        if (view.tag == getString(R.string.tag_whatsapp)) {
            shareToPackage(whatsapp)
        }
    }

    private fun initButtonShare() {
        telegram = PackageFinder(this)
            .getPackageName(resources.getStringArray(R.array.telegram))
        runOnUiThread {
            val tg = findViewById<ImageButton>(R.id.btn_telegram)
            if (telegram == null) {
                tg.isEnabled = false
                tg.setColorFilter(Color.parseColor("#40000000"))
            }
        }

        whatsapp = PackageFinder(this)
            .getPackageName(resources.getStringArray(R.array.whatsapp))
        runOnUiThread {
            val wa = findViewById<ImageButton>(R.id.btn_whatsapp)
            if (whatsapp == null) {
                wa.isEnabled = false
                wa.setColorFilter(Color.parseColor("#40000000"))
            }
        }
    }

    private fun switchView(view: Int) {
        runOnUiThread {
            findViewById<View>(R.id.layout_loading).visibility =
                if (view == ViewMode.LOADING) View.VISIBLE else View.GONE
            findViewById<View>(R.id.card_share).visibility =
                if (view == ViewMode.SHARE) View.VISIBLE else View.GONE
        }
    }

    private fun toastMessage(message: String, gravity: Int = Gravity.BOTTOM) {
        runOnUiThread {
            val toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
            toast.setGravity(gravity, 0, 0)
            toast.show()

            if (BuildConfig.DEBUG) Log.e("Toast", message)
        }
    }

    private fun setArticle(url: String, text: String = "") {
        runOnUiThread {
            findViewById<EditText>(R.id.edit_text).apply {
                setText((text + "\n\n" + url).trim())
                //setSelection(text.length)
                clearFocus()
            }
        }
    }

    private fun getArticle(): String {
        return findViewById<EditText>(R.id.edit_text).text.toString().trim()
    }

    private fun shareToClipboard() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val data = ClipData.newPlainText(getString(R.string.title_share), getArticle())
        clipboard.setPrimaryClip(data)
        toastMessage(getString(R.string.copied_into_clipboard))
        this.finish()
    }

    private fun shareChooser(bundle: Intent? = null) {
        switchView(ViewMode.NONE)

        var intent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getArticle())
        }

        if (bundle != null) intent = bundle

        runOnUiThread {
            startActivityForResult(
                Intent.createChooser(intent, getString(R.string.title_share)),
                sharecode
            )
        }
    }

    private fun shareDefault() {
        switchView(ViewMode.NONE)

        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getArticle())
        }

        runOnUiThread {
            startActivityForResult(intent, sharecode)
        }
    }

    private fun shareToPackage(packageName: String?) {
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getArticle())
            setPackage(packageName)
        }

        runOnUiThread {
            startActivityForResult(intent, sharecode)
        }
    }
}
