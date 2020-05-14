package net.harimurti.babesharer

import android.content.Context
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

class Content {
    companion object {
        fun toFile(context: Context, filename: String, content: String) {
            try {
                val file = File(context.cacheDir, filename)
                val fw = FileWriter(file.absoluteFile)
                val bw = BufferedWriter(fw)

                bw.write(content)

                bw.close()
                fw.close()
            }
            catch (e: Exception) {
                Log.e("Content", "Failed to write : $filename", e)
            }
        }
    }
}