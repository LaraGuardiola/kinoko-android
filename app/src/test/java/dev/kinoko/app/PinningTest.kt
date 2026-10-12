package dev.kinoko.app

import dev.kinoko.app.data.PinnedTrust
import dev.kinoko.app.data.fingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class PinningTest {
    // Made by kinoko's own generator (src/serve/tls.ts), with the fingerprint it puts in the QR.
    private val pem = "-----BEGIN CERTIFICATE-----\nMIIBHDCBxKADAgECAhA3J4LqyyMk7PJcfBrmAz0xMAoGCCqGSM49BAMCMBExDzAN\nBgNVBAMMBmtpbm9rbzAeFw0yNjEwMTEwMDAwMDBaFw00NjEwMTIwMDAwMDBaMBEx\nDzANBgNVBAMMBmtpbm9rbzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABK9vpZHP\n/Ofa8J/5eCP2xXtO1M/Z0yCYIYVobirPMa6c0kM6ITY6M2fCmeHVTU1UBM8M2KLu\nq9sS6oB0f3+6ua0wCgYIKoZIzj0EAwIDRwAwRAIgKI6PUk5zX1OSLinFSgCYpqLB\n37ElgY+Ww6oozGjXo/0CIGDkuDeonK0yOvz7iSnKYt0Dmjn2xL+iHHTlflXoWXvT\n-----END CERTIFICATE-----"
    private val pin = "iW7gejmgYOKvDkIeoMGmJrsdzkbzfpK9XQAso3WdN1U"

    private fun cert(): X509Certificate =
        CertificateFactory.getInstance("X.509").generateCertificate(pem.byteInputStream()) as X509Certificate

    @Test
    fun `the app computes the same fingerprint as the computer`() {
        assertEquals(pin, fingerprint(cert().encoded))
    }

    @Test
    fun `only the pinned certificate is trusted`() {
        PinnedTrust(pin).checkServerTrusted(arrayOf(cert()), "ECDHE_ECDSA")
        assertThrows(CertificateException::class.java) { PinnedTrust("someone-else").checkServerTrusted(arrayOf(cert()), "ECDHE_ECDSA") }
        assertThrows(CertificateException::class.java) { PinnedTrust(pin).checkServerTrusted(arrayOf(), "ECDHE_ECDSA") }
    }
}
