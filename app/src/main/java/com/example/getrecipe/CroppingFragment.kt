package com.example.getrecipe // Your package

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

import androidx.navigation.fragment.findNavController
import com.example.getrecipe.databinding.FragmentCroppingBinding // Your binding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Assuming your Assets class for the source image
// object Assets { ... }

enum class CropStage {
    SELECTING_TITLE,
    SELECTING_INGREDIENTS,
    SELECTING_PREPARATION,
    DONE
}

class CroppingFragment : Fragment() {

    private var _binding: FragmentCroppingBinding? = null
    private val binding get() = _binding!!

    // Use the new ViewModel
    private val recipeAreasViewModel: RecipeAreasViewModel by activityViewModels()

    private var originalBitmap: Bitmap? = null
    private val selectionRect = RectF()
    private var startX = 0f
    private var startY = 0f

    private var currentCropStage = CropStage.SELECTING_TITLE

    private var tempTitleFile: File? = null
    private var tempIngredientsFile: File? = null
    private var tempPreparationFile: File? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCroppingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        originalBitmap = Assets.getImageBitmap(requireContext())

        if (originalBitmap != null) {
            binding.imageViewToCrop.setImageBitmap(originalBitmap)
            setupManualSelection()
            updateUiForCurrentStage()
        } else {
            // ... (handle image load error) ...
            binding.buttonConfirmCurrentArea.isEnabled = false // Example for a confirm button
            recipeAreasViewModel.setCropError("Failed to load source image.")
        }

        // Example: Assume you have a button like 'buttonConfirmCurrentArea'
        binding.buttonConfirmCurrentArea.setOnClickListener {
            originalBitmap?.let { bmp ->
                if (selectionRect.width() > 0 && selectionRect.height() > 0) {
                    processCurrentAreaSelection(bmp, selectionRect)
                } else {
                    recipeAreasViewModel.setCropError("Please select an area for ${currentCropStage.name.toLowerCase(Locale.ROOT).replace('_',' ')}.")
                }
            }
        }

        // Example: Assume a button 'buttonDoneAllCropping'
        binding.buttonDoneAllCropping.setOnClickListener {
            if (recipeAreasViewModel.areAllAreasSet()) {
                // Navigate or signal completion
                // findNavController().navigate(R.id.action_croppingFragment_to_processingFragment)
                Log.i("CroppingFragment", "All areas cropped and set in ViewModel.")
            } else {
                recipeAreasViewModel.setCropError("Not all areas have been selected yet.")
            }
        }
    }

    private fun updateUiForCurrentStage() {
        selectionRect.setEmpty() // Reset selection rectangle for new area
        binding.selectionOverlay.visibility = View.GONE
        binding.buttonConfirmCurrentArea.isEnabled = originalBitmap != null // Enable if image is loaded

        when (currentCropStage) {
            CropStage.SELECTING_TITLE -> {
                binding.textViewCurrentSelectionPrompt.text = "Select Title Area"
                binding.buttonDoneAllCropping.visibility = View.GONE
                binding.buttonConfirmCurrentArea.text = "Confirm Title Area"
            }
            CropStage.SELECTING_INGREDIENTS -> {
                binding.textViewCurrentSelectionPrompt.text = "Select Ingredients Area"
                binding.buttonConfirmCurrentArea.text = "Confirm Ingredients Area"
            }
            CropStage.SELECTING_PREPARATION -> {
                binding.textViewCurrentSelectionPrompt.text = "Select Preparation Area"
                binding.buttonConfirmCurrentArea.text = "Confirm Preparation Area"
            }
            CropStage.DONE -> {
                binding.textViewCurrentSelectionPrompt.text = "All areas selected!"
                binding.buttonConfirmCurrentArea.visibility = View.GONE
                binding.selectionOverlay.visibility = View.GONE
                binding.buttonDoneAllCropping.visibility = View.VISIBLE
                binding.buttonDoneAllCropping.isEnabled = recipeAreasViewModel.areAllAreasSet()
            }
        }
    }

    private fun processCurrentAreaSelection(sourceBitmap: Bitmap, cropRectInViewCoords: RectF) {
        // --- Coordinate Transformation (same as before) ---
        val imageView = binding.imageViewToCrop
        val displayMatrix = Matrix()
        imageView.imageMatrix.invert(displayMatrix)
        val bitmapSpaceRect = RectF(cropRectInViewCoords)
        displayMatrix.mapRect(bitmapSpaceRect)
        // (Clamping logic for bitmapSpaceRect as before)
        bitmapSpaceRect.left = Math.max(0f, bitmapSpaceRect.left)
        bitmapSpaceRect.top = Math.max(0f, bitmapSpaceRect.top)
        bitmapSpaceRect.right = Math.min(sourceBitmap.width.toFloat(), bitmapSpaceRect.right)
        bitmapSpaceRect.bottom = Math.min(sourceBitmap.height.toFloat(), bitmapSpaceRect.bottom)

        if (bitmapSpaceRect.width() <= 0 || bitmapSpaceRect.height() <= 0) {
            recipeAreasViewModel.setCropError("Invalid area selected for ${currentCropStage.name.toLowerCase(Locale.ROOT)}.")
            return
        }

        try {
            val croppedBitmap = Bitmap.createBitmap(
                sourceBitmap,
                bitmapSpaceRect.left.toInt(),
                bitmapSpaceRect.top.toInt(),
                bitmapSpaceRect.width().toInt(),
                bitmapSpaceRect.height().toInt()
            )

            // Save the cropped bitmap to a temporary file
            val areaName = currentCropStage.name.toLowerCase(Locale.ROOT)
            val tempCroppedFile = createTemporaryCroppedFile(areaName)
            FileOutputStream(tempCroppedFile).use { out ->
                croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            val croppedFileUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                tempCroppedFile
            )

            // Update the ViewModel based on the current stage
            when (currentCropStage) {
                CropStage.SELECTING_TITLE -> {
                    recipeAreasViewModel.setTitleUri(croppedFileUri)
                    tempTitleFile = tempCroppedFile // Keep reference if needed for cleanup
                    currentCropStage = CropStage.SELECTING_INGREDIENTS
                }
                CropStage.SELECTING_INGREDIENTS -> {
                    recipeAreasViewModel.setIngredientsUri(croppedFileUri)
                    tempIngredientsFile = tempCroppedFile
                    currentCropStage = CropStage.SELECTING_PREPARATION
                }
                CropStage.SELECTING_PREPARATION -> {
                    recipeAreasViewModel.setPreparationUri(croppedFileUri)
                    tempPreparationFile = tempCroppedFile
                    currentCropStage = CropStage.DONE
                }
                CropStage.DONE -> { /* Should not happen here */ }
            }
            Log.i("CroppingFragment", "Cropped $areaName saved to URI: $croppedFileUri")
            updateUiForCurrentStage() // Update UI for the next stage

        } catch (e: Exception) {
            Log.e("CroppingFragment", "Error cropping/saving for $currentCropStage", e)
            recipeAreasViewModel.setCropError("Failed to crop ${currentCropStage.name.toLowerCase(Locale.ROOT)}: ${e.message}")
        }
    }

    @Throws(IOException::class)
    private fun createTemporaryCroppedFile(areaIdentifier: String): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File? = requireContext().cacheDir
        return File.createTempFile("CROP_${areaIdentifier}_${timeStamp}_", ".jpg", storageDir).apply {
            // deleteOnExit() // Consider if you want these to persist briefly or be cleaned up
        }
    }

    // setupManualSelection(), normalizeAndClampRect(), updateOverlayBounds() remain similar
    // ... (ensure they are present and working) ...

    private fun setupManualSelection() { /* ... same as before ... */ }
    private fun normalizeAndClampRect(rect: RectF, viewWidth: Int, viewHeight: Int) { /* ... same as before ... */ }
    private fun updateOverlayBounds() { /* ... same as before ... */ }


    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up temporary files if they weren't passed on or if fragment is destroyed mid-process
        tempTitleFile?.delete()
        tempIngredientsFile?.delete()
        tempPreparationFile?.delete()
        _binding = null
    }
}