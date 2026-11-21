package com.makd.afinity.util

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Mp4FragmentProbeTest {

    @get:Rule val tempFolder = TemporaryFolder()

    @Test
    fun returnsLastFragmentMaxTimeMsForV0AndV1Boxes() {
        val moov =
            box(
                "moov",
                trakBox(trackId = 1, timescale = 1000L, version = 0) +
                    trakBox(trackId = 2, timescale = 48_000L, version = 1),
            )
        val moof1 =
            box(
                "moof",
                trafBox(trackId = 1, decodeTime = 500L, version = 0) +
                    trafBox(trackId = 2, decodeTime = 24_000L, version = 0),
            )
        val moof2 =
            box(
                "moof",
                trafBox(trackId = 1, decodeTime = 3500L, version = 0) +
                    trafBox(trackId = 2, decodeTime = 240_000L, version = 1),
            )

        val file = writeTempBytes("valid.mp4", box("ftyp", ByteArray(8)) + moov + moof1 + moof2)
        // track 1: 3500*1000/1000 = 3500ms; track 2: 240_000*1000/48_000 = 5000ms -> max is 5000ms
        assertEquals(5000L, Mp4FragmentProbe.lastFragmentTimeMs(file))
    }

    @Test
    fun supports64BitExtendedSizeAndSizeZeroToEofBoxes() {
        val moov = extendedBox("moov", trakBox(trackId = 1, timescale = 90_000L, version = 0))
        val moof = toEofBox("moof", trafBox(trackId = 1, decodeTime = 180_000L, version = 0))

        val file = writeTempBytes("extended.mp4", moov + moof)
        assertEquals(2000L, Mp4FragmentProbe.lastFragmentTimeMs(file))
    }

    @Test
    fun returnsNullForMissingBoxesUnmatchedTracksTruncatedOrMissingFile() {
        val noMoof = writeTempBytes("no_moof.mp4", box("moov", trakBox(1, 1000L, 0)))
        assertNull(Mp4FragmentProbe.lastFragmentTimeMs(noMoof))

        val noMoov = writeTempBytes("no_moov.mp4", box("moof", trafBox(1, 1000L, 0)))
        assertNull(Mp4FragmentProbe.lastFragmentTimeMs(noMoov))

        val wrongTrack =
            writeTempBytes(
                "wrong_track.mp4",
                box("moov", trakBox(1, 1000L, 0)) + box("moof", trafBox(99, 5000L, 0)),
            )
        assertNull(Mp4FragmentProbe.lastFragmentTimeMs(wrongTrack))

        val truncated =
            writeTempBytes("truncated.mp4", byteArrayOf(0, 0, 0, 20, 0x6D, 0x6F, 0x6F, 0x76))
        assertNull(Mp4FragmentProbe.lastFragmentTimeMs(truncated))

        assertNull(Mp4FragmentProbe.lastFragmentTimeMs(File(tempFolder.root, "missing.mp4")))
    }

    private fun writeTempBytes(name: String, bytes: ByteArray): File =
        tempFolder.newFile(name).apply { writeBytes(bytes) }

    private fun trakBox(trackId: Int, timescale: Long, version: Int): ByteArray {
        val tkhdBody = bytes { out ->
            out.writeByte(version)
            out.write(ByteArray(3 + if (version == 1) 16 else 8))
            out.writeInt(trackId)
        }
        val mdhdBody = bytes { out ->
            out.writeByte(version)
            out.write(ByteArray(3 + if (version == 1) 16 else 8))
            out.writeInt(timescale.toInt())
        }
        val mdia = box("mdia", box("mdhd", mdhdBody))
        return box("trak", box("tkhd", tkhdBody) + mdia)
    }

    private fun trafBox(trackId: Int, decodeTime: Long, version: Int): ByteArray {
        val tfhdBody = bytes { out ->
            out.writeInt(0)
            out.writeInt(trackId)
        }
        val tfdtBody = bytes { out ->
            out.writeByte(version)
            out.write(ByteArray(3))
            if (version == 1) out.writeLong(decodeTime) else out.writeInt(decodeTime.toInt())
        }
        return box("traf", box("tfhd", tfhdBody) + box("tfdt", tfdtBody))
    }

    private fun box(type: String, body: ByteArray): ByteArray = bytes { out ->
        out.writeInt(8 + body.size)
        out.writeBytes(type)
        out.write(body)
    }

    private fun extendedBox(type: String, body: ByteArray): ByteArray = bytes { out ->
        out.writeInt(1)
        out.writeBytes(type)
        out.writeLong(16L + body.size)
        out.write(body)
    }

    private fun toEofBox(type: String, body: ByteArray): ByteArray = bytes { out ->
        out.writeInt(0)
        out.writeBytes(type)
        out.write(body)
    }

    private inline fun bytes(block: (DataOutputStream) -> Unit): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use(block)
        return buffer.toByteArray()
    }
}
