package com.example.filecommander

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
    private lateinit var currentDir: File
    private lateinit var adapter: FileAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var breadcrumbContainer: LinearLayout
    private lateinit var breadcrumbScroll: HorizontalScrollView

    companion object {
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
        currentDir = when {
            saved != null -> File(saved)
            paneIndex == 0 -> File("/storage/emulated/0")
            else -> File("/storage/emulated/0/Download")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::currentDir.isInitialized) {
            outState.putString(KEY_CURRENT_DIR, currentDir.absolutePath)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_file_pane, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = view.findViewById(R.id.recyclerView)
        breadcrumbContainer = view.findViewById(R.id.breadcrumbContainer)
        breadcrumbScroll = view.findViewById(R.id.breadcrumbScroll)

        adapter = FileAdapter(
            onItemClick = { adapter.selectedFile = it.file; adapter.notifyDataSetChanged() },
            onItemDoubleClick = { if (it.isDirectory) loadDirectory(it.file) else openFile(it) },
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

        loadDirectory(currentDir)
    }

    fun reloadCurrentDir() {
        if (::currentDir.isInitialized) loadDirectory(currentDir)
    }

    private fun loadDirectory(dir: File) {
        if (!isAdded || view == null) return
        if (!dir.exists() || !dir.isDirectory) return
        currentDir = dir
        (activity as? MainActivity)?.setPanePath(paneIndex, dir.absolutePath)

        val files = dir.listFiles()
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()

        val quickItems = files.map { f ->
            FileItem(
                file = f,
                name = f.name,
                isDirectory = f.isDirectory,
                size = if (f.isDirectory) -1L else f.length(),
                extension = f.extension
            )
        }
        adapter.selectedFile = null
        adapter.submitList(quickItems)
        updateBreadcrumb()

        val dirs = quickItems.filter { it.isDirectory }
        if (dirs.isEmpty()) return
        viewLifecycleOwner.lifecycleScope.launch {
            val sizes: Map<File, Long> = withContext(Dispatchers.IO) {
                dirs.associate { it.file to FileUtils.folderSize(it.file) }
            }
            if (!isAdded || view == null || currentDir != dir) return@launch
            val updated = quickItems.map { item ->
                val sz = sizes[item.file]
                if (item.isDirectory && sz != null) item.copy(size = sz) else item
            }
            adapter.submitList(updated)
        }
    }

    private fun updateBreadcrumb() {
        breadcrumbContainer.removeAllViews()
        val segments = currentDir.absolutePath.split("/").filter { it.isNotEmpty() }
        var acc = ""

        breadcrumbContainer.addView(makeCrumb("/") { loadDirectory(File("/")) })
        for (seg in segments) {
            acc += "/$seg"
            val path = acc
            breadcrumbContainer.addView(makeCrumb(" > $seg") { loadDirectory(File(path)) })
        }
        breadcrumbScroll.post { breadcrumbScroll.fullScroll(View.FOCUS_RIGHT) }
    }

    private fun makeCrumb(text: String, onClick: () -> Unit): TextView =
        TextView(requireContext()).apply {
            this.text = text
            setPadding(8, 4, 8, 4)
            setOnClickListener { onClick() }
        }

    private fun goToParent() {
        val parent = currentDir.parentFile ?: return
        if (!parent.exists()) return
        loadDirectory(parent)
    }

    private fun openFile(item: FileItem) {
        Toast.makeText(requireContext(), "Запуск: ${item.name}", Toast.LENGTH_SHORT).show()
    }

    private fun showCreateMenu() {
        AlertDialog.Builder(requireContext())
            .setTitle("Создать")
            .setItems(arrayOf("Файл", "Каталог")) { _, i -> showCreateDialog(i == 0) }
            .show()
    }

    private fun showCreateDialog(isFile: Boolean) {
        val input = EditText(requireContext()).apply {
            hint = if (isFile) "Имя файла" else "Имя каталога"
        }
        AlertDialog.Builder(requireContext())
            .setTitle(if (isFile) "Новый файл" else "Новый каталог")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                val f = File(currentDir, name)
                try {
                    if (isFile) f.createNewFile() else f.mkdirs()
                    loadDirectory(currentDir)
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
                    3 -> Toast.makeText(requireContext(), "Просмотр", Toast.LENGTH_SHORT).show()
                    4 -> Toast.makeText(requireContext(), "Правка", Toast.LENGTH_SHORT).show()
                    5 -> { if (item.file.deleteRecursively()) loadDirectory(currentDir) }
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
                if (file.renameTo(File(file.parentFile, newName))) loadDirectory(currentDir)
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
        if (!targetDir.exists()) return
        try {
            val dest = File(targetDir, file.name)
            if (move) FileUtils.move(file, dest) else FileUtils.copy(file, dest)
            loadDirectory(currentDir)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}