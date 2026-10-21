@file:Suppress("PackageDirectoryMismatch")

package android.util

/** android.util.Base64 on top of java.util.Base64 (flags other than URL_SAFE/NO_WRAP are ignored). */
object Base64 {
    const val DEFAULT = 0
    const val NO_PADDING = 1
    const val NO_WRAP = 2
    const val CRLF = 4
    const val URL_SAFE = 8

    @JvmStatic
    fun decode(str: String, flags: Int): ByteArray = decode(str.toByteArray(Charsets.ISO_8859_1), flags)

    @JvmStatic
    fun decode(input: ByteArray, flags: Int): ByteArray =
        if (flags and URL_SAFE != 0) java.util.Base64.getUrlDecoder().decode(input)
        else java.util.Base64.getMimeDecoder().decode(input)

    @JvmStatic
    fun encodeToString(input: ByteArray, flags: Int): String = String(encode(input, flags), Charsets.ISO_8859_1)

    @JvmStatic
    fun encode(input: ByteArray, flags: Int): ByteArray {
        var enc = if (flags and URL_SAFE != 0) java.util.Base64.getUrlEncoder() else java.util.Base64.getEncoder()
        if (flags and NO_PADDING != 0) enc = enc.withoutPadding()
        return enc.encode(input)
    }
}
