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

package ac.robinson.mediatablet;

import java.io.File;

import android.graphics.Bitmap;

public class MediaTablet {

	public static final String APPLICATION_NAME = "mediatablet"; // *must* match provider in AndroidManifest.xml
	public static final boolean DEBUG = false;

	// storage, cache and temp directories
	// TODO: check (ie. if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {) each time we r/w
	// NOTE: automatically initialised on application start
	public static File DIRECTORY_STORAGE; // to store user content
	public static File DIRECTORY_THUMBS; // for the frame thumbnails
	public static File DIRECTORY_TEMP; // currently used for outgoing files - must be world readable

	// the directory to watch for bluetooth imports - devices vary (see: http://stackoverflow.com/questions/6125993)
	// NOTE: overridden with values loaded from attrs.xml at startup
	public static String IMPORT_DIRECTORY;
	static {
		String possibleImportDirectory = File.separator + "mnt" + File.separator + "sdcard" + File.separator
				+ "downloads" + File.separator + "bluetooth";
		if (new File(possibleImportDirectory).exists()) {
			IMPORT_DIRECTORY = File.separator + "mnt" + File.separator + "sdcard" + File.separator + "downloads"
					+ File.separator + "bluetooth";
		} else {
			IMPORT_DIRECTORY = File.separator + "mnt" + File.separator + "sdcard" + File.separator + "bluetooth";
		}
	}

	// to record whether we're on the external storage or not, and track when we're moved to the internal storage
	// NOTE: other preference keys are in strings.xml
	public static final String KEY_USE_EXTERNAL_STORAGE = "key_use_external_storage";

	public static final String[] TYPE_IMAGE_EXTENSIONS = { "jpg", "jpeg", "gif", "png", "bmp" };
	public static final String[] TYPE_VIDEO_EXTENSIONS = { "mp4", "mpg", "mov", "avi" };
	public static final String[] TYPE_AUDIO_EXTENSIONS = { "m4a", "3gp", "mp3", "aac", "ogg", "amr" };
	public static final String[] TYPE_TEXT_EXTENSIONS = { "txt" }; // TODO: pdf, doc etc?

	public static final int NARRATIVE_DEFAULT_FRAME_DURATION = 2500; // milliseconds

	// this is generated on first use (when prompting for panorama image) and overwritten here
	public static String ADMINISTRATOR_PASSWORD = "";

	// default to jpeg for smaller file sizes (will be overridden for frames that do not contain image media)
	public static final Bitmap.CompressFormat ICON_CACHE_TYPE = Bitmap.CompressFormat.JPEG;
	public static final int ICON_CACHE_QUALITY = 80;

	// TODO: attrs
	public static final int MAXIMUM_PERSON_TEXT_LENGTH = 18;
	public static final int TIME_UNLOCKED_AFTER_SYNC = 600000; // in milliseconds (10 minutes currently)
	public static final int ANIMATION_FADE_TRANSITION_DURATION = 175;
	public static final int ANIMATION_ICON_SHOW_DELAY = 350; // time after scroll has finished before showing grid icons
	public static final int ANIMATION_GRIDHINT_SHOW_DELAY = 200;
	public static final int ANIMATION_GRIDHINT_HIDE_DELAY = 200;
	public static final int MESSAGE_UPDATE_GRID_ICONS = 6;
}
