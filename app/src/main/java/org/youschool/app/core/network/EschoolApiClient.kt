package org.youschool.app.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody
import org.youschool.app.domain.model.*
import kotlinx.coroutines.CancellationException
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.youschool.app.core.auth.AuthManager
import org.youschool.app.core.network.dto.*
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class EschoolApiClient(private val authManager: AuthManager) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val chatClient = client.newBuilder().retryOnConnectionFailure(false).build()

    private val baseUrl = "https://app.eschool.center/ec-server"

    private fun generateRandomAlphanumeric(length: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }

    suspend fun checkShowRecaptcha(username: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/showRecaptcha?username=$username")
                .get()
                .build()
            val response = client.newCall(request).execute()
            response.body?.string()?.trim() == "true"
        } catch (e: Exception) {
            false
        }
    }

    suspend fun login(username: String, passwordHash: String, captchaToken: String? = null): Result<StateResponse> = withContext(Dispatchers.IO) {
        try {
            val pushToken = generateRandomAlphanumeric(64)
            val deviceId = generateRandomAlphanumeric(32)
            val deviceJson = """{"cliType":"web","cliVer":"v.4441","pushToken":"$pushToken","deviceId":"$deviceId","deviceName":"Chrome","deviceModel":"130","cliOs":"Android","cliOsVer":null}"""

            val finalPassword = if (!captchaToken.isNullOrBlank()) passwordHash + captchaToken else passwordHash

            val formBody = FormBody.Builder()
                .add("username", username)
                .add("password", finalPassword)
                .add("device", deviceJson)
                .build()

            val request = Request.Builder()
                .url("$baseUrl/login")
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "application/json, text/plain, */*")
                .header("Origin", "https://app.eschool.center")
                .header("Referer", "https://app.eschool.center/")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorDetail = when (responseBody.trim()) {
                    "1" -> "Неверный логин или пароль"
                    "3" -> "Требуется пройти капчу (войдите на сайте или подтвердите проверку)"
                    "4" -> "Аккаунт заблокирован"
                    "5" -> "Ошибка параметров устройства"
                    else -> if (responseBody.isNotBlank()) "код $responseBody" else "код ${response.code}"
                }
                return@withContext Result.failure(IOException("Ошибка авторизации: $errorDetail"))
            }

            var sessionId: String? = null
            var route: String? = null

            val cookies = response.headers("Set-Cookie")
            for (cookie in cookies) {
                if (cookie.contains("JSESSIONID=")) {
                    sessionId = cookie.substringAfter("JSESSIONID=").substringBefore(";")
                }
                if (cookie.contains("route=")) {
                    route = cookie.substringAfter("route=").substringBefore(";")
                }
            }

            if (sessionId == null) {
                return@withContext Result.failure(IOException("Не удалось получить сессионный токен"))
            }

            authManager.saveSession(sessionId, route)

            // Запрашиваем состояние пользователя
            val stateResult = getStateInternal(sessionId, route)
            stateResult
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildCookieHeader(sessionId: String, route: String?): String {
        return buildString {
            append("JSESSIONID=$sessionId; site_ver=app")
            if (!route.isNullOrBlank()) {
                append("; route=$route")
            }
        }
    }

    private suspend fun getStateInternal(sessionId: String, route: String?): Result<StateResponse> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/state?menu=false")
                .get()
                .addHeader("Cookie", buildCookieHeader(sessionId, route))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(IOException("Пустой ответ профиля"))
            val state = json.decodeFromString<StateResponse>(body)
            Result.success(state)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun <T> executeWithAutoAuth(block: suspend (sessionId: String, route: String?) -> Result<T>): Result<T> = withContext(Dispatchers.IO) {
        var sessionId = authManager.getSessionId()
        var route = authManager.getRoute()

        if (sessionId.isNullOrBlank()) {
            val login = authManager.getLogin()
            val hash = authManager.getPasswordHash()
            if (!login.isNullOrBlank() && !hash.isNullOrBlank()) {
                val loginResult = login(login, hash)
                if (loginResult.isFailure) {
                    return@withContext Result.failure(loginResult.exceptionOrNull() ?: IOException("Ошибка входа"))
                }
                sessionId = authManager.getSessionId()
                route = authManager.getRoute()
            } else {
                return@withContext Result.failure(IOException("Пользователь не авторизован"))
            }
        }

        val firstAttempt = block(sessionId!!, route)
        if (firstAttempt.isFailure && firstAttempt.exceptionOrNull()?.message?.contains("401") == true) {
            // Re-login on 401
            val login = authManager.getLogin()
            val hash = authManager.getPasswordHash()
            if (!login.isNullOrBlank() && !hash.isNullOrBlank()) {
                val loginResult = login(login, hash)
                if (loginResult.isSuccess) {
                    return@withContext block(authManager.getSessionId()!!, authManager.getRoute())
                }
            }
        }
        firstAttempt
    }

    private suspend fun chatRequest(
        path: String,
        params: Map<String, String> = emptyMap(),
        method: String = "GET",
        body: RequestBody? = null
    ): Result<String> = executeWithAutoAuth { sessionId, route ->
        try {
            val url = "$baseUrl/$path".toHttpUrl().newBuilder().apply {
                params.forEach { (key, value) -> addQueryParameter(key, value) }
            }.build()
            val request = Request.Builder().url(url)
                .header("Cookie", buildCookieHeader(sessionId, route))
                .method(method, body).build()
            chatClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Result.failure(IOException("Ошибка сообщений: HTTP ${response.code}"))
                } else {
                    Result.success(response.body?.string() ?: throw IOException("Пустой ответ сервера"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMessageThreads(before: Long?): Result<List<MessageThread>> =
        chatRequest("chat/threads", buildMap {
            put("row", "1"); put("rowsCount", "25")
            put("newOnly", "false"); put("starredOnly", "false")
            before?.let { put("msgNum", it.toString()) }
        }).mapCatching { json.decodeFromString<List<MessageThread>>(it) }

    suspend fun getMessageThread(id: Long): Result<MessageThread> =
        chatRequest("chat/thread", mapOf("threadId" to id.toString()))
            .mapCatching { json.decodeFromString<MessageThread>(it) }

    suspend fun getMessages(id: Long, before: Long?): Result<List<SchoolMessage>> =
        chatRequest("chat/messages", buildMap {
            put("threadId", id.toString()); put("rowStart", "1"); put("rowsCount", "25")
            put("getNew", "false"); put("isSearch", "false")
            before?.let { put("msgStart", it.toString()) }
        }, "PUT", "{}".toRequestBody("application/json".toMediaType()))
            .mapCatching { json.decodeFromString<List<SchoolMessage>>(it) }

    suspend fun getMessageRecipients(): Result<List<MessageRecipient>> =
        chatRequest("usr/getUserListSearch").mapCatching {
            json.decodeFromString<List<MessageRecipient>>(it).filterNot { person -> person.out }
        }

    suspend fun getNoReplyId(): Result<Long> = chatRequest("chat/noreply").mapCatching { it.trim().toLong() }

    suspend fun createPrivateThread(recipientId: Long): Result<Long> {
        val existing = chatRequest("chat/privateThreads").mapCatching { json.decodeFromString<Map<String, Long>>(it) }
        if (existing.isFailure) return Result.failure(existing.exceptionOrNull()!!)
        existing.getOrThrow()[recipientId.toString()]?.let { return Result.success(it) }
        return chatRequest("chat/saveThread", method = "PUT", body =
            """{"isGroup":false,"isAllowReplay":2,"interlocutor":$recipientId}"""
                .toRequestBody("application/json".toMediaType()))
            .mapCatching { it.trim().toLong().also { id -> check(id > 0) { "Не удалось открыть переписку" } } }
    }

    suspend fun sendMessage(threadId: Long, text: String, uid: String): Result<Unit> {
        if (text.isBlank()) return Result.failure(IllegalArgumentException("Введите текст сообщения"))
        // eSchool expects escaped HTML even for a plain text message.
        val escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;")
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("threadId", threadId.toString())
            .addFormDataPart("msgText", escaped)
            .addFormDataPart("msgUID", uid).build()
        return chatRequest("chat/sendNew", method = "POST", body = body).mapCatching {
            json.decodeFromString<SendMessageResponse>(it).requireSuccess()
        }
    }

    suspend fun markMessageRead(id: Long): Result<Unit> =
        chatRequest("chat/readed", mapOf("msgId" to id.toString())).map { Unit }

    suspend fun getDiary(userId: Long, startMs: Long, endMs: Long): Result<DiaryResponse> {
        return executeWithAutoAuth { sessionId, route ->
            try {
                val request = Request.Builder()
                    .url("$baseUrl/student/diary?userId=$userId&d1=$startMs&d2=$endMs")
                    .get()
                    .addHeader("Cookie", buildCookieHeader(sessionId, route))
                    .build()

                val response = client.newCall(request).execute()
                if (response.code == 401) return@executeWithAutoAuth Result.failure(IOException("401 Unauthorized"))
                val body = response.body?.string() ?: ""
                val diary = json.decodeFromString<DiaryResponse>(body)
                Result.success(diary)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getGradesForPeriod(userId: Long, periodId: Long): Result<GradesResponse> {
        return executeWithAutoAuth { sessionId, route ->
            try {
                val request = Request.Builder()
                    .url("$baseUrl/student/getDiaryPeriod?userId=$userId&eiId=$periodId")
                    .get()
                    .addHeader("Cookie", buildCookieHeader(sessionId, route))
                    .build()

                val response = client.newCall(request).execute()
                if (response.code == 401) return@executeWithAutoAuth Result.failure(IOException("401 Unauthorized"))
                val body = response.body?.string() ?: ""
                val grades = json.decodeFromString<GradesResponse>(body)
                Result.success(grades)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getDiaryUnits(userId: Long, periodId: Long): Result<UnitsResponse> {
        return executeWithAutoAuth { sessionId, route ->
            try {
                val request = Request.Builder()
                    .url("$baseUrl/student/getDiaryUnits/?userId=$userId&eiId=$periodId")
                    .get()
                    .addHeader("Cookie", buildCookieHeader(sessionId, route))
                    .build()

                val response = client.newCall(request).execute()
                if (response.code == 401) return@executeWithAutoAuth Result.failure(IOException("401 Unauthorized"))
                val body = response.body?.string() ?: ""
                val units = json.decodeFromString<UnitsResponse>(body)
                Result.success(units)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getDefaultPeriodId(userId: Long): Result<Long> {
        return executeWithAutoAuth { sessionId, route ->
            try {
                val request = Request.Builder()
                    .url("$baseUrl/student/getDiaryTtlMarksDefPeriod?userId=$userId")
                    .get()
                    .addHeader("Cookie", buildCookieHeader(sessionId, route))
                    .build()

                val response = client.newCall(request).execute()
                if (response.code == 401) return@executeWithAutoAuth Result.failure(IOException("401 Unauthorized"))
                val body = response.body?.string()?.trim() ?: ""
                val periodId = body.toLongOrNull() ?: return@executeWithAutoAuth Result.failure(IOException("Некорректный ID периода: $body"))
                Result.success(periodId)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getPeriods(year: Int): Result<List<PeriodContainerDto>> {
        return executeWithAutoAuth { sessionId, route ->
            try {
                val request = Request.Builder()
                    .url("$baseUrl/dict/periods2?year=$year")
                    .get()
                    .addHeader("Cookie", buildCookieHeader(sessionId, route))
                    .build()

                val response = client.newCall(request).execute()
                if (response.code == 401) return@executeWithAutoAuth Result.failure(IOException("401 Unauthorized"))
                val body = response.body?.string() ?: ""
                val periods = json.decodeFromString<List<PeriodContainerDto>>(body)
                Result.success(periods)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getAcademicPeriodItems(year: Int): Result<List<PeriodItemDto>> {
        return executeWithAutoAuth { sessionId, route ->
            try {
                val request = Request.Builder()
                    .url("$baseUrl/dict/periods2?year=$year")
                    .get()
                    .addHeader("Cookie", buildCookieHeader(sessionId, route))
                    .build()

                val response = client.newCall(request).execute()
                if (response.code == 401) return@executeWithAutoAuth Result.failure(IOException("401 Unauthorized"))
                val body = response.body?.string() ?: ""
                val items = parsePeriodsJson(body, json)
                Result.success(items)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    companion object {
        private val defaultJson = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        fun parsePeriodsJson(body: String, json: kotlinx.serialization.json.Json = defaultJson): List<PeriodItemDto> {
            return try {
                val element = json.parseToJsonElement(body)
                val result = mutableListOf<PeriodItemDto>()

                fun extractItems(el: JsonElement) {
                    when (el) {
                        is JsonArray -> {
                            for (child in el) {
                                extractItems(child)
                            }
                        }
                        is JsonObject -> {
                            val id = el["id"]?.jsonPrimitive?.longOrNull
                            val name = el["name"]?.jsonPrimitive?.contentOrNull ?: ""
                            val typeCode = el["typeCode"]?.jsonPrimitive?.contentOrNull ?: ""
                            if (id != null && id > 0 && name.isNotBlank()) {
                                val date1 = el["date1"]?.jsonPrimitive?.longOrNull ?: 0L
                                val date2 = el["date2"]?.jsonPrimitive?.longOrNull ?: 0L
                                val num = el["num"]?.jsonPrimitive?.intOrNull ?: 1
                                result.add(
                                    PeriodItemDto(
                                        id = id,
                                        name = name,
                                        date1 = date1,
                                        date2 = date2,
                                        typeCode = typeCode,
                                        num = num
                                    )
                                )
                            }
                            for ((_, value) in el) {
                                if (value is JsonArray || value is JsonObject) {
                                    extractItems(value)
                                }
                            }
                        }
                        else -> Unit
                    }
                }

                extractItems(element)
                result.distinctBy { it.id }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    suspend fun downloadFile(fileId: Long, destinationFile: File, variantId: Long? = null): Result<File> {
        return executeWithAutoAuth { sessionId, route ->
            try {
                val candidateUrls = mutableListOf<String>()
                if (variantId != null) {
                    candidateUrls.add("$baseUrl/files/HOMEWORK_VARIANT/$variantId/$fileId")
                }
                candidateUrls.add("$baseUrl/files/getFileById?fileId=$fileId")
                candidateUrls.add("$baseUrl/files/$fileId")

                var lastCode = 0
                for (url in candidateUrls) {
                    val request = Request.Builder()
                        .url(url)
                        .get()
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) Chrome/120.0.0.0 Mobile Safari/537.36")
                        .addHeader("Cookie", buildCookieHeader(sessionId, route))
                        .build()

                    val response = client.newCall(request).execute()
                    lastCode = response.code
                    if (response.code == 401) return@executeWithAutoAuth Result.failure(IOException("401 Unauthorized"))
                    if (response.isSuccessful) {
                        val bytes = response.body?.bytes() ?: byteArrayOf()
                        android.util.Log.d("EschoolFile", "URL: $url, code: ${response.code}, bytes: ${bytes.size}")
                        if (bytes.isNotEmpty()) {
                            destinationFile.parentFile?.mkdirs()
                            destinationFile.writeBytes(bytes)
                            return@executeWithAutoAuth Result.success(destinationFile)
                        }
                    }
                }

                Result.failure(IOException("Ошибка скачивания файла: HTTP $lastCode"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun setHomeworkDone(
        partId: Long,
        isDone: Boolean,
        needRecheck: Boolean = true,
        comment: String? = null
    ): Result<Unit> {
        return executeWithAutoAuth { sessionId, route ->
            try {
                val doneVal = if (isDone) 1 else 0
                val recheckParam = if (needRecheck) "&needRecheck=1" else ""
                val jsonBody = if (!comment.isNullOrBlank()) {
                    org.json.JSONObject().apply { put("pupilComm", comment) }.toString()
                } else "{}"
                val request = Request.Builder()
                    .url("$baseUrl/student/hw?partId=$partId&isDone=$doneVal$recheckParam")
                    .put(jsonBody.toRequestBody("application/json;charset=UTF-8".toMediaType()))
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .header("Origin", "https://app.eschool.center")
                    .header("Referer", "https://app.eschool.center/")
                    .addHeader("Cookie", buildCookieHeader(sessionId, route))
                    .build()

                val response = client.newCall(request).execute()
                if (response.code == 401) return@executeWithAutoAuth Result.failure(IOException("401 Unauthorized"))
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("Ошибка смены статуса ДЗ: HTTP ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getHomeworkDetails(partId: Long, prsId: Long): Result<HomeworkDetailsDto> {
        return executeWithAutoAuth { sessionId, route ->
            try {
                val request = Request.Builder()
                    .url("$baseUrl/student/getLPartPupil?partId=$partId&prsId=$prsId")
                    .get()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .addHeader("Cookie", buildCookieHeader(sessionId, route))
                    .build()

                val response = client.newCall(request).execute()
                if (response.code == 401) return@executeWithAutoAuth Result.failure(IOException("401 Unauthorized"))
                val body = response.body?.string() ?: ""
                val detailsResp = json.decodeFromString<HomeworkDetailsResponse>(body)
                val first = detailsResp.result.firstOrNull()
                if (first != null) {
                    Result.success(first)
                } else {
                    Result.failure(IOException("Детали домашнего задания не найдены"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun uploadHomeworkResultFile(partId: Long, prsId: Long, file: File): Result<UploadFileResultDto> {
        return executeWithAutoAuth { sessionId, route ->
            try {
                val mediaType = "application/octet-stream".toMediaType()
                val fileBody = file.asRequestBody(mediaType)
                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", file.name, fileBody)
                    .build()

                val request = Request.Builder()
                    .url("$baseUrl/student/addFileResult?partId=$partId&for_prs_id=$prsId")
                    .post(multipartBody)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .header("Origin", "https://app.eschool.center")
                    .header("Referer", "https://app.eschool.center/")
                    .addHeader("Cookie", buildCookieHeader(sessionId, route))
                    .build()

                val response = client.newCall(request).execute()
                if (response.code == 401) return@executeWithAutoAuth Result.failure(IOException("401 Unauthorized"))
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val uploadResult = json.decodeFromString<UploadFileResultDto>(body)
                    Result.success(uploadResult)
                } else {
                    Result.failure(IOException("Ошибка загрузки файла: HTTP ${response.code} $body"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun deleteHomeworkResultFile(partId: Long, prsId: Long, fileId: Long): Result<Boolean> {
        return executeWithAutoAuth { sessionId, route ->
            try {
                val request = Request.Builder()
                    .url("$baseUrl/student/delFileResult?partId=$partId&fileId=$fileId&for_prs_id=$prsId")
                    .delete()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .header("Origin", "https://app.eschool.center")
                    .header("Referer", "https://app.eschool.center/")
                    .addHeader("Cookie", buildCookieHeader(sessionId, route))
                    .build()

                val response = client.newCall(request).execute()
                if (response.code == 401) return@executeWithAutoAuth Result.failure(IOException("401 Unauthorized"))
                val body = response.body?.string()?.trim() ?: ""
                val isSuccess = response.isSuccessful && (body == "true" || body == "")
                if (isSuccess) {
                    Result.success(true)
                } else {
                    Result.failure(IOException("Ошибка удаления файла: HTTP ${response.code} $body"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
