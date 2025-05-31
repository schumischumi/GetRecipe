package com.example.getrecipe;

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


object Assets {
    /**
     * Returns locally accessible directory where our assets are extracted.
     */
    fun getLocalDir(context: Context): File {
        return context.filesDir
    }

    /**
     * Returns locally accessible directory path which contains the "tessdata" subdirectory
     * with *.traineddata files.
     */
    fun getTessDataPath(context: Context): String {
        return getLocalDir(context).absolutePath
    }

    fun getImageFile(context: Context): File {
        return File(getLocalDir(context), Config.IMAGE_NAME)
    }

    fun getImageBitmap(context: Context): Bitmap? {
        return BitmapFactory.decodeFile(getImageFile(context).absolutePath)
    }

    fun extractAssets(context: Context) {
        val am = context.assets

        val localDir = getLocalDir(context)
        if (!localDir.exists() && !localDir.mkdir()) {
            throw RuntimeException("Can't create directory $localDir")
        }

        val tessDir = File(getTessDataPath(context), "tessdata")
        if (!tessDir.exists() && !tessDir.mkdir()) {
            throw RuntimeException("Can't create directory $tessDir")
        }

        // Extract our assets to local directory.
        // Note we don't use am.list() to get list of all assets, because we wouldn't know which
        // files are ours and which were added by other libraries
        val filesToExtract = arrayOf(
            "deu.traineddata",
            "good_example.jpg",
        )
        for (assetName in filesToExtract) {
            // Put all *.traineddata into "tessdata" subdirectory, other files into root.
            val targetFile = if (assetName.endsWith(".traineddata")) {
                File(tessDir, assetName)
            } else {
                File(localDir, assetName)
            }
            if (!targetFile.exists()) {
                copyFile(am, assetName, targetFile)
            }
        }
    }

    private fun copyFile(
        am: AssetManager, assetName: String,
        outFile: File
    ) {
        try {
            am.open(assetName).use { `in` ->
                FileOutputStream(outFile).use { out ->
                    val buffer = ByteArray(1024)
                    var read: Int
                    while ((`in`.read(buffer).also { read = it }) != -1) {
                        out.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
