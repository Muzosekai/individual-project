package com.example.individualproject

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.individualproject.databinding.ItemCareerSuggestionCardBinding

class SuggestedCareersAdapter(private var careers: List<SuggestedCareerItem>) :
    RecyclerView.Adapter<SuggestedCareersAdapter.CareerViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CareerViewHolder {
        val binding = ItemCareerSuggestionCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CareerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CareerViewHolder, position: Int) {
        holder.bind(careers[position])
    }

    override fun getItemCount(): Int = careers.size

    fun updateData(newCareers: List<SuggestedCareerItem>?) {
        this.careers = newCareers ?: emptyList()
        notifyDataSetChanged()
    }

    inner class CareerViewHolder(private val binding: ItemCareerSuggestionCardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(career: SuggestedCareerItem) {
            binding.careerItem = career
            binding.executePendingBindings()
        }
    }
}