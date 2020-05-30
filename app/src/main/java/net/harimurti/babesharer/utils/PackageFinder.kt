package net.harimurti.babesharer.utils

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import net.harimurti.babesharer.BuildConfig
import java.lang.Exception

class PackageFinder(private val context: Context) {

    private fun isPackageExist(packageName: String): Boolean {
        return try {
            context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w("PackageFinder", "$packageName not installed")
            }
            false
        }
    }

    fun getPackageName(packages: Array<String>): String? {
        for (packageName in packages) {
            if (isPackageExist(packageName))
                return packageName
        }
        return null
    }
}