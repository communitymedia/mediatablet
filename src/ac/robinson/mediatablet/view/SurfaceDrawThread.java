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

import android.graphics.Canvas;

public class SurfaceDrawThread extends Thread {
	private HomesteadSurfaceView mSurfaceView;
	private boolean mRunning = false;

	public SurfaceDrawThread(HomesteadSurfaceView panel) {
		mSurfaceView = panel;
	}

	public void setRunning(boolean run) {
		mRunning = run;
	}

	public boolean isRunning() {
		return mRunning;
	}

	@Override
	public void run() {
		Canvas c;
		while (mRunning) {
			c = null;
			try {
				c = mSurfaceView.getHolder().lockCanvas(null);
				synchronized (mSurfaceView.getHolder()) {
					mSurfaceView.updatePhysics();
					mSurfaceView.onDraw(c);
				}
				yield();
			} catch (Exception e) {
			} finally {
				// do this in a finally so that if an exception is thrown
				// during the above, we don't leave the Surface in an
				// inconsistent state
				if (c != null) {
					mSurfaceView.getHolder().unlockCanvasAndPost(c);
				}
			}
		}
	}
}
