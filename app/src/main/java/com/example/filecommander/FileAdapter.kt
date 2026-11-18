package com.example.filecommander

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

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

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.size.text = FileUtils.formatSize(item.size)
        holder.icon.setImageResource(
            if (item.isDirectory) android.R.drawable.ic_menu_view
            else android.R.drawable.ic_menu_save
        )

        holder.itemView.setBackgroundColor(
            if (selectedFile?.absolutePath == item.file.absolutePath) 0x3300BFFF else 0x00000000
        )

        // Обработка одиночного/двойного тапа в одном listener'е
        holder.itemView.setOnClickListener(object : View.OnClickListener {
            private var lastClick = 0L
            override fun onClick(v: View?) {
                val now = System.currentTimeMillis()
                if (now - lastClick < 300) {
                    onItemDoubleClick(item)
                    lastClick = 0L
                } else {
                    onItemClick(item)
                    lastClick = now
                }
            }
        })
        holder.itemView.setOnLongClickListener {
            onItemLongClick(item)
            true
        }
    }

    override fun getItemCount(): Int = items.size
}