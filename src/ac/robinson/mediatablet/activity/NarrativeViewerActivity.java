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
import java.io.FileInputStream;
import java.util.ArrayList;

import ac.robinson.mediatablet.MediaTablet;
import ac.robinson.mediatablet.MediaViewerActivity;
import ac.robinson.mediatablet.R;
import ac.robinson.mediautilities.MediaUtilities.FrameMediaContainer;
import ac.robinson.mediautilities.SMILUtilities;
import ac.robinson.util.BitmapUtilities;
import ac.robinson.util.DebugUtilities;
import ac.robinson.util.IOUtilities;
import ac.robinson.util.UIUtilities;
import ac.robinson.view.AutoResizeTextView;
import ac.robinson.view.CustomMediaController;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.PictureDrawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.SoundPool;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.RelativeLayout;

import com.larvalabs.svgandroid.SVGParser;

public class NarrativeViewerActivity extends MediaViewerActivity {

	private final int EXTRA_AUDIO_ITEMS = 2; // 3 audio items max, but only 2 for sound pool (other is in MediaPlayer)
	private SoundPool mSoundPool;
	private ArrayList<Integer> mFrameSounds;
	private int mNumExtraSounds;
	private boolean mMediaPlayerPrepared;
	private boolean mSoundPoolPrepared;
	private AssetFileDescriptor mSilenceFileDescriptor = null;

	private MediaPlayer mMediaPlayer;
	private boolean mMediaPlayerError;
	private CustomMediaController mMediaController;
	private ArrayList<FrameMediaContainer> mNarrativeContentList;
	private int mNarrativeDuration;
	private int mPlaybackPosition;
	private int mInitialPlaybackOffset;
	private int mNonAudioOffset;
	private FrameMediaContainer mCurrentFrameContainer;
	private PictureDrawable mAudioPictureDrawable = null;

	@Override
	protected void initialiseView(Bundle savedInstanceState) {
		setContentView(R.layout.narrative_viewer);

		// load previous state on screen rotation
		mPlaybackPosition = -1;
		if (savedInstanceState != null) {
			mPlaybackPosition = savedInstanceState.getInt(getString(R.string.extra_playback_position));
			mInitialPlaybackOffset = savedInstanceState.getInt(getString(R.string.extra_playback_offset));
			mNonAudioOffset = savedInstanceState.getInt(getString(R.string.extra_playback_non_audio_offset));
		}
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		savedInstanceState.putInt(getString(R.string.extra_playback_position), mPlaybackPosition);
		savedInstanceState.putInt(getString(R.string.extra_playback_offset), mInitialPlaybackOffset);
		savedInstanceState.putInt(getString(R.string.extra_playback_non_audio_offset), mNonAudioOffset);
		super.onSaveInstanceState(savedInstanceState);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			preparePlayback();
		}
	}

	@Override
	protected void onDestroy() {
		releasePlayer();
		super.onDestroy();
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		mMediaPlayerController.pause();
		mMediaController.updatePausePlay();
		return super.onOptionsItemSelected(item);
	}

	private void preparePlayback() {
		if (mNarrativeContentList != null && mNarrativeContentList.size() > 0 && mMediaPlayer != null
				&& mSoundPool != null && mMediaController != null && mPlaybackPosition >= 0) {
			return; // no need to re-initialise
		}

		// TODO: lazily load
		mNarrativeContentList = SMILUtilities.getSMILFrameList(getCurrentMediaFile(), 1, false, 0, false);
		if (mNarrativeContentList == null || mNarrativeContentList.size() <= 0) {
			UIUtilities.showToast(NarrativeViewerActivity.this, R.string.error_loading_narrative_player);
			finish();
			return;
		}
		String startFrameId = mNarrativeContentList.get(0).mFrameId;

		// first launch
		boolean updatePosition = mPlaybackPosition < 0;
		if (updatePosition) {
			mInitialPlaybackOffset = 0;
			mNonAudioOffset = 0;
		}
		mNarrativeDuration = 0;
		for (FrameMediaContainer container : mNarrativeContentList) {
			if (updatePosition && startFrameId.equals(container.mFrameId)) {
				updatePosition = false;
				mPlaybackPosition = mNarrativeDuration;
			}
			mNarrativeDuration += container.mFrameMaxDuration;
		}
		if (mPlaybackPosition < 0) {
			mPlaybackPosition = 0;
			mInitialPlaybackOffset = 0;
			mNonAudioOffset = 0;
		}

		mCurrentFrameContainer = getMediaContainer(mPlaybackPosition, true);

		releasePlayer();
		mMediaPlayer = new MediaPlayer();
		mSoundPool = new SoundPool(EXTRA_AUDIO_ITEMS, AudioManager.STREAM_MUSIC, 100);
		mFrameSounds = new ArrayList<Integer>();

		mMediaController = new CustomMediaController(this);

		RelativeLayout parentLayout = (RelativeLayout) findViewById(R.id.narrative_playback_container);
		RelativeLayout.LayoutParams controllerLayout = new RelativeLayout.LayoutParams(
				RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
		controllerLayout.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
		controllerLayout.setMargins(0, 0, 0, getResources().getDimensionPixelSize(R.dimen.button_padding));
		parentLayout.addView(mMediaController, controllerLayout);
		mMediaController.setAnchorView(findViewById(R.id.image_playback));

		prepareMediaItems(mCurrentFrameContainer);
	}

	private FrameMediaContainer getMediaContainer(int narrativePlaybackPosition, boolean updatePlaybackPosition) {
		int currentPosition = 0;
		for (FrameMediaContainer container : mNarrativeContentList) {
			int newPosition = currentPosition + container.mFrameMaxDuration;
			if (narrativePlaybackPosition >= currentPosition && narrativePlaybackPosition < newPosition) {
				if (updatePlaybackPosition) {
					mPlaybackPosition = currentPosition;
				}
				return container;
			}
			currentPosition = newPosition;
		}
		return null;
	}

	private void prepareMediaItems(FrameMediaContainer container) {
		// load the audio for the media player
		Resources res = getResources();
		mSoundPoolPrepared = false;
		mMediaPlayerPrepared = false;
		mMediaPlayerError = false;
		mNonAudioOffset = 0;
		unloadSoundPool();
		mSoundPool.setOnLoadCompleteListener(mSoundPoolLoadListener);
		mNumExtraSounds = 0;
		String currentAudioItem = null;
		boolean soundPoolAllowed = !DebugUtilities.hasSoundPoolBug();
		for (int i = 0, n = container.mAudioDurations.size(); i < n; i++) {
			if (container.mAudioDurations.get(i).intValue() == container.mFrameMaxDuration) {
				currentAudioItem = container.mAudioPaths.get(i); // TODO: choose by file size, rather than length?
			} else {
				// playing *anything* in SoundPool at the same time as MediaPlayer crashes on Galaxy Tab
				if (soundPoolAllowed) {
					mSoundPool.load(container.mAudioPaths.get(i), 1);
					mNumExtraSounds += 1;
				}
			}
		}
		if (mNumExtraSounds == 0) {
			mSoundPoolPrepared = true;
		}

		try {
			mMediaPlayer.reset();
			mMediaPlayer.setLooping(false);
			if (currentAudioItem == null || (!(new File(currentAudioItem).exists()))) {
				if (mSilenceFileDescriptor == null) {
					mSilenceFileDescriptor = res.openRawResourceFd(R.raw.silence_100ms);
				}
				mMediaPlayer.setDataSource(mSilenceFileDescriptor.getFileDescriptor(),
						mSilenceFileDescriptor.getStartOffset(), mSilenceFileDescriptor.getDeclaredLength());
			} else {
				// can't play from data directory (they're private; permissions don't work), must use an input stream
				// mMediaPlayer.setDataSource(currentAudioItem);
				FileInputStream playerInputStream = new FileInputStream(new File(currentAudioItem));
				mMediaPlayer.setDataSource(playerInputStream.getFD());
				IOUtilities.closeStream(playerInputStream);
			}
			mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			mMediaPlayer.setOnPreparedListener(mMediaPlayerPreparedListener);
			mMediaPlayer.setOnCompletionListener(mMediaPlayerCompletionListener);
			mMediaPlayer.setOnErrorListener(mMediaPlayerErrorListener);
			mMediaPlayer.prepareAsync();
		} catch (Exception e) {
			UIUtilities.showToast(NarrativeViewerActivity.this, R.string.error_loading_narrative_player);
			finish();
			return;
		}

		// load the image
		ImageView photoDisplay = (ImageView) findViewById(R.id.image_playback);
		if (container.mImagePath != null && new File(container.mImagePath).exists()) {
			Bitmap scaledBitmap = BitmapUtilities.loadAndCreateScaledBitmap(container.mImagePath,
					photoDisplay.getWidth(), photoDisplay.getHeight(), BitmapUtilities.ScalingLogic.FIT, true);

			if (scaledBitmap != null) {
				photoDisplay.setImageBitmap(scaledBitmap);

				// hack to align horizontally centred but vertically top when appropriate
				Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
				if (scaledBitmap.getWidth() > scaledBitmap.getHeight()) {
					photoDisplay.setScaleType(ScaleType.CENTER);
					int bottomPadding = 0;
					if (display.getWidth() < display.getHeight()) {
						bottomPadding = getResources().getDimensionPixelSize(R.dimen.navigation_button_height);
					}
					photoDisplay.setPadding(0, 0, 0, bottomPadding);
				} else {
					photoDisplay.setScaleType(ScaleType.FIT_START);
					photoDisplay.setPadding((display.getWidth() - scaledBitmap.getWidth()) / 2, 0, 0, 0);
				}
			}

		} else if (TextUtils.isEmpty(container.mTextContent)) {
			if (mAudioPictureDrawable == null) {
				mAudioPictureDrawable = SVGParser.getSVGFromResource(res, R.raw.ic_audio_playback)
						.createPictureDrawable();
			}
			photoDisplay.setImageDrawable(mAudioPictureDrawable);
			photoDisplay.setPadding(0, 0, 0, res.getDimensionPixelSize(R.dimen.media_controller_height));
			photoDisplay.setScaleType(ScaleType.FIT_CENTER);
		} else {
			photoDisplay.setImageDrawable(null);
			photoDisplay.setBackgroundColor(res.getColor(R.color.icon_background));
		}

		// load the text
		AutoResizeTextView textView = (AutoResizeTextView) findViewById(R.id.text_playback);
		if (!TextUtils.isEmpty(container.mTextContent)) {
			textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimensionPixelSize(R.dimen.playback_text));
			textView.setText(container.mTextContent);
			RelativeLayout.LayoutParams textLayout = new RelativeLayout.LayoutParams(
					RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
			textLayout.addRule(RelativeLayout.CENTER_HORIZONTAL);
			int textViewHeight = res.getDimensionPixelSize(R.dimen.media_controller_height);
			int textViewPadding = res.getDimensionPixelSize(R.dimen.playback_text_padding);
			if (container.mImagePath != null) {
				textView.setMaxHeight(res.getDimensionPixelSize(R.dimen.playback_maximum_text_height_with_image));
				textLayout.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
				textView.setPadding(textViewPadding, textViewPadding, textViewPadding, textViewPadding);
				textView.setBackgroundResource(R.drawable.rounded_playback_text);
				textView.setTextColor(res.getColor(R.color.icon_text_with_image));
			} else {
				textView.setMaxHeight(photoDisplay.getHeight()); // no way to clear, so set to parent height
				textLayout.addRule(RelativeLayout.CENTER_VERTICAL);
				textView.setPadding(textViewPadding, textViewPadding, textViewPadding, textViewHeight);
				textView.setBackgroundColor(res.getColor(android.R.color.transparent));
				textView.setTextColor(res.getColor(R.color.icon_text_no_image));
			}
			textLayout.setMargins(0, 0, 0, textViewHeight);
			textView.setLayoutParams(textLayout);
			textView.setVisibility(View.VISIBLE);
		} else {
			textView.setVisibility(View.GONE);
		}
	}

	private void unloadSoundPool() {
		for (Integer soundId : mFrameSounds) {
			mSoundPool.stop(soundId);
			mSoundPool.unload(soundId);
		}
		mFrameSounds.clear();
	}

	private void releasePlayer() {
		UIUtilities.releaseKeepScreenOn(getWindow());
		// release controller first, so we don't play to a null player
		if (mMediaController != null) {
			mMediaController.hide();
			((RelativeLayout) findViewById(R.id.narrative_playback_container)).removeView(mMediaController);
			mMediaController.setMediaPlayer(null);
			mMediaController = null;
		}
		if (mMediaPlayer != null) {
			try {
				mMediaPlayer.stop();
			} catch (IllegalStateException e) {
			}
			mMediaPlayer.release();
			mMediaPlayer = null;
		}
		if (mSoundPool != null) {
			unloadSoundPool();
			mSoundPool.release();
			mSoundPool = null;
		}
	}

	private CustomMediaController.MediaPlayerControl mMediaPlayerController = new CustomMediaController.MediaPlayerControl() {
		@Override
		public void start() {
			UIUtilities.acquireKeepScreenOn(getWindow());
			// so we return to the start when playing from the end
			if (mPlaybackPosition < 0) {
				mPlaybackPosition = 0;
				mInitialPlaybackOffset = 0;
				mNonAudioOffset = 0;
				mCurrentFrameContainer = getMediaContainer(mPlaybackPosition, true);
				prepareMediaItems(mCurrentFrameContainer);
			} else {
				mMediaPlayer.start();
				mSoundPool.autoResume();
				// TODO: check this works
			}
		}

		@Override
		public void pause() {
			UIUtilities.releaseKeepScreenOn(getWindow());
			mMediaPlayer.pause();
			mSoundPool.autoPause();
			// TODO: check this works
		}

		@Override
		public int getDuration() {
			return mNarrativeDuration;
		}

		@Override
		public int getCurrentPosition() {
			if (mPlaybackPosition < 0) {
				return mNarrativeDuration;
			} else {
				mInitialPlaybackOffset = mMediaPlayer.getCurrentPosition();
				return mPlaybackPosition + mNonAudioOffset + mInitialPlaybackOffset;
			}
		}

		@Override
		public void seekTo(int pos) {
			// TODO: seek others (is it even possible with soundpool?)
			int actualPosition = pos - mPlaybackPosition;
			if (actualPosition >= 0 && actualPosition < mCurrentFrameContainer.mFrameMaxDuration) {
				if (actualPosition < mMediaPlayer.getDuration()) {
					mMediaPlayer.seekTo(actualPosition);
				} else {
					// for image- or text-only frames
					mNonAudioOffset = actualPosition;
					mMediaPlayer.seekTo(0);
					mMediaPlayer.start();
					mMediaController.setProgress(); // called far too often (every 100ms), but it doesn't really matter
				}
			} else if (pos >= 0 && pos < mNarrativeDuration) {
				mCurrentFrameContainer = getMediaContainer(pos, true);
				prepareMediaItems(mCurrentFrameContainer);
			}
		}

		@Override
		public boolean isPlaying() {
			return mMediaPlayer.isPlaying();
		}

		@Override
		public int getBufferPercentage() {
			return 0;
		}

		@Override
		public boolean canPause() {
			return true;
		}

		@Override
		public boolean canSeekBackward() {
			return true;
		}

		@Override
		public boolean canSeekForward() {
			return true;
		}
	};

	private void startPlayers() {
		UIUtilities.acquireKeepScreenOn(getWindow());

		AudioManager mgr = (AudioManager) NarrativeViewerActivity.this.getSystemService(Context.AUDIO_SERVICE);
		float streamVolumeCurrent = mgr.getStreamVolume(AudioManager.STREAM_MUSIC);
		float streamVolumeMax = mgr.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		float volume = streamVolumeCurrent / streamVolumeMax;

		for (Integer soundId : mFrameSounds) {
			mSoundPool.play(soundId, volume, volume, 1, 0, 1f);
			// TODO: seek to mInitialPlaybackOffset
		}

		mMediaPlayer.setVolume(volume, volume);
		mMediaPlayer.start();
		mMediaPlayer.seekTo(mInitialPlaybackOffset);

		mMediaController.setMediaPlayer(mMediaPlayerController);
		mMediaController.show(0); // 0 for permanent visibility TODO: hide playback controls after short timeout?
	}

	private SoundPool.OnLoadCompleteListener mSoundPoolLoadListener = new SoundPool.OnLoadCompleteListener() {
		@Override
		public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
			mFrameSounds.add(sampleId);
			if (mFrameSounds.size() >= mNumExtraSounds) {
				mSoundPoolPrepared = true;
			}
			if (mSoundPoolPrepared && mMediaPlayerPrepared) {
				startPlayers();
			}
		}
	};

	private OnPreparedListener mMediaPlayerPreparedListener = new OnPreparedListener() {
		@Override
		public void onPrepared(MediaPlayer mp) {
			mMediaPlayerPrepared = true;
			if (mSoundPoolPrepared) {
				startPlayers();
			}
		}
	};

	private OnCompletionListener mMediaPlayerCompletionListener = new OnCompletionListener() {
		@Override
		public void onCompletion(MediaPlayer mp) {
			if (mMediaPlayerError) {
				// releasePlayer(); // don't do this, as it means the player will be null; instead we resume from errors
				return;
			}
			mInitialPlaybackOffset = 0;
			int currentPosition = mPlaybackPosition + mNonAudioOffset + mMediaPlayer.getDuration() + 1;
			if (currentPosition < mNarrativeDuration) {
				mMediaPlayerController.seekTo(currentPosition);
			} else {
				// move to just before the end (accounting for mNarrativeDuration errors)
				mMediaPlayerController.seekTo(currentPosition - 2);
				mMediaPlayerController.pause();
				mMediaController.updatePausePlay();
				mPlaybackPosition = -1; // so we start from the beginning
				UIUtilities.releaseKeepScreenOn(getWindow());
			}
		}
	};

	private OnErrorListener mMediaPlayerErrorListener = new OnErrorListener() {
		@Override
		public boolean onError(MediaPlayer mp, int what, int extra) {
			mMediaPlayerError = true;
			// UIUtilities.showToast(NarrativeViewerActivity.this, R.string.error_loading_narrative_player);
			if (MediaTablet.DEBUG)
				Log.d(DebugUtilities.getLogTag(this), "Playback error � what: " + what + ", extra: " + extra);
			return false; // not handled -> onCompletionListener will be called
		}
	};
}
