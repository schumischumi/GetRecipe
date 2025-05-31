package com.example.getrecipe // Your package name

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.fragment.findNavController
import coil.load // Using Coil for image loading, ensure it's in your dependencies
import com.example.getrecipe.databinding.FragmentFirstBinding
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!

    private var currentPhotoUri: Uri? = null // To store URI from camera or picker
    private var tempImageFileForCamera: File? = null // To store file path for camera

    // ActivityResultLaunchers
    private lateinit var requestCameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>
    private lateinit var selectImageLauncher: ActivityResultLauncher<String>


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Camera Permission Launcher
        requestCameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("FirstFragment", "Camera permission granted")
                openCamera()
            } else {
                Log.d("FirstFragment", "Camera permission denied")
                Toast.makeText(requireContext(), "Camera permission is required to take photos.", Toast.LENGTH_SHORT).show()
            }
        }

        // Initialize Take Picture Launcher
        takePictureLauncher = registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success: Boolean ->
            if (success) {
                Log.d("FirstFragment", "Image captured successfully. URI: $currentPhotoUri, File: ${tempImageFileForCamera?.absolutePath}")
                currentPhotoUri = Uri.fromFile(tempImageFileForCamera) // Ensure currentPhotoUri is set from the file
                binding.imageViewPreview.load(currentPhotoUri) {
                    placeholder(R.drawable.ic_launcher_background) // Optional placeholder
                    error(com.google.android.material.R.drawable.mtrl_ic_error) // Optional error drawable
                }
                binding.buttonNext.isEnabled = true
            } else {
                Log.d("FirstFragment", "Image capture failed or was cancelled.")
                // tempImageFileForCamera might still exist, consider deleting if not needed
                // tempImageFileForCamera?.delete()
            }
        }

        // Initialize Select Image Launcher
        selectImageLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                Log.d("FirstFragment", "Image selected from storage: $it")
                currentPhotoUri = it
                binding.imageViewPreview.load(currentPhotoUri) {
                    placeholder(R.drawable.ic_launcher_background)
                    error(com.google.android.material.R.drawable.mtrl_ic_error)
                }
                binding.buttonNext.isEnabled = true
            } ?: Log.d("FirstFragment", "No image selected from storage.")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initially disable next button until an image is loaded
        binding.buttonNext.isEnabled = false

        binding.buttonLoadImage.setOnClickListener {
            showImageSourceDialog()
        }

        binding.buttonNext.setOnClickListener {
            currentPhotoUri?.let { uri ->
                val action = FirstFragmentDirections.actionFirstFragmentToCroppingFragment(uri.toString())
                findNavController().navigate(action)
            } ?: Toast.makeText(requireContext(), "Please select an image first.", Toast.LENGTH_SHORT).show()
        }

        // If you want to load a default image on startup (optional)
        // loadDefaultImage()
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        AlertDialog.Builder(requireContext())
            .setTitle("Select Image Source")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> checkCameraPermissionAndOpen() // Take Photo
                    1 -> openGallery() // Choose from Gallery
                    2 -> dialog.dismiss() // Cancel
                }
            }
            .show()
    }

    private fun checkCameraPermissionAndOpen() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                // Show an explanation to the user *asynchronously*
                AlertDialog.Builder(requireContext())
                    .setTitle("Camera Permission Needed")
                    .setMessage("This app needs camera access to take pictures for recipe processing.")
                    .setPositiveButton("OK") { _, _ ->
                        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                    .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                    .show()
            }
            else -> {
                // Directly request the permission
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun openCamera() {
        try {
            tempImageFileForCamera = createImageFile(requireContext())
            tempImageFileForCamera?.let { file ->
                val authority = "${requireContext().packageName}.fileprovider"
                currentPhotoUri = FileProvider.getUriForFile(
                    requireContext(),
                    authority, // Make sure this matches AndroidManifest
                    file
                )
                Log.d("FirstFragment", "FileProvider URI for camera: $currentPhotoUri")
                takePictureLauncher.launch(currentPhotoUri) // Pass the FileProvider URI to the camera
            }
        } catch (ex: IOException) {
            Log.e("FirstFragment", "Error creating image file for camera", ex)
            Toast.makeText(requireContext(), "Error preparing camera.", Toast.LENGTH_SHORT).show()
        }
    }


    private fun openGallery() {
        selectImageLauncher.launch("image/*") // MIME type for images
    }

    @Throws(IOException::class)
    private fun createImageFile(context: Context): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        // Get the directory for storing images. Use app's cache directory.
        val storageDir: File? = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) // Or context.cacheDir for private cache

        // Ensure the directory exists.
        // For cache, this might not be strictly necessary as it's usually writable,
        // but for getExternalFilesDir it's good practice.
        if (storageDir != null && !storageDir.exists()) {
            if (!storageDir.mkdirs()) {
                Log.e("FirstFragment", "Failed to create directory for images: ${storageDir.absolutePath}")
                // Fallback to cacheDir if primary external dir fails and is not null
                // Or simply throw IOException if this path is critical
            }
        }
        // Save a file: path for use with ACTION_VIEW intents
        // Use cache dir for temporary files
        val imageFile = File.createTempFile(
            imageFileName, /* prefix */
            ".jpg",        /* suffix */
            storageDir    /* directory, falls back to cache if storageDir is null or fails */
                ?: context.cacheDir // Fallback to internal cache if external is unavailable
        )
        Log.d("FirstFragment", "Image file created at: ${imageFile.absolutePath}")
        return imageFile
    }


    // Optional: If you still want to load the default "good_example.jpg" from assets
    // private fun loadDefaultImage() {
    //     val defaultImageFileName = "good_example.jpg"
    //     try {
    //         val inputStream = requireContext().assets.open(defaultImageFileName)
    //         val tempFile = File(requireContext().cacheDir, defaultImageFileName)
    //         tempFile.outputStream().use { fileOut ->
    //             inputStream.copyTo(fileOut)
    //         }
    //         inputStream.close()
    //         currentPhotoUri = Uri.fromFile(tempFile) // Or FileProvider.getUriForFile for sharing
    //         binding.imageViewPreview.load(currentPhotoUri)
    //         binding.buttonNext.isEnabled = true
    //         Log.d("FirstFragment", "Loaded default image: $defaultImageFileName to ${currentPhotoUri}")
    //     } catch (e: IOException) {
    //         Log.e("FirstFragment", "Error loading default image from assets", e)
    //         Toast.makeText(requireContext(), "Failed to load default image.", Toast.LENGTH_SHORT).show()
    //     }
    // }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}