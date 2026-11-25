package com.example.compiler

import android.content.Context
import com.example.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Pure-Kotlin Android APK packager & signer supporting:
 * - Uncompressed 4-byte-aligned resources.arsc (required by Android 11+)
 * - APK Signature Scheme v1 (JAR MANIFEST.MF, NASJE.SF, PKCS#7 DER NASJE.RSA)
 * - APK Signature Scheme v2 (APK Sig Block 42 with RSA-2048 SHA-256)
 */
object ApkSignerEngine {

    data class ApkFileEntry(
        val path: String,
        val bytes: ByteArray,
        val storeUncompressed: Boolean = false
    )

    fun packageAndSignApk(
        context: Context,
        entries: List<ApkFileEntry>,
        outputApkFile: File
    ) {
        outputApkFile.parentFile?.mkdirs()

        val keyStore = KeyStore.getInstance("PKCS12")
        context.resources.openRawResource(R.raw.nasje_debug).use { stream ->
            keyStore.load(stream, "android".toCharArray())
        }
        val alias = keyStore.aliases().nextElement()
        val privateKey = keyStore.getKey(alias, "android".toCharArray()) as PrivateKey
        val cert = keyStore.getCertificate(alias) as X509Certificate

        // 1. Build v1 JAR signature files (MANIFEST.MF, NASJE.SF, NASJE.RSA)
        val manifestMf = buildManifestMf(entries)
        val certSf = buildSignatureFileSf(manifestMf)
        val certRsa = buildPkcs7SignatureBlock(certSf, privateKey, cert)

        val allEntries = entries + listOf(
            ApkFileEntry("META-INF/MANIFEST.MF", manifestMf),
            ApkFileEntry("META-INF/NASJE.SF", certSf),
            ApkFileEntry("META-INF/NASJE.RSA", certRsa)
        )

        // 2. Write ZIP with uncompressed 4-byte-aligned resources.arsc
        val rawZip = writeAlignedZip(allEntries)

        // 3. Inject APK Signature Scheme v2 Signing Block
        val signedApkBytes = applyApkSignatureV2(rawZip, privateKey, cert)
        outputApkFile.writeBytes(signedApkBytes)
    }

    private fun writeAlignedZip(entries: List<ApkFileEntry>): ByteArray {
        val baos = ByteArrayOutputStream()
        var currentOffset = 0L

        val zos = ZipOutputStream(baos)
        entries.forEach { entry ->
            val zipEntry = ZipEntry(entry.path)
            if (entry.storeUncompressed) {
                zipEntry.method = ZipEntry.STORED
                zipEntry.size = entry.bytes.size.toLong()
                zipEntry.compressedSize = entry.bytes.size.toLong()
                val crc = CRC32().apply { update(entry.bytes) }.value
                zipEntry.crc = crc
                // 4-byte alignment padding via ZIP extra field for resources.arsc
                val headerWithoutExtra = 30 + entry.path.toByteArray(Charsets.UTF_8).size
                val mod = ((currentOffset + headerWithoutExtra) % 4).toInt()
                if (mod != 0) {
                    val padLen = 4 - mod
                    zipEntry.extra = ByteArray(padLen)
                }
            } else {
                zipEntry.method = ZipEntry.DEFLATED
                zos.setLevel(Deflater.DEFAULT_COMPRESSION)
            }
            zos.putNextEntry(zipEntry)
            zos.write(entry.bytes)
            zos.closeEntry()
            zos.flush()
            currentOffset = baos.size().toLong()
        }
        zos.close()
        return baos.toByteArray()
    }

    private fun buildManifestMf(entries: List<ApkFileEntry>): ByteArray {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val sb = StringBuilder()
        sb.append("Manifest-Version: 1.0\r\n")
        sb.append("Created-By: NASJE IDE APK Builder 1.0\r\n\r\n")

        entries.forEach { entry ->
            val digest = Base64.getEncoder().encodeToString(sha256.digest(entry.bytes))
            sb.append("Name: ${entry.path}\r\n")
            sb.append("SHA-256-Digest: $digest\r\n\r\n")
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun buildSignatureFileSf(manifestBytes: ByteArray): ByteArray {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val manifestDigest = Base64.getEncoder().encodeToString(sha256.digest(manifestBytes))
        val sb = StringBuilder()
        sb.append("Signature-Version: 1.0\r\n")
        sb.append("Created-By: NASJE IDE APK Signer 1.0\r\n")
        sb.append("X-Android-APK-Signed: 2\r\n")
        sb.append("SHA-256-Digest-Manifest: $manifestDigest\r\n\r\n")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    /**
     * Encodes a valid PKCS#7 / CMS SignedData DER structure for META-INF/NASJE.RSA.
     */
    private fun buildPkcs7SignatureBlock(
        sfBytes: ByteArray,
        privateKey: PrivateKey,
        cert: X509Certificate
    ): ByteArray {
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey)
            update(sfBytes)
        }.sign()

        val oidSignedData = derOid(byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x02))
        val oidData = derOid(byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x07, 0x01))
        val oidSha256 = derOid(byteArrayOf(0x60, 0x86.toByte(), 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01))
        val oidRsaEnc = derOid(byteArrayOf(0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x01))

        val algSha256 = derSeq(oidSha256 + byteArrayOf(0x05, 0x00))
        val algRsa = derSeq(oidRsaEnc + byteArrayOf(0x05, 0x00))
        val digestAlgs = derSet(algSha256)
        val contentInfo = derSeq(oidData)
        val certsTag = derTag(0xA0, cert.encoded)

        val issuerAndSerial = derSeq(cert.issuerX500Principal.encoded + derInt(cert.serialNumber.toByteArray()))
        val signerInfo = derSeq(
            derInt(byteArrayOf(1)) +
                issuerAndSerial +
                algSha256 +
                algRsa +
                derOctet(sig)
        )
        val signerInfos = derSet(signerInfo)

        val signedData = derSeq(
            derInt(byteArrayOf(1)) +
                digestAlgs +
                contentInfo +
                certsTag +
                signerInfos
        )
        return derSeq(oidSignedData + derTag(0xA0, signedData))
    }

    private fun applyApkSignatureV2(
        zipBytes: ByteArray,
        privateKey: PrivateKey,
        cert: X509Certificate
    ): ByteArray {
        // Locate EOCD (0x06054b50) from end of ZIP
        var eocdOffset = -1
        for (i in zipBytes.size - 22 downTo maxOf(0, zipBytes.size - 65558)) {
            if (zipBytes[i] == 0x50.toByte() &&
                zipBytes[i + 1] == 0x4B.toByte() &&
                zipBytes[i + 2] == 0x05.toByte() &&
                zipBytes[i + 3] == 0x06.toByte()
            ) {
                eocdOffset = i
                break
            }
        }
        if (eocdOffset < 0) return zipBytes

        val eocdBuf = ByteBuffer.wrap(zipBytes, eocdOffset, zipBytes.size - eocdOffset)
            .order(ByteOrder.LITTLE_ENDIAN)
        val cdSize = eocdBuf.getInt(12)
        val cdOffset = eocdBuf.getInt(16)

        val contents = zipBytes.copyOfRange(0, cdOffset)
        val centralDir = zipBytes.copyOfRange(cdOffset, eocdOffset)
        val eocd = zipBytes.copyOfRange(eocdOffset, zipBytes.size)

        // APK Signing Block digest uses EOCD with cdOffset set to contents.size (which equals cdOffset before insertion)
        val digestHex = computeApkV2Sha256Digest(contents, centralDir, eocd)

        // Build SignedData: digests, certificates, additionalAttributes
        val algoId = 0x0103 // RSA PKCS1 v1.5 with SHA-256
        val digestPair = lengthPrefixed(
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(algoId).array() +
                lengthPrefixed(digestHex)
        )
        val digestsSeq = lengthPrefixed(digestPair)
        val certsSeq = lengthPrefixed(lengthPrefixed(cert.encoded))
        val addAttrsSeq = lengthPrefixed(ByteArray(0))
        val signedDataBytes = digestsSeq + certsSeq + addAttrsSeq

        val sigBytes = Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey)
            update(signedDataBytes)
        }.sign()

        val signaturesSeq = lengthPrefixed(
            lengthPrefixed(
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(algoId).array() +
                    lengthPrefixed(sigBytes)
            )
        )
        val pubKeyBytes = lengthPrefixed(cert.publicKey.encoded)
        val signerBytes = lengthPrefixed(signedDataBytes) + signaturesSeq + pubKeyBytes
        val signersSeq = lengthPrefixed(lengthPrefixed(signerBytes))

        // Pair: uint64 size + uint32 ID (0x7109871a) + signersSeq
        val pairContent = ByteBuffer.allocate(4 + signersSeq.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x7109871a)
            .put(signersSeq)
            .array()
        val pair = ByteBuffer.allocate(8 + pairContent.size).order(ByteOrder.LITTLE_ENDIAN)
            .putLong(pairContent.size.toLong())
            .put(pairContent)
            .array()

        // Pad block so total size is a multiple of 4096 if needed (optional, spec allows any size >= 32)
        val blockInnerSize = pair.size + 8 + 16 // pairs + trailing uint64 size + magic(16)
        val signingBlock = ByteBuffer.allocate(8 + blockInnerSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            putLong(blockInnerSize.toLong())
            put(pair)
            putLong(blockInnerSize.toLong())
            put("APK Sig Block 42".toByteArray(Charsets.US_ASCII))
        }.array()

        // Update EOCD central directory offset
        val updatedEocd = eocd.copyOf()
        ByteBuffer.wrap(updatedEocd, 16, 4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(cdOffset + signingBlock.size)

        return ByteArrayOutputStream(zipBytes.size + signingBlock.size).apply {
            write(contents)
            write(signingBlock)
            write(centralDir)
            write(updatedEocd)
        }.toByteArray()
    }

    private fun computeApkV2Sha256Digest(
        contents: ByteArray,
        centralDir: ByteArray,
        eocd: ByteArray
    ): ByteArray {
        val chunkSize = 1024 * 1024
        val chunkDigests = mutableListOf<ByteArray>()

        fun processSection(bytes: ByteArray) {
            var offset = 0
            if (bytes.isEmpty()) return
            while (offset < bytes.size) {
                val len = minOf(chunkSize, bytes.size - offset)
                val md = MessageDigest.getInstance("SHA-256")
                md.update(0xA5.toByte())
                md.update(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(len).array())
                md.update(bytes, offset, len)
                chunkDigests.add(md.digest())
                offset += len
            }
        }

        processSection(contents)
        processSection(centralDir)
        processSection(eocd)

        val topMd = MessageDigest.getInstance("SHA-256")
        topMd.update(0x5A.toByte())
        topMd.update(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(chunkDigests.size).array())
        chunkDigests.forEach { topMd.update(it) }
        return topMd.digest()
    }

    private fun lengthPrefixed(data: ByteArray): ByteArray =
        ByteBuffer.allocate(4 + data.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(data.size)
            .put(data)
            .array()

    // ASN.1 DER helpers
    private fun derLength(len: Int): ByteArray = when {
        len < 0x80 -> byteArrayOf(len.toByte())
        len <= 0xFF -> byteArrayOf(0x81.toByte(), len.toByte())
        else -> byteArrayOf(0x82.toByte(), (len ushr 8).toByte(), len.toByte())
    }

    private fun derTag(tag: Int, body: ByteArray): ByteArray =
        byteArrayOf(tag.toByte()) + derLength(body.size) + body

    private fun derSeq(body: ByteArray): ByteArray = derTag(0x30, body)
    private fun derSet(body: ByteArray): ByteArray = derTag(0x31, body)
    private fun derOid(body: ByteArray): ByteArray = derTag(0x06, body)
    private fun derOctet(body: ByteArray): ByteArray = derTag(0x04, body)
    private fun derInt(body: ByteArray): ByteArray = derTag(0x02, body)
}
