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

import ac.robinson.mediatablet.MediaTablet;
import ac.robinson.mediatablet.R;
import ac.robinson.mediatablet.activity.MediaBrowserActivity;
import ac.robinson.mediatablet.view.MediaViewHolder;
import ac.robinson.util.ImageCacheUtilities;
import ac.robinson.view.CrossFadeDrawable;
import ac.robinson.view.FastBitmapDrawable;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
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

public class MediaAdapter extends CursorAdapter implements FilterQueryProvider {

	private final int mInternalIdIndex;
	private final int mParentIdIndex;
	private final int mTypeIndex;
	private final int mVisibilityIndex;

	private final MediaBrowserActivity mActivity;
	private final LayoutInflater mInflater;

	private final Bitmap mDefaultIconBitmap;
	private final FastBitmapDrawable mDefaultIcon;

	private final Filter mFilter;
	private final String mSelectionPublic;
	private final String mSelectionPrivate;
	private final String mSelectionId;
	private String mSelectionType;
	private String mSelection;

	private int mVisibilityFilter = MediaItem.MEDIA_PRIVATE;

	// *crucial* to clone for restarting activity (until proper state saving...)
	private int[] mMediaTypeFilter = MediaTabletProvider.NO_MEDIA_TYPES.clone();
	private StringBuilder mTypeFilterBuilder = new StringBuilder();
	private String mOwnerFilter = null;

	private final String[] mFilterArguments0 = new String[0];
	private final String[] mFilterArguments1 = new String[1];

	public MediaAdapter(MediaBrowserActivity activity, String ownerId, int mediaVisibility) {
		super(activity, activity.managedQuery(MediaItem.CONTENT_URI, MediaItem.PROJECTION_ALL, "1=?",
				new String[] { "0" }, null), true); // hack to show no data initially

		mActivity = activity;
		mInflater = LayoutInflater.from(activity);
		mFilter = ((Filterable) this).getFilter();

		final Cursor c = getCursor();
		mInternalIdIndex = c.getColumnIndexOrThrow(MediaItem.INTERNAL_ID);
		mParentIdIndex = c.getColumnIndexOrThrow(MediaItem.PARENT_ID);
		mTypeIndex = c.getColumnIndexOrThrow(MediaItem.TYPE);
		mVisibilityIndex = c.getColumnIndexOrThrow(MediaItem.VISIBILITY);

		// alternative (without frame): Bitmap.createBitmap(1, 1,
		// ImageCacheUtilities.mBitmapFactoryOptions.inPreferredConfig);
		mDefaultIconBitmap = PersonItem.loadTemporaryIcon(activity.getResources(), false);
		mDefaultIcon = new FastBitmapDrawable(mDefaultIconBitmap);

		final StringBuilder selection = new StringBuilder();
		selection.append(MediaItem.VISIBILITY);
		selection.append("=");
		selection.append(MediaItem.MEDIA_PUBLIC);
		selection.append(" AND ");
		selection.append(MediaItem.DELETED);
		selection.append("=0 AND ");
		selection.append(MediaItem.TYPE);
		selection.append(" IN (");
		mSelectionPublic = selection.toString();

		selection.setLength(0);
		selection.append(MediaItem.TYPE);
		selection.append(" IN (");
		mSelectionPrivate = selection.toString();

		selection.setLength(0);
		selection.append(") AND ");
		selection.append("(");
		selection.append(MediaItem.DELETED);
		selection.append("=0 AND ");
		selection.append(MediaItem.PARENT_ID);
		selection.append("=?");
		selection.append(")");
		mSelectionId = selection.toString();

		setFilterQueryProvider(this);

		mVisibilityFilter = mediaVisibility;
		buildMediaFilter();
		setOwnerFilter(ownerId);
	}

	public FastBitmapDrawable getDefaultIcon() {
		return mDefaultIcon;
	}

	private void reFilter() {
		mFilter.filter(null);
	}

	public void setOwnerFilter(String ownerFilter) {
		mOwnerFilter = ownerFilter;
		if (mOwnerFilter == null) {
			mVisibilityFilter = MediaItem.MEDIA_PUBLIC; // only public media if no owner specified
		}
		reFilter();
	}

	public void enableMediaFilters(int... filtersToEnable) {
		for (int i = 0, n = mMediaTypeFilter.length; i < n; i++) {
			mMediaTypeFilter[i] = 0;
		}
		for (int i = 0, n = filtersToEnable.length; i < n; i++) {
			mMediaTypeFilter[filtersToEnable[i] - 1] = filtersToEnable[i];
		}
		buildMediaFilter();
		reFilter();
	}

	public void disableMediaFilters(int... filtersToDisable) {
		for (int i = 0, n = filtersToDisable.length; i < n; i++) {
			mMediaTypeFilter[filtersToDisable[i] - 1] = 0;
		}
		buildMediaFilter();
		reFilter();
	}

	public void setMediaFilters(boolean enable, int... filterValues) {
		if (enable) {
			enableMediaFilters(filterValues);
		} else {
			disableMediaFilters(filterValues);
		}
	}

	public void disableAllMediaFilters() {
		mMediaTypeFilter = MediaTabletProvider.NO_MEDIA_TYPES.clone();
		buildMediaFilter();
		reFilter();
	}

	public void setVisibilityFilter(int visibilityFilter) {
		if (mOwnerFilter == null && visibilityFilter == MediaItem.MEDIA_PRIVATE) {
			// TODO: throw exception or alert user - can't view private when in all public view
		} else {
			mVisibilityFilter = visibilityFilter;
		}
		reFilter();
	}

	public int getVisibilityFilter() {
		return mVisibilityFilter;
	}

	private void buildMediaFilter() {
		boolean allEnabled = true;
		for (int i = 0, n = mMediaTypeFilter.length; i < n; i++) {
			if (mMediaTypeFilter[i] > 0) {
				allEnabled = false;
				break;
			}
		}

		mTypeFilterBuilder.setLength(0);
		for (int i = 0, n = MediaTabletProvider.ALL_MEDIA_TYPES.length; i < n; i++) {
			if (allEnabled) {
				mTypeFilterBuilder.append(MediaTabletProvider.ALL_MEDIA_TYPES[i]);
			} else {
				mTypeFilterBuilder.append(mMediaTypeFilter[i]);
			}
			mTypeFilterBuilder.append(',');
		}
		mTypeFilterBuilder.setLength(mTypeFilterBuilder.length() - 1);
		mSelectionType = mTypeFilterBuilder.toString();
	}

	public View newView(Context context, Cursor cursor, ViewGroup parent) {
		final View view = mInflater.inflate(R.layout.grid_image_item, parent, false);

		MediaViewHolder holder = new MediaViewHolder();
		holder.display = (ImageView) view.findViewById(R.id.grid_image_view_image);
		holder.loader = (ProgressBar) view.findViewById(R.id.grid_image_view_progress);
		holder.overlay = (ImageView) view.findViewById(R.id.grid_image_view_overlay);
		view.setTag(holder);

		final CrossFadeDrawable transition = new CrossFadeDrawable(mDefaultIconBitmap, null);
		transition.setCallback(view);
		transition.setCrossFadeEnabled(true);
		holder.transition = transition;

		return view;
	}

	// setBackgroundDrawable is deprecated from API 16+ (Jelly Bean), but we still want to target earlier versions;
	// since this is purely a name change, there's no real reason to do anything platform-independent
	@SuppressWarnings("deprecation")
	public void bindView(View view, Context context, Cursor c) {
		MediaViewHolder holder = (MediaViewHolder) view.getTag();

		holder.mediaInternalId = c.getString(mInternalIdIndex);
		holder.mediaOwnerId = c.getString(mParentIdIndex);
		holder.mediaType = c.getInt(mTypeIndex);
		holder.mediaVisibility = c.getInt(mVisibilityIndex);

		final MediaBrowserActivity activity = mActivity;
		int iconVisibility = mOwnerFilter == null ? MediaItem.MEDIA_PUBLIC : MediaItem.MEDIA_PRIVATE;
		if (activity.getScrollState() == AbsListView.OnScrollListener.SCROLL_STATE_FLING
				|| activity.isPendingIconsUpdate()) {
			holder.loader.setVisibility(View.VISIBLE);
			holder.display.setImageDrawable(mDefaultIcon);
			holder.queryIcon = true;
		} else {
			// if the icon has gone missing (recently imported or cache deletion), regenerate it
			// this will happen on every new person, but we check for the file before generation, so not too bad
			String mediaCacheId = MediaItem.getCacheId(holder.mediaInternalId, iconVisibility);
			FastBitmapDrawable cachedIcon = ImageCacheUtilities.getCachedIcon(MediaTablet.DIRECTORY_THUMBS,
					mediaCacheId, ImageCacheUtilities.NULL_DRAWABLE);
			if (ImageCacheUtilities.NULL_DRAWABLE.equals(cachedIcon)) {
				MediaManager.reloadMediaIcon(mActivity.getResources(), mActivity.getContentResolver(),
						holder.mediaInternalId, iconVisibility);
				cachedIcon = ImageCacheUtilities
						.getCachedIcon(MediaTablet.DIRECTORY_THUMBS, mediaCacheId, mDefaultIcon);
			}
			holder.display.setImageDrawable(cachedIcon);
			holder.loader.setVisibility(View.GONE);
			holder.queryIcon = false;
		}
		if (mOwnerFilter != null && holder.mediaVisibility == MediaItem.MEDIA_PUBLIC) {
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
		final StringBuilder buffer = new StringBuilder();
		if (mVisibilityFilter == MediaItem.MEDIA_PRIVATE) {
			buffer.append(mSelectionPrivate);
		} else {
			buffer.append(mSelectionPublic);
		}
		buffer.append(mSelectionType);

		final String[] filterArguments;
		if (mOwnerFilter != null) {
			buffer.append(mSelectionId);
			filterArguments = mFilterArguments1;
			filterArguments[0] = mOwnerFilter;
		} else {
			buffer.append(")"); // hack!
			filterArguments = mFilterArguments0;
		}
		mSelection = buffer.toString(); // buffer.append('%') // wildcard

		// TODO: sort out projection to only return necessary columns
		return mActivity.managedQuery(MediaItem.CONTENT_URI, MediaItem.PROJECTION_ALL, mSelection, filterArguments,
				MediaItem.DEFAULT_SORT_ORDER);
	}
}
