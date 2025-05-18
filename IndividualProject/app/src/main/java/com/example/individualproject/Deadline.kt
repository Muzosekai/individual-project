package com.example.individualproject

import com.google.gson.annotations.SerializedName

data class Deadline(
    @SerializedName("id") val id: String,
    @SerializedName("internship_title") val internshipTitle: String,
    @SerializedName("company_name") val companyName: String,
    @SerializedName("deadline_date") val deadlineDate: String // "YYYY-MM-DD"
)


data class DashboardDataResponse(
    @SerializedName("error") val error: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("resume_progress") val resumeProgress: Int?,
    @SerializedName("deadlines") val deadlines: List<Deadline>?
)