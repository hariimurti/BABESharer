package net.harimurti.babesharer

import android.content.*
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_share.*
import kotlinx.serialization.UnstableDefault
import net.harimurti.babesharer.utils.BabeClient
import net.harimurti.babesharer.utils.PackageFinder

class ShareActivity : AppCompatActivity() {
    internal class ViewMode {
        companion object {
            const val NONE = 0
            const val LOADING = 1
            const val SHARE = 2
        }
    }

    internal class Key {
        companion object {
            const val CODE = 2020
            const val SOURCE = "share_source"
            const val BABE = "share_babe"
        }
    }

    private var linkSource: String? = null
    private var linkBabe: String? = null
    private var telegram: String? = null
    private var whatsapp: String? = null

    private lateinit var preferences: SharedPreferences

    @UnstableDefault
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share)

        // switch view -> loading
        switchView(ViewMode.LOADING)
        text_link.editText?.inputType = InputType.TYPE_NULL

        // init preferences
        preferences = getSharedPreferences("preferences", Context.MODE_PRIVATE)

        // if not intent send
        if (!intent.action.equals(Intent.ACTION_SEND)) {
            this.finish()
            return
        }

        // filtering intent
        val sendFrom = resources.getStringArray(R.array.babeapps).find{ p -> p == callingPackage }
        if (!intent.type.equals("text/plain") || sendFrom.isNullOrEmpty()) {
            shareChooser(intent)
            return
        }

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
        linkBabe = match.groups[2]?.value.toString()
        updateLinkSource()
        setArticleTitle(match.groups[1]?.value.toString())

        // process the link
        BabeClient(this)
            .setCallback(object : BabeClient.OnCallback {
                override fun onStart() {
                    initButtonShare()
                }

                override fun onError(msg: String) {
                    toastMessage(msg, Gravity.CENTER)
                }

                override fun onFoundTitle(text: String) {
                    setArticleTitle(text)
                }

                override fun onFoundSource(link: String) {
                    linkSource = link
                    updateLinkSource()
                }

                override fun onFinish() {
                    switchView(ViewMode.SHARE)
                }
            })
            .getArticle(linkBabe!!)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_MENU) {
            onSettingClick(View(this))
            true
        } else super.onKeyUp(keyCode, event)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Key.CODE) this.finish()
    }

    fun onRootLayoutClick(view: View) {
        //this.finish()
    }

    fun onSettingClick(view: View) {
        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage(R.string.alert_message_settings)
            .setNeutralButton(getString(R.string.label_babe_and_source)) { _, _ ->
                preferences.edit().apply {
                    putBoolean(Key.SOURCE, true)
                    putBoolean(Key.BABE, true)
                    apply()
                }
                updateLinkSource()
            }
            .setPositiveButton(getString(R.string.label_source)) { _, _ ->
                preferences.edit().apply {
                    putBoolean(Key.SOURCE, true)
                    putBoolean(Key.BABE, false)
                    apply()
                }
                updateLinkSource()
            }
            .setNegativeButton(getString(R.string.label_babe)) { _, _ ->
                preferences.edit().apply {
                    putBoolean(Key.SOURCE, false)
                    putBoolean(Key.BABE, true)
                    apply()
                }
                updateLinkSource()
            }
            .setCancelable(false)
            .show()
    }

    fun onShareClick(view: View) {
        if (view.tag == getString(R.string.label_copy)) {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText(getString(R.string.title_share), getArticle()))

            toastMessage(getString(R.string.copied_into_clipboard))
            this.finish()
        }
        if (view.tag == getString(R.string.label_share)) {
            shareChooser()
        }
        if (view.tag == getString(R.string.label_telegram)) {
            shareToPackage(telegram)
        }
        if (view.tag == getString(R.string.label_whatsapp)) {
            shareToPackage(whatsapp)
        }
    }

    private fun initButtonShare() {
        telegram = PackageFinder(this)
            .getPackageName(resources.getStringArray(R.array.telegram))
        whatsapp = PackageFinder(this)
            .getPackageName(resources.getStringArray(R.array.whatsapp))
        runOnUiThread {
            if (telegram == null) {
                findViewById<ImageButton>(R.id.btn_telegram).apply {
                    isEnabled = false
                    setImageResource(R.drawable.ic_telegram_disable)
                }
            }
            if (whatsapp == null) {
                findViewById<ImageButton>(R.id.btn_whatsapp).apply {
                    isEnabled = false
                    setImageResource(R.drawable.ic_whatsapp_disable)
                }
            }
        }
    }

    private fun switchView(view: Int) {
        runOnUiThread {
            window.setBackgroundDrawableResource(
                if (view == ViewMode.NONE) R.color.transparent
                else R.color.background_transparent
            )
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

    private fun updateLinkSource() {
        runOnUiThread {
            val link = if (linkSource != null && preferences.getBoolean(Key.SOURCE, true)) linkSource else linkBabe
            text_link.editText?.setText(link)
        }
    }

    private fun setArticleTitle(text: String) {
        runOnUiThread {
            text_title.editText?.setText(text.trim())
            text_title.editText?.clearFocus()
        }
    }

    private fun getArticle(): String {
        var article = text_title.editText?.text.toString()
        if (preferences.getBoolean(Key.SOURCE, true))
            article += "\n\nLink » $linkSource"

        if (preferences.getBoolean(Key.BABE, false))
            article += "\n\nBabe » $linkBabe"

        return article.trim()
    }

    private fun shareChooser(bundle: Intent? = null) {
        switchView(ViewMode.NONE)

        var intent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getArticle())
        }

        if (bundle != null) {
            intent.type = bundle.type
            bundle.extras?.let { intent.putExtras(it) }
        }

        runOnUiThread {
            startActivityForResult(
                Intent.createChooser(intent, getString(R.string.title_share)),
                Key.CODE
            )
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
            startActivityForResult(intent, Key.CODE)
        }
    }
}
