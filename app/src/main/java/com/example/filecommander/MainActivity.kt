package com.example.filecommander

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private var allFilesRequested = false

    private val panePaths = mutableMapOf<Int, String>()

    private val allFilesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        reloadPanes()
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            launchAllFilesSettings()
        } else {
            reloadPanes()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        allFilesRequested = savedInstanceState?.getBoolean(KEY_ALL_FILES_REQ) ?: false
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2
            override fun createFragment(position: Int): Fragment =
                FilePaneFragment.newInstance(position)
        }
        viewPager.offscreenPageLimit = 1

        if (!allFilesRequested) {
            requestStoragePermissions()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_ALL_FILES_REQ, allFilesRequested)
    }

    fun setPanePath(paneIndex: Int, path: String) {
        panePaths[paneIndex] = path
    }

    fun getOtherPanePath(paneIndex: Int): String? = panePaths[1 - paneIndex]

    private fun launchAllFilesSettings() {
        if (allFilesRequested) return
        allFilesRequested = true
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:$packageName")
        }
        try {
            allFilesLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            try {
                allFilesLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (_: ActivityNotFoundException) { }
        }
    }

    private fun reloadPanes() {
        supportFragmentManager.fragments.forEach { frag ->
            if (frag is FilePaneFragment) frag.reloadCurrentDir()
        }
    }

    private fun requestStoragePermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            launchAllFilesSettings()
        }
    }

    companion object {
        private const val KEY_ALL_FILES_REQ = "all_files_requested"
    }
}