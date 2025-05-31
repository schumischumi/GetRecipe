package com.example.getrecipe

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
//import androidx.compose.ui.semantics.text
//import androidx.compose.ui.semantics.text
//import androidx.compose.ui.semantics.text
//import androidx.compose.ui.semantics.text
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.getrecipe.databinding.FragmentSecondBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null
    private lateinit var extractTextViewModel: ExtractText
    private val recipeAreasViewModel: RecipeAreasViewModel by activityViewModels()
    private var currentCroppedImageUri: Uri? = null
    private var currentRecipeAreas: CropAreas? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        extractTextViewModel =
            ViewModelProvider(requireActivity())[ExtractText::class.java] // Example
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textViewProcessingStatus.text = "Waiting for all cropped recipe areas..."

        recipeAreasViewModel.croppedAreas.observe(viewLifecycleOwner) { areas ->
            currentRecipeAreas = areas
            if (areas != null && recipeAreasViewModel.areAllAreasSet()) {
                binding.buttonProcessCropped.isEnabled = true // Assuming a button 'buttonProcessRecipe'
                binding.textViewProcessingStatus.text = "All recipe areas ready. Click to process."
                // Load previews (assuming you have ImageViews like imageViewTitlePreview, etc.)
                areas.titleUri?.let { binding.imageViewTitlePreview.load(it) }
                areas.ingredientsUri?.let { binding.imageViewIngredientsPreview.load(it) }
                areas.preparationUri?.let { binding.imageViewPreparationPreview.load(it) }
                Log.i("ProcessingFragment", "Received all cropped areas: Title: ${areas.titleUri}, Ingredients: ${areas.ingredientsUri}, Prep: ${areas.preparationUri}")
            } else if (areas != null) {
                binding.buttonProcessCropped.isEnabled = false
                var status = "Waiting for: "
                if (areas.titleUri == null) status += "Title, "
                if (areas.ingredientsUri == null) status += "Ingredients, "
                if (areas.preparationUri == null) status += "Preparation"
                binding.textViewProcessingStatus.text = status.trimEnd(',', ' ')
            }
            else {
                binding.buttonProcessCropped.isEnabled = false
                binding.textViewProcessingStatus.text = "No recipe areas available."
                // Clear previews
                binding.imageViewTitlePreview.setImageDrawable(null)
                binding.imageViewIngredientsPreview.setImageDrawable(null)
                binding.imageViewPreparationPreview.setImageDrawable(null)
            }
        }
        recipeAreasViewModel.cropError.observe(viewLifecycleOwner) { error ->
            error?.let {
                binding.textViewProcessingStatus.text = "Cropping Error: $it"
                binding.buttonProcessCropped.isEnabled = false
            }
        }
        if (!extractTextViewModel.isInitialized) {
            val dataPath = Assets.getTessDataPath(requireContext())
            extractTextViewModel.initTesseract(dataPath, Config.TESS_LANG, Config.TESS_ENGINE)
        }
//        sharedViewModel.croppedImageFileUri.observe(viewLifecycleOwner) { uri -> // The 'uri' parameter here
//            Log.d("ProcessingFragment", "OBSERVER FIRED. ViewModel emitted URI: $uri")
//            currentCroppedImageUri = uri // Assigning the emitted value to the fragment's variable
//
//            if (uri != null) {
//                Log.d("ProcessingFragment", "OBSERVER: URI is NOT NULL. Enabling button. currentCroppedImageUri is now: $currentCroppedImageUri")
//                binding.buttonProcessCropped.isEnabled = true
//                binding.imageViewPreviewCropped.load(uri)
//                binding.textViewProcessingStatus.text = "Cropped image ready. Click to process."
//            } else {
//                Log.d("ProcessingFragment", "OBSERVER: URI IS NULL. Disabling button. currentCroppedImageUri is now: $currentCroppedImageUri")
//                binding.buttonProcessCropped.isEnabled = false
//                binding.imageViewPreviewCropped.setImageDrawable(null)
//                binding.textViewProcessingStatus.text = "No cropped image available (observer sets to null)."
//            }
//        }
//        binding.buttonProcessCropped.setOnClickListener {
//            Log.d("ProcessingFragment", "CLICKED: currentCroppedImageUri value is: $currentCroppedImageUri")
//            Log.d("ProcessingFragment", "CLICKED: ViewModel's liveData value is: ${sharedViewModel.croppedImageFileUri.value}")
//
//            currentCroppedImageUri?.let { uri -> // This is the 'it' that is null
//                binding.textViewProcessingStatus.text = "Preparing to process..."
//                // ... (rest of your logic)
//            } ?: run {
//                Log.w("ProcessingFragment", "Process button clicked, but currentCroppedImageUri was null.")
//                binding.textViewProcessingStatus.text = "No cropped image to process."
//            }
//        }
        binding.buttonProcessCropped.setOnClickListener {
            currentRecipeAreas?.let { areas ->
                if (!recipeAreasViewModel.areAllAreasSet()) {
                    binding.textViewProcessingStatus.text = "Not all areas are ready for processing."
                    return@setOnClickListener
                }
                binding.textViewProcessingStatus.text = "Preparing to process..."

                // Process Title
                areas.titleUri?.toFileFromContentUri(requireContext())?.let { titleFile ->
                    Log.d("ProcessingFragment", "Processing Title: ${titleFile.absolutePath}")
                    // You might want to adapt recognizeImage or have separate LiveData for results
                    extractTextViewModel.recognizeImage(titleFile, "title") // Or recognizeImage(titleFile, "title")
                    // Observe title result from extractTextViewModel
                } ?: Log.e("ProcessingFragment", "Title URI or File is null")

                // Process Ingredients
                areas.ingredientsUri?.toFileFromContentUri(requireContext())?.let { ingredientsFile ->
                    Log.d("ProcessingFragment", "Processing Ingredients: ${ingredientsFile.absolutePath}")
                    extractTextViewModel.recognizeImage(ingredientsFile, "ingredients") // Or recognizeImage(ingredientsFile, "ingredients")
                    // Observe ingredients result
                } ?: Log.e("ProcessingFragment", "Ingredients URI or File is null")

                // Process Preparation
                areas.preparationUri?.toFileFromContentUri(requireContext())?.let { preparationFile ->
                    Log.d("ProcessingFragment", "Processing Preparation: ${preparationFile.absolutePath}")
                    extractTextViewModel.recognizeImage(preparationFile, "preparation") // Or recognizeImage(preparationFile, "preparation")
                    // Observe preparation result
                } ?: Log.e("ProcessingFragment", "Preparation URI or File is null")
            } ?: run {
                Log.w("ProcessingFragment", "Process button clicked, but no cropped image URI.")
                binding.textViewProcessingStatus.text = "No cropped image to process."
            }
        }

        extractTextViewModel.processing.observe(viewLifecycleOwner) {
        }
        extractTextViewModel.progress.observe(viewLifecycleOwner) { progressText ->
             binding.textViewProcessingStatus.text = progressText
        }
        extractTextViewModel.titleResult.observe(viewLifecycleOwner) { titleText ->
            // titleText will be the String result from OCR, or null/error string
            // you defined in ExtractTextViewModel
            if (titleText != null) {
                binding.textViewTitleResult.text = titleText
                Log.i("ProcessingFragment", "Title OCR Result: $titleText")
            } else {
                // Handle cases where titleText is null (e.g., initial state, or if you post null on error/clear)
                // binding.textViewTitleResult.text = "Waiting for title or error..." // Or keep current "Processing..."
                Log.i("ProcessingFragment", "Title OCR Result received null or cleared.")
            }
        }

        // Observe Ingredients Result
        extractTextViewModel.ingredientsResult.observe(viewLifecycleOwner) { ingredientsText ->
            if (ingredientsText != null) {
                binding.textViewIngredientsResult.text = ingredientsText
                Log.i("ProcessingFragment", "Ingredients OCR Result: $ingredientsText")
            } else {
                // binding.textViewIngredientsResult.text = "Waiting for ingredients or error..."
                Log.i("ProcessingFragment", "Ingredients OCR Result received null or cleared.")
            }
        }

        // Observe Preparation Result
        extractTextViewModel.preparationResult.observe(viewLifecycleOwner) { preparationText ->
            if (preparationText != null) {
                binding.textViewPreparationResult.text = preparationText
                Log.i("ProcessingFragment", "Preparation OCR Result: $preparationText")
            } else {
                // binding.textViewPreparationResult.text = "Waiting for preparation or error..."
                Log.i("ProcessingFragment", "Preparation OCR Result received null or cleared.")
            }
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
fun Uri.toFileFromContentUri(context: Context): File? {
    if (this.scheme != "content") {
        Log.w("UriToFile", "URI scheme is not 'content': $this. Trying to create File directly if it's a file URI.")
        // If it's already a file URI, try to make a file object from path
        if (this.scheme == "file") {
            this.path?.let { return File(it) }
        }
        return null
    }

    var file: File? = null
    try {
        // Attempt to get the display name to use for the temp file, fallback if null
        val displayName = System.currentTimeMillis().toString() // Fallback name
        val tempFile = File(context.cacheDir, "$displayName.tmp") // Create in cache

        context.contentResolver.openInputStream(this)?.use { inputStream ->
            FileOutputStream(tempFile).use { outputStream ->
                inputStream.copyTo(outputStream)
                file = tempFile // Assign file only if copy is successful
            }
        }
    } catch (e: IOException) {
        Log.e("UriToFile", "IOException during content URI to File conversion: $this", e)
    } catch (e: Exception) {
        Log.e("UriToFile", "Exception during content URI to File conversion: $this", e)
    }
    return file
}