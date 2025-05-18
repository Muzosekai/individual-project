import com.example.individualproject.AICareerSuggestionRequest
import com.example.individualproject.DashboardDataResponse
import com.example.individualproject.FileUploadResponse
import com.example.individualproject.FlaskAISuggestionResponse
import com.example.individualproject.GenericAuthResponse
import com.example.individualproject.InternshipApplicationResponse
import com.example.individualproject.InternshipsResponse
import com.example.individualproject.LoginResponse
import com.example.individualproject.PortfolioResponse
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.Part
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query



interface AuthApiService {

    @POST("ai/career-suggestions") // Matches your Flask route
    suspend fun getAICareerSuggestions(@Body requestBody: AICareerSuggestionRequest): Response<FlaskAISuggestionResponse>

    @POST("users/register")
    suspend fun registerUser(@Body requestBody: Map<String, String>): Response<GenericAuthResponse>

    @POST("users/login")
    suspend fun loginUser(@Body requestBody: Map<String, String>): Response<LoginResponse>

    @POST("users/forgot_password")
    suspend fun forgotPassword(@Body requestBody: Map<String, String>): Response<GenericAuthResponse>


    @GET("users/dashboard")
    suspend fun getDashboardData(): Response<DashboardDataResponse>

    @GET("internships")
    suspend fun getInternships(
        @Query("query") searchQuery: String?,
        @Query("filters") filters: String?
    ): Response<InternshipsResponse>

    @POST("internships/{internshipId}/apply")
    suspend fun applyForInternship(@Path("internshipId") internshipId: String): Response<InternshipApplicationResponse>



    @POST("users/bookmarks/{internshipId}")
    suspend fun addBookmark(@Path("internshipId") internshipId: String): Response<GenericAuthResponse>

    @DELETE("users/bookmarks/{internshipId}")
    suspend fun removeBookmark(@Path("internshipId") internshipId: String): Response<GenericAuthResponse>

    @GET("portfolio")
    suspend fun getPortfolioItems(): Response<PortfolioResponse>

    @Multipart
    @POST("portfolio")
    suspend fun uploadPortfolioFile(
        @Part file: MultipartBody.Part
    ): Response<FileUploadResponse>

}
