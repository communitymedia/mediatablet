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

package ac.robinson.mediatablet.view;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import ac.robinson.mediatablet.MediaTablet;
import ac.robinson.mediatablet.provider.HomesteadItem;
import ac.robinson.mediatablet.provider.HomesteadManager;
import ac.robinson.util.IOUtilities;
import ac.robinson.util.ImageCacheUtilities;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class SurfaceLoadThread extends Thread {

	private HomesteadSurfaceView mSurfaceView;
	private boolean mRunning = false;

	public SurfaceLoadThread(HomesteadSurfaceView panel) {
		mSurfaceView = panel;
	}

	public void setRunning(boolean run) {
		mRunning = run;
	}

	public boolean isRunning() {
		return mRunning;
	}

	public Bitmap loadIcon(String iconFile, boolean allowRecursive) {
		File file = new File(MediaTablet.DIRECTORY_THUMBS, iconFile);
		if (file.exists()) {
			InputStream stream = null;
			try {
				stream = new FileInputStream(file);
				return BitmapFactory.decodeStream(stream, null, ImageCacheUtilities.mBitmapFactoryOptions);
			} catch (FileNotFoundException e) {
				// ignore
			} finally {
				IOUtilities.closeStream(stream);
			}
		} else if (allowRecursive && !iconFile.startsWith(HomesteadSurfaceView.BACKGROUND_IMAGE_NAME)) {
			HomesteadManager.reloadHomesteadIcon(mSurfaceView.getContext().getResources(), mSurfaceView.getContext()
					.getContentResolver(), HomesteadItem.getInternalIdFromCacheId(iconFile));
			return loadIcon(iconFile, false);
		}
		return null;
	}

	@Override
	public void run() {
		String iconFile;
		while (mRunning) {
			// these are synchronized in the methods, rather than here
			iconFile = null;
			synchronized (mSurfaceView.getHolder()) {
				iconFile = mSurfaceView.getIconToLoad();
			}
			Bitmap loadedIcon = null;
			if (iconFile != null) {
				loadedIcon = loadIcon(iconFile, true);
			}
			synchronized (mSurfaceView.getHolder()) {
				mSurfaceView.addLoadedTile(iconFile, loadedIcon);
			}
			yield();
		}
	}
}
