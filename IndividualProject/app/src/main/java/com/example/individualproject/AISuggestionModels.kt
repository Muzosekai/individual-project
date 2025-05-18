package com.example.individualproject

import com.google.gson.annotations.SerializedName


data class AICareerSuggestionRequest(
    @SerializedName("interests") val interests: String,
    @SerializedName("skills") val skills: String,
    @SerializedName("goals") val goals: String? // Optional
)


data class FlaskAISuggestionResponse(
    @SerializedName("error") val error: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("suggestions") val suggestions: ParsedAISuggestions?,
    @SerializedName("raw_ai_response") val rawAiResponse: String?
)


data class ParsedAISuggestions(
    @SerializedName("suggested_careers")
    val suggestedCareers: List<SuggestedCareerItem>?,
    @SerializedName("skill_gaps")
    val skillGaps: List<SkillGapItem>?,
    @SerializedName("general_advice")
    val generalAdvice: String?
)

data class SuggestedCareerItem(
    @SerializedName("career_name")
    val careerName: String?,
    @SerializedName("match_percentage")
    val matchPercentage: Int?,
    @SerializedName("reasoning")
    val reasoning: String?
)

data class SkillGapItem(
    @SerializedName("skill_needed")
    val skillNeeded: String?,
    @SerializedName("reasoning")
    val reasoning: String?
)