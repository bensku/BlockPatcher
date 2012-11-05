/*
 *  BlockPatcher - Safely convert one block ID to another for the client only 
 *  Copyright (C) 2012 Kristian S. Stangeland
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the 
 *  GNU General Public License as published by the Free Software Foundation; either version 2 of 
 *  the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; 
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with this program; 
 *  if not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 
 *  02111-1307 USA
 */
package com.comphenix.blockpatcher;

import com.comphenix.blockpatcher.lookup.ChunkLookup;
import com.comphenix.blockpatcher.lookup.ChunkSegmentLookup;

/**
 * Able to automatically translate every block and item on the server to a different type. 
 * <p>
 * This conversion is only client side and will never affect the actual world files. To use
 * different conversions per player or chunk, subscribe to the ChunkPostProcessingEvent.
 * <p>
 * To convert items, subscribe to the event ItemConvertingEvent.
 * @author Kristian
 */
public final class PatcherAPI extends ChunkSegmentLookup {
	/**
	 * Generated by Eclipse.
	 */
	private static final long serialVersionUID = 9149758527563036643L;

	public PatcherAPI() {
		// Use the identity lookup table
		super(new ChunkLookup());
	}

	/**
	 * Generate a translation table that doesn't change any value.
	 * @param max - the maximum number of entries in the table.
	 * @return The identity translation table.
	 */
	public byte[] getIdentityTranslation(int max) {
		return ChunkLookup.getIdentityTranslation(max);
	}
	
	/**
	 * Generate an identity translation table that doesn't change anything.
	 * @param blocks - number of blocks.
	 * @param entries - number of data values per block.
	 * @return The translation table.
	 */
	public byte[] getDataIdentity(int blocks, int entries) {
		return ChunkLookup.getDataIdentity(blocks, entries);
	}
}
