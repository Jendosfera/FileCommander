package com.example.filecommander

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

class FileAdapter(
    private val onItemClick: (FileItem) -> Unit,
    private val onItemDoubleClick: (FileItem) -> Unit,
    private val onItemLongClick: (FileItem) -> Unit
) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

    private var items: List<FileItem> = emptyList()
    var selectedFile: java.io.File? = null

    fun submitList(newItems: List<FileItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.icon)
        val name: TextView = view.findViewById(R.id.name)
        val size: TextView = view.findViewById(R.id.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.size.text = FileUtils.formatSize(item.size)

        when {
            item.isDirectory -> holder.icon.load(R.drawable.ic_folder_yellow)
            isImage(item.extension) -> holder.icon.load(item.file) {
                crossfade(true)
                placeholder(R.drawable.ic_file_gray)
                error(R.drawable.ic_file_gray)
                size(256, 256)
            }
            else -> holder.icon.load(R.drawable.ic_file_gray)
        }

        holder.itemView.setBackgroundColor(
            if (selectedFile?.absolutePath == item.file.absolutePath) 0x55FFEB3B else 0x00000000
        )

        val detector = GestureDetector(holder.itemView.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    onItemClick(item)
                    return true
                }
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    onItemDoubleClick(item)
                    return true
                }
                override fun onLongPress(e: MotionEvent) {
                    onItemLongClick(item)
                }
            })

        holder.itemView.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false
        }
    }

    override fun getItemCount(): Int = items.size

    private fun isImage(ext: String): Boolean =
        ext.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
}