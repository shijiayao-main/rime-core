package com.jiaoay.rime.data.opencc

import android.content.Context
import com.jiaoay.rime.data.DataManager
import com.jiaoay.rime.data.opencc.dict.Dictionary
import com.jiaoay.rime.data.opencc.dict.OpenCCDictionary
import com.jiaoay.rime.data.opencc.dict.TextDictionary
import java.io.File
import java.io.InputStream
import kotlin.system.measureTimeMillis

object OpenCCDictManager {
    init {
        System.loadLibrary("rime")
    }

    private val openccDictDir = File(
        DataManager.getDataDir("opencc")
    ).also { it.mkdirs() }

    fun dictionaries(): List<Dictionary> = openccDictDir
        .listFiles()
        ?.mapNotNull { Dictionary.new(it) }
        ?.toList() ?: listOf()

    fun openccDictionaries(): List<OpenCCDictionary> =
        dictionaries().mapNotNull { it as? OpenCCDictionary }

    fun importFromFile(file: File): OpenCCDictionary {
        val raw = Dictionary.new(file)
            ?: throw IllegalArgumentException("${file.path} is not a opencc/text dictionary")
        // convert to opencc format in dictionaries dir
        // preserve original file name
        val new = raw.toOpenCCDictionary(
            File(
                openccDictDir,
                file.nameWithoutExtension + ".${Dictionary.Type.OPENCC.ext}"
            )
        )
        return new
    }

    /**
     * Convert internal text dict to opencc format
     */
    @JvmStatic
    fun internalDeploy() {
        for (d in dictionaries()) {
            if (d is TextDictionary) {
                val result: OpenCCDictionary
                measureTimeMillis {
                    result = d.toOpenCCDictionary()
                }
            }
        }
    }

    fun importFromInputStream(context: Context, stream: InputStream, name: String): OpenCCDictionary {
        val tempFile = File(context.applicationContext.cacheDir, name)
        tempFile.outputStream().use {
            stream.copyTo(it)
        }
        val new = importFromFile(tempFile)
        tempFile.delete()
        return new
    }

    @JvmStatic
    external fun openccDictConv(src: String, dest: String, mode: Boolean)

    const val MODE_BIN_TO_TXT = true // OCD2 to TXT
    const val MODE_TXT_TO_BIN = false // TXT to OCD2
}
