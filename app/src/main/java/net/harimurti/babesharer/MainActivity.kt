package net.harimurti.babesharer

import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {
    private lateinit var prefrences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // define sharedpreferences
        prefrences = getSharedPreferences("preferences", Context.MODE_PRIVATE)
        val usingUImode = prefrences.getBoolean(getString(R.string.pref_ui_mode), true)
        findViewById<RadioButton>(R.id.rb_with).isChecked = usingUImode
        findViewById<RadioButton>(R.id.rb_without).isChecked = !usingUImode

        // paste from clipboard if no shared link
        /*val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.primaryClip?.apply {
            val text = this.getItemAt(0).text.toString()
            if (processLinkFromText(text)) return
        }*/

        // check if babe installed
        val installed = getInstalledBabe()
        if (installed.isNullOrEmpty()) {
            // alert install app
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.alert_warning_title))
                .setMessage(getString(R.string.alert_please_install_first))
                .setPositiveButton(getString(R.string.button_install)) { _, _ ->
                    openPlayStore()
                    this.finish()
                }
                .setNegativeButton(getString(R.string.button_exit), null)
                .show()
            return
        }

        findViewById<TextView>(R.id.txt_installed).text = installed
        findViewById<TextView>(R.id.txt_share_flow).text =
            String.format(getString(R.string.babe_this_app_target), installed)
    }

    fun onRadioClick(view: View) {
        val withUIchecked = (view.tag == getString(R.string.with_ui))
        findViewById<RadioButton>(R.id.rb_with).isChecked = withUIchecked
        findViewById<RadioButton>(R.id.rb_without).isChecked = !withUIchecked
        prefrences.edit().apply() {
            putBoolean(getString(R.string.pref_ui_mode), withUIchecked)
            apply()
        }
    }

    private fun getInstalledBabe(): String? {
        for (packagename in resources.getStringArray(R.array.packages)) {
            try {
                //startActivity(packageManager.getLaunchIntentForPackage(packagename))
                val info = packageManager.getApplicationInfo(packagename, PackageManager.GET_META_DATA)
                return packageManager.getApplicationLabel(info).toString()
            } catch (e: Exception) {}
        }
        return null
    }

    private fun openPlayStore() {
        for (uri in resources.getStringArray(R.array.market)) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                return
            } catch (e: ActivityNotFoundException) {
                Log.e("PlayStore", "Launch Failed", e)
            }
        }
    }

    private fun toastMessage(message: String, vararg strings: String) {
        runOnUiThread {
            Toast.makeText(this, String.format(message, strings), Toast.LENGTH_SHORT).show()
            Log.e("Toast", String.format(message, strings))
        }
    }
}
