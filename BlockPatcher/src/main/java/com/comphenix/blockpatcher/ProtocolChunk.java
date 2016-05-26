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
 * See <a href="http://wiki.vg/SMP_Map_Format">SMP Map Format</a> for
 * more information.
 * 
 * This snippet is licensed under MIT license, even if BlockPatcher is not.
 * @author bensku
 *
 */
public class ProtocolChunk {
	
	public static final int LIGHT_DATA = 2048; // Byte array size
	public static final int WORLD_HEIGHT = 16; // World height in chunk sections (16 blocks)
	
	/**
	 * Gets protocol block id, which contains block id and meta.
	 * @param blockId Normal block id.
	 * @param meta Metadata (0 is allowed)
	 * @return Protocol block id.
	 */
	public static int getProtocolId(int blockId, int meta) {
		return blockId << 4 | meta;
	}
	
	/**
	 * Gets protocol block id, which contains block id and default meta.
	 * @param blockId Normal block id.
	 * @return Protocol block id.
	 */
	public static int getProtocolId(int blockId) {
		return getProtocolId(blockId, 0);
	}
	
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
	
	/**
	 * Chunk section, aka 16x16x16 (4096) blocks. Remember to call
	 * {@link #read()} first, it parses the data to usable form.
	 * 
	 * All block ids used are protocal block ids. You can use
	 * {@link ProtocolChunk#getProtocolId(int, int)} to get them for you.
	 * Note that some operations here are quite heavy, so be careful.
	 *
	 */
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
			int innerPos = pos - gone;
			current >>>= innerPos;
			for (int i = 0; i < bitsPerBlock; i++) {
				if (innerPos > 63) {
					start += 1;
					current = data[start];
				}
				
				bits |= current & 1; // 1 == ...00000001; AND sets everything else to 0; 1 or 0 is added to bits
				current >>>= 1;
				bits <<= 1;
				innerPos++;
			}
			
			return bits;
		}
		
		/**
		 * Sets block at given index to given value.
		 * @param index Index
		 * @param block Block id.
		 */
		public void setBlock(int index, int block) {
			int pos = index * bitsPerBlock;
			int gone = 0;
			
			int start = 0;
			for (int i = 0; i * 64 < pos; i++) {
				gone += 64; // One long is 64 bits
				start = i;
			}
			
			long current = data[start]; // Current long where data is fetched
			int innerPos = pos - gone;
			current >>>= innerPos;
			for (int i = 0; i < bitsPerBlock; i++) {
				if (innerPos > 63) {
					start += 1;
					data[start] = current;
					current = data[start];
					innerPos = 0;
				}
				
				current |= index & 1 << 63 - innerPos;
				index >>>= 1;
				innerPos++;
			}
			
			data[start] = current;
		}
		
		/**
		 * Gets index for block at chunk coordinates given.
		 * @param x X coordinate.
		 * @param y Y coordinate.
		 * @param z Z coordinate.
		 * @return Index for other methods.
		 */
	    public int getIndex(int x, int y, int z) {
	        return y << 8 | z << 4 | x;
	    }
		
	    /**
	     * Replaces all entries of a block with given replacement.
	     * This has way better performance than your DIY loops might get, so
	     * use it if you can.
	     * @param from Block to change.
	     * @param to Result block.
	     */
		public void massReplace(int from, int to) {
			int block = 0;
			int len = 0;
			long current = 0;
			int pos = 0;
			for (int i = 0; i < data.length; i++) {
				current = data[i];
				for (int in = 0; in < 64; in++) {
					if (len == bitsPerBlock) { // Parse the data
						if (block == from) {
							setBlock(pos, to);
						}
						
						pos += len;
						len = 0;
						block = 0;
					}
					
					block |= current & 1;
					block <<= 1;
					current >>>= 1;
					len++;
				}
			}
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
