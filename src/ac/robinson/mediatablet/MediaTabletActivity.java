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

import ac.robinson.mediatablet.activity.AddUserActivity;
import ac.robinson.mediatablet.activity.HomesteadBrowserActivity;
import ac.robinson.mediatablet.activity.MediaBrowserActivity;
import ac.robinson.mediatablet.activity.PreferencesActivity;
import ac.robinson.mediatablet.importing.ImportedFileParser;
import ac.robinson.mediatablet.provider.MediaItem;
import ac.robinson.mediatablet.provider.PersonItem;
import ac.robinson.mediautilities.FrameMediaContainer;
import ac.robinson.mediautilities.MediaUtilities;
import ac.robinson.util.DebugUtilities;
import ac.robinson.util.UIUtilities;
import ac.robinson.util.ViewServer;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;

public abstract class MediaTabletActivity extends Activity {

	abstract protected void loadPreferences(SharedPreferences mediaTabletSettings);

	abstract protected String getCurrentPersonId();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (MediaTablet.DEBUG) {
			ViewServer.get(this).addWindow(this);
		}
		// for API < 11, buttons are in the main screen, so hide the title/action bar
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			requestWindowFeature(Window.FEATURE_NO_TITLE);
		}
		UIUtilities.setPixelDithering(getWindow());
		checkDirectoriesExist();

		SharedPreferences panoramaSettings = getSharedPreferences(MediaTablet.APPLICATION_NAME, Context.MODE_PRIVATE);
		MediaTablet.ADMINISTRATOR_PASSWORD = panoramaSettings.getString(getString(R.string.key_administrator_password),
				MediaTablet.ADMINISTRATOR_PASSWORD);
	}

	@Override
	protected void onStart() {
		loadAllPreferences();
		super.onStart();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (MediaTablet.DEBUG) {
			ViewServer.get(this).setFocusedWindow(this);
		}
		((MediaTabletApplication) this.getApplication()).registerActivityHandle(this);
	}

	@Override
	protected void onPause() {
		super.onPause();
		((MediaTabletApplication) this.getApplication()).removeActivityHandle(this);
	}

	@Override
	protected void onDestroy() {
		if (MediaTablet.DEBUG) {
			ViewServer.get(this).removeWindow(this);
		}
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.preferences, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				Intent intent = new Intent(MediaTabletActivity.this, HomesteadBrowserActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(intent);
				finish();
				return true;

			case R.id.menu_preferences:
				final Intent preferencesIntent = new Intent(MediaTabletActivity.this, PreferencesActivity.class);
				startActivityForResult(preferencesIntent, R.id.intent_preferences);
				return true;

			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent resultIntent) {
		switch (requestCode) {
			case R.id.intent_preferences:
				loadAllPreferences();
				break;
			default:
				super.onActivityResult(requestCode, resultCode, resultIntent);
		}
	}

	private void loadAllPreferences() {
		SharedPreferences mediaTabletSettings = PreferenceManager.getDefaultSharedPreferences(MediaTabletActivity.this);

		// bluetooth observer
		configureBluetoothObserver(mediaTabletSettings, getResources());

		// other activity-specific preferences
		loadPreferences(mediaTabletSettings);
	}

	protected void configureBluetoothObserver(SharedPreferences mediaTabletSettings, Resources res) {
		boolean watchForFiles = res.getBoolean(R.bool.default_watch_for_files);
		try {
			watchForFiles = mediaTabletSettings.getBoolean(getString(R.string.key_watch_for_files), watchForFiles);
		} catch (Exception e) {
			watchForFiles = res.getBoolean(R.bool.default_watch_for_files);
		}
		if (watchForFiles) {
			// file changes are handled in startWatchingBluetooth();
			((MediaTabletApplication) getApplication()).startWatchingBluetooth();
		} else {
			((MediaTabletApplication) getApplication()).stopWatchingBluetooth();
		}
	}

	public void checkDirectoriesExist() {

		// nothing will work, and previously saved files will not load
		if (MediaTablet.DIRECTORY_STORAGE == null) {

			// if we're not in the main activity, quit everything else and launch the homestead browser to exit
			boolean clearTop = false;
			if (!((Object) MediaTabletActivity.this instanceof HomesteadBrowserActivity)) {
				clearTop = true;
			} else if (((HomesteadBrowserActivity) MediaTabletActivity.this).isInEditMode()) {
				clearTop = true;
			}
			if (clearTop) {
				Intent homeIntent = new Intent(this, HomesteadBrowserActivity.class);
				homeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(homeIntent);
				Log.d(DebugUtilities.getLogTag(this), "Couldn't open storage directory - clearing top to exit");
				return;
			}

			SharedPreferences mediaTabletSettings = getSharedPreferences(MediaTablet.APPLICATION_NAME,
					Context.MODE_PRIVATE);
			final String storageKey = getString(R.string.key_use_external_storage);
			if (mediaTabletSettings.contains(storageKey)) {
				if (mediaTabletSettings.getBoolean(storageKey, true)) { // defValue is irrelevant, we know value exists
					if (!isFinishing()) {
						UIUtilities.showToast(MediaTabletActivity.this, R.string.error_opening_media_content_sd, true);
					}
					Log.d(DebugUtilities.getLogTag(this), "Couldn't open storage directory (SD card) - exiting");
					finish();
					return;
				}
			}

			if (!isFinishing()) {
				UIUtilities.showToast(MediaTabletActivity.this, R.string.error_opening_media_content, true);
			}
			Log.d(DebugUtilities.getLogTag(this), "Couldn't open storage directory - exiting");
			finish();
			return;
		} else {
			// the UNKNOWN_PERSON_ID directory is where all public media is transferred to - it must exist
			if (!PersonItem.getStorageDirectory(PersonItem.UNKNOWN_PERSON_ID).exists()) {
				if (!PersonItem.getStorageDirectory(PersonItem.UNKNOWN_PERSON_ID).mkdirs()) {
					Log.d(DebugUtilities.getLogTag(this), "Unable to create public directory");
				}
			}
		}

		// thumbnail cache won't work, but not really fatal (thumbnails will be loaded into memory on demand)
		if (MediaTablet.DIRECTORY_THUMBS == null) {
			Log.d(DebugUtilities.getLogTag(this), "Thumbnail directory not found");
		}

		// external narrative sending (Bluetooth etc) may not work, but not really fatal (will warn on export)
		if (MediaTablet.DIRECTORY_TEMP == null) {
			Log.d(DebugUtilities.getLogTag(this), "Temporary directory not found - will warn before export");
		}

		// bluetooth directory availability may have changed if we're calling from an SD card availability notification
		configureBluetoothObserver(PreferenceManager.getDefaultSharedPreferences(MediaTabletActivity.this),
				getResources());
	}

	public void processIncomingFiles(Message msg) {

		// deal with messages from the BluetoothObserver
		Bundle fileData = msg.peekData();
		if (fileData == null) {
			return; // error - no parameters passed
		}

		String importedFileName = fileData.getString(MediaUtilities.KEY_FILE_NAME);
		if (importedFileName == null) {
			return; // error - no filename
		}

		// get the imported file object
		final File importedFile = new File(importedFileName);
		if (!importedFile.canRead() || !importedFile.canWrite()) {
			importedFile.delete(); // error - probably won't work, but might
									// as well try; doesn't throw, so is okay
			return;
		}

		final String mediaParent = getCurrentPersonId();
		final int mediaVisibility = PersonItem.UNKNOWN_PERSON_ID.equals(mediaParent) ? MediaItem.MEDIA_PUBLIC
				: MediaItem.MEDIA_PRIVATE;
		switch (msg.what) {
			case MediaUtilities.MSG_RECEIVED_IMPORT_FILE:
				ImportedFileParser.importMediaItem(getContentResolver(), mediaParent, importedFile, mediaVisibility,
						true);
				break;

			case MediaUtilities.MSG_RECEIVED_SMIL_FILE:
				FrameMediaContainer currentSmilFile = new FrameMediaContainer(importedFile.getAbsolutePath(),
						mediaVisibility); // hacky, but it works
				currentSmilFile.mParentId = mediaParent;
				new ImportSmilTask().execute(new FrameMediaContainer[] { currentSmilFile });
				break;

			case MediaUtilities.MSG_RECEIVED_HTML_FILE:
				UIUtilities.showToast(MediaTabletActivity.this, R.string.html_feature_coming_soon);
				importedFile.delete();
				// TODO: this
				break;
		}
	}

	private class ImportSmilTask extends AsyncTask<FrameMediaContainer, Void, Void> {
		@Override
		protected Void doInBackground(FrameMediaContainer... smilParents) {
			for (int i = 0, n = smilParents.length; i < n; i++) {
				final FrameMediaContainer currentSmilFile = smilParents[i];
				ImportedFileParser.importSMILNarrative(getContentResolver(), new File(currentSmilFile.mFrameId),
						currentSmilFile.mParentId, currentSmilFile.mFrameSequenceId);
				publishProgress();
			}
			return null;
		}

		@Override
		protected void onProgressUpdate(Void... unused) {
		}

		@Override
		protected void onPostExecute(Void unused) {
		}
	}

	protected void viewPublicMedia() {
		Intent publicMediaIntent = new Intent(MediaTabletActivity.this, MediaBrowserActivity.class);
		startActivityForResult(publicMediaIntent, R.id.intent_media_browser);
	}

	protected void editPerson(String homesteadId, String personId) {
		Intent addUserIntent = new Intent(MediaTabletActivity.this, AddUserActivity.class);
		addUserIntent.putExtra(getString(R.string.extra_parent_id), homesteadId);
		if (personId != null) {
			addUserIntent.putExtra(getString(R.string.extra_internal_id), personId);
			addUserIntent.putExtra(getString(R.string.extra_edit_mode), true);
		}
		startActivityForResult(addUserIntent, R.id.intent_add_user);
	}
}
