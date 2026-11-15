package com.aiverse.feature.backup.domain

/** 数据从正式版传给 beta 的通道。 */
enum class HandoffChannel {
    /** 通道 1：FileProvider URI，临时授权读完收回，不经过公共位置。 */
    FileProviderUri,

    /** 通道 2：HTTP 服务，127.0.0.1 临时监听，用完即关。 */
    HttpServer,
}