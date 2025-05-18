package com.example.individualproject

import com.google.gson.annotations.SerializedName


data class FileUploadResponse(
    @SerializedName("error") val error: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("filename") val filename: String?,
    @SerializedName("path") val path: String?,
    @SerializedName("file_url") val file_url: String?,
    @SerializedName("portfolio_item_id") val portfolio_item_id: Int?
)
