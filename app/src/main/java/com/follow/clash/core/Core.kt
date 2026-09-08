package com.follow.clash.core

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL

data object Core {
    private var isLoaded: Boolean = false

    init {
        isLoaded = runCatching {
            System.loadLibrary("core")
            true
        }.getOrDefault(false)
    }

    private external fun nativeStartTun(
        fd: Int,
        cb: TunInterface,
        stack: String,
        address: String,
        dns: String,
    )

    fun forceGC() {
        if (isLoaded) runCatching { nativeForceGC() }
    }

    private external fun nativeForceGC()

    fun updateDNS(dns: String) {
        if (isLoaded) runCatching { nativeUpdateDNS(dns) }
    }

    private external fun nativeUpdateDNS(dns: String)

    private fun parseInetSocketAddress(address: String): InetSocketAddress {
        val url = URL("https://$address")
        return InetSocketAddress(InetAddress.getByName(url.host), url.port)
    }

    fun startTun(
        fd: Int,
        protect: (Int) -> Boolean,
        resolverProcess: (protocol: Int, source: InetSocketAddress, target: InetSocketAddress, uid: Int) -> String,
        stack: String,
        address: String,
        dns: String,
    ) {
        if (isLoaded) {
            runCatching {
                nativeStartTun(
                    fd,
                    object : TunInterface {
                        override fun protect(fd: Int) {
                            protect(fd)
                        }

                        override fun resolverProcess(
                            protocol: Int,
                            source: String,
                            target: String,
                            uid: Int,
                        ): String {
                            return resolverProcess(
                                protocol,
                                parseInetSocketAddress(source),
                                parseInetSocketAddress(target),
                                uid,
                            )
                        }
                    },
                    stack,
                    address,
                    dns,
                )
            }
        }
    }

    fun suspended(suspended: Boolean) {
        if (isLoaded) runCatching { nativeSuspended(suspended) }
    }

    private external fun nativeSuspended(suspended: Boolean)

    private external fun nativeInvokeAction(data: String, cb: InvokeInterface)

    fun invokeAction(data: String, cb: (result: String?) -> Unit) {
        if (isLoaded) {
            runCatching {
                nativeInvokeAction(
                    data,
                    object : InvokeInterface {
                        override fun onResult(result: String?) {
                            cb(result)
                        }
                    },
                )
            }.onFailure { cb(null) }
        } else {
            cb(null)
        }
    }

    private external fun nativeSetEventListener(cb: InvokeInterface?)

    fun callSetEventListener(cb: ((result: String?) -> Unit)?) {
        if (!isLoaded) return
        if (cb == null) {
            runCatching { nativeSetEventListener(null) }
            return
        }
        runCatching {
            nativeSetEventListener(
                object : InvokeInterface {
                    override fun onResult(result: String?) {
                        cb(result)
                    }
                },
            )
        }
    }

    private external fun nativeQuickSetup(
        initParamsString: String,
        setupParamsString: String,
        cb: InvokeInterface,
    )

    fun quickSetup(
        initParamsString: String,
        setupParamsString: String,
        cb: (result: String?) -> Unit,
    ) {
        if (isLoaded) {
            runCatching {
                nativeQuickSetup(
                    initParamsString,
                    setupParamsString,
                    object : InvokeInterface {
                        override fun onResult(result: String?) {
                            cb(result)
                        }
                    },
                )
            }.onFailure { cb(null) }
        } else {
            cb("""{"success":true}""")
        }
    }

    fun stopTun() {
        if (isLoaded) runCatching { nativeStopTun() }
    }

    private external fun nativeStopTun()

    fun getTraffic(onlyStatisticsProxy: Boolean): String {
        return if (isLoaded) runCatching { nativeGetTraffic(onlyStatisticsProxy) }.getOrDefault("{}") else "{}"
    }

    private external fun nativeGetTraffic(onlyStatisticsProxy: Boolean): String

    fun getTotalTraffic(onlyStatisticsProxy: Boolean): String {
        return if (isLoaded) runCatching { nativeGetTotalTraffic(onlyStatisticsProxy) }.getOrDefault("{}") else "{}"
    }

    private external fun nativeGetTotalTraffic(onlyStatisticsProxy: Boolean): String
}
