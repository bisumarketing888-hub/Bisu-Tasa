package com.example.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GitHubReleaseInfo(
    val tagName: String,
    val releaseName: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val publishedAt: String,
    val apkAssetName: String = "",
)

class GitHubUpdateChecker {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetchLatestRelease(
        repoInput: String,
        githubToken: String = "",
    ): Result<GitHubReleaseInfo> = withContext(Dispatchers.IO) {
        val slug = normalizeRepoSlug(repoInput)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Escribe el repositorio como usuario/repositorio (ej. miusuario/tasa-bisu)."),
            )

        val cleanToken = githubToken.trim()
        val releaseUrl = "https://api.github.com/repos/$slug/releases/latest"
        val releaseRequest = buildGitHubRequest(releaseUrl, cleanToken)

        try {
            client.newCall(releaseRequest).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code == 404) {
                    val diagnosticMessage = diagnoseNotFound(slug, cleanToken)
                    return@withContext Result.failure(IOException(diagnosticMessage))
                }
                if (response.code == 401 || response.code == 403) {
                    return@withContext Result.failure(
                        IOException("GitHub rechazó el acceso (HTTP ${response.code}). Verifica si tu token es válido o si se alcanzó el límite temporal."),
                    )
                }
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("GitHub respondió HTTP ${response.code}."),
                    )
                }

                val json = JSONObject(body)
                val tagName = json.optString("tag_name").trim()
                val htmlUrl = json.optString("html_url").trim()
                if (tagName.isBlank() || htmlUrl.isBlank()) {
                    return@withContext Result.failure(
                        IOException("El último Release en GitHub no contiene información válida."),
                    )
                }

                val releaseName = json.optString("name").trim().ifBlank { tagName }
                val releaseNotes = json.optString("body").trim()
                val publishedAt = json.optString("published_at").trim()

                var apkAssetName = ""
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val assetName = asset.optString("name").trim()
                        if (assetName.endsWith(".apk", ignoreCase = true)) {
                            apkAssetName = assetName
                            break
                        }
                    }
                }

                Result.success(
                    GitHubReleaseInfo(
                        tagName = tagName,
                        releaseName = releaseName,
                        releaseNotes = releaseNotes,
                        htmlUrl = htmlUrl,
                        publishedAt = publishedAt,
                        apkAssetName = apkAssetName,
                    ),
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Distinguishes whether HTTP 404 means:
     * 1) The repository IS visible, so simply no GitHub Release has been published yet (check Actions tab), or
     * 2) The repository is Private (or misspelled) and requires making it Public or entering a GitHub Token.
     */
    private fun diagnoseNotFound(slug: String, cleanToken: String): String {
        return try {
            val repoReq = buildGitHubRequest("https://api.github.com/repos/$slug", cleanToken)
            client.newCall(repoReq).execute().use { repoResp ->
                if (repoResp.isSuccessful) {
                    "Repositorio $slug conectado ✓, pero aún no tiene ningún Release publicado. Entra a la pestaña Actions en GitHub para verificar que el workflow haya terminado en verde."
                } else if (cleanToken.isBlank()) {
                    "El repositorio $slug es privado o no existe. Hazlo público en GitHub (Settings → Change visibility) o pega un Token de GitHub abajo."
                } else {
                    "No se pudo acceder a $slug con ese Token. Verifica que el token tenga permiso de lectura en Contents y que el nombre del repo sea exacto."
                }
            }
        } catch (_: Exception) {
            "Aún no hay un Release publicado en $slug (o el repositorio es privado)."
        }
    }

    private fun buildGitHubRequest(url: String, cleanToken: String): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "TasaBisu-Android")
            .header("Cache-Control", "no-cache")
        if (cleanToken.isNotBlank()) {
            builder.header("Authorization", "Bearer $cleanToken")
        }
        return builder.build()
    }

    companion object {
        private val VALID_SEGMENT_REGEX = Regex("^[A-Za-z0-9._-]+$")

        /**
         * Accepts formats such as:
         * - "usuario/tasa-bisu"
         * - "https://github.com/usuario/tasa-bisu"
         * - "https://github.com/usuario/tasa-bisu.git"
         * - "github.com/usuario/tasa-bisu/releases"
         */
        fun normalizeRepoSlug(rawInput: String): String? {
            var cleaned = rawInput.trim()
            if (cleaned.isBlank()) return null

            cleaned = cleaned
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .removePrefix("github.com/")
                .trim('/')

            val parts = cleaned.split('/').filter { it.isNotBlank() }
            if (parts.size < 2) return null

            val owner = parts[0]
            val repo = parts[1].removeSuffix(".git")
            if (owner.isBlank() || repo.isBlank()) return null
            if (!VALID_SEGMENT_REGEX.matches(owner) || !VALID_SEGMENT_REGEX.matches(repo)) {
                return null
            }
            return "$owner/$repo"
        }

        fun isNewerVersion(
            latestTag: String,
            installedVersion: String,
            acknowledgedTag: String = "",
        ): Boolean {
            val latestClean = stripVersionPrefix(latestTag)
            val installedClean = stripVersionPrefix(installedVersion)
            val ackClean = stripVersionPrefix(acknowledgedTag)

            if (latestClean.isBlank()) return false
            if (ackClean.isNotBlank() && latestClean.equals(ackClean, ignoreCase = true)) {
                return false
            }

            val latestParts = parseVersionNumbers(latestClean)
            val installedParts = parseVersionNumbers(installedClean)

            if (latestParts != null && installedParts != null) {
                val maxLen = maxOf(latestParts.size, installedParts.size)
                for (i in 0 until maxLen) {
                    val a = latestParts.getOrElse(i) { 0 }
                    val b = installedParts.getOrElse(i) { 0 }
                    if (a != b) return a > b
                }
                return false
            }

            return !latestClean.equals(installedClean, ignoreCase = true)
        }

        private fun stripVersionPrefix(raw: String): String {
            return raw.trim().removePrefix("v").removePrefix("V").trim()
        }

        private fun parseVersionNumbers(version: String): List<Int>? {
            val core = version.substringBefore('-').substringBefore('+').trim()
            if (core.isBlank()) return null
            val parts = core.split('.')
            val numbers = parts.map { it.toIntOrNull() ?: return null }
            return numbers.ifEmpty { null }
        }
    }
}
