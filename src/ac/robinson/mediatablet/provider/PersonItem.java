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

import java.io.File;

import com.larvalabs.svgandroid.SVG;
import com.larvalabs.svgandroid.SVGParser;

import ac.robinson.mediatablet.MediaTablet;
import ac.robinson.mediatablet.R;
import ac.robinson.util.BitmapUtilities;
import ac.robinson.util.BitmapUtilities.CacheTypeContainer;
import ac.robinson.util.ImageCacheUtilities;
import android.content.ContentValues;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.provider.BaseColumns;

public class PersonItem implements BaseColumns {

	public static final Uri CONTENT_URI = Uri.parse(MediaTabletProvider.URI_PREFIX + MediaTabletProvider.URI_AUTHORITY
			+ MediaTabletProvider.URI_SEPARATOR + MediaTabletProvider.PEOPLE_LOCATION);

	public static final String[] PROJECTION_ALL = new String[] { PersonItem._ID, PersonItem.INTERNAL_ID,
			PersonItem.PARENT_ID, PersonItem.NAME, PersonItem.DATE_CREATED, PersonItem.LOCK_STATUS,
			PersonItem.PASSWORD_HASH, PersonItem.UNLOCKED_TIMESTAMP, PersonItem.DELETED };

	public static final String INTERNAL_ID = "internal_id";
	public static final String PARENT_ID = "parent_id";
	public static final String NAME = "name";
	public static final String DATE_CREATED = "date_created";
	public static final String PASSWORD_HASH = "password_hash";
	public static final String LOCK_STATUS = "lock_status";
	public static final String UNLOCKED_TIMESTAMP = "unlocked_timestamp";
	public static final String DELETED = "deleted";

	public static final int PERSON_LOCKED = 0;
	public static final int PERSON_UNLOCKED = 1;

	// *DO NOT CHANGE*
	// internal id used for public media with no known owner (used for choosing storage locations)
	public static final String UNKNOWN_PERSON_ID = "028ff7a0-bb9b-11e1-afa7-0800200c9a66";
	public static final int UNKNOWN_PERSON_ICON = R.raw.ic_unknown_person;

	public static final String DEFAULT_SORT_ORDER = LOCK_STATUS + " DESC, " + UNLOCKED_TIMESTAMP + " DESC";

	private String mInternalId;
	private String mParentId;
	private String mName;
	private long mCreationDate;
	private String mPasswordHash;
	private int mLockStatus;
	private long mUnlockedTimestamp;
	private int mDeleted;

	public PersonItem(String internalId) {
		mInternalId = internalId;
		mParentId = null;
		mName = null;
		mCreationDate = System.currentTimeMillis();
		mPasswordHash = null;
		mLockStatus = PERSON_UNLOCKED;
		mUnlockedTimestamp = mCreationDate;
		mDeleted = 0;
	}

	public PersonItem() {
		this(MediaTabletProvider.getNewInternalId());
	}

	public String getInternalId() {
		return mInternalId;
	}

	public String getParentId() {
		return mParentId;
	}

	public void setParentId(String parentId) {
		mParentId = parentId;
	}

	public String getName() {
		return mName;
	}

	public void setName(String name) {
		mName = name;
	}

	public String getPasswordHash() {
		return mPasswordHash;
	}

	public void setPasswordHash(String newPasswordHash) {
		mPasswordHash = newPasswordHash;
	}

	public boolean isLocked() {
		return (mLockStatus == PERSON_LOCKED);
	}

	public boolean lockExpired() {
		return (mUnlockedTimestamp < System.currentTimeMillis() - MediaTablet.TIME_UNLOCKED_AFTER_SYNC);
	}

	public void setLockStatus(int lockStatus) {
		mLockStatus = lockStatus;
		if (mLockStatus == PERSON_UNLOCKED) {
			mUnlockedTimestamp = System.currentTimeMillis();
		}
	}

	public File getProfilePictureFile() {
		final File filePath = new File(getStorageDirectory(), mInternalId + ".jpg");
		return filePath;
	}

	public File getStorageDirectory() {
		return getStorageDirectory(mInternalId);
	}

	public static File getStorageDirectory(String personInternalId) {
		final File filePath = new File(MediaTablet.DIRECTORY_STORAGE, personInternalId);
		return filePath;
	}

	public boolean getDeleted() {
		return mDeleted == 0 ? false : true;
	}

	public void setDeleted(boolean deleted) {
		mDeleted = deleted ? 1 : 0;
	}

	public String getCacheId() {
		return getCacheId(mInternalId);
	}

	public static String getCacheId(String internalId) {
		return internalId;
	}

	public Bitmap loadIcon(Resources resources, CacheTypeContainer cacheTypeContainer) {

		File imageFile = getProfilePictureFile();
		int iconWidth = resources.getDimensionPixelSize(R.dimen.person_icon_width);
		int iconHeight = resources.getDimensionPixelSize(R.dimen.person_icon_height);
		Bitmap personBitmap = Bitmap.createBitmap(iconWidth, iconHeight,
				ImageCacheUtilities.mBitmapFactoryOptions.inPreferredConfig);
		Canvas personCanvas = new Canvas(personBitmap);
		Paint personPaint = BitmapUtilities.getPaint(Color.BLACK, 1);
		personCanvas.drawColor(resources.getColor(R.color.icon_background));

		if (imageFile.exists()) {
			Bitmap photoBitmap = BitmapUtilities.loadAndCreateScaledBitmap(imageFile.getAbsolutePath(), iconWidth,
					iconHeight, BitmapUtilities.ScalingLogic.CROP, true);
			personCanvas.drawBitmap(photoBitmap, (iconWidth - photoBitmap.getWidth()) / 2,
					(iconHeight - photoBitmap.getHeight()) / 2, personPaint);
		} else {
			// using SVG so that we don't need resolution-specific icons
			Rect drawRect = new Rect(0, 0, iconWidth, iconHeight);
			SVG personSVG = SVGParser.getSVGFromResource(resources, UNKNOWN_PERSON_ICON);
			personCanvas.drawPicture(personSVG.getPicture(), drawRect);

			cacheTypeContainer.type = Bitmap.CompressFormat.PNG; // PNG is much better for file sizes for non-jpeg data
		}

		return personBitmap;
	}

	public static Bitmap loadTemporaryIcon(Resources res, boolean addBorder) {
		int iconWidth = res.getDimensionPixelSize(R.dimen.person_icon_width);
		int iconHeight = res.getDimensionPixelSize(R.dimen.person_icon_height);
		Bitmap tempBitmap = Bitmap.createBitmap(iconWidth, iconHeight,
				ImageCacheUtilities.mBitmapFactoryOptions.inPreferredConfig);
		if (addBorder) {
			int borderWidth = res.getDimensionPixelSize(R.dimen.icon_border_width);
			Canvas tempBitmapCanvas = new Canvas(tempBitmap);
			Paint tempBitmapPaint = BitmapUtilities.getPaint(0, 1);
			tempBitmapCanvas.drawColor(res.getColor(R.color.icon_background));
			BitmapUtilities
					.addBorder(tempBitmapCanvas, tempBitmapPaint, borderWidth, res.getColor(R.color.icon_border));
		} else {
			tempBitmap.eraseColor(res.getColor(R.color.icon_background));
		}
		return tempBitmap;
	}

	public ContentValues getContentValues() {
		final ContentValues values = new ContentValues();
		values.put(INTERNAL_ID, mInternalId);
		values.put(PARENT_ID, mParentId);
		values.put(NAME, mName);
		values.put(DATE_CREATED, mCreationDate);
		values.put(PASSWORD_HASH, mPasswordHash);
		values.put(LOCK_STATUS, mLockStatus);
		values.put(UNLOCKED_TIMESTAMP, mUnlockedTimestamp);
		values.put(DELETED, mDeleted);
		return values;
	}

	public static PersonItem fromCursor(Cursor c) {
		final PersonItem person = new PersonItem();
		person.mInternalId = c.getString(c.getColumnIndexOrThrow(INTERNAL_ID));
		person.mParentId = c.getString(c.getColumnIndexOrThrow(PARENT_ID));
		person.mName = c.getString(c.getColumnIndexOrThrow(NAME));
		person.mCreationDate = c.getLong(c.getColumnIndexOrThrow(DATE_CREATED));
		person.mPasswordHash = c.getString(c.getColumnIndexOrThrow(PASSWORD_HASH));
		person.mLockStatus = c.getInt(c.getColumnIndexOrThrow(LOCK_STATUS));
		person.mUnlockedTimestamp = c.getLong(c.getColumnIndexOrThrow(UNLOCKED_TIMESTAMP));
		person.mDeleted = c.getInt(c.getColumnIndexOrThrow(DELETED));
		return person;
	}

	@Override
	public String toString() {
		return this.getClass().getName() + "[" + mInternalId + "," + mParentId + "," + mCreationDate + ","
				+ mLockStatus + "," + mUnlockedTimestamp + "," + mDeleted + "]";
	}
}
