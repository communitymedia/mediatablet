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

package ac.robinson.mediatablet.importing;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import ac.robinson.mediatablet.MediaTablet;
import ac.robinson.mediatablet.provider.MediaItem;
import ac.robinson.mediatablet.provider.MediaManager;
import ac.robinson.mediatablet.provider.MediaTabletProvider;
import ac.robinson.mediautilities.FrameMediaContainer;
import ac.robinson.mediautilities.MediaUtilities;
import ac.robinson.mediautilities.SMILUtilities;
import ac.robinson.util.DebugUtilities;
import ac.robinson.util.IOUtilities;
import android.content.ContentResolver;
import android.text.TextUtils;
import android.util.Log;

public class ImportedFileParser {

	public static MediaItem importMediaItem(ContentResolver contentResolver, String mediaParent, File importedFile,
			int mediaVisibility, boolean deleteFiles) {
		MediaItem newMediaItem = new MediaItem(mediaParent, importedFile.getName(), mediaVisibility);
		try {
			IOUtilities.copyFile(importedFile, newMediaItem.getFile());
			MediaManager.addMedia(contentResolver, newMediaItem);
		} catch (IOException e) {
			if (MediaTablet.DEBUG)
				Log.e(DebugUtilities.getLogTag(importedFile), "Error: unable to copy file for " + importedFile);
			return null;
		}
		if (deleteFiles) {
			importedFile.delete();
		}
		return newMediaItem;
	}

	public static MediaItem importTextItem(ContentResolver contentResolver, String mediaParent,
			String originalFilename, String text, int mediaVisibility) {
		MediaItem newMediaItem = new MediaItem(mediaParent, originalFilename, mediaVisibility);
		FileOutputStream fileOutputStream = null;
		try {
			fileOutputStream = new FileOutputStream(newMediaItem.getFile());
			fileOutputStream.write(text.getBytes());
			newMediaItem.setTextExtra(text);
			MediaManager.addMedia(contentResolver, newMediaItem);
		} catch (FileNotFoundException e) {
			return null;
		} catch (IOException e) {
			return null;
		} finally {
			IOUtilities.closeStream(fileOutputStream);
		}
		return newMediaItem;
	}

	public static void importSMILNarrative(ContentResolver contentResolver, File smilFile, String parentId,
			int visibility) {

		File tempSMILFile = new File(MediaTablet.DIRECTORY_TEMP, MediaTabletProvider.getNewInternalId()
				+ MediaUtilities.SYNC_FILE_EXTENSION);
		try {
			// so we don't delete (sync file is a non-media item in getSMILFrameList)
			IOUtilities.copyFile(smilFile, tempSMILFile);
		} catch (IOException e) {
			if (MediaTablet.DEBUG)
				Log.e(DebugUtilities.getLogTag(smilFile), "Unable to copy SMIL file");
		}

		ArrayList<FrameMediaContainer> smilFrames = SMILUtilities.getSMILFrameList(smilFile, 1, true);
		duplicateSMILElements(contentResolver, smilFrames, tempSMILFile, parentId, visibility, true);

		new File(smilFile.getParent(), IOUtilities.removeExtension(smilFile.getName())
				+ MediaUtilities.SMIL_FILE_EXTENSION).delete();
		tempSMILFile.delete();
	}

	public static void duplicateSMILElements(ContentResolver contentResolver,
			ArrayList<FrameMediaContainer> smilFrames, File smilFile, String parentId, int visibility,
			boolean deleteFiles) {

		ArrayList<StringPair> replacementSMILElements = new ArrayList<StringPair>();
		for (FrameMediaContainer frame : smilFrames) {
			if (frame.mImagePath != null) {
				final MediaItem newImageMedia = importMediaItem(contentResolver, parentId, new File(frame.mImagePath),
						visibility, deleteFiles);
				if (newImageMedia != null) {
					replacementSMILElements.add(new StringPair(new File(frame.mImagePath).getName(), newImageMedia
							.getFile().getName()));
				}
			}

			final ArrayList<String> newAudioPaths = new ArrayList<String>();
			for (String mediaPath : frame.mAudioPaths) {
				final MediaItem newAudioMedia = importMediaItem(contentResolver, parentId, new File(mediaPath),
						visibility, deleteFiles);
				if (newAudioMedia != null) {
					replacementSMILElements.add(new StringPair(new File(mediaPath).getName(), newAudioMedia.getFile()
							.getName()));
				}
			}
			frame.mAudioPaths = newAudioPaths;

			if (!TextUtils.isEmpty(frame.mTextContent)) {
				importTextItem(contentResolver, parentId, MediaTabletProvider.getNewInternalId() + ".txt",
						frame.mTextContent, visibility); // no need to save or replace text items
			}
		}

		// replace with the new names (hacky!)
		String newFileName = IOUtilities.removeExtension(smilFile.getName()) + MediaUtilities.SYNC_FILE_EXTENSION;
		MediaItem newNarrativeItem = new MediaItem(parentId, newFileName, visibility);
		BufferedReader smilFileReader = null;
		BufferedWriter smilFileWriter = null;
		try {
			smilFileReader = new BufferedReader(new FileReader(smilFile));
			smilFileWriter = new BufferedWriter(new FileWriter(newNarrativeItem.getFile()));
			String readLine = null;
			while ((readLine = smilFileReader.readLine()) != null) {
				for (StringPair sp : replacementSMILElements) {
					if (!sp.getReplaced() && readLine.contains(sp.getOriginal())) {
						readLine = readLine.replace(sp.getOriginal(), sp.getReplacement());
						sp.setReplaced();
					}
				}
				smilFileWriter.write(readLine + '\n');
			}
			MediaManager.addMedia(contentResolver, newNarrativeItem);
		} catch (Exception e) {
			if (MediaTablet.DEBUG)
				Log.e(DebugUtilities.getLogTag(smilFile), "Unable to update SMIL file paths");
		} finally {
			IOUtilities.closeStream(smilFileReader);
			IOUtilities.closeStream(smilFileWriter);
		}
	}

	private static class StringPair {
		private String mOriginal = null;
		private String mReplacement = null;
		private boolean mReplaced = false;

		public StringPair(String original, String replacement) {
			mOriginal = original;
			mReplacement = replacement;
		}

		public String getOriginal() {
			return mOriginal;
		}

		public String getReplacement() {
			return mReplacement;
		}

		public void setReplaced() {
			mReplaced = true;
		}

		public boolean getReplaced() {
			return mReplaced;
		}
	}
}
