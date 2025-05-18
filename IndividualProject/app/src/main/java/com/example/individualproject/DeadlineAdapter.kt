package com.example.individualproject

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.individualproject.databinding.ItemDeadlineCardBinding

class DeadlineAdapter(private var deadlines: List<Deadline>) :
    RecyclerView.Adapter<DeadlineAdapter.DeadlineViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeadlineViewHolder {
        val binding = ItemDeadlineCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DeadlineViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DeadlineViewHolder, position: Int) {
        holder.bind(deadlines[position])
    }

    override fun getItemCount(): Int = deadlines.size

    fun updateData(newDeadlines: List<Deadline>) {
        this.deadlines = newDeadlines
        notifyDataSetChanged()
    }

    inner class DeadlineViewHolder(private val binding: ItemDeadlineCardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(deadline: Deadline) {
            binding.internshipTitleText.text = deadline.internshipTitle
            binding.companyNameText.text = deadline.companyName
            binding.deadlineDateText.text = deadline.deadlineDate
        }
    }
}