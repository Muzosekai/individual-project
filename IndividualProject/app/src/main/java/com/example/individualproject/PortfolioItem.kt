package com.example.individualproject

import com.google.gson.annotations.SerializedName


data class PortfolioItem(
    @SerializedName("id") val id: String,
    @SerializedName("user_id") val userId: Int?,
    @SerializedName("file_name") val fileName: String,
    @SerializedName("file_url") val fileUrl: String,
    @SerializedName("file_type") val fileType: String?,
    @SerializedName("thumbnail_url") val thumbnailUrl: String?,
    @SerializedName("upload_date") val uploadDate: String?
)


data class PortfolioResponse(
    @SerializedName("error") val error: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("portfolio_items") val portfolioItems: List<PortfolioItem>?
)


