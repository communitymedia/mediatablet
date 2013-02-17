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

import group.pals.android.lib.ui.lockpattern.LockPatternActivity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import ac.robinson.cropimage.CropImage;
import ac.robinson.mediatablet.MediaTablet;
import ac.robinson.mediatablet.MediaTabletActivity;
import ac.robinson.mediatablet.R;
import ac.robinson.mediatablet.importing.BluetoothObserver;
import ac.robinson.mediatablet.provider.HomesteadItem;
import ac.robinson.mediatablet.provider.HomesteadManager;
import ac.robinson.mediatablet.provider.PersonItem;
import ac.robinson.mediatablet.provider.PersonManager;
import ac.robinson.util.BitmapUtilities;
import ac.robinson.util.BitmapUtilities.CacheTypeContainer;
import ac.robinson.util.DebugUtilities;
import ac.robinson.util.IOUtilities;
import ac.robinson.util.UIUtilities;
import ac.robinson.view.CenteredImageTextButton;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.LightingColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

public class AddUserActivity extends MediaTabletActivity {

	private String mPersonInternalId;
	private boolean mEditMode;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// load previous id and mode on screen rotation
		mPersonInternalId = null;
		mEditMode = false;
		if (savedInstanceState != null) {
			mPersonInternalId = savedInstanceState.getString(getString(R.string.extra_internal_id));
			mEditMode = savedInstanceState.getBoolean(getString(R.string.extra_edit_mode));
		} else {
			final Intent intent = getIntent();
			if (intent != null) {
				mPersonInternalId = intent.getStringExtra(getString(R.string.extra_internal_id));
				mEditMode = intent.getBooleanExtra(getString(R.string.extra_edit_mode), false);
			}
		}

		UIUtilities.configureActionBar(this, true, true,
				mEditMode ? R.string.title_edit_user : R.string.title_add_user, 0);
		setContentView(R.layout.add_user);

		// for API 11 and above, buttons are in the action bar - could use XML-v11 but maintenance is a hassle
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			findViewById(R.id.button_add_user_finish).setVisibility(View.GONE);
		}
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		savedInstanceState.putString(getString(R.string.extra_internal_id), mPersonInternalId);
		savedInstanceState.putBoolean(getString(R.string.extra_edit_mode), mEditMode);
		super.onSaveInstanceState(savedInstanceState);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			// do this here so that we know the layout's size for loading the person's icon
			loadUserElements();
		}
	}

	@Override
	public void onBackPressed() {
		// TODO: currently, picture modifications will *always* be saved, regardless of save/cancel press
		if (mEditMode) {
			setResult(Activity.RESULT_CANCELED);
			finish();
			return;
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(AddUserActivity.this);
		builder.setTitle(R.string.cancel_add_confirmation);
		builder.setMessage(R.string.cancel_add_hint);
		builder.setIcon(android.R.drawable.ic_dialog_alert);
		builder.setNegativeButton(R.string.cancel_add_continue, null);
		builder.setPositiveButton(R.string.cancel_add_delete, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				ContentResolver contentResolver = getContentResolver();
				Resources resources = getResources();
				PersonItem personItem = PersonManager.findPersonByInternalId(contentResolver, mPersonInternalId);
				String parentId = personItem.getParentId();
				personItem.setDeleted(true);
				PersonManager.updatePerson(resources, contentResolver, personItem, false);

				// delete the previous homestead if it is now empty
				if (parentId != null) {
					ArrayList<PersonItem> testPeople = PersonManager.findPeopleByParentId(contentResolver, parentId);
					if (testPeople.size() <= 0) { // no other people found in this homestead
						HomesteadManager.deleteHomesteadByInternalId(contentResolver, parentId);
					} else {
						HomesteadManager.reloadHomesteadIcon(resources, contentResolver, parentId);
					}
				}

				setResult(Activity.RESULT_CANCELED);
				finish();
			}
		});
		AlertDialog alert = builder.create();
		alert.show();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.cancel, menu);
		inflater.inflate(R.menu.save, menu);
		if (mEditMode) {
			menu.findItem(R.id.menu_save).setTitle(R.string.menu_update);
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// hide the keyboard so it doesn't show in front of the camera or homestead browser
		// if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
		// InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		// manager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
		// }

		switch (item.getItemId()) {
			case android.R.id.home:
			case R.id.menu_cancel:
				onBackPressed();
				return super.onOptionsItemSelected(item);

			case R.id.menu_save:
				finishAddingUser();
				return true;

			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void loadPreferences(SharedPreferences mediaTabletSettings) {
	}

	@Override
	protected String getCurrentPersonId() {
		return mPersonInternalId;
	}

	private void loadUserElements() {

		// first launch
		ContentResolver contentResolver = getContentResolver();
		if (mPersonInternalId == null) {

			// editing an existing person or adding to a specific homestead
			String parentInternalId = null;
			final Intent intent = getIntent();
			if (intent != null) {
				mPersonInternalId = intent.getStringExtra(getString(R.string.extra_internal_id));
				parentInternalId = intent.getStringExtra(getString(R.string.extra_parent_id));
			}

			// add a new person item if it doesn't already exist
			if (mPersonInternalId == null) {
				PersonItem personItem = new PersonItem();
				if (parentInternalId != null) {
					personItem.setParentId(parentInternalId);
				}
				PersonManager.addPerson(contentResolver, personItem);
				mPersonInternalId = personItem.getInternalId();
			}
		}

		// load the existing image, text and homestead icon
		PersonItem personItem = PersonManager.findPersonByInternalId(contentResolver, mPersonInternalId);
		if (personItem != null) {
			File pictureFile = personItem.getProfilePictureFile();
			if (pictureFile.exists()) {
				Resources resources = getResources();
				int pictureSize = getPictureSize(resources, R.id.button_take_profile_picture);
				BitmapDrawable cachedIcon = new BitmapDrawable(resources, BitmapUtilities.loadAndCreateScaledBitmap(
						pictureFile.getAbsolutePath(), pictureSize, pictureSize, BitmapUtilities.ScalingLogic.CROP,
						true));
				CenteredImageTextButton photoButton = (CenteredImageTextButton) findViewById(R.id.button_take_profile_picture);
				photoButton.setCompoundDrawablesWithIntrinsicBounds(null, cachedIcon, null, null);

				String parentId = personItem.getParentId();
				if (parentId != null) {
					reloadHomesteadIcon(parentId);
				}
			}

			String personName = personItem.getName();
			EditText textBox = (EditText) findViewById(R.id.text_add_user_name);
			if (!TextUtils.isEmpty(personName)) {
				textBox.setText(personName);
				// } else if (TextUtils.isEmpty(textBox.getText().toString())) {
				// // show the keyboard as a further hint (below Honeycomb it is automatic)
				// if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
				// InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
				// manager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
				// } else {
				// textBox.requestFocus();
				// }
			}

		} else {
			UIUtilities.showToast(AddUserActivity.this, R.string.error_loading_person_editor);
			onBackPressed();
		}
	}

	private int getPictureSize(Resources resources, int buttonId) {
		View photoButton = findViewById(buttonId);
		TypedValue resourceValue = new TypedValue();
		resources.getValue(R.attr.image_button_fill_percentage, resourceValue, true);
		return (int) ((resources.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? photoButton
				.getWidth() : photoButton.getHeight()) * resourceValue.getFloat());
	}

	private void reloadHomesteadIcon(String parentId) {
		Resources resources = getResources();
		ContentResolver contentResolver = getContentResolver();
		HomesteadItem parentHomestead = HomesteadManager.findHomesteadByInternalId(contentResolver, parentId);
		int pictureSize = getPictureSize(resources, R.id.button_select_profile_homestead);
		BitmapDrawable cachedIcon = new BitmapDrawable(getResources(), parentHomestead.loadIcon(resources,
				contentResolver, new CacheTypeContainer(MediaTablet.ICON_CACHE_TYPE), pictureSize));
		if (parentHomestead.getColour() != 0) {
			cachedIcon.setColorFilter(new LightingColorFilter(parentHomestead.getColour(), 1));
		}
		CenteredImageTextButton homesteadButton = (CenteredImageTextButton) findViewById(R.id.button_select_profile_homestead);
		homesteadButton.setCompoundDrawablesWithIntrinsicBounds(null, cachedIcon, null, null);
	}

	private void finishAddingUser() {
		ContentResolver contentResolver = getContentResolver();
		PersonItem personItem = PersonManager.findPersonByInternalId(contentResolver, mPersonInternalId);
		if (!personItem.getProfilePictureFile().exists()) {
			UIUtilities.showToast(AddUserActivity.this, R.string.hint_take_picture);
			return;
		} else if (personItem.getParentId() == null) {
			UIUtilities.showToast(AddUserActivity.this, R.string.hint_select_homestead);
			return;
		} else if (TextUtils.isEmpty(((EditText) findViewById(R.id.text_add_user_name)).getText().toString())) {
			UIUtilities.showToast(AddUserActivity.this, R.string.hint_enter_name);
			return;
		}

		Intent lockPatternIntent = new Intent(AddUserActivity.this, LockPatternActivity.class);
		lockPatternIntent.putExtra(LockPatternActivity._Theme, android.R.style.Theme_Dialog);
		lockPatternIntent.putExtra(LockPatternActivity._Mode, LockPatternActivity.LPMode.CreatePattern);
		lockPatternIntent.putExtra(LockPatternActivity._AutoSave, false);
		startActivityForResult(lockPatternIntent, R.id.intent_lock_pattern);
	}

	public void handleButtonClicks(View currentButton) {
		// hide the keyboard so it doesn't show in front of the camera or homestead browser
		// if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
		// InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		// manager.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
		// }

		switch (currentButton.getId()) {
			case R.id.button_take_profile_picture:
				PersonItem personItem = PersonManager.findPersonByInternalId(getContentResolver(), mPersonInternalId);
				if (personItem != null) {
					// must create these at some point - they're needed for the profile picture, so here is good
					personItem.getStorageDirectory().mkdirs();

					// the picture *must* be in a publicly-readable directory
					File photoFile = new File(MediaTablet.DIRECTORY_TEMP, personItem.getInternalId() + ".jpg");
					if (!photoFile.exists()) {
						try {
							if (MediaTablet.DIRECTORY_TEMP != null) {
								photoFile.createNewFile();
								IOUtilities.setFullyPublic(photoFile);
							} else {
								throw new IOException();
							}
						} catch (IOException e) {
							if (MediaTablet.DEBUG)
								Log.d(DebugUtilities.getLogTag(this),
										"Couldn't create picture file - continuing anyway");
						}
					}
					Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
					takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
					startActivityForResult(takePictureIntent, R.id.intent_take_picture);
				}
				break;

			case R.id.button_select_profile_homestead:
				Intent selectHomesteadIntent = new Intent(this, HomesteadBrowserActivity.class);
				selectHomesteadIntent.putExtra(getString(R.string.extra_edit_mode), true);
				startActivityForResult(selectHomesteadIntent, R.id.intent_homestead_selector);
				break;

			case R.id.button_add_user_finish:
				finishAddingUser();
				break;
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent resultIntent) {
		ContentResolver contentResolver = getContentResolver();
		PersonItem personItem = PersonManager.findPersonByInternalId(contentResolver, mPersonInternalId);
		if (resultCode != Activity.RESULT_OK || personItem == null) {
			super.onActivityResult(requestCode, resultCode, resultIntent);
			return;
		}
		switch (requestCode) {
			case R.id.intent_take_picture:
				File photoFile = new File(MediaTablet.DIRECTORY_TEMP, personItem.getInternalId() + ".jpg");
				if (photoFile.exists() && photoFile.length() > 0) {
					File newPictureFile = personItem.getProfilePictureFile();
					try {
						IOUtilities.copyFile(photoFile, newPictureFile);
						photoFile.delete();
					} catch (IOException e) {
						UIUtilities.showToast(AddUserActivity.this, R.string.error_taking_picture);
					}
					if (newPictureFile.exists()) {
						Intent cropIntent = new Intent(AddUserActivity.this, CropImage.class);
						cropIntent.putExtra("image-path", newPictureFile.getAbsolutePath());
						cropIntent.putExtra("aspectX", 1);
						cropIntent.putExtra("aspectY", 1);
						cropIntent.putExtra("scale", true);
						startActivityForResult(cropIntent, R.id.intent_crop_picture);
					}
				} else {
					UIUtilities.showToast(AddUserActivity.this, R.string.error_taking_picture);
				}
				break;

			case R.id.intent_crop_picture:
				String parentId = personItem.getParentId();
				if (parentId != null) {
					reloadHomesteadIcon(parentId);
				}
				break;

			case R.id.intent_homestead_selector:
				String newHomesteadId = resultIntent.getStringExtra(getString(R.string.extra_internal_id));
				if (newHomesteadId != null && !newHomesteadId.equals(personItem.getParentId())) {
					// update the homestead but clone the old id to remove if necessary
					String previousHomestead = (personItem.getParentId() != null ? new String(personItem.getParentId())
							: null);
					personItem.setParentId(newHomesteadId);
					PersonManager.updatePerson(contentResolver, personItem);

					// delete the previous homestead if it is now empty
					if (previousHomestead != null) {
						ArrayList<PersonItem> testPeople = PersonManager.findPeopleByParentId(contentResolver,
								previousHomestead);
						if (testPeople.size() <= 0) { // no other people found in this homestead
							HomesteadManager.deleteHomesteadByInternalId(contentResolver, previousHomestead);
						}
					}
				}
				break;

			case R.id.intent_lock_pattern:
				personItem.setPasswordHash(resultIntent.getStringExtra(LockPatternActivity._PaternSha1));
				personItem.setName(((EditText) findViewById(R.id.text_add_user_name)).getText().toString());
				Resources resources = getResources();
				PersonManager.updatePerson(resources, contentResolver, personItem, true);
				HomesteadManager.reloadHomesteadIcon(resources, contentResolver, personItem.getParentId());

				if (!mEditMode) {
					// so they can pair and start sending files - only necessary on first registration
					BluetoothObserver.setBluetoothVisibility(this, true); // load from preferences?

					final Intent showPeopleIntent = new Intent(this, PeopleBrowserActivity.class);
					showPeopleIntent.putExtra(getString(R.string.extra_parent_id), personItem.getParentId());
					showPeopleIntent.putExtra(getString(R.string.extra_internal_id), personItem.getInternalId());
					startActivity(showPeopleIntent);
				}
				finish();
				break;

			default:
				super.onActivityResult(requestCode, resultCode, resultIntent);
		}
	}
}
