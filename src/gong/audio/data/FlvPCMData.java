/*
 * Copyright 2002-2011 The Gong Project (http://gong.ust.hk)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package gong.audio.data;

import gong.audio.AudioDataException;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import javax.sound.sampled.AudioFormat;

/**
 * This class stores audio data using the PCM codec in Flv file.
 * @author Gibson Lam
 * @version 1.0, 11/12/2006
 * @version 1.1, 11/09/2009
 * @version 4.2, 13/05/2011
 */
public class FlvPCMData extends BlockAudioData {
    
    /**
     * The extension used by FLV files
     */
    public static final String FILE_EXTENSION = ".flv";

    /**
     * The default samples per block
     */
    protected static final int DEFAULT_SAMPLES_PER_BLOCK = 4096;
    
    /**
     * Creates a new instance of FlvPCMData
     */
    public FlvPCMData() {
        super();
        samplesPerBlock = DEFAULT_SAMPLES_PER_BLOCK;
    }
    
    /**
     * Creates a new instance of FlvPCMData
     * @param format the audio format
     */
    public FlvPCMData(AudioFormat format) {
        super(format);
        samplesPerBlock = DEFAULT_SAMPLES_PER_BLOCK;
    }

    /**
     * Returns the file extension of the data
     * @return the file extension
     */
    public String getFileExtension() {
        return FILE_EXTENSION;
    }

    /**
     * Creates a new block
     * @return the new block
     */
    protected BlockAudioData.Block createBlock() {
        return new FlvPCMBlock(samplesPerBlock);
    }

    /**
     * Gets the memory usage the audio data
     * @return the length of memory usage
     */
    public long getMemoryUsage() {
        return (long) blockData.size() * samplesPerBlock * 2;
    }
    
    /**
     * Sends the flv header to the output stream
     * @param stream the output stream
     * @throws java.io.IOException failed to send header to stream
     */
    protected void sendHeaderToStream(OutputStream stream) throws IOException {
        DataOutputStream dataStream = new DataOutputStream(stream);
        
        // The header
        dataStream.write(new String("FLV").getBytes());                     // 0  - 2 : Signature "FLV"
        dataStream.writeByte(1);                                            // 3      : Version 1
        dataStream.writeByte(4);                                            // 4      : Flags - has audio (4)
        dataStream.writeInt(9);                                             // 5  - 8 : Header size (9)
        dataStream.writeInt(0);                                             // 9  - 12: Previous tag size (0)

        FlvMetaDataWriter writer = new FlvMetaDataWriter();
        writer.put("duration", new Double(getDuration() / 1000d));
        writer.put("audiodatarate", new Double(format.getFrameRate() * format.getFrameSize()));
        writer.put("metadatacreator", "The Gong Project");
        writer.put("audiocodecid", new Integer(0));
        writer.put("filesize", new Double(13 + writer.size() + blockData.size() * (samplesPerBlock * 2 + 16)));

        writer.sendToStream(dataStream);

        dataStream.writeInt(writer.size());                                 // metadata tag size
    }
    
    /**
     * Sends the block data to the output stream
     * @param stream the output stream
     * @throws java.io.IOException failed to send data to stream
     * @throws gong.audio.AudioDataException Invalid data
     */
    protected void sendDataToStream(OutputStream stream) throws IOException, AudioDataException {
        BitOutputStream bitStream = new BitOutputStream(stream);
        
        for (int index = 0; index < blockData.size(); index ++) {
            FlvPCMBlock block = (FlvPCMBlock) blockData.get(index);
            
            // FLV Tag
            bitStream.write(8);                                             // 0      : Audio tag (8)
            int dataSize = samplesPerBlock * 2 + 1;
            bitStream.write(dataSize, 24);                                  // 1  - 3 : Data size
            int timestamp = (int) ((double) (index * samplesPerBlock * 1000d) / (double) format.getSampleRate());
            bitStream.write(timestamp, 24);                                 // 4  - 6 : Timestamp
            bitStream.write(0, 8);                                          // 7      : Extended timestamp (0)
            bitStream.write(0, 24);                                         // 8  - 10: Stream ID (0)

            // Audio data
            bitStream.write(0, 4);                                          // bit 4-7: Sound format (PCM)
            switch ((int) format.getSampleRate()) {                               // bit 2-3: Sample rate
            case 5512:
                bitStream.write(0, 2);  // 5.5kHz
                break;
            case 11025:
                bitStream.write(1, 2);  // 11kHz
                break;
            case 22050:
                bitStream.write(2, 2);  // 22kHz
                break;
            case 44100:
                bitStream.write(3, 2);  // 44kHz
                break;
            }
            bitStream.write(1, 1);                                          // bit 1:   Sample size (8 bits, 16 bits)
            bitStream.write(0, 1);                                          // bit 0:   Sound type (mono, stereo)
            bitStream.flush();

            block.sendToStream(stream);

            // Tag length
            if (index < blockData.size() - 1) bitStream.write(dataSize + 11, 32);

            if (listener != null) listener.sent(this, getBlockTime(index));
        }
        bitStream.flush();
    }

    /**
     * Sends the audio data to an output stream
     * @param stream the output stream
     * @throws java.io.IOException failed to send the audio data
     * @throws gong.audio.AudioDataException Invalid audio data
     */
    public void sendToStream(OutputStream stream) throws IOException, AudioDataException {
        if (format.getSampleRate() != 5512 &&
            format.getSampleRate() != 11025 &&
            format.getSampleRate() != 22050 &&
            format.getSampleRate() != 44100) {
            throw new AudioDataException("Sampling rate is not supported by the FLV format.");
        }
        sendHeaderToStream(stream);
        sendDataToStream(stream);
    }

    /**
     * Receives the audio data from the input stream (not implemented)
     * @param stream the input stream
     * @param synchronous true if the function will block until the transfer finishes
     * @throws java.io.IOException failed to receive data from stream
     * @throws gong.audio.AudioDataException Invalid audio data
     */
    public void receiveFromStream(InputStream stream, boolean synchronous) throws IOException, AudioDataException {
        throw new AudioDataException("The Flv PCM format is write only.");
    }

    /**
     * Checks whether a feature is supported
     * @param feature the feature to be tested
     * @return true if the feature is supported
     */
    public boolean isSupported(String feature) {
        if (feature.equals(FEATURE_SENT_PROGRESS))
            return true;
        if (feature.equals(FEATURE_RECEIVED_PROGRESS))
            return false;
        return false;
    }
    
    /**
     * This class stores a block of Flv PCM data.
     */
    protected class FlvPCMBlock extends Block {
        
        /**
         * Creates a new instance of FlvPCMBlock
         * @param size the block size
         */
        public FlvPCMBlock(int size) {
            super(size);
        }
        
        /**
         * Reads a sample from the block (not implemented)
         * @return the sample
         * @throws gong.audio.AudioDataException failed to read a sample
         */
        public synchronized int read() throws AudioDataException {
            throw new AudioDataException("The Flv PCM format is write only.");
        }
        
        /**
         * Writes a sample to the block
         * @param sample the sample to be written
         * @throws gong.audio.AudioDataException failed to write the sample
         */
        public synchronized void write(int sample) throws AudioDataException {
            if (eob()) throw new AudioDataException("Invalid write request.");
            
            if (data == null) {
                data = new byte[size * 2];
                Arrays.fill(data, (byte) 0);
            }

            int dataIndex = position << 1;
            data[dataIndex] = (byte) (sample & 0xFF);
            data[dataIndex + 1] = (byte) ((sample >> 8) & 0xFF);

            position++;
        }
    
        /**
         * Sends the block to an output stream
         * @param stream the output stream
         * @throws gong.audio.AudioDataException failed to send the block
         */
        public synchronized void sendToStream(OutputStream stream) throws IOException, AudioDataException {
            if (data == null) throw new AudioDataException("Invalid send request.");
            stream.write(data);
        }
        
    }
    
}
