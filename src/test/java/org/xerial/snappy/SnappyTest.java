/*--------------------------------------------------------------------------
 *  Copyright 2011 Taro L. Saito
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *--------------------------------------------------------------------------*/
//--------------------------------------
// snappy-java Project
//
// SnappyTest.java
// Since: 2011/03/30
//
// $URL$ 
// $Author$
//--------------------------------------
package org.xerial.snappy;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;
import org.xerial.util.log.Logger;

public class SnappyTest
{
    private static Logger _logger = Logger.getLogger(SnappyTest.class);

    @Test
    public void getVersion() throws Exception {
        String version = Snappy.getNativeLibraryVersion();
        _logger.debug("version: " + version);
    }

    @Test
    public void directBufferCheck() throws Exception {

        try {
            ByteBuffer src = ByteBuffer.allocate(1024);
            src.put("hello world".getBytes());
            src.flip();
            ByteBuffer dest = ByteBuffer.allocate(1024);
            int maxCompressedLen = Snappy.compress(src, dest);
        }
        catch (SnappyError e) {
            Assert.assertTrue(e.errorCode == SnappyErrorCode.NOT_A_DIRECT_BUFFER);
            return;
        }

        fail("shouldn't reach here");

    }

    @Test
    public void directBuffer() throws Exception {

        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 20; ++i) {
            s.append("Hello world!");
        }
        String origStr = s.toString();
        byte[] orig = origStr.getBytes();
        int BUFFER_SIZE = orig.length;
        ByteBuffer src = ByteBuffer.allocateDirect(orig.length);
        src.put(orig);
        src.flip();
        _logger.debug("input size: " + src.remaining());
        int maxCompressedLen = Snappy.maxCompressedLength(src.remaining());
        _logger.debug("max compressed length:" + maxCompressedLen);

        ByteBuffer compressed = ByteBuffer.allocateDirect(maxCompressedLen);
        int compressedSize = Snappy.compress(src, compressed);
        _logger.debug("compressed length: " + compressedSize);

        assertTrue(Snappy.isValidCompressedBuffer(compressed));

        assertEquals(0, src.position());
        assertEquals(orig.length, src.remaining());
        assertEquals(orig.length, src.limit());

        assertEquals(0, compressed.position());
        assertEquals(compressedSize, compressed.limit());
        assertEquals(compressedSize, compressed.remaining());

        int uncompressedLen = Snappy.uncompressedLength(compressed);
        _logger.debug("uncompressed length: " + uncompressedLen);
        ByteBuffer extract = ByteBuffer.allocateDirect(uncompressedLen);
        int uncompressedLen2 = Snappy.uncompress(compressed, extract);
        assertEquals(uncompressedLen, uncompressedLen2);
        assertEquals(uncompressedLen, extract.remaining());

        byte[] b = new byte[uncompressedLen];
        extract.get(b);
        String decompressed = new String(b);
        _logger.debug(decompressed);

        assertEquals(origStr, decompressed);
    }

    @Test
    public void bufferOffset() throws Exception {

        String m = "ACCAGGGGGGGGGGGGGGGGGGGGATAGATATTTCCCGAGATATTTTATATAAAAAAA";
        byte[] orig = m.getBytes();
        final int offset = 100;
        ByteBuffer input = ByteBuffer.allocateDirect(orig.length + offset);
        input.position(offset);
        input.put(orig);
        input.flip();
        input.position(offset);

        // compress
        int maxCompressedLength = Snappy.maxCompressedLength(input.remaining());
        final int offset2 = 40;
        ByteBuffer compressed = ByteBuffer.allocateDirect(maxCompressedLength + offset2);
        compressed.position(offset2);
        Snappy.compress(input, compressed);
        assertTrue(Snappy.isValidCompressedBuffer(compressed));

        // uncompress
        final int offset3 = 80;
        int uncompressedLength = Snappy.uncompressedLength(compressed);
        ByteBuffer uncompressed = ByteBuffer.allocateDirect(uncompressedLength + offset3);
        uncompressed.position(offset3);
        Snappy.uncompress(compressed, uncompressed);
        assertEquals(offset3, uncompressed.position());
        assertEquals(offset3 + uncompressedLength, uncompressed.limit());
        assertEquals(uncompressedLength, uncompressed.remaining());

        // extract string
        byte[] recovered = new byte[uncompressedLength];
        uncompressed.get(recovered);
        String m2 = new String(recovered);

        assertEquals(m, m2);
    }

    @Test
    public void byteArrayCompress() throws Exception {

        String m = "ACCAGGGGGGGGGGGGGGGGGGGGATAGATATTTCCCGAGATATTTTATATAAAAAAA";
        byte[] input = m.getBytes();
        byte[] output = new byte[Snappy.maxCompressedLength(input.length)];
        int compressedSize = Snappy.compress(input, 0, input.length, output, 0);
        byte[] uncompressed = new byte[input.length];

        assertTrue(Snappy.isValidCompressedBuffer(output, 0, compressedSize));
        int uncompressedSize = Snappy.uncompress(output, 0, compressedSize, uncompressed, 0);
        String m2 = new String(uncompressed);
        assertEquals(m, m2);

    }

    @Test
    public void rangeCheck() throws Exception {
        String m = "ACCAGGGGGGGGGGGGGGGGGGGGATAGATATTTCCCGAGATATTTTATATAAAAAAA";
        byte[] input = m.getBytes();
        byte[] output = new byte[Snappy.maxCompressedLength(input.length)];
        int compressedSize = Snappy.compress(input, 0, input.length, output, 0);

        assertTrue(Snappy.isValidCompressedBuffer(output, 0, compressedSize));
        // Intentionally set an invalid range
        assertFalse(Snappy.isValidCompressedBuffer(output, 0, compressedSize + 1));
        assertFalse(Snappy.isValidCompressedBuffer(output, 1, compressedSize));

        // Test the ByteBuffer API
        ByteBuffer bin = ByteBuffer.allocateDirect(input.length);
        bin.put(input);
        bin.flip();
        ByteBuffer bout = ByteBuffer.allocateDirect(Snappy.maxCompressedLength(bin.remaining()));
        int compressedSize2 = Snappy.compress(bin, bout);
        assertEquals(compressedSize, compressedSize2);

        assertTrue(Snappy.isValidCompressedBuffer(bout));
        // Intentionally set an invalid range
        bout.limit(bout.limit() + 1);
        assertFalse(Snappy.isValidCompressedBuffer(bout));
        bout.limit(bout.limit() - 1);
        bout.position(1);
        assertFalse(Snappy.isValidCompressedBuffer(bout));

    }

    @Test
    public void highLevelAPI() throws Exception {

        String m = "Hello! 01234 ACGDSFSDFJ World. FDSDF02394234 fdsfda03924";
        byte[] input = m.getBytes();
        byte[] output = Snappy.compress(input);

        byte[] uncompressed = Snappy.uncompress(output);
        String m2 = new String(uncompressed);
        assertEquals(m, m2);
    }

    @Test
    public void simpleUsage() throws Exception {

        String input = "Hello snappy-java! Snappy-java is a JNI-based wrapper for using Snappy from Google (written in C++), a fast compresser/decompresser.";
        byte[] compressed = Snappy.compress(input.getBytes("UTF-8"));
        byte[] uncompressed = Snappy.uncompress(compressed);
        String result = new String(uncompressed, "UTF-8");
        System.out.println(result);

    }

}