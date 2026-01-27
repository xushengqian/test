package com.example.audio;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.Assert.assertTrue;

public class PcmToMp3ConverterTest {

    @Test
    public void testConversion() throws Exception {
        // Generate 1 second of 440Hz sine wave
        int sampleRate = 44100;
        int durationSeconds = 1; // 1 second
        int numSamples = sampleRate * durationSeconds;
        byte[] pcmData = new byte[numSamples * 2]; // 16-bit
        
        double freq = 440.0;
        for (int i = 0; i < numSamples; i++) {
            double angle = 2.0 * Math.PI * i * freq / sampleRate;
            short sample = (short) (Math.sin(angle) * Short.MAX_VALUE);
            // Little endian
            pcmData[2*i] = (byte) (sample & 0xFF);
            pcmData[2*i+1] = (byte) ((sample >> 8) & 0xFF);
        }

        ByteArrayInputStream pcmIn = new ByteArrayInputStream(pcmData);
        ByteArrayOutputStream mp3Out = new ByteArrayOutputStream();

        // 44100 Hz, 1 channel (mono), 16 bits, signed, little endian
        AudioConfig config = new AudioConfig(sampleRate, 1, 16, true, false);
        PcmToMp3Converter converter = new PcmToMp3Converter();
        
        System.out.println("Starting conversion...");
        long startTime = System.currentTimeMillis();
        converter.convert(pcmIn, mp3Out, config);
        long endTime = System.currentTimeMillis();
        
        byte[] mp3Bytes = mp3Out.toByteArray();
        System.out.println("Conversion took: " + (endTime - startTime) + "ms");
        System.out.println("PCM size: " + pcmData.length + " bytes");
        System.out.println("MP3 size: " + mp3Bytes.length + " bytes");

        assertTrue("MP3 output should not be empty", mp3Bytes.length > 0);
        // MP3 should be compressed (1 second of MP3 at 128kbps is approx 16KB, raw PCM is 88KB)
        assertTrue("MP3 should be smaller than PCM", mp3Bytes.length < pcmData.length);
        
        // Basic check for ID3/MP3 header frame sync (usually starts with FF Fsomething)
        // LAME might add ID3 tags.
    }
}
