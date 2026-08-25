package io.github.ameralkhorasani.outpost.data.security

import android.util.Base64
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.common.Factory
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.userauth.keyprovider.FileKeyProvider
import net.schmizz.sshj.userauth.keyprovider.KeyProviderUtil
import net.schmizz.sshj.userauth.password.PasswordUtils

/**
 * Derives the public half of a stored private key.
 *
 * A key only works once its public half is listed in the server's authorized_keys, and
 * that half is not something the user necessarily has to hand - so the app reconstructs
 * it from the private key it already holds. The output is byte-identical to
 * `ssh-keygen -y -f <key>`.
 */
object SshKeyUtils {

    fun derivePublicKeyLine(
        privateKeyPem: String,
        passphrase: String? = null,
        comment: String? = null
    ): Result<String> = runCatching {
        val pem = privateKeyPem.replace("\r\n", "\n").replace("\r", "\n").trim() + "\n"

        val passwordFinder = passphrase
            ?.takeIf { it.isNotEmpty() }
            ?.let { PasswordUtils.createOneOff(it.toCharArray()) }

        val format = KeyProviderUtil.detectKeyFileFormat(pem, false)
        val provider: FileKeyProvider = Factory.Named.Util.create(
            DefaultConfig().fileKeyProviderFactories,
            format.toString()
        ) ?: throw IllegalStateException("Unsupported key format: $format")

        provider.init(pem, null, passwordFinder)

        val publicKey = provider.public
        val keyType = KeyType.fromKey(publicKey)
        val buffer = Buffer.PlainBuffer()
        keyType.putPubKeyIntoBuffer(publicKey, buffer)

        val encoded = Base64.encodeToString(buffer.compactData, Base64.NO_WRAP)
        buildString {
            append(keyType.toString())
            append(' ')
            append(encoded)
            if (!comment.isNullOrBlank()) {
                append(' ')
                append(comment)
            }
        }
    }
}
