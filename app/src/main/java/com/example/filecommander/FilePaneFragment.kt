package com.example.filecommander

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FilePaneFragment : Fragment() {

    private var paneIndex: Int = 0
    private var currentDir: File? = null
    private lateinit var adapter: FileAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var breadcrumbContainer: LinearLayout
    private lateinit var breadcrumbScroll: HorizontalScrollView

    private val rootDir: File by lazy {
        Environment.getExternalStorageDirectory() ?: File("/storage/emulated/0")
    }

    companion object {
        private const val TAG = "FileCommander"
        private const val ARG_PANE_INDEX = "pane_index"
        private const val KEY_CURRENT_DIR = "current_dir"
        fun newInstance(index: Int) = FilePaneFragment().apply {
            arguments = Bundle().apply { putInt(ARG_PANE_INDEX, index) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        paneIndex = arguments?.getInt(ARG_PANE_INDEX) ?: 0
        val saved = savedInstanceState?.getString(KEY_CURRENT_DIR)
        currentDir = if (saved != null) File(saved) else defaultDir(paneIndex)
    }

    private fun defaultDir(index: Int): File {
        if (index == 0) return if (rootDir.exists()) rootDir else File("/")
        val dl = File(rootDir, "Download")
        return if (dl.exists()) dl else rootDir
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentDir?.let { outState.putString(KEY_CURRENT_DIR, it.absolutePath) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_file_pane, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        try {
            recyclerView = view.findViewById(R.id.recyclerView)
            breadcrumbContainer = view.findViewById(R.id.breadcrumbContainer)
            breadcrumbScroll = view.findViewById(R.id.breadcrumbScroll)

            adapter = FileAdapter(
                onItemClick = { adapter.selectedFile = it.file; adapter.notifyDataSetChanged() },
                onItemDoubleClick = {
                    if (it.isDirectory) loadDirectory(it.file) else openFile(it)
                },
                onItemLongClick = { showContextMenu(it) }
            )
            recyclerView.layoutManager = LinearLayoutManager(requireContext())
            recyclerView.adapter = adapter

            view.findViewById<View>(R.id.btnBack).setOnClickListener { goToParent() }
            view.findViewById<View>(R.id.btnMenu).setOnClickListener { showCreateMenu() }
            view.findViewById<View>(R.id.btnCreateFile).setOnClickListener { showCreateDialog(true) }
            view.findViewById<View>(R.id.btnCreateDir).setOnClickListener { showCreateDialog(false) }
            view.findViewById<View>(R.id.btnCopy).setOnClickListener { copySelected(false) }
            view.findViewById<View>(R.id.btnMove).setOnClickListener { copySelected(true) }

            currentDir?.let { loadDirectory(it) }
        } catch (e: Throwable) {
            Log.e(TAG, "onViewCreated failed", e)
        }
    }

    fun reloadCurrentDir() {
        currentDir?.let { loadDirectory(it) }
    }

    private fun loadDirectory(dir: File) {
        try {
            if (!isAdded || view == null) return
            if (!dir.exists() || !dir.isDirectory) return

            currentDir = dir
            (activity as? MainActivity)?.setPanePath(paneIndex, dir.absolutePath)

            val files = try { dir.listFiles() } catch (e: Throwable) { null }
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()

            val quickItems = files.map { f ->
                FileItem(
                    file = f,
                    name = f.name,
                    isDirectory = f.isDirectory,
                    size = if (f.isDirectory) -1L else runCatching { f.length() }.getOrDefault(0L),
                    extension = f.extension
                )
            }
            adapter.selectedFile = null
            adapter.submitList(quickItems)
            updateBreadcrumb()

            val dirs = quickItems.filter { it.isDirectory }
            if (dirs.isEmpty()) return
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val sizes: Map<File, Long> = withContext(Dispatchers.IO) {
                        dirs.associate { it.file to FileUtils.folderSize(it.file) }
                    }
                    if (!isAdded || view == null || currentDir != dir) return@launch
                    val updated = quickItems.map { item ->
                        val sz = sizes[item.file]
                        if (item.isDirectory && sz != null) item.copy(size = sz) else item
                    }
                    adapter.submitList(updated)
                } catch (e: Throwable) {
                    Log.e(TAG, "folderSize failed", e)
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "loadDirectory failed on ${dir.absolutePath}", e)
        }
    }

    private fun updateBreadcrumb() {
        val dir = currentDir ?: return
        breadcrumbContainer.removeAllViews()

        breadcrumbContainer.addView(makeCrumb("root") { loadDirectory(rootDir) })

        val rootPath = rootDir.absolutePath
        val fullPath = dir.absolutePath
        if (!fullPath.startsWith(rootPath)) return

        val rel = fullPath.removePrefix(rootPath).trim('/')
        if (rel.isEmpty()) return

        var acc = rootPath
        for (seg in rel.split("/").filter { it.isNotEmpty() }) {
            acc += "/$seg"
            val path = acc
            breadcrumbContainer.addView(makeCrumb(" > $seg") { loadDirectory(File(path)) })
        }
        breadcrumbScroll.post { breadcrumbScroll.fullScroll(View.FOCUS_RIGHT) }
    }

    private fun makeCrumb(text: String, onClick: () -> Unit): TextView =
        TextView(requireContext()).apply {
            this.text = text
            setTextColor(0xFF000000.toInt())
            textSize = 15f
            setPadding(8, 4, 8, 4)
            setOnClickListener { onClick() }
        }

    private fun goToParent() {
        val cur = currentDir ?: return
        if (cur.absolutePath == rootDir.absolutePath) {
            Toast.makeText(requireContext(), "Это корень", Toast.LENGTH_SHORT).show()
            return
        }
        val parent = cur.parentFile
        if (parent == null || !parent.exists() ||
            parent.absolutePath.length < rootDir.absolutePath.length) {
            loadDirectory(rootDir)
            return
        }
        loadDirectory(parent)
    }

    private fun openFile(item: FileItem) {
        try {
            val ctx = requireContext()
            val uri = FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.fileprovider",
                item.file
            )
            val ext = item.extension.lowercase()
            val mime = if (ext.isEmpty()) "*/*"
                       else MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), "Нет приложения для открытия", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Не удалось открыть: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCreateMenu() {
        AlertDialog.Builder(requireContext())
            .setTitle("Создать")
            .setItems(arrayOf("Файл", "Каталог")) { _, i -> showCreateDialog(i == 0) }
            .show()
    }

    private fun showCreateDialog(isFile: Boolean) {
        val dir = currentDir ?: return
        val input = EditText(requireContext()).apply {
            hint = if (isFile) "Имя файла" else "Имя каталога"
        }
        AlertDialog.Builder(requireContext())
            .setTitle(if (isFile) "Новый файл" else "Новый каталог")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                val f = File(dir, name)
                try {
                    if (isFile) f.createNewFile() else f.mkdirs()
                    loadDirectory(dir)
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showContextMenu(item: FileItem) {
        val opts = arrayOf("Копировать", "Вырезать", "Переименовать", "Просмотр", "Правка", "Удалить")
        AlertDialog.Builder(requireContext())
            .setTitle(item.name)
            .setItems(opts) { _, w ->
                when (w) {
                    0 -> copyToOtherPane(item.file, false)
                    1 -> copyToOtherPane(item.file, true)
                    2 -> showRenameDialog(item.file)
                    3 -> openFile(item)
                    4 -> Toast.makeText(requireContext(), "Правка (в разработке)", Toast.LENGTH_SHORT).show()
                    5 -> {
                        if (item.file.deleteRecursively()) currentDir?.let { loadDirectory(it) }
                    }
                }
            }.show()
    }

    private fun showRenameDialog(file: File) {
        val input = EditText(requireContext()).apply { setText(file.name) }
        AlertDialog.Builder(requireContext())
            .setTitle("Переименовать")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) return@setPositiveButton
                if (file.renameTo(File(file.parentFile, newName))) currentDir?.let { loadDirectory(it) }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun copySelected(move: Boolean) {
        val f = adapter.selectedFile
        if (f == null) {
            Toast.makeText(requireContext(), "Сначала выберите объект", Toast.LENGTH_SHORT).show()
            return
        }
        copyToOtherPane(f, move)
    }

    private fun copyToOtherPane(file: File, move: Boolean) {
        val target = (activity as? MainActivity)?.getOtherPanePath(paneIndex)
        if (target == null) {
            Toast.makeText(requireContext(), "Второе окно не готово", Toast.LENGTH_SHORT).show()
            return
        }
        val targetDir = File(target)
        if (!targetDir.exists() || !targetDir.isDirectory) {
            Toast.makeText(requireContext(), "Целевой каталог недоступен", Toast.LENGTH_SHORT).show()
            return
        }
        if (targetDir.absolutePath == file.parentFile?.absolutePath) {
            Toast.makeText(requireContext(), "Источник и приёмник совпадают", Toast.LENGTH_SHORT).show()
            return
        }
        val dest = File(targetDir, file.name)
        if (dest.exists()) {
            Toast.makeText(requireContext(), "Уже существует: ${file.name}", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            if (move) FileUtils.move(file, dest) else FileUtils.copy(file, dest)
            Toast.makeText(requireContext(),
                (if (move) "Перемещено" else "Скопировано") + ": ${file.name} → ${targetDir.absolutePath}",
                Toast.LENGTH_SHORT).show()
            // Обновляем оба окна
            (activity as? MainActivity)?.reloadAll()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}