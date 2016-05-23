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
	
	public static final int LIGHT_DATA = 2048; // Byte array size
	public static final int WORLD_HEIGHT = 16; // World height in chunk sections (16 blocks)
	
	private byte[] buf;
	private StreamSerializer mcSerializer;
	private boolean hasSkylight;
	
	public Section[] sections;
	
	public ProtocolChunk(byte[] buf, boolean hasSkylight) {
		this.buf = buf;
		this.mcSerializer = StreamSerializer.getDefault();
		this.hasSkylight = hasSkylight;
		this.sections = new Section[WORLD_HEIGHT];
	}
	
	public ProtocolChunk read() {
		DataInputStream is = new DataInputStream(new ByteArrayInputStream(buf));
		for (int i = 0; i < WORLD_HEIGHT; i++) {
			sections[i] = new Section(is).read();
		}
		
		return this;
	}
	
	public class Section {
		
		private DataInputStream is;

		public int bitsPerBlock;
		public int[] palette;
		public long[] data;
		public byte[] blockLight;
		public byte[] skylight;

		public Section(DataInputStream is) {
			this.is = is;
		}

		/**
		 * Should be called before doing anything else.
		 * @return This for chaining.
		 */
		public Section read() {
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

				blockLight = new byte[LIGHT_DATA];
				for (int i = 0; i < LIGHT_DATA; i++) {
					blockLight[i] = is.readByte();
				}
				
				skylight = new byte[LIGHT_DATA];
				if (hasSkylight) {
					for (int i = 0; i < LIGHT_DATA; i++) {
						skylight[i] = is.readByte();
					}
				}
			} catch (IOException e) {
				throw new ChunkReadException("Invalid chunk section (IOException)!");
			}
			
			return this;
		}
		
		/**
		 * Gets palette type.
		 * @return Palette type enum.
		 */
		public PaletteType getPaletteType() {
			if (bitsPerBlock <= 4)
				return PaletteType.LIST;
			else if (bitsPerBlock <= 8)
				return PaletteType.INT_MAP;
			else
				return PaletteType.GLOBAL;
		}
		
		/**
		 * Gets palette id for given block id. If palette type is
		 * GLOBAL, -1 is returned.
		 * @param blockId Block id.
		 * @return Palette id or -1 if not found.
		 */
		public int getPaletteId(int blockId) {
			for (int i = 0; i < palette.length; i++) {
				if (palette[i] == blockId)
					return i;
			}
			
			return -1;
		}
		
		/**
		 * Gets block id for given palette id.
		 * @param paletteId Palette id.
		 * @return Block id or -1 if not found.
		 */
		public int getBlockId(int paletteId) {
			if (bitsPerBlock > 8) // Global palette check
				return -1;
			else if (palette.length <= paletteId) // Array bounds check
				return -1;
			
			return palette[paletteId];
		}
		
		/**
		 * Gets block at given index.
		 * @return Palette id (not block id, usually!) of block.
		 */
		public int getBlock(int index) {
			int pos = index * bitsPerBlock;
			int gone = 0;
			
			int start = 0;
			for (int i = 0; i * 64 < pos; i++) {
				gone += 64; // One long is 64 bits
				start = i;
			}
			
			int bits = 0; // We hold bits here for later parsing
			long current = data[start]; // Current long where data is fetched
			current >>>= (pos - gone);
			for (int i = 0; i < bitsPerBlock; i++) {
				bits |= current & 1; // 1 == ...00000001; AND sets everything else to 0; 1 or 0 is added to bits
				bits <<= 1;
			}
			
			return -1;
		}

	}
	
	public class ChunkReadException extends RuntimeException {

		private static final long serialVersionUID = 3907261534758829959L; // By Eclipse
		
		public ChunkReadException(String cause) {
			super(cause);
		}
	}
	
	public enum PaletteType {
		
		/**
		 * Minecraft uses simple list as backend.
		 * <p>
		 * Up to 4 bits per block.
		 */
		LIST,
		
		/**
		 * Minecraft uses some sort of custom int map as backend;
		 * does not affect int array form.
		 * <p>
		 * From 5 to 8 bits per block.
		 */
		INT_MAP,
		
		/**
		 * Palette does not exist for chunk, instead global
		 * palette is used.
		 * <p>
		 * More than 9 bits per block.
		 */
		GLOBAL
	}

}
