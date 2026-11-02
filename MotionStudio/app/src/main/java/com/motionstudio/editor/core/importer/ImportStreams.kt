package com.motionstudio.editor.core.importer

import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.max

/** Streams with byte counting — the archive is never loaded into RAM. */
class CountingInputStream(
    input: InputStream,
    val totalBytes: Long,
    private val onRead: (Long) -> Unit
) : FilterInputStream(input) {
    var read: Long = 0L
        private set

    override fun read(): Int {
        val v = super.read()
        if (v >= 0) { read++; onRead(read) }
        return v
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = super.read(b, off, len)
        if (n > 0) { read += n; onRead(read) }
        return n
    }

    override fun skip(n: Long): Long {
        val s = super.skip(n)
        read += max(0L, s)
        onRead(read)
        return s
    }
}

fun InputStream.copyToStreaming(out: OutputStream, bufferSize: Int = 64 * 1024, onChunk: (Long) -> Unit = {}): Long {
    val buf = ByteArray(bufferSize)
    var total = 0L
    while (true) {
        val n = read(buf)
        if (n < 0) break
        out.write(buf, 0, n)
        total += n
        onChunk(total)
    }
    out.flush()
    return total
}

fun InputStream.readTextStreaming(encoding: String = "UTF-8"): String {
    val out = java.io.ByteArrayOutputStream()
    copyToStreaming(out)
    return out.toString(encoding)
}

object ImportIds {
    fun newId(prefix: String = "id"): String =
        "$prefix-" + java.util.UUID.randomUUID().toString()
}
