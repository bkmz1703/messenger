package ru.ilyakirollov.messenger.data.upload

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import ru.ilyakirollov.messenger.BuildConfig

/**
 * Cloudinary resource type. Cloudinary's URL path determines how the file is processed:
 *   - IMAGE: photos (jpg, png, webp, etc.)
 *   - VIDEO: video and audio (mp4, m4a, mp3, ogg, wav)
 *   - AUTO: let Cloudinary detect for arbitrary files (images, videos, raw documents)
 */
enum class CloudinaryResourceType(val path: String) {
    IMAGE("image"),
    VIDEO("video"),
    AUTO("auto"),
}

/** Result of a successful Cloudinary upload. `secureUrl` is what we persist in Firestore. */
data class CloudinaryUploadResult(
    val secureUrl: String,
    val publicId: String,
    val resourceType: String,
    val format: String?,
    val bytes: Long,
)

/**
 * Uploads a content [Uri] directly to Cloudinary using an unsigned upload preset.
 *
 * Configure via `local.properties`:
 * ```
 * cloudinary.cloudName=<dashboard cloud name>
 * cloudinary.uploadPreset=<unsigned preset name>
 * ```
 */
@Singleton
class CloudinaryUploader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    fun isConfigured(): Boolean =
        BuildConfig.CLOUDINARY_CLOUD_NAME.isNotBlank() &&
            BuildConfig.CLOUDINARY_UPLOAD_PRESET.isNotBlank()

    suspend fun upload(
        uri: Uri,
        type: CloudinaryResourceType,
        fileName: String? = null,
    ): CloudinaryUploadResult = withContext(Dispatchers.IO) {
        check(isConfigured()) {
            "Cloudinary не сконфигурирован. Добавьте cloudinary.cloudName и " +
                "cloudinary.uploadPreset в local.properties и пересоберите проект."
        }

        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: when (type) {
            CloudinaryResourceType.IMAGE -> "image/jpeg"
            CloudinaryResourceType.VIDEO -> "video/mp4"
            CloudinaryResourceType.AUTO -> "application/octet-stream"
        }

        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Не удалось открыть файл: $uri")

        val displayName = fileName?.takeIf { it.isNotBlank() } ?: "upload"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("upload_preset", BuildConfig.CLOUDINARY_UPLOAD_PRESET)
            .addFormDataPart(
                name = "file",
                filename = displayName,
                body = bytes.toRequestBody(mime.toMediaTypeOrNull()),
            )
            .build()

        val url = "https://api.cloudinary.com/v1_1/" +
            "${BuildConfig.CLOUDINARY_CLOUD_NAME}/${type.path}/upload"
        val request = Request.Builder().url(url).post(body).build()

        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IOException("Cloudinary upload failed: HTTP ${resp.code}: $text")
            }
            val json = JSONObject(text)
            CloudinaryUploadResult(
                secureUrl = json.getString("secure_url"),
                publicId = json.getString("public_id"),
                resourceType = json.optString("resource_type", type.path),
                format = json.optString("format").takeIf { it.isNotBlank() },
                bytes = json.optLong("bytes", 0L),
            )
        }
    }
}
