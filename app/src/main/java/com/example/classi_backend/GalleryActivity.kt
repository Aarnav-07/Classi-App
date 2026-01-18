package com.example.classi_backend

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class GalleryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        val imageUris = intent.getParcelableArrayListExtra<Uri>("image_uris") ?: arrayListOf()

        val recyclerView = findViewById<RecyclerView>(R.id.gallery_recycler_view)
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = GalleryAdapter(imageUris)
    }

    class GalleryAdapter(private val uris: List<Uri>) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: ImageView = view.findViewById(R.id.gallery_image)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_gallery, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.imageView.setImageURI(uris[position])
        }

        override fun getItemCount() = uris.size
    }
}
