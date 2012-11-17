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
import java.util.ArrayList;
import java.util.ListIterator;

import ac.robinson.mediatablet.R;
import ac.robinson.util.BitmapUtilities;
import ac.robinson.util.BitmapUtilities.CacheTypeContainer;
import ac.robinson.util.ImageCacheUtilities;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Region;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.TypedValue;

import com.larvalabs.svgandroid.SVG;
import com.larvalabs.svgandroid.SVGParser;

public class HomesteadItem implements BaseColumns {

	public static final Uri CONTENT_URI = Uri.parse(MediaTabletProvider.URI_PREFIX + MediaTabletProvider.URI_AUTHORITY
			+ MediaTabletProvider.URI_SEPARATOR + MediaTabletProvider.HOMESTEADS_LOCATION);

	public static final String[] PROJECTION_ALL = new String[] { HomesteadItem._ID, HomesteadItem.INTERNAL_ID,
			HomesteadItem.X_POSITION, HomesteadItem.Y_POSITION, HomesteadItem.COLOUR, MediaItem.DELETED };

	public static final String INTERNAL_ID = "internal_id";
	public static final String X_POSITION = "x_position";
	public static final String Y_POSITION = "y_position";
	public static final String COLOUR = "colour";
	public static final String DELETED = "deleted";

	public static final String DEFAULT_SORT_ORDER = X_POSITION + " ASC";

	private String mInternalId;
	private int mXPosition;
	private int mYPosition;
	private int mColour;
	private int mDeleted;

	// for drawing only; not saved in database
	private boolean mSelected;
	private ColorFilter mColourFilter;
	private static final int DEFAULT_COLOUR = Color.WHITE;

	public HomesteadItem(String internalId, int xPosition, int yPosition) {
		mInternalId = internalId;
		mXPosition = xPosition;
		mYPosition = yPosition;
		mColour = DEFAULT_COLOUR;
		mDeleted = 0;
		mSelected = false;
	}

	public HomesteadItem(int xPosition, int yPosition) {
		this(MediaTabletProvider.getNewInternalId(), xPosition, yPosition);
	}

	public HomesteadItem() {
		this(0, 0);
	}

	public String getInternalId() {
		return mInternalId;
	}

	public int getXPosition() {
		return mXPosition;
	}

	public void setXPosition(int xPosition) {
		mXPosition = xPosition;
	}

	public int getYPosition() {
		return mYPosition;
	}

	public void setYPosition(int yPosition) {
		mYPosition = yPosition;
	}

	public int getColour() {
		return mColour;
	}

	public void setColour(int colour) {
		mColourFilter = null;
		mColour = colour;
	}

	public ColorFilter getColourFilter() {
		if (mColour != DEFAULT_COLOUR) { // no need for white (the default colour)
			if (mColourFilter == null) {
				mColourFilter = new LightingColorFilter(mColour, 1);
			}
			return mColourFilter;
		}
		return null;
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

	public static String getInternalIdFromCacheId(String cacheId) {
		return cacheId;
	}

	public boolean getSelected() {
		return mSelected;
	}

	public void setSelected(boolean selected) {
		mSelected = selected;
	}

	public Bitmap loadIcon(Resources resources, ContentResolver contentResolver, CacheTypeContainer cacheTypeContainer) {
		return loadIcon(resources, contentResolver, cacheTypeContainer, 0);
	}

	public Bitmap loadIcon(Resources resources, ContentResolver contentResolver, CacheTypeContainer cacheTypeContainer,
			int requestedSize) {

		// must always be PNG for transparent background
		cacheTypeContainer.type = Bitmap.CompressFormat.PNG;

		int totalSize = resources.getDimensionPixelSize(R.dimen.homestead_icon_total_size);
		int requestedTotalSizeRevised = requestedSize <= 0 ? totalSize : requestedSize;
		Bitmap homesteadBitmap = Bitmap.createBitmap(requestedTotalSizeRevised, requestedTotalSizeRevised,
				ImageCacheUtilities.mBitmapFactoryOptions.inPreferredConfig);

		Canvas homesteadBitmapCanvas = new Canvas(homesteadBitmap);
		int iconSize = resources.getDimensionPixelSize(R.dimen.homestead_icon_size);
		int requestedSizeRevised = requestedSize <= 0 ? iconSize : Math.round((iconSize / (float) totalSize)
				* requestedSize);
		int iconStart = Math.round((requestedTotalSizeRevised - requestedSizeRevised) / 2f);
		Rect drawRect = new Rect(iconStart, iconStart, iconStart + requestedSizeRevised, iconStart
				+ requestedSizeRevised);

		// using SVG so that we don't need resolution-specific icons
		// TODO: may not work with hardware acceleration (fix - see: https://gist.github.com/6ebe5b818652d5ccc27c)
		SVG audioSVG = SVGParser.getSVGFromResource(resources, R.raw.ic_homestead);
		homesteadBitmapCanvas.drawPicture(audioSVG.getPicture(), drawRect);

		ArrayList<PersonItem> homesteadPeople = PersonManager.findPeopleByParentId(contentResolver, mInternalId);
		int numPeople = homesteadPeople.size();
		if (numPeople > 0) {

			Bitmap personBitmap;
			Bitmap unknownPersonBitmap = null;
			int strokeWidth = resources.getDimensionPixelSize(R.dimen.icon_border_width);
			Paint personPaint = BitmapUtilities.getPaint(resources.getColor(R.color.icon_person_border), strokeWidth);
			personPaint.setStyle(Paint.Style.STROKE);
			Path clipPath = new Path();
			Canvas clipCanvas;

			// make sure we can fit all the images in
			float scaleFactor = 1;
			TypedValue resourceValue = new TypedValue();
			resources.getValue(R.attr.homestead_icon_person_maximum_diameter_factor, resourceValue, true);
			int personIconSize = Math.round(resourceValue.getFloat() * requestedTotalSizeRevised);
			if (personIconSize * numPeople > 360) {
				scaleFactor = 360f / numPeople / personIconSize;
				personIconSize = Math.round(personIconSize * scaleFactor);
				resources.getValue(R.attr.homestead_icon_person_minimum_diameter_factor, resourceValue, true);
				int minimumSize = Math.round(resourceValue.getFloat() * requestedTotalSizeRevised);
				if (personIconSize < minimumSize) {
					personIconSize = minimumSize;
				}
			}

			// set up for drawing outlined person icons
			int halfPersonIconSize = personIconSize / 2;
			int halfStrokeWidth = (int) Math.floor(strokeWidth / 2f);
			Point centrePoint = new Point(Math.round(homesteadBitmap.getWidth() / 2f), Math.round(homesteadBitmap
					.getHeight() / 2f));
			Point startPoint = new Point(Math.round(homesteadBitmap.getWidth() - halfPersonIconSize - halfStrokeWidth),
					Math.round(homesteadBitmap.getHeight() / 2f));
			double currentAngle = (numPeople == 2 ? 0 : 2 * Math.PI * 7 / 8d); // make 2 people look better
			Point drawPoint = rotatePoint(new Point(startPoint.x, startPoint.y), startPoint, centrePoint, currentAngle);
			double angleIncrement = (Math.PI * 2) / numPeople;

			// finally, draw the actual icons
			// for (PersonItem person : homesteadPeople) { //instead, reverse the list to get items in better draw order
			for (ListIterator<PersonItem> it = homesteadPeople.listIterator(homesteadPeople.size()); it.hasPrevious();) {
				final PersonItem person = it.previous();
				final File pictureFile = person.getProfilePictureFile();
				if (pictureFile.exists()) {
					personBitmap = BitmapUtilities.loadAndCreateScaledBitmap(pictureFile.getAbsolutePath(),
							personIconSize, personIconSize, BitmapUtilities.ScalingLogic.CROP, true);
				} else {
					if (unknownPersonBitmap == null) {
						unknownPersonBitmap = Bitmap.createBitmap(personIconSize, personIconSize,
								ImageCacheUtilities.mBitmapFactoryOptions.inPreferredConfig);
						Rect unknownPersonRect = new Rect(0, 0, personIconSize, personIconSize);
						SVG personSVG = SVGParser.getSVGFromResource(resources, PersonItem.UNKNOWN_PERSON_ICON);
						Canvas unknownPersonCanvas = new Canvas(unknownPersonBitmap);
						unknownPersonCanvas.drawColor(Color.DKGRAY);
						unknownPersonCanvas.drawPicture(personSVG.getPicture(), unknownPersonRect);
					}
					personBitmap = unknownPersonBitmap;
				}

				currentAngle -= angleIncrement; // because we're drawing in reverse
				drawPoint = rotatePoint(drawPoint, startPoint, centrePoint, currentAngle);

				clipCanvas = new Canvas(personBitmap);
				clipPath.reset();
				clipPath.addCircle(halfPersonIconSize, halfPersonIconSize, halfPersonIconSize, Path.Direction.CW);
				clipCanvas.clipPath(clipPath, Region.Op.DIFFERENCE);
				clipCanvas.drawColor(0x00000000, PorterDuff.Mode.CLEAR);

				homesteadBitmapCanvas.drawBitmap(personBitmap, drawPoint.x - halfPersonIconSize, drawPoint.y
						- halfPersonIconSize, personPaint);

				clipPath.reset();
				clipPath.addCircle(drawPoint.x, drawPoint.y, halfPersonIconSize - halfStrokeWidth + 1,
						Path.Direction.CW);
				homesteadBitmapCanvas.drawPath(clipPath, personPaint);
			}
		}

		return homesteadBitmap;
	}

	private Point rotatePoint(Point drawPoint, Point startPoint, Point centrePoint, double angle) {
		double cosT = Math.cos(angle);
		double sinT = Math.sin(angle);
		drawPoint.x = (int) Math.round(cosT * (startPoint.x - centrePoint.x) - sinT * (startPoint.y - centrePoint.y)
				+ centrePoint.x);
		drawPoint.y = (int) Math.round(sinT * (startPoint.x - centrePoint.x) + cosT * (startPoint.y - centrePoint.y)
				+ centrePoint.y);
		return drawPoint;
	}

	public ContentValues getContentValues() {
		final ContentValues values = new ContentValues();
		values.put(INTERNAL_ID, mInternalId);
		values.put(X_POSITION, mXPosition);
		values.put(Y_POSITION, mYPosition);
		values.put(COLOUR, mColour);
		values.put(DELETED, mDeleted);
		return values;
	}

	public static HomesteadItem fromCursor(Cursor c) {
		final HomesteadItem homestead = new HomesteadItem();
		homestead.mInternalId = c.getString(c.getColumnIndexOrThrow(INTERNAL_ID));
		homestead.mXPosition = c.getInt(c.getColumnIndexOrThrow(X_POSITION));
		homestead.mYPosition = c.getInt(c.getColumnIndexOrThrow(Y_POSITION));
		homestead.mColour = c.getInt(c.getColumnIndexOrThrow(COLOUR));
		homestead.mDeleted = c.getInt(c.getColumnIndexOrThrow(DELETED));
		return homestead;
	}

	@Override
	public String toString() {
		return "HomesteadItem[" + mInternalId + "," + mXPosition + "," + mYPosition + "," + mColour + "," + mDeleted
				+ "]";
	}
}
