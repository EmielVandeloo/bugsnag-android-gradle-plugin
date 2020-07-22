package com.bugsnag.android.gradle

import com.android.build.gradle.api.ApkVariant
import com.android.build.gradle.api.ApkVariantOutput
import org.apache.http.ParseException
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.mime.MultipartEntity
import org.apache.http.entity.mime.content.StringBody
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.params.HttpConnectionParams
import org.apache.http.util.EntityUtils
import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.IOException

/**
 * Task to upload ProGuard mapping files to Bugsnag.
 *
 * Reads meta-data tags from the project's AndroidManifest.xml to extract a
 * build UUID (injected by BugsnagManifestTask) and a Bugsnag API Key:
 *
 * https://developer.android.com/guide/topics/manifest/manifest-intro.html
 * https://developer.android.com/guide/topics/manifest/meta-data-element.html
 *
 * This task must be called after ProGuard mapping files are generated, so
 * it is usually safe to have this be the absolute last task executed during
 * a build.
 */
class BugsnagMultiPartUploadRequest {

    lateinit var variantOutput: ApkVariantOutput
    lateinit var variant: ApkVariant

    fun uploadMultipartEntity(project: Project,
                              mpEntity: MultipartEntity,
                              manifestInfo: AndroidManifestInfo) {
        val logger = project.logger
        val bugsnag = project.extensions.getByName("bugsnag") as BugsnagPluginExtension
        if (manifestInfo.apiKey == "") {
            logger.warn("Skipping upload due to invalid parameters")
            if (bugsnag.isFailOnUploadError) {
                throw GradleException("Aborting upload due to invalid parameters")
            } else {
                return
            }
        }
        addPropertiesToMultipartEntity(project, mpEntity, manifestInfo, bugsnag)
        var uploadSuccessful = uploadToServer(project, mpEntity, bugsnag)
        val maxRetryCount = getRetryCount(bugsnag)
        var retryCount = maxRetryCount
        while (!uploadSuccessful && retryCount > 0) {
            logger.warn(String.format("Retrying Bugsnag upload (%d/%d) ...",
                maxRetryCount - retryCount + 1, maxRetryCount))
            uploadSuccessful = uploadToServer(project, mpEntity, bugsnag)
            retryCount--
        }
        if (!uploadSuccessful && bugsnag.isFailOnUploadError) {
            throw GradleException("Upload did not succeed")
        }
    }

    private fun addPropertiesToMultipartEntity(project: Project,
                                               mpEntity: MultipartEntity,
                                               manifestInfo: AndroidManifestInfo,
                                               bugsnag: BugsnagPluginExtension) {
        mpEntity.addPart("apiKey", StringBody(manifestInfo.apiKey))
        mpEntity.addPart("appId", StringBody(variant.applicationId))
        mpEntity.addPart("versionCode", StringBody(manifestInfo.versionCode))
        mpEntity.addPart("buildUUID", StringBody(manifestInfo.buildUUID))
        mpEntity.addPart("versionName", StringBody(manifestInfo.versionName))
        if (bugsnag.isOverwrite) {
            mpEntity.addPart("overwrite", StringBody("true"))
        }
        val logger = project.logger
        logger.debug("apiKey: ${manifestInfo.apiKey}")
        logger.debug("appId: ${variant.applicationId}")
        logger.debug("versionCode: ${manifestInfo.versionCode}")
        logger.debug("buildUUID: ${manifestInfo.buildUUID}")
        logger.debug("versionName: ${manifestInfo.versionName}")
    }

    private fun uploadToServer(project: Project,
                               mpEntity: MultipartEntity?,
                               bugsnag: BugsnagPluginExtension): Boolean {
        val logger = project.logger
        logger.lifecycle("Attempting upload of mapping file to Bugsnag")

        // Make the request
        val httpPost = HttpPost(bugsnag.endpoint)
        httpPost.entity = mpEntity
        val httpClient: HttpClient = DefaultHttpClient()
        val params = httpClient.params
        HttpConnectionParams.setConnectionTimeout(params, bugsnag.requestTimeoutMs)
        HttpConnectionParams.setSoTimeout(params, bugsnag.requestTimeoutMs)
        val statusCode: Int
        val responseEntity: String
        try {
            val response = httpClient.execute(httpPost)
            statusCode = response.statusLine.statusCode
            val entity = response.entity
            responseEntity = EntityUtils.toString(entity, "utf-8")
        } catch (e: IOException) {
            logger.error(String.format("Bugsnag upload failed: %s", e))
            return false
        } catch (e: ParseException) {
            logger.error(String.format("Bugsnag upload failed: %s", e))
            return false
        }
        if (statusCode == 200) {
            logger.lifecycle("Bugsnag upload successful")
            return true
        }
        logger.error(String.format("Bugsnag upload failed with code %d: %s", statusCode, responseEntity))
        return false
    }

    /**
     * Get the retry count defined by the user. If none is set the default is 0 (zero).
     * Also to avoid too much retries the max value is 5 (five).
     *
     * @return the retry count
     */
    private fun getRetryCount(bugsnag: BugsnagPluginExtension): Int {
        return if (bugsnag.retryCount >= MAX_RETRY_COUNT) MAX_RETRY_COUNT else bugsnag.retryCount
    }

    companion object {
        const val MAX_RETRY_COUNT = 5
    }
}