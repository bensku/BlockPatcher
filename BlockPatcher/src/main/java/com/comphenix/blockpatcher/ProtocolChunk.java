package com.comphenix.blockpatcher;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import com.comphenix.protocol.utility.StreamSerializer;

/**
 * Chunk, as represented in protocol of Minecraft. Operates directly to
 * byte array, so reading lots of values might be slow. Works
 * with Minecraft 1.9's protocol (and maybe in future versions, too).
 * 
 * See <a href="http://wiki.vg/SMP_Map_Format>SMP Map Format</a> for
 * more information.
 * 
 * This snippet is licensed under MIT license, even if BlockPatcher is not.
 * @author bensku
 *
 */
public class ProtocolChunk {
	
	private byte[] buf;
	private StreamSerializer mcSerializer;
	
	public int bitsPerBlock;
	public int[] palette;
	public long[] data;
	public byte[] blockLight;
	
	public ProtocolChunk(byte[] buf) {
		this.buf = buf;
	}
	
	/**
	 * Should be called before doing anything else.
	 * @return This for chaining.
	 */
	public ProtocolChunk read() {
		DataInputStream is = new DataInputStream(new ByteArrayInputStream(buf));
		try {
			bitsPerBlock = is.readUnsignedByte();
			
			int paletteLength = mcSerializer.deserializeVarInt(is);
			palette = new int[paletteLength];
			for (int i = 0; i < paletteLength; i++) {
				palette[i] = mcSerializer.deserializeVarInt(is);
			}
			
			int dataLength = mcSerializer.deserializeVarInt(is);
			data = new long[dataLength];
			for (int i = 0; i < dataLength; i++) {
				data[i] = is.readLong();
			}
			
			blockLight = new byte[4096];
			for (int i = 0; i < 4096; i++) {
				blockLight[i] = is.readByte();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return this;
	}
	
	
}
