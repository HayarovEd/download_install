package com.edurda77.download_install.downloader.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VersionResponse(
    @SerialName("createdDate")
    val createdDate: String? = null,
    @SerialName("group")
    val groupResponseDto: GroupResponseDto = GroupResponseDto(),
    @SerialName("hasFile")
    val hasFile: Boolean = false,
    @SerialName("id")
    val id: String? = null,
    @SerialName("name")
    val name: String = ""
)
