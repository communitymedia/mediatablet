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
	public static final boolean DEBUG = false; // note: must add android.permission.INTERNET for ViewServer debugging

	// file extensions to help with sorting media items
	public static final String[] TYPE_IMAGE_EXTENSIONS = { "jpg", "jpeg", "gif", "png", "bmp" };
	public static final String[] TYPE_VIDEO_EXTENSIONS = { "mp4", "mpg", "mpeg", "mov", "avi" };
	public static final String[] TYPE_AUDIO_EXTENSIONS = { "m4a", "aac", "mp3", "wav", "3gp", "3gpp", "amr", "ogg" };
	public static final String[] TYPE_TEXT_EXTENSIONS = { "txt" }; // TODO: pdf, doc etc?

	// default to jpeg for smaller file sizes (will be overridden for frames that do not contain image media)
	public static final Bitmap.CompressFormat ICON_CACHE_TYPE = Bitmap.CompressFormat.JPEG;
	public static final int ICON_CACHE_QUALITY = 80;

	// -----------------------------------------------------------------------------------------------------------------
	// The following are globals for cases where we can't get a context (or it's not worth it) - all of these are
	// overridden at startup with values that are either detected automatically (e.g., paths), or loaded from attrs.xml
	// -----------------------------------------------------------------------------------------------------------------

	// storage, cache and temp directories
	public static File DIRECTORY_STORAGE; // to store user content
	public static File DIRECTORY_THUMBS; // for the frame thumbnails
	public static File DIRECTORY_TEMP; // currently used for outgoing files - must be world readable

	// the directory to watch for bluetooth imports - devices vary (see: http://stackoverflow.com/questions/6125993)
	public static String IMPORT_DIRECTORY;
	static {
		final String possibleImportDirectory = File.separator + "mnt" + File.separator + "sdcard" + File.separator
				+ "downloads" + File.separator + "bluetooth";
		if (new File(possibleImportDirectory).exists()) {
			IMPORT_DIRECTORY = possibleImportDirectory;
		} else {
			IMPORT_DIRECTORY = File.separator + "mnt" + File.separator + "sdcard" + File.separator + "bluetooth";
		}
	}

	// this is generated on first use (when prompting for the panorama image) and overwritten here thereafter
	public static String ADMINISTRATOR_PASSWORD = "";

	// -----------------------------------------------------------------------------------------------------------------
	// The following are globals that should eventually be moved to preferences, detected, or overridden at startup
	// -----------------------------------------------------------------------------------------------------------------
	public static final int NARRATIVE_DEFAULT_FRAME_DURATION = 2500; // milliseconds
	public static final int MAXIMUM_PERSON_TEXT_LENGTH = 18;
	public static final int TIME_UNLOCKED_AFTER_SYNC = 600000; // in milliseconds (10 minutes currently)
	public static final int ANIMATION_FADE_TRANSITION_DURATION = 175;
	public static final int ANIMATION_ICON_SHOW_DELAY = 350; // time after scroll has finished before showing grid icons
	public static final int ANIMATION_GRIDHINT_SHOW_DELAY = 200;
	public static final int ANIMATION_GRIDHINT_HIDE_DELAY = 200;
	public static final int MESSAGE_UPDATE_GRID_ICONS = 6;
}
