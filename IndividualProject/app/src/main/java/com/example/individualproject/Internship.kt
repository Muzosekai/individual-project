package com.example.individualproject

import com.google.gson.annotations.SerializedName

data class Internship(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("company_name") val companyName: String,
    @SerializedName("location") val location: String,
    @SerializedName("deadline") val deadline: String,
    @SerializedName("company_logo_url") val companyLogoUrl: String?,
    @SerializedName("is_bookmarked") var isBookmarked: Boolean = false
)


data class InternshipsResponse(
    @SerializedName("error") val error: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("internships") val internships: List<Internship>?
)


data class InternshipApplicationResponse(
    @SerializedName("error") val error: Boolean,
    @SerializedName("message") val message: String
)