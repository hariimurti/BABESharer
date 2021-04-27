package net.harimurti.babesharer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.serialization.UnstableDefault
import net.harimurti.babesharer.utils.BabeClient


class OpenActivity : AppCompatActivity() {
    @OptIn(UnstableDefault::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share)

        val intent = intent
        if (Intent.ACTION_VIEW != intent.action) {
            startActivity(intent)
            this.finish()
        }

        BabeClient(this)
            .setCallback(object : BabeClient.OnCallback {
                override fun onStart() {}

                override fun onError(msg: String) {
                    Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).apply {
                        setGravity(Gravity.BOTTOM, 0, 0)
                    }.show()

                    if (BuildConfig.DEBUG) Log.e("Toast", msg)
                }

                override fun onFoundTitle(text: String) {}

                override fun onFoundSource(link: String) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                }

                override fun onFinish() {
                    this.onFinish()
                }
            })
            .getArticle(intent.data.toString())
    }
}