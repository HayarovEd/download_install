package com.edurda77.download_install.downloader.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GroupResponseDto(
    @SerialName("app")
    val appResponseDto: AppResponseDto = AppResponseDto(),
    @SerialName("createdDate")
    val createdDate: String? = null,
    @SerialName("id")
    val id: String? = null,
    @SerialName("name")
    val name: String = ""
)
