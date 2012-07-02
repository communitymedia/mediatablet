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

package ac.robinson.mediatablet.activity;

import java.io.File;

import ac.robinson.mediatablet.R;
import ac.robinson.mediautilities.SelectDirectoryActivity;
import ac.robinson.util.UIUtilities;
import ac.robinson.util.UIUtilities.ReflectionTab;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;

public class PreferencesActivity extends PreferenceActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		UIUtilities.setPixelDithering(getWindow());
		UIUtilities.configureActionBar(this, true, false, R.string.title_preferences, 0);
		UIUtilities.addActionBarTabs(this, new ReflectionTab[] { new ReflectionTab(R.id.intent_preferences,
				android.R.drawable.ic_menu_preferences, getString(R.string.title_preferences), true) }, null);
		addPreferencesFromResource(R.xml.preferences);

		Preference button = (Preference) findPreference(getString(R.string.key_bluetooth_directory));
		button.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				SharedPreferences mediaTabletSettings = preference.getSharedPreferences();
				String currentDirectory = null;
				try {
					currentDirectory = mediaTabletSettings.getString(getString(R.string.key_bluetooth_directory), null);
				} catch (Exception e) {
				}
				if (currentDirectory == null) {
					currentDirectory = getString(R.string.default_bluetooth_directory);
				}
				final Intent intent = new Intent(getBaseContext(), SelectDirectoryActivity.class);
				intent.putExtra(SelectDirectoryActivity.START_PATH, currentDirectory);
				startActivityForResult(intent, R.id.intent_directory_chooser);
				return true;
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.save, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;

			case R.id.menu_save:
				onBackPressed();
				return true;

			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent resultIntent) {
		switch (requestCode) {
			case R.id.intent_directory_chooser:
				if (resultCode == Activity.RESULT_OK && resultIntent != null) {
					String resultPath = resultIntent.getStringExtra(SelectDirectoryActivity.RESULT_PATH);
					if (resultPath != null) {
						File newPath = new File(resultPath);
						if (newPath.canRead()) {
							SharedPreferences mediaTabletSettings = PreferenceManager
									.getDefaultSharedPreferences(PreferencesActivity.this);
							SharedPreferences.Editor prefsEditor = mediaTabletSettings.edit();
							prefsEditor.putString(getString(R.string.key_bluetooth_directory), resultPath);
							prefsEditor.commit(); // apply is better, but only in API > 8
						} else {
							UIUtilities.showToast(PreferencesActivity.this,
									R.string.preferences_bluetooth_directory_error);
						}
					}
				}
				break;

			default:
				super.onActivityResult(requestCode, resultCode, resultIntent);
		}
	}
}
