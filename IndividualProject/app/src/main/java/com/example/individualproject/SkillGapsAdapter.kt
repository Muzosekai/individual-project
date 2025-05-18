package com.example.individualproject

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.individualproject.databinding.ItemSkillGapBinding

class SkillGapsAdapter(private var skillGaps: List<SkillGapItem>) :
    RecyclerView.Adapter<SkillGapsAdapter.SkillGapViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SkillGapViewHolder {
        val binding = ItemSkillGapBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SkillGapViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SkillGapViewHolder, position: Int) {
        holder.bind(skillGaps[position])
    }

    override fun getItemCount(): Int = skillGaps.size

    fun updateData(newSkillGaps: List<SkillGapItem>?) {
        this.skillGaps = newSkillGaps ?: emptyList()
        notifyDataSetChanged()
    }

    inner class SkillGapViewHolder(private val binding: ItemSkillGapBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(skillGap: SkillGapItem) {
            binding.skillGapItem = skillGap
            binding.executePendingBindings()
        }
    }
}