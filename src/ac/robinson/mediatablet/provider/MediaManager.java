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

import ac.robinson.mediatablet.MediaTablet;
import ac.robinson.util.BitmapUtilities.CacheTypeContainer;
import ac.robinson.util.ImageCacheUtilities;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;

public class MediaManager {

	private static String[] mArguments1 = new String[1];

	private static String mMediaInternalIdSelection;

	static {
		StringBuilder selection = new StringBuilder();
		selection.append(MediaItem.INTERNAL_ID);
		selection.append("=?");
		mMediaInternalIdSelection = selection.toString();
	}

	public static void reloadMediaIcon(Resources resources, ContentResolver contentResolver, MediaItem media,
			int visibility) {
		// use the best type for photo/text/icon
		CacheTypeContainer cacheTypeContainer = new CacheTypeContainer(MediaTablet.ICON_CACHE_TYPE);
		Bitmap mediaIcon = media.loadIcon(resources, cacheTypeContainer, contentResolver,
				visibility == MediaItem.MEDIA_PUBLIC ? media.getParentId() : null);

		ImageCacheUtilities.addIconToCache(MediaTablet.DIRECTORY_THUMBS, media.getCacheId(visibility), mediaIcon,
				cacheTypeContainer.type, MediaTablet.ICON_CACHE_QUALITY);
	}

	public static void reloadMediaIcon(Resources resources, ContentResolver contentResolver, String mediaId,
			int visibility) {
		reloadMediaIcon(resources, contentResolver, findMediaByInternalId(contentResolver, mediaId), visibility);
	}

	public static MediaItem addMedia(ContentResolver resolver, MediaItem media) {
		return addMedia(null, resolver, media, false);
	}

	public static MediaItem addMedia(Resources resources, ContentResolver resolver, MediaItem media, boolean loadIcon) {
		final Uri uri = resolver.insert(MediaItem.CONTENT_URI, media.getContentValues());
		if (uri != null) {
			if (loadIcon) {
				reloadMediaIcon(resources, resolver, media, MediaItem.MEDIA_PRIVATE);
				if (media.isPubliclyShared()) {
					reloadMediaIcon(resources, resolver, media, MediaItem.MEDIA_PUBLIC);
				}
			}
			return media;
		}
		return null;
	}

	/** 
	 * Set deleted instead; do this onDestroy (but think carefully about deleting narrative components)
	 */
	@Deprecated
	public static boolean deleteMedia(ContentResolver contentResolver, MediaItem media) {
		final String[] arguments1 = mArguments1;
		arguments1[0] = media.getInternalId();
		int count = contentResolver.delete(MediaItem.CONTENT_URI, mMediaInternalIdSelection, arguments1);
		// delete cached icons (public and private) and media file
		return count > 0;
	}

	public static boolean updateMedia(ContentResolver contentResolver, MediaItem media) {
		return updateMedia(null, contentResolver, media, false);
	}

	public static boolean updateMedia(Resources resources, ContentResolver contentResolver, MediaItem media,
			boolean reloadIcon) {
		if (reloadIcon) {
			reloadMediaIcon(resources, contentResolver, media, MediaItem.MEDIA_PRIVATE);
			if (media.isPubliclyShared()) {
				reloadMediaIcon(resources, contentResolver, media, MediaItem.MEDIA_PUBLIC);
			}
		}
		final String[] arguments1 = mArguments1;
		arguments1[0] = media.getInternalId();
		int count = contentResolver.update(MediaItem.CONTENT_URI, media.getContentValues(), mMediaInternalIdSelection,
				arguments1);
		return count == 1;
	}

	public static MediaItem findMediaByInternalId(ContentResolver contentResolver, String internalId) {
		final String[] arguments1 = mArguments1;
		arguments1[0] = internalId;
		return findMedia(contentResolver, mMediaInternalIdSelection, arguments1);
	}

	private static MediaItem findMedia(ContentResolver contentResolver, String clause, String[] arguments) {
		Cursor c = null;
		try {
			// could add sort order here, but we assume no duplicates...
			c = contentResolver.query(MediaItem.CONTENT_URI, MediaItem.PROJECTION_ALL, clause, arguments, null);
			if (c.getCount() > 0) {
				if (c.moveToFirst()) {
					final MediaItem media = MediaItem.fromCursor(c);
					return media;
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
