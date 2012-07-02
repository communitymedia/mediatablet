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

import java.util.ArrayList;

import ac.robinson.mediatablet.MediaTablet;
import ac.robinson.mediatablet.R;
import ac.robinson.mediatablet.activity.PeopleBrowserActivity;
import ac.robinson.mediatablet.view.PersonViewHolder;
import ac.robinson.util.ImageCacheUtilities;
import ac.robinson.view.CrossFadeDrawable;
import ac.robinson.view.FastBitmapDrawable;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.CursorAdapter;
import android.widget.Filter;
import android.widget.FilterQueryProvider;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class PersonAdapter extends CursorAdapter implements FilterQueryProvider {

	private final int mInternalIdIndex;
	private final int mNameIndex;

	private final PeopleBrowserActivity mActivity;
	private final LayoutInflater mInflater;

	private final Bitmap mDefaultIconBitmap;
	private final FastBitmapDrawable mDefaultIcon;

	private final Filter mFilter;
	private final String mSelectionHomestead;
	private final String mSelectionNotDeleted;
	private String mHomesteadFilter = null;

	private ArrayList<String> mSelectedItems = new ArrayList<String>();

	private final String[] mFilterArguments1 = new String[1];

	public PersonAdapter(PeopleBrowserActivity activity, String homesteadId) {
		super(activity, activity.managedQuery(PersonItem.CONTENT_URI, PersonItem.PROJECTION_ALL, "1=?",
				new String[] { "0" }, null), true); // hack to show no data initially

		mActivity = activity;
		mInflater = LayoutInflater.from(activity);
		mFilter = ((Filterable) this).getFilter();

		final Cursor c = getCursor();
		mInternalIdIndex = c.getColumnIndexOrThrow(PersonItem.INTERNAL_ID);
		mNameIndex = c.getColumnIndexOrThrow(PersonItem.NAME);

		// alternative (without frame): Bitmap.createBitmap(1, 1,
		// ImageCacheUtilities.mBitmapFactoryOptions.inPreferredConfig);
		mDefaultIconBitmap = PersonItem.loadTemporaryIcon(activity.getResources(), false);
		mDefaultIcon = new FastBitmapDrawable(mDefaultIconBitmap);

		final StringBuilder selection = new StringBuilder();
		selection.append(PersonItem.DELETED);
		selection.append("=0");
		mSelectionNotDeleted = selection.toString();

		selection.setLength(0);
		selection.append("(");
		selection.append(PersonItem.DELETED);
		selection.append("=0 AND ");
		selection.append(PersonItem.PARENT_ID);
		selection.append("=?");
		selection.append(")");
		mSelectionHomestead = selection.toString();

		setFilterQueryProvider(this);

		setHomesteadFilter(homesteadId);
	}

	public FastBitmapDrawable getDefaultIcon() {
		return mDefaultIcon;
	}

	private void reFilter() {
		mFilter.filter(null);
	}

	public void setHomesteadFilter(String homesteadFilter) {
		mHomesteadFilter = homesteadFilter;
		reFilter();
	}

	public void setItemSelected(String internalId) {
		mSelectedItems.add(internalId);
	}

	public void setItemNotSelected(String internalId) {
		mSelectedItems.remove(internalId);
	}

	public ArrayList<String> getSelectedItems() {
		return mSelectedItems;
	}

	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		final View view = mInflater.inflate(R.layout.grid_image_text_item, parent, false);

		PersonViewHolder holder = new PersonViewHolder();
		holder.display = (ImageView) view.findViewById(R.id.grid_image_text_view_image);
		holder.text = (TextView) view.findViewById(R.id.grid_image_text_view_text);
		holder.selected = false;
		holder.loader = (ProgressBar) view.findViewById(R.id.grid_image_text_view_progress);
		holder.overlay = (ImageView) view.findViewById(R.id.grid_image_text_view_overlay);
		view.setTag(holder);

		final CrossFadeDrawable transition = new CrossFadeDrawable(mDefaultIconBitmap, null);
		transition.setCallback(view);
		transition.setCrossFadeEnabled(true);
		holder.transition = transition;

		return view;
	}

	public void bindView(View view, Context context, Cursor c) {
		PersonViewHolder holder = (PersonViewHolder) view.getTag();

		holder.personInternalId = c.getString(mInternalIdIndex);
		holder.selected = mSelectedItems.contains(holder.personInternalId);
		String personCacheId = PersonItem.getCacheId(holder.personInternalId);

		final PeopleBrowserActivity activity = mActivity;
		if (activity.getScrollState() == AbsListView.OnScrollListener.SCROLL_STATE_FLING
				|| activity.isPendingIconsUpdate()) {
			holder.loader.setVisibility(View.VISIBLE);
			holder.display.setBackgroundDrawable(mDefaultIcon);
			holder.queryIcon = true;
		} else {
			// if the icon has gone missing (recently imported or cache deletion), regenerate it
			// this will happen on every new person, but we check for the file before generation, so not too bad
			FastBitmapDrawable cachedIcon = ImageCacheUtilities.getCachedIcon(MediaTablet.DIRECTORY_THUMBS,
					personCacheId, ImageCacheUtilities.NULL_DRAWABLE);
			if (ImageCacheUtilities.NULL_DRAWABLE.equals(cachedIcon)) {
				PersonManager.reloadPersonIcon(mActivity.getResources(), mActivity.getContentResolver(),
						holder.personInternalId);
				cachedIcon = ImageCacheUtilities.getCachedIcon(MediaTablet.DIRECTORY_THUMBS, personCacheId,
						mDefaultIcon);
			}
			holder.display.setBackgroundDrawable(cachedIcon);
			holder.loader.setVisibility(View.GONE);
			holder.queryIcon = false;
		}

		String personName = c.getString(mNameIndex);
		if (!TextUtils.isEmpty(personName)) {
			holder.text.setText(personName.substring(0,
					Math.min(personName.length(), MediaTablet.MAXIMUM_PERSON_TEXT_LENGTH)));
		} else {
			holder.text.setText(" ");
		}
		if (holder.selected) {
			holder.overlay.setBackgroundResource(R.drawable.item_public);
			holder.overlay.setPadding(0, 0, 0, 0);
		} else {
			holder.overlay.setBackgroundDrawable(null);
		}
	}

	@Override
	public void changeCursor(Cursor cursor) {
		final Cursor oldCursor = getCursor();
		if (oldCursor != null)
			mActivity.stopManagingCursor(oldCursor);
		super.changeCursor(cursor);
	}

	public Cursor runQuery(CharSequence constraint) {
		if (mHomesteadFilter != null) {
			final String[] filterArguments = mFilterArguments1;
			filterArguments[0] = mHomesteadFilter;

			// TODO: sort out projection to only return necessary columns
			return mActivity.managedQuery(PersonItem.CONTENT_URI, PersonItem.PROJECTION_ALL, mSelectionHomestead,
					filterArguments, PersonItem.DEFAULT_SORT_ORDER);
		} else {
			// TODO: sort out projection to only return necessary columns
			return mActivity.managedQuery(PersonItem.CONTENT_URI, PersonItem.PROJECTION_ALL, mSelectionNotDeleted,
					null, PersonItem.DEFAULT_SORT_ORDER);
		}
	}
}
