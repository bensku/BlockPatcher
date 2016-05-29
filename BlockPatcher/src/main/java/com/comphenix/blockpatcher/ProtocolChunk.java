package com.comphenix.blockpatcher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.apache.commons.lang3.Validate;

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
	public static final int PALETTE_FREE = 10; // Additional space reserved in palette array by default
	public static final int BIT_ARRAY_SIZE = 4096; // Data bit array size
	public static final int BIOME_DATA = 256; // Byte array size
	
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
	private int chunkMask;
	
	public Section[] sections;
	public byte[] biomes;
	
	public ProtocolChunk(byte[] buf, boolean hasSkylight, int chunkMask) {
		this.buf = buf;
		this.mcSerializer = StreamSerializer.getDefault();
		this.hasSkylight = hasSkylight;
		this.sections = new Section[WORLD_HEIGHT];
		this.chunkMask = chunkMask;
	}
	
	public ProtocolChunk read() {
		DataInputStream is = new DataInputStream(new ByteArrayInputStream(buf));
		for (int i = 0; i < WORLD_HEIGHT; i++) {
			if ((chunkMask & 1 << i) > 0)
				sections[i] = new Section().read(is);
		}
		
		biomes = new byte[256];
		try {
			is.read(biomes);
		} catch (IOException e) {
			throw new ChunkReadException("Invalid biome data (IOException)!");
		}
		
		return this;
	}
	
	public byte[] write() {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream os = new DataOutputStream(bos);
		for (int i = 0; i < sections.length; i++) {
			if ((chunkMask & 1 << i) > 0)
				sections[i].write(os);
		}
		
		try {
			os.write(biomes);
		} catch (IOException e) {
			throw new ChunkReadException("Invalid biome data (IOException)!");
		}
		
		return bos.toByteArray();
	}
	
	/**
	 * Replaces all blocks of given id. This is faster than finding those
	 * blocks yourself and manually editing them. Doesn't use palette ids.
	 * @param from Replaced block.
	 * @param to Replacement block.
	 */
	public void replaceAll(int from, int to) {
		for (int i = 0; i < sections.length; i++) {
			Section sec = sections[i];
			if ((chunkMask & 1 << i) > 0)
				sec.replaceAll(sec.getPaletteId(from), sec.getPaletteId(to));
		}
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

		public int bitsPerBlock;
		public int[] palette;
		public int paletteFree;
		public long[] data;
		public byte[] blockLight;
		public byte[] skylight;
		
		private long maxEntryValue;

		public Section() {
			
		}

		/**
		 * Should be called before doing anything else.
		 * @return This for chaining.
		 */
		public Section read(DataInputStream is) {
			try {
				bitsPerBlock = is.readUnsignedByte();
				maxEntryValue = (1L << bitsPerBlock) - 1L;

				int paletteLength = mcSerializer.deserializeVarInt(is);
				//System.out.println("palette:" + paletteLength);
				palette = new int[paletteLength + PALETTE_FREE];
				paletteFree = PALETTE_FREE;
				for (int i = 0; i < paletteLength; i++) {
					palette[i] = mcSerializer.deserializeVarInt(is);
				}

				int dataLength = mcSerializer.deserializeVarInt(is);
				//System.out.println("data:" + dataLength);
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
				//System.out.println("read() done");
			} catch (IOException e) {
				//e.printStackTrace();
				throw new ChunkReadException("Invalid chunk section (IOException)!");
			}
			
			return this;
		}
		
		public Section write(DataOutputStream os) {
			try {
				os.writeByte(bitsPerBlock);
				
				int paletteLength = palette.length - paletteFree;
				mcSerializer.serializeVarInt(os, paletteLength);
				for (int i = 0; i < paletteLength; i++) {
					mcSerializer.serializeVarInt(os, palette[i]);
				}
				
				mcSerializer.serializeVarInt(os, data.length);
				for (long i : data) {
					os.writeLong(i);
				}
				
				for (int i = 0; i < LIGHT_DATA; i++) {
					os.writeByte(blockLight[i]);
				}
				
				if (hasSkylight) {
					for (int i = 0; i < LIGHT_DATA; i++) {
						os.writeByte(skylight[i]);
					}
				}
			} catch (IOException e) {
				throw new ChunkWriteException("Invalid chunk section (IOException)!");
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
		 * GLOBAL, given block id is returned.
		 * @param blockId Block id.
		 * @return Palette id or given block id.
		 */
		public int getPaletteId(int blockId) {
			if (bitsPerBlock > 8)
				return blockId;
			
			for (int i = 0; i < palette.length; i++) {
				if (palette[i] == blockId)
					return i;
			}
			
			if (paletteFree == 0) {
				int[] newPalette = new int[palette.length + PALETTE_FREE];
				System.arraycopy(palette, 0, newPalette, 0, palette.length);
				palette = newPalette;
				paletteFree = PALETTE_FREE;
			}
			int pos = palette.length - paletteFree;
			palette[pos] = blockId;
			paletteFree--;
			
			return pos;
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
		 * @param index Index.
		 * @return Palette id (not block id, usually!) of block.
		 */
		public int getBlock(int index) {
			return getAt(index);
		}
		
		/**
		 * Sets block at given index to given value.
		 * @param index Index
		 * @param block Block id.
		 */
		public void setBlock(int index, int block) {
			setAt(index, block);
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
	     * use it if you can. Uses palette ids.
	     * @param from Block to change.
	     * @param to Result block.
	     */
		public void replaceAll(int from, int to) {
			for (int i = 0; i < BIT_ARRAY_SIZE; i++) {
				int block = getAt(i);
				if (block == from) {
					setAt(i, to);
					//System.out.println("setAt()");
				}
			}
		}
		
	    /**
	     * Sets the entry at the given location to the given value
	     */
	    private void setAt(int index, int value) {
	        int i = index * this.bitsPerBlock;
	        int j = i / 64;
	        int k = ((index + 1) * this.bitsPerBlock - 1) / 64;
	        int l = i % 64;
	        this.data[j] = this.data[j] & ~(this.maxEntryValue << l) | ((long)value & this.maxEntryValue) << l;

	        if (j != k) {
	            int i1 = 64 - l;
	            int j1 = this.bitsPerBlock - i1;
	            this.data[k] = this.data[k] >>> j1 << j1 | ((long)value & this.maxEntryValue) >> i1;
	        }
	    }

	    /**
	     * Gets the entry at the given index
	     */
	    private int getAt(int index) {
	        Validate.inclusiveBetween(0L, (long)(BIT_ARRAY_SIZE - 1), (long)index);
	        int i = index * this.bitsPerBlock;
	        int j = i / 64;
	        int k = ((index + 1) * this.bitsPerBlock - 1) / 64;
	        int l = i % 64;

	        if (j == k) {
	            return (int)(this.data[j] >>> l & this.maxEntryValue);
	        }
	        else
	        {
	            int i1 = 64 - l;
	            return (int)((this.data[j] >>> l | this.data[k] << i1) & this.maxEntryValue);
	        }
	    }

	}
	
	public class ChunkReadException extends RuntimeException {

		private static final long serialVersionUID = 3907261534758829959L; // By Eclipse
		
		public ChunkReadException(String cause) {
			super(cause);
		}
	}
	
	public class ChunkWriteException extends RuntimeException {
		
		private static final long serialVersionUID = -643442627095677464L; // By Eclipse

		public ChunkWriteException(String cause) {
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
