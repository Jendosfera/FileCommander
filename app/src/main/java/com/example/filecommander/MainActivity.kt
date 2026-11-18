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
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private var allFilesRequested = false

    private val panePaths = mutableMapOf<Int, String>()

    private val allFilesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        Log.d(TAG, "returned from all-files settings")
        reloadPanes()
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        Log.d(TAG, "permission result: $result")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        ) {
            launchAllFilesSettings()
        } else {
            reloadPanes()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installCrashHandler()
        super.onCreate(savedInstanceState)
        allFilesRequested = savedInstanceState?.getBoolean(KEY_ALL_FILES_REQ) ?: false
        Log.d(TAG, "onCreate, allFilesRequested=$allFilesRequested, isExtMgr=${Environment.isExternalStorageManager()}")

        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        viewPager.offscreenPageLimit = 1
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 2
            override fun createFragment(position: Int): Fragment =
                FilePaneFragment.newInstance(position)
        }

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
        } catch (e: ActivityNotFoundException) {
            try {
                allFilesLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (e2: Exception) {
                Log.e(TAG, "cannot open settings", e2)
                Toast.makeText(this, "Не удалось открыть настройки доступа", Toast.LENGTH_LONG).show()
            }
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
            permissions += Manifest.permission.READ_MEDIA_IMAGES
            permissions += Manifest.permission.READ_MEDIA_VIDEO
            permissions += Manifest.permission.READ_MEDIA_AUDIO
        } else {
            permissions += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        ) {
            launchAllFilesSettings()
        }
    }

    private fun installCrashHandler() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val log = "\n=== $ts ===\n${Log.getStackTraceString(e)}\n"
            // app-specific: доступен через MTP
            try {
                getExternalFilesDir(null)?.let { dir ->
                    dir.mkdirs()
                    File(dir, "crash.log").appendText(log)
                }
            } catch (_: Throwable) {}
            // корень внешнего стораджа: доступен через файловый менеджер (если уже дали доступ)
            try {
                File("/storage/emulated/0/FileCommander-crash.log").appendText(log)
            } catch (_: Throwable) {}
            // внутренний: на случай если ничего снаружи не пишется
            try {
                File(filesDir, "crash.log").appendText(log)
            } catch (_: Throwable) {}
            prev?.uncaughtException(t, e)
        }
    }

    companion object {
        private const val TAG = "FileCommander"
        private const val KEY_ALL_FILES_REQ = "all_files_requested"
    }
}