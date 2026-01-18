package com.example.classi_backend

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class GalleryActivity : AppCompatActivity() {

    private lateinit var adapter: GalleryAdapter
    private lateinit var submitButton: Button
    private lateinit var selectAllButton: Button
    private var isAllSelected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        val imageUris = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("image_uris", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra("image_uris")
        } ?: arrayListOf()

        submitButton = findViewById(R.id.submit_button)
        selectAllButton = findViewById(R.id.select_all_button)
        
        val recyclerView = findViewById<RecyclerView>(R.id.gallery_recycler_view)
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        
        adapter = GalleryAdapter(imageUris) { selectedCount ->
            updateSubmitButton(selectedCount)
        }
        recyclerView.adapter = adapter

        selectAllButton.setOnClickListener {
            isAllSelected = !isAllSelected
            adapter.selectAll(isAllSelected)
            selectAllButton.text = if (isAllSelected) "Deselect All" else "Select All"
        }

        submitButton.setOnClickListener {
            onSubmit(adapter.getSelectedUris())
        }
    }

    private fun updateSubmitButton(selectedCount: Int) {
        submitButton.isEnabled = selectedCount > 0
    }

    private fun onSubmit(selectedUris: List<Uri>) {
        // This is the empty function for now
        // In the future, this is where you'd trigger your NN processing
    }

    class GalleryAdapter(
        private val uris: List<Uri>,
        private val onSelectionChanged: (Int) -> Unit
    ) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

        private val selectedIndices = mutableSetOf<Int>()

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.gallery_image)
            val overlay: View = view.findViewById(R.id.selection_overlay)
            val checkbox: CheckBox = view.findViewById(R.id.selection_checkbox)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gallery, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val uri = uris[position]
            holder.imageView.setImageURI(uri)
            
            val isSelected = selectedIndices.contains(position)
            holder.checkbox.isChecked = isSelected
            holder.overlay.visibility = if (isSelected) View.VISIBLE else View.GONE

            holder.itemView.setOnClickListener {
                toggleSelection(position)
            }
        }

        private fun toggleSelection(position: Int) {
            if (selectedIndices.contains(position)) {
                selectedIndices.remove(position)
            } else {
                selectedIndices.add(position)
            }
            notifyItemChanged(position)
            onSelectionChanged(selectedIndices.size)
        }

        fun selectAll(select: Boolean) {
            if (select) {
                selectedIndices.addAll(uris.indices)
            } else {
                selectedIndices.clear()
            }
            notifyDataSetChanged()
            onSelectionChanged(selectedIndices.size)
        }

        fun getSelectedUris(): List<Uri> {
            return selectedIndices.map { uris[it] }
        }

        override fun getItemCount() = uris.size
    }
}
