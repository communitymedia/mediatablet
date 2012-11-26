/*
 *  Copyright (C) 2012 Simon Robinson
 * 
 *  This file is part of Com-Me.
 * 
 *  Com-Me is free software; you can redistribute it and/or modify it 
 *  under the terms of the GNU Lesser General Public License as 
 *  published by the Free Software Foundation; either version 3 of the 
 *  License, or (at your option) any later version.
 *
 *  Com-Me is distributed in the hope that it will be useful, but WITHOUT 
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General 
 *  Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with Com-Me.
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package ac.robinson.mediatablet.provider;

import java.util.Vector;

import ac.robinson.mediatablet.MediaTablet;
import ac.robinson.util.BitmapUtilities.CacheTypeContainer;
import ac.robinson.util.ImageCacheUtilities;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;

public class HomesteadManager {
	private static String mHomesteadInternalIdSelection;
	private static String[] mArguments1 = new String[1];

	static {
		StringBuilder selection = new StringBuilder();
		selection.append(HomesteadItem.INTERNAL_ID);
		selection.append("=?");
		mHomesteadInternalIdSelection = selection.toString();
	}

	public static void reloadHomesteadIcon(Resources resources, ContentResolver contentResolver, HomesteadItem homestead) {
		// use the best type for photo/icon
		CacheTypeContainer cacheTypeContainer = new CacheTypeContainer(MediaTablet.ICON_CACHE_TYPE);
		Bitmap homesteadIcon = homestead.loadIcon(resources, contentResolver, cacheTypeContainer);

		ImageCacheUtilities.addIconToCache(MediaTablet.DIRECTORY_THUMBS, homestead.getCacheId(), homesteadIcon,
				cacheTypeContainer.type, MediaTablet.ICON_CACHE_QUALITY);
	}

	public static void reloadHomesteadIcon(Resources resources, ContentResolver contentResolver, String homesteadId) {
		reloadHomesteadIcon(resources, contentResolver, findHomesteadByInternalId(contentResolver, homesteadId));
	}

	public static HomesteadItem addHomestead(ContentResolver resolver, HomesteadItem homestead) {
		return addHomestead(null, resolver, homestead, false);
	}

	public static HomesteadItem addHomestead(Resources resources, ContentResolver resolver, HomesteadItem homestead,
			boolean loadIcon) {
		final Uri uri = resolver.insert(HomesteadItem.CONTENT_URI, homestead.getContentValues());
		if (uri != null) {
			if (loadIcon) {
				reloadHomesteadIcon(resources, resolver, homestead);
			}
			return homestead;
		}
		return null;
	}

	public static boolean updateHomestead(ContentResolver contentResolver, HomesteadItem homestead) {
		final String[] arguments1 = mArguments1;
		arguments1[0] = homestead.getInternalId();
		int count = contentResolver.update(HomesteadItem.CONTENT_URI, homestead.getContentValues(),
				mHomesteadInternalIdSelection, arguments1);
		return count == 1;
	}

	// okay to actually delete here (rather than just setting deleted) - we have no adapter trying to load deleted items
	// TODO: delete people when homestead is deleted?
	public static boolean deleteHomesteadByInternalId(ContentResolver contentResolver, String homesteadId) {
		final String[] arguments1 = mArguments1;
		arguments1[0] = homesteadId;
		int count = contentResolver.delete(HomesteadItem.CONTENT_URI, mHomesteadInternalIdSelection, arguments1);
		return count > 0;
	}

	public static void loadHomesteads(ContentResolver contentResolver, Vector<HomesteadItem> homesteadStore) {
		homesteadStore.clear();
		Cursor c = null;
		try {
			c = contentResolver.query(HomesteadItem.CONTENT_URI, HomesteadItem.PROJECTION_ALL, null, null, null);
			if (c.getCount() > 0) {
				while (c.moveToNext()) {
					final HomesteadItem homestead = HomesteadItem.fromCursor(c);
					homesteadStore.add(homestead);
				}
			}
		} finally {
			if (c != null) {
				c.close();
			}
		}
	}

	public static HomesteadItem findHomesteadByInternalId(ContentResolver contentResolver, String internalId) {
		Cursor c = null;
		try {
			final String[] arguments1 = mArguments1;
			arguments1[0] = internalId;
			c = contentResolver.query(HomesteadItem.CONTENT_URI, HomesteadItem.PROJECTION_ALL,
					mHomesteadInternalIdSelection, arguments1, null);
			if (c.getCount() > 0) { // TODO: this assumes there are no duplicates...
				if (c.moveToFirst()) {
					final HomesteadItem homestead = HomesteadItem.fromCursor(c);
					return homestead;
				}
			}
		} finally {
			if (c != null) {
				c.close();
			}
		}
		return null;
	}
}
