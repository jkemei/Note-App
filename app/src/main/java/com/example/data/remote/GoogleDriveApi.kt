package com.example.data.remote

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.*

data class TokenResponse(
    val access_token: String,
    val refresh_token: String?,
    val expires_in: Long,
    val scope: String?,
    val token_type: String
)

data class DriveFile(
    val id: String,
    val name: String,
    val mimeType: String? = null,
    val modifiedTime: String? = null
)

data class DriveFileListResponse(
    val files: List<DriveFile>
)

data class CreateFileRequest(
    val name: String,
    val parents: List<String>
)

data class UserInfoResponse(
    val email: String,
    val name: String?,
    val picture: String?
)

interface GoogleDriveApi {

    @FormUrlEncoded
    @POST("https://oauth2.googleapis.com/token")
    suspend fun exchangeCode(
        @Field("code") code: String,
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("grant_type") grantType: String = "authorization_code"
    ): TokenResponse

    @FormUrlEncoded
    @POST("https://oauth2.googleapis.com/token")
    suspend fun refreshToken(
        @Field("refresh_token") refreshToken: String,
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("grant_type") grantType: String = "refresh_token"
    ): TokenResponse

    @GET("https://www.googleapis.com/drive/v3/files")
    suspend fun listFiles(
        @Header("Authorization") authHeader: String,
        @Query("spaces") spaces: String = "appDataFolder",
        @Query("q") query: String,
        @Query("fields") fields: String = "files(id, name, mimeType, modifiedTime)"
    ): DriveFileListResponse

    @POST("https://www.googleapis.com/drive/v3/files")
    suspend fun createFileMetadata(
        @Header("Authorization") authHeader: String,
        @Body metadata: CreateFileRequest
    ): DriveFile

    @Headers("Content-Type: application/json")
    @PATCH("https://www.googleapis.com/upload/drive/v3/files/{fileId}")
    suspend fun uploadFileContent(
        @Header("Authorization") authHeader: String,
        @Path("fileId") fileId: String,
        @Query("uploadType") uploadType: String = "media",
        @Body content: RequestBody
    ): DriveFile

    @GET("https://www.googleapis.com/drive/v3/files/{fileId}")
    suspend fun downloadFileContent(
        @Header("Authorization") authHeader: String,
        @Path("fileId") fileId: String,
        @Query("alt") alt: String = "media"
    ): ResponseBody

    @GET("https://www.googleapis.com/oauth2/v3/userinfo")
    suspend fun getUserInfo(
        @Header("Authorization") authHeader: String
    ): UserInfoResponse
}
