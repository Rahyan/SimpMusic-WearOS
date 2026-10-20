package com.maxrave.data.repository

import com.maxrave.common.ForkReleases
import com.maxrave.domain.data.model.update.UpdateData
import com.maxrave.domain.repository.UpdateRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.kotlinytmusicscraper.YouTube
import com.maxrave.kotlinytmusicscraper.models.simpmusic.newerPhoneRelease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

internal class UpdateRepositoryImpl(
    private val youTube: YouTube,
) : UpdateRepository {
    override fun checkForGithubReleaseUpdate(currentVersion: String, flavor: String, supportedAbis: List<String>): Flow<Resource<UpdateData?>> =
        flow {
            youTube
                .checkForGithubReleaseUpdate()
                .onSuccess { releases ->
                    val release = releases.newerPhoneRelease(currentVersion, flavor, supportedAbis)
                    emit(Resource.Success<UpdateData?>(release?.let {
                        UpdateData(it.tagName.orEmpty(), it.publishedAt, it.body.orEmpty(), "${ForkReleases.PAGE_URL}/tag/${it.tagName}")
                    }))
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    emit(Resource.Error<UpdateData?>(error.localizedMessage ?: "Could not check releases"))
                }
        }.flowOn(Dispatchers.IO)
}
