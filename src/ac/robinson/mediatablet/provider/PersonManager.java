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

import java.util.ArrayList;

import ac.robinson.mediatablet.MediaTablet;
import ac.robinson.util.BitmapUtilities.CacheTypeContainer;
import ac.robinson.util.ImageCacheUtilities;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;

public class PersonManager {

	private static String[] mArguments1 = new String[1];

	private static String mPersonInternalIdSelection;
	private static String mPersonParentIdSelection;

	static {
		StringBuilder selection = new StringBuilder();
		selection.append(PersonItem.INTERNAL_ID);
		selection.append("=?");
		mPersonInternalIdSelection = selection.toString();

		selection.setLength(0);
		selection.append("(");
		selection.append(PersonItem.DELETED);
		selection.append("=0 AND ");
		selection.append(PersonItem.PARENT_ID);
		selection.append("=?");
		selection.append(")");
		mPersonParentIdSelection = selection.toString();
	}

	public static void reloadPersonIcon(Resources resources, PersonItem person) {
		// use the best type for photo/icon
		CacheTypeContainer cacheTypeContainer = new CacheTypeContainer(MediaTablet.ICON_CACHE_TYPE);
		Bitmap personIcon = person.loadIcon(resources, cacheTypeContainer);

		ImageCacheUtilities.addIconToCache(MediaTablet.DIRECTORY_THUMBS, person.getCacheId(), personIcon,
				cacheTypeContainer.type, MediaTablet.ICON_CACHE_QUALITY);
	}

	public static void reloadPersonIcon(Resources resources, ContentResolver contentResolver, String personId) {
		reloadPersonIcon(resources, findPersonByInternalId(contentResolver, personId));
	}

	public static PersonItem addPerson(ContentResolver resolver, PersonItem person) {
		return addPerson(null, resolver, person, false);
	}

	public static PersonItem addPerson(Resources resources, ContentResolver resolver, PersonItem person,
			boolean loadIcon) {
		final Uri uri = resolver.insert(PersonItem.CONTENT_URI, person.getContentValues());
		if (uri != null) {
			if (loadIcon) {
				reloadPersonIcon(resources, person);
			}
			return person;
		}
		return null;
	}

	/** 
	 * Set deleted instead; do this onDestroy (but think carefully about deleting narrative components)
	 */
	@Deprecated
	public static boolean deletePerson(ContentResolver contentResolver, PersonItem person) {
		final String[] arguments1 = mArguments1;
		arguments1[0] = person.getInternalId();
		int count = contentResolver.delete(PersonItem.CONTENT_URI, mPersonInternalIdSelection, arguments1);
		// delete cached icons and photo file, plus sub-media
		return count > 0;
	}

	public static boolean lockAllPeople(ContentResolver contentResolver) {
		final ContentValues values = new ContentValues();
		values.put(PersonItem.LOCK_STATUS, PersonItem.PERSON_LOCKED);
		int count = contentResolver.update(PersonItem.CONTENT_URI, values, null, null);
		return count > 0;
	}

	public static boolean updatePerson(ContentResolver contentResolver, PersonItem person) {
		return updatePerson(null, contentResolver, person, false);
	}

	public static boolean updatePerson(Resources resources, ContentResolver contentResolver, PersonItem person,
			boolean reloadIcon) {
		if (reloadIcon) {
			reloadPersonIcon(resources, person);
		}
		final String[] arguments1 = mArguments1;
		arguments1[0] = person.getInternalId();
		int count = contentResolver.update(PersonItem.CONTENT_URI, person.getContentValues(),
				mPersonInternalIdSelection, arguments1);
		return count == 1;
	}

	public static PersonItem findPersonByInternalId(ContentResolver contentResolver, String internalId) {
		final String[] arguments1 = mArguments1;
		arguments1[0] = internalId;
		return findPerson(contentResolver, mPersonInternalIdSelection, arguments1);
	}

	private static PersonItem findPerson(ContentResolver contentResolver, String clause, String[] arguments) {
		Cursor c = null;
		try {
			c = contentResolver.query(PersonItem.CONTENT_URI, PersonItem.PROJECTION_ALL, clause, arguments, null);
			if (c.getCount() > 0) { // TODO: this assumes there are no duplicates...
				if (c.moveToFirst()) {
					return PersonItem.fromCursor(c);
				}
			}
		} finally {
			if (c != null) {
				c.close();
			}
		}
		return null;
	}

	public static ArrayList<PersonItem> findPeopleByParentId(ContentResolver contentResolver, String parentId) {
		final String[] arguments1 = mArguments1;
		arguments1[0] = parentId;
		ArrayList<PersonItem> people = new ArrayList<PersonItem>();
		Cursor c = null;
		try {
			c = contentResolver.query(PersonItem.CONTENT_URI, PersonItem.PROJECTION_ALL, mPersonParentIdSelection,
					arguments1, PersonItem.DEFAULT_SORT_ORDER);
			if (c.getCount() > 0) {
				while (c.moveToNext()) {
					people.add(PersonItem.fromCursor(c));
				}
			}
		} finally {
			if (c != null) {
				c.close();
			}
		}
		return people;
	}
}
