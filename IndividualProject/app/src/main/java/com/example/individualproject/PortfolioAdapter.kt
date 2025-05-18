package com.example.individualproject

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.individualproject.databinding.ItemPortfolioThumbnailBinding

class PortfolioAdapter(

    private var items: MutableList<PortfolioItem>,
    private val onItemClick: (PortfolioItem) -> Unit
) : RecyclerView.Adapter<PortfolioAdapter.PortfolioViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PortfolioViewHolder {
        val binding = ItemPortfolioThumbnailBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PortfolioViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PortfolioViewHolder, position: Int) {
        if (position < items.size) {
            holder.bind(items[position])
        }
    }

    override fun getItemCount(): Int = items.size


    fun addPortfolioItem(newItem: PortfolioItem) {
        this.items.add(0, newItem)
        notifyItemInserted(0)
    }

    inner class PortfolioViewHolder(private val binding: ItemPortfolioThumbnailBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PortfolioItem) {


            val imageUrl = item.thumbnailUrl ?: item.fileUrl
            Glide.with(itemView.context)
                .load(imageUrl)
                .placeholder(R.drawable.bg_dashed_border)
                .error(R.drawable.ic_file_error_placeholder)
                .into(binding.portfolioItemImage)

            itemView.setOnClickListener { onItemClick(item) }
        }
    }
}