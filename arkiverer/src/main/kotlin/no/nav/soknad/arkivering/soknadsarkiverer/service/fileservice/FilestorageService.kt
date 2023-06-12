package no.nav.soknad.arkivering.soknadsarkiverer.service.fileservice

import no.nav.soknad.arkivering.avroschemas.Soknadarkivschema
import no.nav.soknad.arkivering.soknadsarkiverer.config.ArchivingException
import no.nav.soknad.arkivering.soknadsarkiverer.supervision.ArchivingMetrics
import no.nav.soknad.arkivering.soknadsfillager.api.FilesApi
import no.nav.soknad.arkivering.soknadsfillager.api.HealthApi
import no.nav.soknad.arkivering.soknadsfillager.model.FileData
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class FilestorageService(
	private val filesApi: FilesApi,
	private val healthApi: HealthApi,
	private val metrics: ArchivingMetrics
) : FileserviceInterface {

	private val logger = LoggerFactory.getLogger(javaClass)


	override fun ping(): String {
		healthApi.ping()
		return "pong"
	}


	override fun getFilesFromFilestorage(key: String, data: Soknadarkivschema): FetchFileResponse {
		val timer = metrics.filestorageGetLatencyStart()
		try {
			val fileIds = getFileIds(data)
			logger.info("$key: Getting files with ids: '$fileIds'")

			val fetchFileResponse = getFiles(key, fileIds)

			logger.info("$key: Received ${fetchFileResponse.files?.size} files with a sum of ${fetchFileResponse.files?.sumOf { it.content?.size ?: 0 }} bytes")
			return fetchFileResponse

		} finally {
			metrics.endTimer(timer)
		}
	}

	override fun deleteFilesFromFilestorage(key: String, data: Soknadarkivschema) {
		val timer = metrics.filestorageDelLatencyStart()

		val fileIds = getFileIds(data)
		try {

			logger.info("$key: Calling file storage to delete $fileIds")
			deleteFiles(key, fileIds)
			logger.info("$key: Deleted these files: $fileIds")

			metrics.incDelFilestorageSuccesses()
		} catch (e: Exception) {
			metrics.incDelFilestorageErrors()
			logger.warn(
				"$key: Failed to delete files from file storage. Everything is saved to Joark correctly, " +
					"so this error will be ignored. Affected file ids: '$fileIds'", e
			)

		} finally {
			metrics.endTimer(timer)
		}
	}

	private fun getFiles(key: String, fileIds: List<String>) =
		mergeFetchResponses(fileIds.map { performGetCall(key, listOf(it)) } )

	private fun mergeFetchResponses(responses: List<FetchFileResponse>): FetchFileResponse {
		return if (responses == null || responses.isEmpty())
			FetchFileResponse("ok", listOf(), null)
		else if (responses.any{it.status== "error"})
			FetchFileResponse(status = "error", files = null, exception = responses.map{it.exception}.firstOrNull())
		else if (responses.all{it.status == "deleted"})
			FetchFileResponse(status = "deleted", files = null, exception = null)
		else if (responses.any { it.status != "ok" })
			FetchFileResponse(status = "not-found", files = responses.flatMap { it.files?:listOf() }.toList(), exception = null)
		else
			FetchFileResponse(status = "ok", files = responses.flatMap { it.files?:listOf() }.toList(), exception = null)
	}

	private fun deleteFiles(key: String, fileIds: List<String>) {
		filesApi.deleteFiles(fileIds, key)
	}

	public fun performGetCall(key: String, fileIds: List<String>): FetchFileResponse {
		try {
			if (fileIds.isEmpty()) return FetchFileResponse("ok", listOf(), null )
			val files = filesApi.findFilesByIds(ids = fileIds, xInnsendingId = key, metadataOnly = false)

			if (files.all { it.status == "deleted" })
				return FetchFileResponse(status = "deleted", files = null, exception = null)
			if (files.any { it.status != "ok" })
				return FetchFileResponse(status = "not-found", files = files, exception = null)
			else return FetchFileResponse(status = "ok", files = files, exception = null)
		} catch (ex: Exception) {
			return FetchFileResponse(status = "error", files = null, exception = ex)
		}
	}


	private fun getFileIds(data: Soknadarkivschema) =
		data.mottatteDokumenter
			.flatMap { it.mottatteVarianter.map { variant -> variant.uuid } }
}
