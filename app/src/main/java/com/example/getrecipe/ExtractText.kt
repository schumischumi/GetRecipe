package com.example.getrecipe

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.googlecode.tesseract.android.TessBaseAPI
// import com.googlecode.tesseract.android.TessBaseAPI.ProgressValues // Keep if you uncomment init block
import java.io.File
import java.util.Locale
import kotlin.concurrent.Volatile
// import kotlin.text.toLowerCase // Not strictly needed if using Locale.ROOT for safety


class ExtractText(application: Application) : AndroidViewModel(application) {
    // TODO: Initialize tessApi properly, perhaps in initTesseract or an init block.
    //  The 'TODO()' will cause a crash if tessApi is accessed before being initialized.
    //  Consider making it lateinit if initialized in a dedicated method.
    private lateinit var tessApi: TessBaseAPI // Changed to lateinit

    // Original 'result' LiveData - If you intend to use this for general results
    // in addition to specific area results, ensure its purpose is clear.
    // If it's just a leftover, consider removing it to avoid confusion with _result.
    val result = MutableLiveData<String>() // This is the one causing a clash with getResult()

    var isInitialized: Boolean = false
        private set

    // Using companion object TAG for consistency within this class
    // private val TAG = "ExtractTextViewModel" // This was okay, but often ViewModel TAGs are in companion

    // --- Specific area results ---
    private val _titleResult = MutableLiveData<String?>()
    val titleResult: LiveData<String?> = _titleResult

    private val _ingredientsResult = MutableLiveData<String?>()
    val ingredientsResult: LiveData<String?> = _ingredientsResult

    private val _preparationResult = MutableLiveData<String?>()
    val preparationResult: LiveData<String?> = _preparationResult

    // --- Processing State LiveData ---
    private val _processing = MutableLiveData<Boolean>()
    val processing: LiveData<Boolean> = _processing // Public property, no explicit getProcessing() needed

    private val _progress = MutableLiveData<String>()
    val progress: LiveData<String> = _progress // Public property, no explicit getProgress() needed

    @Volatile
    private var stopped = false

    @Volatile
    private var tessProcessing = false

    @Volatile
    private var recycleAfterProcessing = false

    private val recycleLock = Any()

    // init {
    //     tessApi = TessBaseAPI { progressValues: ProgressValues ->
    //         _progress.postValue("Progress: " + progressValues.percent + " %") // Use _progress
    //     }
    //     // Show Tesseract version and library flavor at startup
    //     _progress.value = String.format( // Use _progress
    //         Locale.ENGLISH, "Tesseract %s (%s)",
    //         tessApi.version, tessApi.libraryFlavor
    //     )
    // }

    override fun onCleared() {
        super.onCleared() // It's good practice to call super.onCleared()
        synchronized(recycleLock) {
            if (::tessApi.isInitialized) { // Check if lateinit var is initialized
                if (tessProcessing) {
                    recycleAfterProcessing = true
                    tessApi.stop()
                    Log.d(TAG, "onCleared: Tess processing, will recycle after.")
                } else {
                    tessApi.recycle()
                    Log.d(TAG, "onCleared: Tess recycled immediately.")
                }
            }
        }
    }

    fun initTesseract(dataPath: String, language: String, engineMode: Int) {
        // Initialize tessApi here if it's lateinit
        tessApi = TessBaseAPI { progressValues: TessBaseAPI.ProgressValues ->
            _progress.postValue("Progress: " + progressValues.percent + " %")
        }
        // Show Tesseract version and library flavor at startup (optional, can be here or in an init block)
        _progress.value = String.format(
            Locale.ENGLISH, "Tesseract %s (%s)",
            tessApi.version, tessApi.libraryFlavor
        )

        Log.i(
            TAG, "Initializing Tesseract with: dataPath = [$dataPath], " +
                    "language = [$language], engineMode = [$engineMode]"
        )
        try {
            isInitialized = tessApi.init(dataPath, language, engineMode)
            if (!isInitialized) {
                Log.e(TAG, "Tesseract initialization failed (init returned false).")
            }
        } catch (e: IllegalArgumentException) {
            isInitialized = false
            Log.e(TAG, "Cannot initialize Tesseract (IllegalArgumentException):", e)
        } catch (e: Exception) {
            isInitialized = false
            Log.e(TAG, "Unknown error during Tesseract initialization:", e)
        }
    }

    fun recognizeImage(imagePath: File, areaType: String) {
        if (!isInitialized) {
            Log.e(TAG, "recognizeImage: Tesseract is not initialized for $areaType")
            // Optionally post an error to the specific LiveData
            postErrorToRelevantLiveData(areaType, "Tesseract not initialized.")
            return
        }
        if (tessProcessing) {
            Log.w(TAG, "recognizeImage: Processing is already in progress. Request for $areaType ignored.")
            // Optionally post an error or queue the request
            postErrorToRelevantLiveData(areaType, "Another OCR process is active.")
            return
        }
        tessProcessing = true

        // result.value = "" // This refers to the public 'result' MutableLiveData.
        // If it's for general/old results, it's fine.
        // If it was meant for _result, it's different.

        _processing.value = true // Use the backing field for the property
        stopped = false

        when (areaType.toLowerCase(Locale.ROOT)) {
            "title" -> _titleResult.postValue(null) // Clear previous specific result
            "ingredients" -> _ingredientsResult.postValue(null)
            "preparation" -> _preparationResult.postValue(null)
        }
        _progress.postValue("Processing $areaType...") // Use backing field

        Thread {
            try {
                tessApi.setImage(imagePath)
                // Consider making pageSegMode configurable if needed
                tessApi.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO_OSD

                val startTime = SystemClock.uptimeMillis()
                // tessApi.getHOCRText(0) // If you use this, the result might be HOCR, not plain text
                val text = tessApi.utF8Text ?: ""
                // It's good practice to clear the Tesseract internal data after getting the text
                // if you are done with this specific image and settings.
                // tessApi.clear() // Done inside loop if processing multiple images, or at end.
                // For single image per call, here is fine.

                when (areaType.toLowerCase(Locale.ROOT)) {
                    "title" -> _titleResult.postValue(text)
                    "ingredients" -> _ingredientsResult.postValue(text)
                    "preparation" -> _preparationResult.postValue(text)
                    else -> {
                        Log.w(TAG, "Unrecognized areaType: $areaType for text: $text")
                        // this.result.postValue("For $areaType: $text") // Post to the public 'result'
                    }
                }

                // If 'this.result' is for a general combined result, update it here.
                // If not, the line 'this.result.postValue(text)' might be redundant
                // if specific area results are what you need.
                // this.result.postValue(text) // This posts the current area's text to the general 'result'

                if (stopped) {
                    _progress.postValue("$areaType: Stopped.")
                } else {
                    val duration = SystemClock.uptimeMillis() - startTime
                    _progress.postValue(
                        String.format(
                            Locale.ENGLISH,
                            "$areaType: Completed in %.3fs.", (duration / 1000f)
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during OCR for $areaType", e)
                postErrorToRelevantLiveData(areaType, "OCR Error: ${e.message}")
                _progress.postValue("$areaType: Error.") // Update progress on error
            } finally {
                // tessApi.clear() // Clear Tesseract's internal state for the image.
                // Good to do this before releasing tessProcessing flag.
                if(::tessApi.isInitialized) tessApi.clear()


                synchronized(recycleLock) {
                    tessProcessing = false
                    _processing.postValue(false) // Update processing state AFTER this operation
                    if (recycleAfterProcessing) {
                        if (::tessApi.isInitialized) { // Check before recycling
                            tessApi.recycle()
                            Log.d(TAG, "Tesseract recycled post-processing for $areaType.")
                            recycleAfterProcessing = false // Reset flag
                        }
                    }
                }
            }
        }.start()
    }

    private fun postErrorToRelevantLiveData(areaType: String, errorMessage: String) {
        val fullError = "Error for $areaType: $errorMessage"
        when (areaType.toLowerCase(Locale.ROOT)) {
            "title" -> _titleResult.postValue(fullError)
            "ingredients" -> _ingredientsResult.postValue(fullError)
            "preparation" -> _preparationResult.postValue(fullError)
            else -> this.result.postValue(fullError) // Post to general result if area unknown
        }
    }


    fun stop() {
        if (!tessProcessing) { // Check if actually processing
            Log.d(TAG, "Stop called, but not currently processing.")
            return
        }
        _progress.value = "Stopping..." // Use backing field
        stopped = true
        if (::tessApi.isInitialized) { // Check before calling stop
            tessApi.stop() // Request Tesseract to stop its current operation
        }
    }

    // REMOVE these explicit getter functions:
    // fun getProcessing(): LiveData<Boolean> {
    //     return processing // or _processing
    // }

    // fun getProgress(): LiveData<String> {
    //     return progress // or _progress
    // }

    // fun getResult(): LiveData<String> {
    //     return result
    // }

    companion object {
        // Renamed TAG to avoid conflict if this class was also named "MainViewModel"
        private const val TAG = "ExtractTextVM"
    }
}