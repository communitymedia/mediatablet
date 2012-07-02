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
import ac.robinson.mediatablet.MediaViewerActivity;
import ac.robinson.mediatablet.R;
import ac.robinson.mediatablet.provider.MediaItem;
import ac.robinson.mediatablet.provider.MediaManager;
import ac.robinson.mediatablet.provider.MediaTabletProvider;
import ac.robinson.util.BitmapUtilities;
import ac.robinson.util.ImageCacheUtilities;
import ac.robinson.util.UIUtilities;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory.Options;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Display;

import com.polites.android.GestureImageView;

public class ImageViewerActivity extends MediaViewerActivity {

	@Override
	protected void initialiseView(Bundle savedInstanceState) {
		setContentView(R.layout.image_viewer);

		Display display = getWindowManager().getDefaultDisplay();
		int displayWidth = display.getWidth();
		int displayHeight = display.getHeight();

		String mediaPath = getCurrentMediaFile().getAbsolutePath(); // guaranteed to exist and not to be null
		int imageWidth;
		int imageHeight;
		if (MediaItem.getMediaTypeFromFileName(mediaPath) != MediaTabletProvider.TYPE_IMAGE_BACK) {
			// need to do this as we're the viewer for unknown media items that might not have had their icon loaded yet
			Resources resources = getResources();
			String mediaId = getCurrentMediaId();
			File mediaFile = new File(MediaTablet.DIRECTORY_THUMBS, MediaItem.getCacheId(mediaId,
					MediaItem.MEDIA_PRIVATE));
			if (!mediaFile.exists()) {
				MediaManager.reloadMediaIcon(resources, getContentResolver(), mediaId, MediaItem.MEDIA_PRIVATE);
				if (!mediaFile.exists()) {
					UIUtilities.showToast(ImageViewerActivity.this, R.string.error_loading_media);
					finish();
					return;
				}
			}
			mediaPath = mediaFile.getAbsolutePath();
			imageWidth = resources.getDimensionPixelSize(R.dimen.media_icon_width);
			imageHeight = resources.getDimensionPixelSize(R.dimen.media_icon_height);
		} else {
			Options imageOptions = BitmapUtilities.getImageDimensions(mediaPath); // called twice, but not a big issue
			imageWidth = imageOptions.outWidth;
			imageHeight = imageOptions.outHeight;
			if (imageWidth <= 0 || imageHeight <= 0) {
				UIUtilities.showToast(ImageViewerActivity.this, R.string.error_loading_media);
				finish();
				return;
			}
		}

		// TODO: need screen-sized bitmap due to bug in scale view - fix
		Bitmap backgroundBitmap;
		if (imageWidth >= displayWidth || imageHeight >= displayHeight) {
			backgroundBitmap = BitmapUtilities.loadAndCreateScaledBitmap(mediaPath, displayWidth, displayHeight,
					BitmapUtilities.ScalingLogic.FIT, true);
		} else {
			backgroundBitmap = Bitmap.createBitmap(displayWidth, imageHeight,
					ImageCacheUtilities.mBitmapFactoryOptions.inPreferredConfig);
			Canvas backgroundCanvas = new Canvas(backgroundBitmap);

			Bitmap imageBitmap = BitmapUtilities.loadAndCreateScaledBitmap(mediaPath, imageHeight, imageHeight,
					BitmapUtilities.ScalingLogic.FIT, true);
			backgroundCanvas.drawBitmap(imageBitmap, (backgroundBitmap.getWidth() - imageBitmap.getWidth()) / 2,
					(backgroundBitmap.getHeight() - imageBitmap.getHeight()) / 2,
					BitmapUtilities.getPaint(Color.BLACK, 1));
		}

		GestureImageView imageView = (GestureImageView) findViewById(R.id.media_image);
		imageView.setImageBitmap(backgroundBitmap);
	}
}
