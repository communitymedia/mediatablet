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

import ac.robinson.mediatablet.MediaTablet;
import ac.robinson.mediatablet.MediaTabletActivity;
import ac.robinson.mediatablet.R;
import ac.robinson.mediatablet.provider.HomesteadItem;
import ac.robinson.mediatablet.provider.HomesteadManager;
import ac.robinson.mediatablet.provider.PersonItem;
import ac.robinson.mediatablet.view.HomesteadSurfaceView;
import ac.robinson.mediatablet.view.HomesteadSurfaceView.HomesteadTouchListener;
import ac.robinson.mediautilities.SelectDirectoryActivity;
import ac.robinson.util.BitmapUtilities;
import ac.robinson.util.ImageCacheUtilities;
import ac.robinson.util.StringUtilities;
import ac.robinson.util.UIUtilities;
import ac.robinson.util.UIUtilities.ReflectionTab;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory.Options;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class HomesteadBrowserActivity extends MediaTabletActivity {

	private HomesteadSurfaceView mHomesteadSurfaceView;

	private boolean mEditMode;
	private HomesteadItem mTouchedHomestead;
	private boolean mDialogShown;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// load mode on screen rotation
		mEditMode = false;
		if (savedInstanceState != null) {
			mEditMode = savedInstanceState.getBoolean(getString(R.string.extra_edit_mode));
		} else {
			final Intent intent = getIntent();
			if (intent != null) {
				mEditMode = intent.getBooleanExtra(getString(R.string.extra_edit_mode), false);
			}
		}

		// editing is a different theme, for clarity (but must be done here before content is added)
		if (mEditMode) {
			setTheme(R.style.default_light_theme);
		}

		UIUtilities.configureActionBar(this, false, false, R.string.title_homestead_browser, 0);
		UIUtilities.addActionBarTabs(this, new ReflectionTab[] { new ReflectionTab(R.id.intent_homestead_browser,
				R.drawable.ic_menu_homesteads, getString(R.string.title_homestead_browser), true) }, null);
		setContentView(R.layout.homestead_browser);

		mDialogShown = false;
		initialiseHomesteadsView();
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		savedInstanceState.putBoolean(getString(R.string.extra_edit_mode), mEditMode);
		super.onSaveInstanceState(savedInstanceState);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			// do this here so that we can create a dialog
			if (!mHomesteadSurfaceView.imagesLoaded()) {

				// so that they can press back to exit
				if (mDialogShown) {
					finish();
					return;
				}
				mDialogShown = true;

				SharedPreferences panoramaSettings = getSharedPreferences(MediaTablet.APPLICATION_NAME,
						Context.MODE_PRIVATE);
				String existingPassword = panoramaSettings.getString(
						getString(R.string.key_administrator_password_temp), null);
				if (existingPassword == null) {
					existingPassword = Long.toHexString(Double.doubleToLongBits(Math.random())).substring(3, 10);
					String passwordHash = StringUtilities.sha1Hash(existingPassword);
					SharedPreferences.Editor prefsEditor = panoramaSettings.edit();
					prefsEditor.putString(getString(R.string.key_administrator_password), passwordHash);
					prefsEditor.putString(getString(R.string.key_administrator_password_temp), existingPassword);
					prefsEditor.apply(); // plaintext will be deleted after registration is completed
				}

				AlertDialog.Builder builder = new AlertDialog.Builder(HomesteadBrowserActivity.this);
				builder.setTitle(R.string.first_launch_prompt);

				// make sure the panorama is linked
				final SpannableString message = new SpannableString(String.format(
						getString(R.string.first_launch_hint), getString(R.string.app_name), existingPassword,
						getString(R.string.panorama_name)));
				Linkify.addLinks(message, Linkify.WEB_URLS);
				builder.setMessage(message);
				builder.setIcon(android.R.drawable.ic_dialog_info);
				// builder.setCancelable(false);
				builder.setPositiveButton(R.string.first_launch_select, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						if (validatePanorama(
								new File(
										Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
										getString(R.string.panorama_name)), false)) {
							mHomesteadSurfaceView.requestLoadImages();
						} else {
							Intent intent = new Intent(getBaseContext(), SelectDirectoryActivity.class);
							if (Environment.getExternalStorageDirectory().canRead()) {
								intent.putExtra(SelectDirectoryActivity.START_PATH, Environment
										.getExternalStorageDirectory().getAbsolutePath());
							}
							startActivityForResult(intent, R.id.intent_directory_chooser);
							mDialogShown = false;
						}
						dialog.dismiss();
					}
				});
				AlertDialog alert = builder.create();
				alert.show();

				// try to make sure they notice that the view scrolls
				View textView = alert.findViewById(android.R.id.message);
				if (textView != null) {
					ViewParent scrollView = textView.getParent();
					if (scrollView != null && scrollView instanceof ScrollView) {
						((ScrollView) scrollView).setScrollbarFadingEnabled(false);
					}
				}

				// make the textview clickable (for the panorama link)
				((TextView) alert.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod
						.getInstance());
			} else if (mEditMode) {
				UIUtilities.showToast(HomesteadBrowserActivity.this, R.string.add_user_select_homestead);
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		mHomesteadSurfaceView.refreshHomesteads();
	}

	@Override
	protected void onDestroy() {
		ImageCacheUtilities.cleanupCache();
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		if (!mEditMode) {
			inflater.inflate(R.menu.add_user, menu);
			inflater.inflate(R.menu.public_media, menu);
		} else {
			inflater.inflate(R.menu.cancel, menu);
			inflater.inflate(R.menu.save, menu);
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				return true;

			case R.id.menu_add_user:
				editPerson(null, null);
				return true;

			case R.id.menu_view_public_media:
				viewPublicMedia();
				return true;

			case R.id.menu_cancel:
				finish();
				return true;

			case R.id.menu_save:
				saveNewHomestead();
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
		return PersonItem.UNKNOWN_PERSON_ID;
	}

	private void initialiseHomesteadsView() {
		// for API 11 and above, buttons are in the action bar - could use XML-v11 but maintenance is a hassle
		boolean api11plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
		if (api11plus || mEditMode) {
			findViewById(R.id.button_homesteads_add_user).setVisibility(View.GONE);
			findViewById(R.id.button_homesteads_view_public_media).setVisibility(View.GONE);
			findViewById(R.id.button_homesteads_save_new_homstead).setVisibility(
					(mEditMode && !api11plus ? View.VISIBLE : View.GONE));
		} else {
			findViewById(R.id.button_homesteads_save_new_homstead).setVisibility(View.GONE);
		}

		mHomesteadSurfaceView = (HomesteadSurfaceView) findViewById(R.id.surface_homesteads);
		mHomesteadSurfaceView.setEditMode(mEditMode);
		mHomesteadSurfaceView.registerTouchListener(mHomesteadTouchListener);

		((LinearLayout) findViewById(R.id.button_homesteads_scroll_left)).addView(new LeftRightImageView(this, -1));
		((LinearLayout) findViewById(R.id.button_homesteads_scroll_right)).addView(new LeftRightImageView(this, 1));
	}

	private void saveNewHomestead() {
		if (mTouchedHomestead == null) {
			UIUtilities.showToast(HomesteadBrowserActivity.this, R.string.add_user_select_homestead);
			return;
		}

		// they touched a new location - add to database (but don't load the icon yet)
		if (mTouchedHomestead == mHomesteadSurfaceView.getTemporaryHomestead()) {
			HomesteadManager.addHomestead(getContentResolver(), mTouchedHomestead);
		}

		// let the add user dialog know that we've completed
		Intent resultIntent = new Intent(HomesteadBrowserActivity.this, AddUserActivity.class);
		resultIntent.putExtra(getString(R.string.extra_internal_id), mTouchedHomestead.getInternalId());
		setResult(Activity.RESULT_OK, resultIntent);
		finish();
	}

	public void handleButtonClicks(View currentButton) {
		switch (currentButton.getId()) {
			case R.id.button_homesteads_add_user:
				editPerson(null, null);
				break;

			case R.id.button_homesteads_view_public_media:
				viewPublicMedia();
				break;

			case R.id.button_homesteads_save_new_homstead:
				saveNewHomestead();
				break;
		}
	}

	private HomesteadTouchListener mHomesteadTouchListener = new HomesteadTouchListener() {
		@Override
		public void homesteadTouched(HomesteadItem touchedHomestead) {
			if (mEditMode) {
				mTouchedHomestead = touchedHomestead;
			} else {
				Intent showPeopleIntent = new Intent(HomesteadBrowserActivity.this, PeopleBrowserActivity.class);
				showPeopleIntent.putExtra(getString(R.string.extra_parent_id), touchedHomestead.getInternalId());
				startActivity(showPeopleIntent);
			}
		}
	};

	private boolean validatePanorama(File newPath, boolean showNotFound) {
		if (newPath.canRead()) {
			String panoramaPath = newPath.getAbsolutePath();
			Options imageDimensions = BitmapUtilities.getImageDimensions(panoramaPath);
			if (imageDimensions != null && imageDimensions.outWidth > imageDimensions.outHeight) {
				SharedPreferences panoramaSettings = getSharedPreferences(MediaTablet.APPLICATION_NAME,
						Context.MODE_PRIVATE);
				SharedPreferences.Editor prefsEditor = panoramaSettings.edit();
				prefsEditor.putString(getString(R.string.key_panorama_file), panoramaPath);
				prefsEditor.putString(getString(R.string.key_administrator_password_temp), "");
				prefsEditor.apply();
				UIUtilities.showToast(HomesteadBrowserActivity.this, R.string.panorama_found);
				return true;
			}
		}
		if (showNotFound) {
			UIUtilities.showFormattedToast(HomesteadBrowserActivity.this, R.string.panorama_not_found,
					getString(R.string.panorama_name));
		}
		return false;
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent resultIntent) {
		switch (requestCode) {
			case R.id.intent_directory_chooser:
				if (resultCode == Activity.RESULT_OK && resultIntent != null) {
					String resultPath = resultIntent.getStringExtra(SelectDirectoryActivity.RESULT_PATH);
					if (resultPath != null) {
						validatePanorama(new File(resultPath, getString(R.string.panorama_name)), true);
					}
				}
				break;

			default:
				super.onActivityResult(requestCode, resultCode, resultIntent);
		}
	}

	private class LeftRightImageView extends ImageView {
		private final int ARROW_DIRECTION;

		public LeftRightImageView(Context context, int direction) {
			super(context);
			ARROW_DIRECTION = direction;
			setSelectedImage(false);
		}

		private void setSelectedImage(boolean selected) {
			if (selected) {
				setImageResource(ARROW_DIRECTION < 0 ? R.drawable.arrow_left_selected : R.drawable.arrow_right_selected);
			} else {
				setImageResource(ARROW_DIRECTION < 0 ? R.drawable.arrow_left : R.drawable.arrow_right);
			}
		}

		@Override
		public boolean onTouchEvent(MotionEvent event) {
			switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					setSelectedImage(true);
					mHomesteadSurfaceView.setScrollSpeed(-ARROW_DIRECTION);
					return true;

				case MotionEvent.ACTION_UP:
					mHomesteadSurfaceView.setScrollSpeed(0);
					setSelectedImage(false);
					return true;
			}
			return false;
		}
	}
}
