package alexcmb.mytvlauncher.dynamic

import java.nio.ByteBuffer
import java.nio.charset.Charset

/**
 * A minimal reader for Apple binary property lists ("bplist00"), the format of the schedule
 * embedded in a dynamic HEIC wallpaper. Handles what those schedules use — dictionaries,
 * arrays, integers, reals, strings, booleans and data — and returns plain Kotlin values:
 * Map<String, Any?>, List<Any?>, Long, Double, String, Boolean, ByteArray.
 */
object BinaryPlist {

    class FormatException(message: String) : Exception(message)

    fun parse(bytes: ByteArray): Any? {
        if (bytes.size < 40 || String(bytes, 0, 8, Charsets.US_ASCII) != "bplist00") {
            throw FormatException("not a binary plist")
        }
        // Trailer: the last 32 bytes.
        val t = ByteBuffer.wrap(bytes, bytes.size - 32, 32)
        t.position(bytes.size - 32 + 6)
        val offsetSize = t.get().toInt() and 0xFF
        val refSize = t.get().toInt() and 0xFF
        val objectCount = t.long.toInt()
        val topObject = t.long.toInt()
        val tableOffset = t.long.toInt()
        val offsets = IntArray(objectCount) { readInt(bytes, tableOffset + it * offsetSize, offsetSize).toInt() }
        return Reader(bytes, offsets, refSize).read(topObject, depth = 0)
    }

    private class Reader(val b: ByteArray, val offsets: IntArray, val refSize: Int) {
        fun read(index: Int, depth: Int): Any? {
            if (depth > 32 || index !in offsets.indices) throw FormatException("bad object reference")
            var p = offsets[index]
            val marker = b[p].toInt() and 0xFF
            val type = marker shr 4
            val info = marker and 0x0F
            p++
            return when (type) {
                0x0 -> when (info) {
                    0x8 -> false
                    0x9 -> true
                    else -> null
                }
                0x1 -> readInt(b, p, 1 shl info)
                0x2 -> {
                    val size = 1 shl info
                    if (size == 4) java.lang.Float.intBitsToFloat(readInt(b, p, 4).toInt()).toDouble()
                    else java.lang.Double.longBitsToDouble(readInt(b, p, 8))
                }
                0x4 -> { val (n, s) = length(info, p); b.copyOfRange(s, s + n) }
                0x5 -> { val (n, s) = length(info, p); String(b, s, n, Charsets.US_ASCII) }
                0x6 -> { val (n, s) = length(info, p); String(b, s, n * 2, UTF16BE) }
                0xA -> {
                    val (n, s) = length(info, p)
                    List(n) { read(readInt(b, s + it * refSize, refSize).toInt(), depth + 1) }
                }
                0xD -> {
                    val (n, s) = length(info, p)
                    val map = LinkedHashMap<String, Any?>()
                    for (i in 0 until n) {
                        val key = read(readInt(b, s + i * refSize, refSize).toInt(), depth + 1)
                        val value = read(readInt(b, s + (n + i) * refSize, refSize).toInt(), depth + 1)
                        map[key.toString()] = value
                    }
                    map
                }
                else -> throw FormatException("unsupported object type $type")
            }
        }

        /** An object's element count and where its contents start (counts ≥ 15 follow as an int). */
        fun length(info: Int, p: Int): Pair<Int, Int> {
            if (info != 0xF) return info to p
            val intMarker = b[p].toInt() and 0xFF
            val size = 1 shl (intMarker and 0x0F)
            return readInt(b, p + 1, size).toInt() to (p + 1 + size)
        }
    }

    private val UTF16BE: Charset = Charsets.UTF_16BE

    /** A big-endian unsigned integer of [size] bytes (8 bytes read as a signed long). */
    private fun readInt(b: ByteArray, at: Int, size: Int): Long {
        if (at < 0 || at + size > b.size) throw FormatException("truncated plist")
        var v = 0L
        for (i in 0 until size) v = (v shl 8) or (b[at + i].toLong() and 0xFF)
        return v
    }
}
