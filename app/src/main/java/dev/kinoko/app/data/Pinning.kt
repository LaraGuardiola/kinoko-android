package dev.kinoko.app.data

import okhttp3.OkHttpClient
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** SHA-256 of a certificate's DER, base64url without padding: what `kinoko serve` puts in the QR. */
fun fingerprint(der: ByteArray): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(der))

/**
 * Trusts one certificate only: the computer's own, by its fingerprint. Nobody vouches for a
 * self-signed certificate on 192.168.x, so the pin is the whole check: another machine at that
 * address (another network, someone on this one) fails it.
 */
class PinnedTrust(private val pin: String) : X509TrustManager {
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("no certificate")
        if (fingerprint(leaf.encoded) != pin) throw CertificateException("not this computer's certificate")
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = throw CertificateException("no client certificates")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/** `base` that talks to the computer on the local network: its pinned certificate, a short wait. */
fun pinnedClient(base: OkHttpClient, lan: Lan, connectSeconds: Long = 3): OkHttpClient {
    val trust = PinnedTrust(lan.pin)
    val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
    return base.newBuilder()
        .sslSocketFactory(tls.socketFactory, trust)
        // The name is an IP the certificate does not list: the pin above is the identity check.
        .hostnameVerifier { _, _ -> true }
        .connectTimeout(connectSeconds, TimeUnit.SECONDS)
        .build()
}
