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

import ac.robinson.mediatablet.MediaViewerActivity;
import ac.robinson.mediatablet.R;
import ac.robinson.util.IOUtilities;
import ac.robinson.view.AutoResizeTextView;
import android.os.Bundle;
import android.util.TypedValue;

public class TextViewerActivity extends MediaViewerActivity {

	@Override
	protected void initialiseView(Bundle savedInstanceState) {
		setContentView(R.layout.text_viewer);

		String mediaPath = getCurrentMediaFile().getAbsolutePath(); // guaranteed to exist and not to be null
		AutoResizeTextView textView = (AutoResizeTextView) findViewById(R.id.media_text);
		textView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
				getResources().getDimensionPixelSize(R.dimen.playback_text_size));
		textView.setText(IOUtilities.getFileContents(mediaPath));
	}
}
