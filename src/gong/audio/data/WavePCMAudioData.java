package gong.audio.data;

import gong.audio.AudioDataException;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Enumeration;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

public class WavePCMAudioData extends BlockAudioData {

	private static final int BLOCK_SIZE_IN_SAMPLES = 4096;

	public static final String FILE_EXTENSION = ".wav";

	public WavePCMAudioData(AudioFormat format) {
		super(new AudioFormat(format.getSampleRate(), 16, 1, true, true));
		super.samplesPerBlock = BLOCK_SIZE_IN_SAMPLES;
	}

	public String getFileExtension() {
		return FILE_EXTENSION;
	}

	@Override
	protected Block createBlock() {
		return new WavePCMBlock();
	}

	@Override
	public boolean isSupported(String feature) {
		return false;
	}

	@Override
	public long getMemoryUsage() {
		return getLength() * 2;
	}

	@Override
	public void sendToStream(OutputStream stream) throws IOException,
			AudioDataException {

		ByteArrayOutputStream baos = new ByteArrayOutputStream(getLength() * 2);
		Enumeration e = blockData.elements();
		while (e.hasMoreElements()) {
			WavePCMBlock block = (WavePCMBlock) e.nextElement();
			block.sendToStream(baos);
		}
		AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(
				baos.toByteArray()), getFormat(), getLength());
		AudioSystem.write(ais, AudioFileFormat.Type.WAVE, stream);
	}

	@Override
	public void receiveFromStream(InputStream stream, boolean synchronous)
			throws IOException, AudioDataException {
		try {
			AudioInputStream ais = AudioSystem.getAudioInputStream(stream);
			if (ais.getFormat().matches(getFormat()) == false) {
				// transcode
				ais = AudioSystem.getAudioInputStream(getFormat(), ais);
			}
			WavePCMBlock block = new WavePCMBlock();
			boolean eof = block.readFromStream(ais);
			super.blockData.add(block);
			while (!eof) {
				block = new WavePCMBlock();
				super.blockData.add(block);
				eof = block.readFromStream(ais);
			}
			super.availableBlocks = super.blockData.size();
			super.position = getLength();
		} catch (UnsupportedAudioFileException e) {
			throw new AudioDataException(e.getMessage());
		}

	}

	private class WavePCMBlock extends Block {

		public WavePCMBlock() {
			super(BLOCK_SIZE_IN_SAMPLES);
		}

		public synchronized int read() throws AudioDataException {
			int dataIndex = position << 1;
			int a = data[dataIndex] & 0x00FF;
			int b = data[dataIndex + 1] & 0x00FF;
			position++;

			int sample = a << 8 | b;
			return sample;
		}

		public synchronized void write(int sample) throws AudioDataException {
			if (eob())
				throw new AudioDataException("Invalid write request.");
			if (data == null) {
				data = new byte[size * 2];
			}

			int dataIndex = position << 1;
			data[dataIndex] = (byte) ((sample >> 8) & 0xFF);
			data[dataIndex + 1] = (byte) (sample & 0xFF);
			position++;
		}

		public synchronized void sendToStream(OutputStream stream)
				throws IOException, AudioDataException {
			if (data == null)
				throw new AudioDataException("Invalid send request.");
			stream.write(data);
		}

		public synchronized boolean readFromStream(InputStream stream) throws IOException, AudioDataException {
            if (data == null) {
                data = new byte[size * 2];
            }
            // Provides a read() method that will read as much as possible without blocking
            BufferedInputStream bis = new BufferedInputStream(stream);
            
            // Read some data.
            int read = bis.read(data, 0, data.length);
            int length = read;
            boolean eof = read < 0;
            // If we haven't reached the end of the stream and we still have room to store samples, read some more
            while(!eof && length < data.length) {
            	read = bis.read(data, length, data.length - length);
            	eof = read < 0;
            	if(!eof) length += read;
            }
            // Place the position at the end (unless we didn't read anything
        	position += length >= 0 ? length / 2 : 0;
            return eof;
        }
	}

}
