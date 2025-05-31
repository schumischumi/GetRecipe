package com.example.getrecipe;

import com.googlecode.tesseract.android.TessBaseAPI


object Config {
    const val TESS_ENGINE: Int = TessBaseAPI.OEM_LSTM_ONLY

    const val TESS_LANG: String = "eng"

    const val TESS_DATA_ENG: String = "eng.traineddata"

    const val TESSDATA_SUBDIR = "tessdata"
}
