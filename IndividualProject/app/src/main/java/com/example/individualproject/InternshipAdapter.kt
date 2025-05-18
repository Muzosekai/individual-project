package com.example.individualproject

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.individualproject.databinding.ItemInternshipCardBinding


class InternshipAdapter(
    private var internships: List<Internship>,
    private val onItemClick: (Internship) -> Unit,
    private val onApplyClick: (Internship) -> Unit,
    private val onBookmarkClick: (Internship) -> Unit
) : RecyclerView.Adapter<InternshipAdapter.InternshipViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InternshipViewHolder {
        val binding = ItemInternshipCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return InternshipViewHolder(binding)
    }

    override fun onBindViewHolder(holder: InternshipViewHolder, position: Int) {
        holder.bind(internships[position])
    }

    override fun getItemCount(): Int = internships.size

    fun updateData(newInternships: List<Internship>) {
        this.internships = newInternships
        notifyDataSetChanged()
    }

    inner class InternshipViewHolder(private val binding: ItemInternshipCardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(internship: Internship) {
            binding.internshipTitleText.text = internship.title
            binding.companyNameText.text = internship.companyName
            binding.locationText.text = internship.location
            binding.deadlineText.text = internship.deadline


            binding.companyLogoImage.setImageResource(R.mipmap.ic_launcher_round)


            updateBookmarkIcon(internship)

            binding.applyButton.setOnClickListener { onApplyClick(internship) }
            binding.bookmarkButton.setOnClickListener {
                internship.isBookmarked = !internship.isBookmarked
                updateBookmarkIcon(internship)
                onBookmarkClick(internship)
            }
            itemView.setOnClickListener { onItemClick(internship) }
        }

        private fun updateBookmarkIcon(internship: Internship) {
            if (internship.isBookmarked) {
                binding.bookmarkButton.setImageResource(android.R.drawable.btn_star_big_on)
            } else {
                binding.bookmarkButton.setImageResource(android.R.drawable.btn_star_big_off)
            }
        }
    }
}