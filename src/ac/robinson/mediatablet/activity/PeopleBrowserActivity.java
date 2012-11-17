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

import java.lang.reflect.Method;
import java.util.ArrayList;

import ac.robinson.mediatablet.MediaTablet;
import ac.robinson.mediatablet.MediaTabletActivity;
import ac.robinson.mediatablet.MediaViewerActivity;
import ac.robinson.mediatablet.R;
import ac.robinson.mediatablet.provider.HomesteadItem;
import ac.robinson.mediatablet.provider.HomesteadManager;
import ac.robinson.mediatablet.provider.MediaItem;
import ac.robinson.mediatablet.provider.PersonAdapter;
import ac.robinson.mediatablet.provider.PersonItem;
import ac.robinson.mediatablet.provider.PersonManager;
import ac.robinson.mediatablet.view.ColorPickerDialog;
import ac.robinson.mediatablet.view.PersonViewHolder;
import ac.robinson.util.ImageCacheUtilities;
import ac.robinson.util.UIUtilities;
import ac.robinson.util.UIUtilities.ReflectionTab;
import ac.robinson.util.UIUtilities.ReflectionTabListener;
import ac.robinson.view.CrossFadeDrawable;
import ac.robinson.view.FastBitmapDrawable;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;

public class PeopleBrowserActivity extends MediaTabletActivity {

	private PersonAdapter mPersonAdapter;
	private FastBitmapDrawable mDefaultIcon;
	private GridView mGrid;

	private final Handler mScrollHandler = new ScrollHandler();
	private int mScrollState = ScrollManager.SCROLL_STATE_IDLE;
	private boolean mPendingIconsUpdate;
	private boolean mFingerUp = true;

	private String mParentId; // the homestead of these, or null if it's the sharing selection view

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// load previous position and parent on screen rotation
		mParentId = null;
		if (savedInstanceState != null) {
			mParentId = savedInstanceState.getString(getString(R.string.extra_parent_id));
		} else {
			final Intent intent = getIntent();
			if (intent != null) {
				mParentId = intent.getStringExtra(getString(R.string.extra_parent_id));
				String personId = intent.getStringExtra(getString(R.string.extra_internal_id));
				if (personId != null) {
					onView(personId);
				}
			}
		}

		if (mParentId != null) {
			UIUtilities.addActionBarTabs(this, new ReflectionTab[] {
					new ReflectionTab(R.id.intent_homestead_browser, R.drawable.ic_menu_homesteads,
							getString(R.string.title_homestead_browser)),
					new ReflectionTab(R.id.intent_people_browser, R.drawable.ic_menu_people,
							getString(R.string.title_people_browser), true) }, mReflectionTabListener);
		} else {
			UIUtilities.addActionBarTabs(this, new ReflectionTab[] { new ReflectionTab(R.id.intent_people_browser,
					R.drawable.ic_menu_people, getString(R.string.title_people_browser_selection), true) }, null);
		}
		UIUtilities.configureActionBar(this, true, false, R.string.title_people_browser, 0);
		setContentView(R.layout.people_browser);

		initialisePeopleGridView();
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		savedInstanceState.putString(getString(R.string.extra_parent_id), mParentId);
		super.onSaveInstanceState(savedInstanceState);
	}

	@Override
	protected void onPause() {
		super.onPause();
		mScrollHandler.removeMessages(MediaTablet.MESSAGE_UPDATE_GRID_ICONS);
	}

	@Override
	protected void onDestroy() {
		ImageCacheUtilities.cleanupCache();
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		if (mParentId == null) {
			inflater.inflate(R.menu.cancel, menu);
			inflater.inflate(R.menu.share, menu);
		} else {
			inflater.inflate(R.menu.public_media, menu);
			inflater.inflate(R.menu.add_user, menu);
			try {
				Method setShowAsAction = MenuItem.class.getMethod("setShowAsAction", Integer.TYPE);
				setShowAsAction.invoke(menu.findItem(R.id.menu_add_user),
						MenuItem.class.getField("SHOW_AS_ACTION_NEVER").getInt(null));
			} catch (Exception e) { // platform is probably < 11
			}
		}
		inflater.inflate(R.menu.people_preferences, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				finish(); // quicker than clearing top
				return true;

			case R.id.menu_cancel:
				finish();
				return true;

			case R.id.menu_share:
				shareToPeople();
				return true;

			case R.id.menu_view_public_media:
				viewPublicMedia();
				return true;

			case R.id.menu_add_user:
				editPerson(mParentId, null);
				return true;

			case R.id.menu_people_change_colour:
				editHomesteadColour();
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

	private void initialisePeopleGridView() {
		// for API 11 and above, buttons are in the action bar - could use XML-v11 but maintenance is a hassle
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			findViewById(R.id.panel_people).setVisibility(View.GONE);
		}

		if (mParentId == null) {
			findViewById(R.id.button_people_add_user).setVisibility(View.GONE);
			findViewById(R.id.button_people_change_colour).setVisibility(View.GONE);
			((Button) findViewById(R.id.button_people_view_homesteads)).setCompoundDrawablesWithIntrinsicBounds(0,
					android.R.drawable.ic_menu_revert, 0, 0);
			((Button) findViewById(R.id.button_people_view_public_media)).setCompoundDrawablesWithIntrinsicBounds(0,
					android.R.drawable.ic_menu_share, 0, 0);
		}

		mPersonAdapter = new PersonAdapter(this, mParentId);
		mDefaultIcon = mPersonAdapter.getDefaultIcon();
		mGrid = (GridView) findViewById(R.id.grid_people);

		final GridView grid = mGrid;
		grid.setAdapter(mPersonAdapter);
		grid.setOnScrollListener(new ScrollManager());
		grid.setOnTouchListener(new FingerTracker());
		grid.setOnItemSelectedListener(new SelectionTracker());
		grid.setOnItemClickListener(new PersonViewer());

		// TODO: make this work
		// TextView emptyView = new TextView(this);
		// emptyView.setText("unknown");
		// grid.setEmptyView(emptyView);
	}

	private void editHomesteadColour() {
		HomesteadItem tempHomestead = HomesteadManager.findHomesteadByInternalId(getContentResolver(), mParentId);
		int currentColour = tempHomestead.getColour();
		new ColorPickerDialog(PeopleBrowserActivity.this, mColourChangedListener, currentColour).show();
	}

	private ColorPickerDialog.OnColorChangedListener mColourChangedListener = new ColorPickerDialog.OnColorChangedListener() {
		@Override
		public void colorChanged(int colour) {
			HomesteadItem editedHomestead = HomesteadManager.findHomesteadByInternalId(getContentResolver(), mParentId);
			editedHomestead.setColour(colour);
			HomesteadManager.updateHomestead(getContentResolver(), editedHomestead);
			UIUtilities.showToast(PeopleBrowserActivity.this, R.string.message_colour_changed);
		}
	};

	private void shareToPeople() {
		// TODO: currently, if the view is re-created, the selected ids are lost - fix (save in temp prefs?)
		ArrayList<String> selectedItems = mPersonAdapter.getSelectedItems();
		Intent resultIntent = new Intent(PeopleBrowserActivity.this, MediaViewerActivity.class);
		resultIntent.putExtra(getString(R.string.extra_selected_items),
				selectedItems.toArray(new String[selectedItems.size()]));
		setResult(Activity.RESULT_OK, resultIntent);
		finish();
	}

	public void handleButtonClicks(View currentButton) {

		final int buttonId = currentButton.getId();
		switch (buttonId) {

			case R.id.button_people_add_user:
				editPerson(mParentId, null);

			case R.id.button_people_change_colour:
				if (mParentId == null) {
					finish();
				} else {
					editHomesteadColour();
				}
				break;

			case R.id.button_people_view_homesteads:
				finish();
				break;

			case R.id.button_people_view_public_media:
				if (mParentId == null) {
					shareToPeople();
				} else {
					viewPublicMedia();
				}
				break;
		}
	}

	private ReflectionTabListener mReflectionTabListener = new ReflectionTabListener() {
		@Override
		public void onTabSelected(int tabId) {
			if (tabId == R.id.intent_homestead_browser) {
				finish();
			}
		}

		@Override
		public void onTabReselected(int tabId) {
		}

		@Override
		public void onTabUnselected(int tabId) {
		}
	};

	public int getScrollState() {
		return mScrollState; // for PersonAdapter purposes
	}

	public boolean isPendingIconsUpdate() {
		return mPendingIconsUpdate; // for PersonAdapter purposes
	}

	/**
	 * Switch to an item's view when it is touched
	 * 
	 * @param ownerId the PersonItem.INTERNAL_ID of the person whose media should be shown
	 */
	private void onView(String ownerId) {
		PersonItem person = PersonManager.findPersonByInternalId(getContentResolver(), ownerId);
		Intent browseMediaIntent = new Intent(PeopleBrowserActivity.this, MediaBrowserActivity.class);
		browseMediaIntent.putExtra(getString(R.string.extra_parent_id), ownerId);

		// not really the best place to manage person re-locking, but never mind...
		if (!person.isLocked()) {
			if (person.lockExpired()) {
				person.setLockStatus(PersonItem.PERSON_LOCKED);
				PersonManager.updatePerson(getContentResolver(), person);
			} else {
				browseMediaIntent.putExtra(getString(R.string.extra_media_visibility), MediaItem.MEDIA_PRIVATE);
			}
		} else {
			UIUtilities.showToast(PeopleBrowserActivity.this, R.string.message_view_locked_person, true);
		}

		startActivityForResult(browseMediaIntent, R.id.intent_media_browser);
	}

	private void updatePeopleIcons(boolean fadeIn) {
		mPendingIconsUpdate = false;

		final GridView grid = mGrid;
		final FastBitmapDrawable icon = mDefaultIcon;
		final int count = grid.getChildCount();

		for (int i = 0; i < count; i++) {
			final View view = grid.getChildAt(i);
			final PersonViewHolder holder = (PersonViewHolder) view.getTag();
			if (holder.queryIcon) {
				// if the icon has gone missing (recently imported or cache deletion), regenerate it
				String personCacheId = PersonItem.getCacheId(holder.personInternalId);
				FastBitmapDrawable cachedIcon = ImageCacheUtilities.getCachedIcon(MediaTablet.DIRECTORY_THUMBS,
						personCacheId, ImageCacheUtilities.NULL_DRAWABLE);
				if (ImageCacheUtilities.NULL_DRAWABLE.equals(cachedIcon)) {
					PersonManager.reloadPersonIcon(getResources(), getContentResolver(), holder.personInternalId);
					cachedIcon = ImageCacheUtilities.getCachedIcon(MediaTablet.DIRECTORY_THUMBS, personCacheId, icon);
				}

				if (fadeIn) {
					CrossFadeDrawable d = holder.transition;
					d.setEnd(cachedIcon.getBitmap());
					holder.display.setBackgroundDrawable(d);
					d.startTransition(MediaTablet.ANIMATION_FADE_TRANSITION_DURATION);
				} else {
					holder.display.setBackgroundDrawable(cachedIcon);
				}

				holder.loader.setVisibility(View.GONE);
				holder.queryIcon = false;

				if (holder.selected) {
					holder.overlay.setBackgroundResource(R.drawable.item_public);
					holder.overlay.setPadding(0, 0, 0, 0);
				} else {
					holder.overlay.setBackgroundDrawable(null);
				}
			}
		}

		grid.invalidate();
	}

	private void postUpdatePeopleIcons() {
		Handler handler = mScrollHandler;
		Message message = handler.obtainMessage(MediaTablet.MESSAGE_UPDATE_GRID_ICONS, PeopleBrowserActivity.this);
		handler.removeMessages(MediaTablet.MESSAGE_UPDATE_GRID_ICONS);
		mPendingIconsUpdate = true;
		handler.sendMessage(message);
	}

	private class ScrollManager implements AbsListView.OnScrollListener {
		public void onScrollStateChanged(AbsListView view, int scrollState) {
			if (mScrollState == SCROLL_STATE_FLING && scrollState != SCROLL_STATE_FLING) {
				final Handler handler = mScrollHandler;
				final Message message = handler.obtainMessage(MediaTablet.MESSAGE_UPDATE_GRID_ICONS,
						PeopleBrowserActivity.this);
				handler.removeMessages(MediaTablet.MESSAGE_UPDATE_GRID_ICONS);
				handler.sendMessageDelayed(message, mFingerUp ? 0 : MediaTablet.ANIMATION_ICON_SHOW_DELAY);
				mPendingIconsUpdate = true;
			} else if (scrollState == SCROLL_STATE_FLING) {
				mPendingIconsUpdate = false;
				mScrollHandler.removeMessages(MediaTablet.MESSAGE_UPDATE_GRID_ICONS);
			}
			mScrollState = scrollState;
		}

		// for showing the overlay with current item information - no need in people view
		public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
			return;
		}
	}

	private static class ScrollHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case MediaTablet.MESSAGE_UPDATE_GRID_ICONS:
					((PeopleBrowserActivity) msg.obj).updatePeopleIcons(true);
					break;
			}
		}
	}

	private class FingerTracker implements View.OnTouchListener {
		public boolean onTouch(View view, MotionEvent event) {
			final int action = event.getAction();
			mFingerUp = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
			if (mFingerUp && mScrollState != ScrollManager.SCROLL_STATE_FLING) {
				postUpdatePeopleIcons();
			}
			return false;
		}
	}

	private class SelectionTracker implements AdapterView.OnItemSelectedListener {
		public void onItemSelected(AdapterView<?> adapterView, View view, int position, long id) {
			if (mScrollState != ScrollManager.SCROLL_STATE_IDLE) {
				mScrollState = ScrollManager.SCROLL_STATE_IDLE;
				postUpdatePeopleIcons();
			}
		}

		public void onNothingSelected(AdapterView<?> adapterView) {
		}
	}

	private class PersonViewer implements AdapterView.OnItemClickListener {
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			if (mParentId == null) {
				PersonViewHolder currentHolder = (PersonViewHolder) view.getTag();
				if (currentHolder.selected) {
					mPersonAdapter.setItemNotSelected(currentHolder.personInternalId);
					currentHolder.selected = false;
				} else {
					mPersonAdapter.setItemSelected(currentHolder.personInternalId);
					currentHolder.selected = true;
				}
				currentHolder.queryIcon = true;
				updatePeopleIcons(false);
			} else {
				onView(((PersonViewHolder) view.getTag()).personInternalId);
			}
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent resultIntent) {
		switch (requestCode) {
			case R.id.intent_media_browser:
				if (resultCode == Activity.RESULT_OK && resultIntent != null) {
					if (resultIntent.getBooleanExtra(getString(R.string.extra_finish_activity), false)) {
						finish();
					}
				}
				break;
			default:
				super.onActivityResult(requestCode, resultCode, resultIntent);
		}
	}
}
