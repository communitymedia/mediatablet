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
import java.util.UUID;

import ac.robinson.mediatablet.MediaTablet;
import ac.robinson.mediatablet.R;
import ac.robinson.util.DebugUtilities;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

public class MediaTabletProvider extends ContentProvider {

	public static final String URI_AUTHORITY = MediaTablet.APPLICATION_NAME;
	private static final String DATABASE_NAME = URI_AUTHORITY + ".db";
	private static final int DATABASE_VERSION = 1;

	public static final String URI_PREFIX = "content://";
	public static final String URI_SEPARATOR = File.separator;
	private final String URI_PACKAGE = this.getClass().getPackage().getName();

	public static final String HOMESTEADS_LOCATION = "homesteads";
	public static final String PEOPLE_LOCATION = "people";
	public static final String MEDIA_LOCATION = "media";

	// *must* start at 1... quite hacky
	public static final int TYPE_IMAGE_BACK = 1; // normal (rear) camera
	public static final int TYPE_IMAGE_FRONT = 2; // front camera
	public static final int TYPE_VIDEO = 3;
	public static final int TYPE_AUDIO = 4;
	public static final int TYPE_TEXT = 5;
	public static final int TYPE_NARRATIVE = 6;
	public static final int TYPE_UNKNOWN = 7; // for unknown media types

	// *must* be correct length (number of media types)
	public static final int[] ALL_MEDIA_TYPES = { TYPE_IMAGE_BACK, TYPE_IMAGE_FRONT, TYPE_VIDEO, TYPE_AUDIO, TYPE_TEXT,
			TYPE_NARRATIVE, TYPE_UNKNOWN };

	// *must* be correct length (number of media types)
	public static final int[] NO_MEDIA_TYPES = { 0, 0, 0, 0, 0, 0, 0 };

	private static final UriMatcher URI_MATCHER;
	static {
		URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
		URI_MATCHER.addURI(URI_AUTHORITY, HOMESTEADS_LOCATION, R.id.uri_homesteads);
		URI_MATCHER.addURI(URI_AUTHORITY, PEOPLE_LOCATION, R.id.uri_people);
		URI_MATCHER.addURI(URI_AUTHORITY, MEDIA_LOCATION, R.id.uri_media);
	}

	private SQLiteOpenHelper mOpenHelper;

	@Override
	public boolean onCreate() {
		mOpenHelper = new DatabaseHelper(getContext());
		return true;
	}

	public static String getNewInternalId() {
		return UUID.randomUUID().toString();
	}

	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {

		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

		switch (URI_MATCHER.match(uri)) {
			case R.id.uri_homesteads:
				qb.setTables(HOMESTEADS_LOCATION);
				break;
			case R.id.uri_people:
				qb.setTables(PEOPLE_LOCATION);
				break;
			case R.id.uri_media:
				qb.setTables(MEDIA_LOCATION);
				break;
			default:
				throw new IllegalArgumentException("Unknown URI " + uri);
		}

		// if no sort order is specified use none
		String orderBy;
		if (TextUtils.isEmpty(sortOrder)) {
			orderBy = null;
		} else {
			orderBy = sortOrder;
		}

		SQLiteDatabase db = mOpenHelper.getReadableDatabase();
		Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);
		c.setNotificationUri(getContext().getContentResolver(), uri);

		return c;
	}

	public String getType(Uri uri) {
		switch (URI_MATCHER.match(uri)) {
			case R.id.uri_homesteads:
			case R.id.uri_people:
			case R.id.uri_media:
				return "vnd.android.cursor.dir/vnd." + URI_PACKAGE; // do these need to be unique?

			default:
				throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	public Uri insert(Uri uri, ContentValues initialValues) {

		ContentValues values;
		if (initialValues != null) {
			values = new ContentValues(initialValues);
		} else {
			throw new IllegalArgumentException("No content values passed");
		}

		getType(uri); // so we don't get the database unless necessary
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();

		long rowId = 0;
		Uri contentUri = null;
		switch (URI_MATCHER.match(uri)) {
			case R.id.uri_homesteads:
				rowId = db.insert(HOMESTEADS_LOCATION, null, values);
				contentUri = HomesteadItem.CONTENT_URI;
				break;
			case R.id.uri_people:
				rowId = db.insert(PEOPLE_LOCATION, null, values);
				contentUri = PersonItem.CONTENT_URI;
				break;

			case R.id.uri_media:
				rowId = db.insert(MEDIA_LOCATION, null, values);
				contentUri = MediaItem.CONTENT_URI;
				break;
		}

		if (rowId > 0) {
			Uri insertUri = ContentUris.withAppendedId(contentUri, rowId);
			getContext().getContentResolver().notifyChange(uri, null);
			return insertUri;
		}
		throw new SQLException("Failed to insert row into " + uri);
	}

	public int delete(Uri uri, String selectionClause, String[] selectionArgs) {
		getType(uri); // so we don't get the database unless necessary
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();

		int count;
		switch (URI_MATCHER.match(uri)) {
			case R.id.uri_homesteads:
				count = db.delete(HOMESTEADS_LOCATION, selectionClause, selectionArgs);
				break;
			case R.id.uri_people:
				count = db.delete(PEOPLE_LOCATION, selectionClause, selectionArgs);
				break;
			case R.id.uri_media:
				count = db.delete(MEDIA_LOCATION, selectionClause, selectionArgs);
				break;
			default:
				throw new IllegalArgumentException("Unknown URI " + uri);
		}

		if (count > 0) {
			getContext().getContentResolver().notifyChange(uri, null);
		}
		return count;
	}

	public int update(Uri uri, ContentValues initialValues, String selectionClause, String[] selectionArgs) {

		ContentValues values;
		if (initialValues != null) {
			values = new ContentValues(initialValues);
		} else {
			throw new IllegalArgumentException("No content values passed");
		}

		getType(uri); // so we don't get the database unless necessary
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();

		int rowsAffected = 0;
		switch (URI_MATCHER.match(uri)) {
			case R.id.uri_media:
				rowsAffected = db.update(MEDIA_LOCATION, values, selectionClause, selectionArgs);
				break;
			case R.id.uri_people:
				rowsAffected = db.update(PEOPLE_LOCATION, values, selectionClause, selectionArgs);
				break;
			case R.id.uri_homesteads:
				rowsAffected = db.update(HOMESTEADS_LOCATION, values, selectionClause, selectionArgs);
				break;
		}

		if (rowsAffected > 0) {
			getContext().getContentResolver().notifyChange(uri, null);
		}
		return rowsAffected;
	}

	private static class DatabaseHelper extends SQLiteOpenHelper {
		DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {

			db.execSQL("CREATE TABLE " + HOMESTEADS_LOCATION + " (" //
					+ HomesteadItem._ID + " INTEGER PRIMARY KEY, " // required for Android Adapters
					+ HomesteadItem.INTERNAL_ID + " TEXT, " // the GUID of this homestead item
					+ HomesteadItem.X_POSITION + " INTEGER, " // the x position of this homestead
					+ HomesteadItem.Y_POSITION + " INTEGER, " // the y position of this homestead
					+ HomesteadItem.COLOUR + " INTEGER, " // the customised colour of this homestead
					+ HomesteadItem.DELETED + " INTEGER);"); // whether this homestead item has been deleted
			db.execSQL("CREATE INDEX " + HOMESTEADS_LOCATION + "Index" + HomesteadItem.INTERNAL_ID + " ON "
					+ HOMESTEADS_LOCATION + "(" + HomesteadItem.INTERNAL_ID + ");");
			db.execSQL("CREATE INDEX " + HOMESTEADS_LOCATION + "Index" + HomesteadItem.X_POSITION + " ON "
					+ HOMESTEADS_LOCATION + "(" + HomesteadItem.X_POSITION + ");");

			db.execSQL("CREATE TABLE " + PEOPLE_LOCATION + " (" //
					+ PersonItem._ID + " INTEGER PRIMARY KEY, " // required for Android Adapters
					+ PersonItem.INTERNAL_ID + " TEXT, " // the GUID of this person item
					+ PersonItem.PARENT_ID + " TEXT, " // the GUID of the parent of this person item
					+ PersonItem.NAME + " TEXT, " // the name this person has chosen (not unique)
					+ PersonItem.DATE_CREATED + " INTEGER, " // the timestamp when this person item was created
					+ PersonItem.PASSWORD_HASH + " TEXT, " // the SHA-1 hash of this person item's unlock code
					+ PersonItem.LOCK_STATUS + " INTEGER, " // either PersonItem.PERSON_LOCKED or PERSON_UNLOCKED
					+ PersonItem.UNLOCKED_TIMESTAMP + " TEXT, " // the timestamp when this person item was last unlocked
					+ PersonItem.DELETED + " INTEGER);"); // whether this person item has been deleted
			db.execSQL("CREATE INDEX " + PEOPLE_LOCATION + "Index" + PersonItem.INTERNAL_ID + " ON " + PEOPLE_LOCATION
					+ "(" + PersonItem.INTERNAL_ID + ");");
			db.execSQL("CREATE INDEX " + PEOPLE_LOCATION + "Index" + PersonItem.PARENT_ID + " ON " + PEOPLE_LOCATION
					+ "(" + PersonItem.PARENT_ID + ");");

			db.execSQL("CREATE TABLE " + MEDIA_LOCATION + " (" //
					+ MediaItem._ID + " INTEGER PRIMARY KEY, " // required for Android Adapters
					+ MediaItem.INTERNAL_ID + " TEXT, " // the GUID of this media item
					+ MediaItem.PARENT_ID + " TEXT, " // the GUID of the parent of this media item
					+ MediaItem.DATE_CREATED + " INTEGER, " // the timestamp when this media item was created
					+ MediaItem.FILE_EXTENSION + " TEXT, " // the file extension of this media item
					+ MediaItem.MEDIA_EXTRA + " TEXT, " // the original name of this media item, or its text content
					+ MediaItem.TYPE + " INTEGER, " // the type of this media (this.TYPE_<x>)
					+ MediaItem.VISIBILITY + " INTEGER, " // whether shared: MediaItem.MEDIA_PUBLIC or MEDIA_PRIVATE
					+ MediaItem.DELETED + " INTEGER);"); // whether this media item has been deleted
			db.execSQL("CREATE INDEX " + MEDIA_LOCATION + "Index" + MediaItem.INTERNAL_ID + " ON " + MEDIA_LOCATION
					+ "(" + MediaItem.INTERNAL_ID + ");");
			db.execSQL("CREATE INDEX " + MEDIA_LOCATION + "Index" + MediaItem.PARENT_ID + " ON " + MEDIA_LOCATION + "("
					+ MediaItem.PARENT_ID + ");");
			db.execSQL("CREATE INDEX " + MEDIA_LOCATION + "Index" + MediaItem.MEDIA_EXTRA + " ON " + MEDIA_LOCATION
					+ "(" + MediaItem.MEDIA_EXTRA + ");");
			db.execSQL("CREATE INDEX " + MEDIA_LOCATION + "Index" + MediaItem.VISIBILITY + " ON " + MEDIA_LOCATION
					+ "(" + MediaItem.VISIBILITY + ");");

		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			if (MediaTablet.DEBUG) {
				Log.d(DebugUtilities.getLogTag(this), "Database upgrade requested from version " + oldVersion + " to "
						+ newVersion + " - ignoring.");
			}
			// backup database if necessary
		}

		@Override
		public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			if (MediaTablet.DEBUG) {
				Log.d(DebugUtilities.getLogTag(this), "Database downgrade requested from version " + oldVersion
						+ " to " + newVersion + " - ignoring.");
			}
			// backup database if necessary
		}
	}
}
