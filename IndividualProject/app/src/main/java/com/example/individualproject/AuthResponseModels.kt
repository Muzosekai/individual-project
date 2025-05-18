package com.example.individualproject

import com.google.gson.annotations.SerializedName


data class GenericAuthResponse(
    val filename: String? = null,
    @SerializedName("error") val error: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("user_id") val userId: Int? = null
)

data class User(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String
)

data class LoginResponse(
    @SerializedName("error") val error: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("user") val user: User?,
    @SerializedName("token") val token: String?
)

