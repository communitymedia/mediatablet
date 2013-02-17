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
import ac.robinson.mediautilities.FrameMediaContainer;
import ac.robinson.mediautilities.SMILUtilities;
import ac.robinson.util.BitmapUtilities;
import ac.robinson.util.DebugUtilities;
import ac.robinson.util.IOUtilities;
import ac.robinson.util.UIUtilities;
import ac.robinson.view.AutoResizeTextView;
import ac.robinson.view.CustomMediaController;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.Bitmap;
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
import android.view.MenuItem;
import android.view.View;
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
	private boolean mSilenceFilePlaying;
	private long mPlaybackStartTime;
	private long mPlaybackPauseTime;

	private MediaPlayer mMediaPlayer;
	private boolean mMediaPlayerError;
	private boolean mHasPlayed;
	private boolean mIsLoading;
	private CustomMediaController mMediaController;
	private ArrayList<FrameMediaContainer> mNarrativeContentList;
	private int mNarrativeDuration;
	private int mPlaybackPosition;
	private int mInitialPlaybackOffset;
	private int mNonAudioOffset;
	private FrameMediaContainer mCurrentFrameContainer;
	private Bitmap mAudioPictureBitmap = null;

	@Override
	protected void initialiseView(Bundle savedInstanceState) {
		setContentView(R.layout.narrative_viewer);

		mIsLoading = false;
		mMediaPlayerError = false;

		// load previous state on screen rotation
		mHasPlayed = false; // will begin playing if not playing already; used to stop unplayable narratives
		mPlaybackPosition = -1;
		if (savedInstanceState != null) {
			// mIsPlaying = savedInstanceState.getBoolean(getString(R.string.extra_is_playing));
			mPlaybackPosition = savedInstanceState.getInt(getString(R.string.extra_playback_position));
			mInitialPlaybackOffset = savedInstanceState.getInt(getString(R.string.extra_playback_offset));
			mNonAudioOffset = savedInstanceState.getInt(getString(R.string.extra_playback_non_audio_offset));
		}
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		// savedInstanceState.putBoolean(getString(R.string.extra_is_playing), mIsPlaying);
		savedInstanceState.putInt(getString(R.string.extra_playback_position), mPlaybackPosition);
		savedInstanceState.putInt(getString(R.string.extra_playback_offset),
				mMediaPlayerController.getCurrentPosition() - mPlaybackPosition);
		savedInstanceState.putInt(getString(R.string.extra_playback_non_audio_offset), mNonAudioOffset);
		super.onSaveInstanceState(savedInstanceState);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			if (!mHasPlayed) {
				preparePlayback();
			} else {
				if (mMediaPlayer != null && mMediaPlayer.isPlaying()) { // don't hide the controller if we're paused
					showMediaController(CustomMediaController.DEFAULT_VISIBILITY_TIMEOUT);
				}
			}
		} else {
			showMediaController(-1); // so if we're interacting with an overlay we don't constantly hide/show
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		pauseMediaController();
	}

	@Override
	protected void onDestroy() {
		releasePlayer();
		super.onDestroy();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		pauseMediaController();
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
		showMediaController(CustomMediaController.DEFAULT_VISIBILITY_TIMEOUT); // (can use 0 for permanent visibility)

		mHasPlayed = true;
		prepareMediaItems(mCurrentFrameContainer);
	}

	public void handleButtonClicks(View currentButton) {
		pauseMediaController();
		super.handleButtonClicks(currentButton);
	}

	public void handleNarrativeClicks(View currentButton) {
		showMediaController(CustomMediaController.DEFAULT_VISIBILITY_TIMEOUT);
	}

	private void showMediaController(int timeout) {
		if (mMediaController != null) {
			if (!mMediaController.isShowing() || timeout <= 0) {
				mMediaController.show(timeout);
			} else {
				mMediaController.refreshShowTimeout();
			}
		}
	}

	private void makeMediaItemsVisible(boolean mediaControllerIsShowing) {
		// make sure the text view is visible above the playback bar
		Resources res = getResources();
		int mediaControllerHeight = res.getDimensionPixelSize(R.dimen.media_controller_height);
		if (mCurrentFrameContainer != null && mCurrentFrameContainer.mImagePath != null) {
			AutoResizeTextView textView = (AutoResizeTextView) findViewById(R.id.text_playback);
			RelativeLayout.LayoutParams textLayout = (RelativeLayout.LayoutParams) textView.getLayoutParams();
			int textPadding = res.getDimensionPixelSize(R.dimen.playback_text_padding);
			textLayout.setMargins(0, 0, 0, (mediaControllerIsShowing ? mediaControllerHeight : textPadding));
			textView.setLayoutParams(textLayout);
		}
	}

	private void pauseMediaController() {
		mMediaPlayerController.pause();
		showMediaController(-1); // to keep on showing until done here
		UIUtilities.releaseKeepScreenOn(getWindow());
	}

	private FrameMediaContainer getMediaContainer(int narrativePlaybackPosition, boolean updatePlaybackPosition) {
		mIsLoading = true;
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
				currentAudioItem = container.mAudioPaths.get(i);
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

		FileInputStream playerInputStream = null;
		mSilenceFilePlaying = false;
		boolean dataLoaded = false;
		int dataLoadingErrorCount = 0;
		while (!dataLoaded && dataLoadingErrorCount <= 2) {
			try {
				mMediaPlayer.reset();
				if (currentAudioItem == null || (!(new File(currentAudioItem).exists()))) {
					mSilenceFilePlaying = true;
					if (mSilenceFileDescriptor == null) {
						mSilenceFileDescriptor = res.openRawResourceFd(R.raw.silence_100ms);
					}
					mMediaPlayer.setDataSource(mSilenceFileDescriptor.getFileDescriptor(),
							mSilenceFileDescriptor.getStartOffset(), mSilenceFileDescriptor.getDeclaredLength());
				} else {
					// can't play from data directory (they're private; permissions don't work), must use an input
					// stream - original was: mMediaPlayer.setDataSource(currentAudioItem);
					playerInputStream = new FileInputStream(new File(currentAudioItem));
					mMediaPlayer.setDataSource(playerInputStream.getFD());
				}
				dataLoaded = true;
			} catch (Throwable t) {
				// sometimes setDataSource fails for mysterious reasons - loop to open it, rather than failing
				dataLoaded = false;
				dataLoadingErrorCount += 1;
			} finally {
				IOUtilities.closeStream(playerInputStream);
			}
		}

		try {
			if (dataLoaded) {
				mMediaPlayer.setLooping(false);
				mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
				mMediaPlayer.setOnPreparedListener(mMediaPlayerPreparedListener);
				// mMediaPlayer.setOnCompletionListener(mMediaPlayerCompletionListener); // done later - better pausing
				mMediaPlayer.setOnErrorListener(mMediaPlayerErrorListener);
				mMediaPlayer.prepareAsync();
			} else {
				throw new IllegalStateException();
			}
		} catch (Throwable t) {
			UIUtilities.showToast(NarrativeViewerActivity.this, R.string.error_loading_narrative_player);
			finish();
			return;
		}

		// load the image
		ImageView photoDisplay = (ImageView) findViewById(R.id.image_playback);
		if (container.mImagePath != null && new File(container.mImagePath).exists()) {
			Bitmap scaledBitmap = BitmapUtilities.loadAndCreateScaledBitmap(container.mImagePath,
					photoDisplay.getWidth(), photoDisplay.getHeight(), BitmapUtilities.ScalingLogic.FIT, true);
			photoDisplay.setImageBitmap(scaledBitmap);
			photoDisplay.setScaleType(ScaleType.CENTER_INSIDE);
		} else if (TextUtils.isEmpty(container.mTextContent)) { // no text and no image: audio icon
			if (mAudioPictureBitmap == null) {
				mAudioPictureBitmap = SVGParser.getSVGFromResource(res, R.raw.ic_audio_playback).getBitmap(
						photoDisplay.getWidth(), photoDisplay.getHeight());
			}
			photoDisplay.setImageBitmap(mAudioPictureBitmap);
			photoDisplay.setScaleType(ScaleType.FIT_CENTER);
		} else {
			photoDisplay.setImageDrawable(null);
		}

		// load the text
		AutoResizeTextView textView = (AutoResizeTextView) findViewById(R.id.text_playback);
		if (!TextUtils.isEmpty(container.mTextContent)) {
			textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimensionPixelSize(R.dimen.playback_text_size));
			textView.setText(container.mTextContent);
			RelativeLayout.LayoutParams textLayout = new RelativeLayout.LayoutParams(
					RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
			textLayout.addRule(RelativeLayout.CENTER_HORIZONTAL);
			int textViewHeight = res.getDimensionPixelSize(R.dimen.media_controller_height);
			int textViewPadding = res.getDimensionPixelSize(R.dimen.playback_text_padding);
			if (container.mImagePath != null) {
				textView.setMaxHeight(res.getDimensionPixelSize(R.dimen.playback_maximum_text_height_with_image));
				textLayout.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
				textLayout.setMargins(0, 0, 0, (mMediaController.isShowing() ? textViewHeight : textViewPadding));
				textView.setBackgroundResource(R.drawable.rounded_playback_text);
				textView.setTextColor(res.getColor(R.color.icon_text_with_image));
			} else {
				textView.setMaxHeight(photoDisplay.getHeight()); // no way to clear, so set to parent height
				textLayout.addRule(RelativeLayout.CENTER_VERTICAL);
				textLayout.setMargins(0, 0, 0, textViewPadding);
				textView.setBackgroundColor(res.getColor(android.R.color.transparent));
				textView.setTextColor(res.getColor(R.color.icon_text_no_image));
			}
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
			mPlaybackPauseTime = -1;
			if (mPlaybackPosition < 0) { // so we return to the start when playing from the end
				mPlaybackPosition = 0;
				mInitialPlaybackOffset = 0;
				mNonAudioOffset = 0;
				mCurrentFrameContainer = getMediaContainer(mPlaybackPosition, true);
				prepareMediaItems(mCurrentFrameContainer);
			} else {
				if (mMediaPlayer != null && mSoundPool != null) {
					mMediaPlayer.setOnCompletionListener(mMediaPlayerCompletionListener);
					mPlaybackStartTime = System.currentTimeMillis() - mMediaPlayer.getCurrentPosition();
					mMediaPlayer.start();
					mSoundPool.autoResume(); // TODO: check this works
					showMediaController(CustomMediaController.DEFAULT_VISIBILITY_TIMEOUT);
				} else {
					UIUtilities.showToast(NarrativeViewerActivity.this, R.string.error_loading_narrative_player);
					finish();
					return;
				}
			}
			UIUtilities.acquireKeepScreenOn(getWindow());
		}

		@Override
		public void pause() {
			mIsLoading = false;
			if (mPlaybackPauseTime < 0) { // save the time paused, but don't overwrite if we call pause multiple times
				mPlaybackPauseTime = System.currentTimeMillis();
			}
			if (mMediaPlayer != null) {
				mMediaPlayer.setOnCompletionListener(null); // make sure we don't continue accidentally
				mMediaPlayer.pause();
			}
			if (mSoundPool != null) {
				mSoundPool.autoPause(); // TODO: check this works
			}
			showMediaController(-1); // to keep on showing until done here
			UIUtilities.releaseKeepScreenOn(getWindow());
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
				int rootPlaybackPosition = mPlaybackPosition + mNonAudioOffset;
				if (mSilenceFilePlaying) {
					// must calculate the actual time at the point of pausing, rather than the current time
					if (mPlaybackPauseTime > 0) {
						rootPlaybackPosition += (int) (mPlaybackPauseTime - mPlaybackStartTime);
					} else {
						rootPlaybackPosition += (int) (System.currentTimeMillis() - mPlaybackStartTime);
					}
				} else {
					rootPlaybackPosition += (mMediaPlayer != null ? mMediaPlayer.getCurrentPosition() : 0);
				}
				return rootPlaybackPosition;
			}
		}

		@Override
		public void seekTo(int pos) {
			// TODO: seek others (is it even possible with soundpool?)
			int actualPos = pos - mPlaybackPosition;
			if (mPlaybackPosition < 0) { // so we allow seeking from the end
				mPlaybackPosition = mNarrativeDuration - mCurrentFrameContainer.mFrameMaxDuration;
			}
			mPlaybackPauseTime = -1; // we'll be playing after this call
			if (actualPos >= 0 && actualPos < mCurrentFrameContainer.mFrameMaxDuration) {
				if (mIsLoading
						|| (actualPos < mMediaPlayer.getDuration() && mCurrentFrameContainer.mAudioPaths.size() > 0)) {
					if (!mIsLoading) {
						if (mMediaController.isDragging()) {
							mMediaPlayer.setOnCompletionListener(mMediaPlayerCompletionListener);
						}
						mPlaybackStartTime = System.currentTimeMillis() - actualPos;
						mMediaPlayer.seekTo(actualPos);
						if (!mMediaPlayer.isPlaying()) { // we started from the end
							mMediaPlayer.start();
							UIUtilities.acquireKeepScreenOn(getWindow());
						}
					} else {
						// still loading - come here so we don't reload the same item again
					}
				} else {
					// for image- or text-only frames
					mNonAudioOffset = actualPos;
					if (mMediaController.isDragging()) {
						mMediaPlayer.setOnCompletionListener(mMediaPlayerCompletionListener);
					}
					mPlaybackStartTime = System.currentTimeMillis();
					mMediaPlayer.seekTo(0);
					mMediaPlayer.start();
					mMediaController.setProgress();
				}
			} else if (pos >= 0 && pos < mNarrativeDuration) {
				FrameMediaContainer newContainer = getMediaContainer(pos, true);
				if (newContainer != mCurrentFrameContainer) {
					mCurrentFrameContainer = newContainer;
					prepareMediaItems(mCurrentFrameContainer);
					mInitialPlaybackOffset = pos - mPlaybackPosition;
				} else {
					mIsLoading = false;
				}
			}
		}

		@Override
		public boolean isPlaying() {
			return mMediaPlayer == null ? false : mMediaPlayer.isPlaying();
		}

		@Override
		public boolean isLoading() {
			return mIsLoading;
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

		@Override
		public void onControllerVisibilityChange(boolean visible) {
			makeMediaItemsVisible(visible);
		}
	};

	private void startPlayers() {
		// so that we don't start playing after pause if we were loading
		if (mIsLoading) {
			for (Integer soundId : mFrameSounds) {
				mSoundPool.play(soundId, 1, 1, 1, 0, 1f); // volume is % of *current*, rather than maximum
				// TODO: seek to mInitialPlaybackOffset
			}

			mMediaPlayer.setOnCompletionListener(mMediaPlayerCompletionListener);
			mPlaybackStartTime = System.currentTimeMillis() - mInitialPlaybackOffset;
			mMediaPlayer.seekTo(mInitialPlaybackOffset);
			mMediaPlayer.start();

			mIsLoading = false;
			mMediaController.setMediaPlayer(mMediaPlayerController);

			UIUtilities.acquireKeepScreenOn(getWindow());
		}
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
				mCurrentFrameContainer = getMediaContainer(mPlaybackPosition, false);
				prepareMediaItems(mCurrentFrameContainer);
				mMediaPlayerError = false;
				return;
			}
			mInitialPlaybackOffset = 0;
			int currentPosition = mMediaPlayerController.getCurrentPosition()
					+ (mMediaPlayer.getDuration() - mMediaPlayer.getCurrentPosition()) + 1;
			if (currentPosition < mNarrativeDuration) {
				mMediaPlayerController.seekTo(currentPosition);
			} else if (!mMediaController.isDragging()) {
				// move to just before the end (accounting for mNarrativeDuration errors)
				mMediaPlayerController.seekTo(currentPosition - 2);
				pauseMediaController(); // will also show the controller if applicable
				mPlaybackPosition = -1; // so we start from the beginning
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
