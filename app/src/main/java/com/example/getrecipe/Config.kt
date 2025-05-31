package com.example.getrecipe;

import com.googlecode.tesseract.android.TessBaseAPI


object Config {
    const val TESS_ENGINE: Int = TessBaseAPI.OEM_LSTM_ONLY

    const val TESS_LANG: String = "deu"

    const val IMAGE_NAME: String = "good_example.jpg"

    const val TESS_DATA_DEU: String = "deu.traineddata"

    const val TESSDATA_SUBDIR = "tessdata"
}
