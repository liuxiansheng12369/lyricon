
package io.github.proify.lyricon.provider.service

/**
 * 远端服务绑定器接口。
 *
 * 用于把 AIDL 注册回调返回的远端服务实例交给内部 endpoint。
 *
 * @param T 远程服务类型
 */
internal interface RemoteServiceBinder<T> {

    /**
     * 绑定远程服务实例。
     *
     * @param service 远程服务实例
     */
    fun bindRemoteService(service: T)
}
