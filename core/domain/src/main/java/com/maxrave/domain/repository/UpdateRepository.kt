package com.maxrave.domain.repository

import com.maxrave.domain.data.model.update.UpdateData
import com.maxrave.domain.utils.Resource
import kotlinx.coroutines.flow.Flow

interface UpdateRepository {
    fun checkForGithubReleaseUpdate(currentVersion: String, flavor: String, supportedAbis: List<String>): Flow<Resource<UpdateData?>>
}
