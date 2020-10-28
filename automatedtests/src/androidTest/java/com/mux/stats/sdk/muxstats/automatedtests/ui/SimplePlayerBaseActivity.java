package com.mux.stats.sdk.muxstats.automatedtests.ui;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.PlaybackPreparer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.PlayerNotificationManager;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.mux.stats.sdk.core.model.CustomerPlayerData;
import com.mux.stats.sdk.core.model.CustomerVideoData;
import com.mux.stats.sdk.muxstats.automatedtests.BuildConfig;
import com.mux.stats.sdk.muxstats.MuxStatsExoPlayer;
import com.mux.stats.sdk.muxstats.automatedtests.R;
import com.mux.stats.sdk.muxstats.automatedtests.mockup.MockNetworkRequest;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class SimplePlayerBaseActivity extends AppCompatActivity implements
        PlaybackPreparer, Player.EventListener {

    static final String TAG = "SimplePlayerActivity";

    public static final String PLAYBACK_URL_KEY = "playback_url";
    public static final int PLAY_AUDIO_SAMPLE = 0;
    private static final String PLAYBACK_CHANNEL_ID = "playback_channel";
    private static final int PLAYBACK_NOTIFICATION_ID = 1;
    private static final String ARG_URI = "uri_string";
    private static final String ARG_TITLE = "title";
    private static final String ARG_START_POSITION = "start_position";

    PlayerView playerView;
    SimpleExoPlayer player;
    MediaSource testMediaSource;
    MuxStatsExoPlayer muxStats;
    MockNetworkRequest mockNetwork;
    AtomicBoolean onResumedCalled = new AtomicBoolean(false);
    PlayerNotificationManager notificationManager;
    MediaSessionCompat mediaSessionCompat;
    MediaSessionConnector mediaSessionConnector;

    Lock activityLock = new ReentrantLock();
    Condition playbackEnded = activityLock.newCondition();
    Condition playbackStarted = activityLock.newCondition();
    Condition playbackBuffering = activityLock.newCondition();
    Condition activityClosed = activityLock.newCondition();
    Condition activityInitialized = activityLock.newCondition();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_player_test);

        disableUserActions();

        playerView = findViewById(R.id.player_view);
        playerView.setPlaybackPreparer(this);

        initExoPlayer();
        player.addListener(this);
        playerView.setPlayer(player);

        // Do not hide controlls
        playerView.setControllerShowTimeoutMs(0);
        playerView.setControllerHideOnTouch(false);

        // Setup notification and media session.
        initAudioSession();

        initMuxSats();
        Intent i = getIntent();
        Bundle extra = i.getExtras();
        String url_to_play = "http://localhost:5000/vod.mp4";
        if (extra != null &&
                extra.containsKey(PLAYBACK_URL_KEY)) {
            switch(extra.getInt(PLAYBACK_URL_KEY)) {
                case PLAY_AUDIO_SAMPLE:
                    url_to_play = "http://localhost:5000/audio.aac";
                    break;
                default:
                    url_to_play = "http://localhost:5000/vod.mp4";
                    break;
            }
        }
        Uri testUri = Uri.parse(url_to_play);
        testMediaSource = new ExtractorMediaSource.Factory(
                new DefaultDataSourceFactory(this, "Test"))
                .createMediaSource(testUri);
        player.setPlayWhenReady(true);
        player.prepare(testMediaSource, false, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        onResumedCalled.set(true);
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        signalActivityClosed();
        muxStats.release();
    }

    public abstract void initExoPlayer();

    public void initAudioSession() {
        notificationManager = PlayerNotificationManager.createWithNotificationChannel(
                getApplicationContext(),
                PLAYBACK_CHANNEL_ID,
                R.string.channel_name,
                R.string.channel_description,
                PLAYBACK_NOTIFICATION_ID,
                new MDAdapter(),
                new CustomNotificationListener()
        );
        notificationManager.setUseNavigationActions(false);
        notificationManager.setUseStopAction(true);
        notificationManager.setPlayer(player);

        mediaSessionCompat = new MediaSessionCompat(this, "hello_world_media");
        notificationManager.setMediaSessionToken(mediaSessionCompat.getSessionToken());
        mediaSessionConnector = new MediaSessionConnector(mediaSessionCompat);
        mediaSessionConnector.setPlayer(player);
    }

    public void initMuxSats() {
        // Mux details
        CustomerPlayerData customerPlayerData = new CustomerPlayerData();
        if (BuildConfig.SHOULD_REPORT_INSTRUMENTATION_TEST_EVENTS_TO_SERVER) {
            customerPlayerData.setEnvironmentKey(BuildConfig.INSTRUMENTATION_TEST_ENVIRONMENT_KEY);
        } else {
            customerPlayerData.setEnvironmentKey("YOUR_ENVIRONMENT_KEY");
        }
        CustomerVideoData customerVideoData = new CustomerVideoData();
        customerVideoData.setVideoTitle("Test video");
        mockNetwork = new MockNetworkRequest();
        muxStats = new MuxStatsExoPlayer(
                this, player, "demo-player", customerPlayerData, customerVideoData,
                null,true, mockNetwork);
        Point size = new Point();
        getWindowManager().getDefaultDisplay().getSize(size);
        muxStats.setScreenSize(size.x, size.y);
        muxStats.setPlayerView(playerView);
        muxStats.enableMuxCoreDebug(true, false);
    }

    public MediaSource getTestMediaSource() {
        return testMediaSource;
    }

    public PlayerView getPlayerView() {
        return playerView;
    }

    public MockNetworkRequest getMockNetwork() {
        return mockNetwork;
    }

    public void waitForPlaybackToFinish() {
        try {
            activityLock.lock();
            playbackEnded.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            activityLock.unlock();
        }
    }

    public void waitForActivityToInitialize() {
        if (!onResumedCalled.get()) {
            try {
                activityLock.lock();
                activityInitialized.await();
                activityLock.unlock();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean waitForPlaybackToStart(long timeoutInMs) {
        try {
            activityLock.lock();
            return playbackStarted.await(timeoutInMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        } finally {
            activityLock.unlock();
        }
    }

    public void waitForPlaybackToStartBuffering() {
        if (player.getPlaybackState() == Player.STATE_READY &&
            player.getPlayWhenReady()) {
            try {
                activityLock.lock();
                playbackBuffering.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                activityLock.unlock();
            }
        }
    }

    public void waitForActivityToClose() {
        try {
            activityLock.lock();
            activityClosed.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            activityLock.unlock();
        }
    }

    public void signalPlaybackStarted() {
        activityLock.lock();
        playbackStarted.signalAll();
        activityLock.unlock();
    }

    public void signalPlaybackBuffering() {
        activityLock.lock();
        playbackBuffering.signalAll();
        activityLock.unlock();
    }

    public void signalPlaybackEnded() {
        activityLock.lock();
        playbackEnded.signalAll();
        activityLock.unlock();
    }

    public void signalActivityClosed() {
        activityLock.lock();
        activityClosed.signalAll();
        activityLock.unlock();
    }

    private void disableUserActions() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    private void enableUserActions() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    ///////////////////////////////////////////////////////////////////////
    ///////// PlaybackPreparer ////////////////////////////////////////////

    @Override
    public void preparePlayback() {
//        player.retry();
    }

    //////////////////////////////////////////////////////////////////////
    ////// Player.EventListener //////////////////////////////////////////

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {

    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

    }

    @Override
    public void onLoadingChanged(boolean isLoading) {

    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        switch (playbackState) {
            case Player.STATE_BUFFERING:
                signalPlaybackBuffering();
                break;
            case Player.STATE_ENDED:
                signalPlaybackEnded();
                break;
            case Player.STATE_READY:
                // By the time we get here, it depends on playWhenReady to know if we're playing
                if (playWhenReady) {
                    signalPlaybackStarted();
                } else {
                    // TODO implement this
//                    signalPlaybackPaused();
                }
                break;
        }
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        activityLock.lock();
        activityInitialized.signalAll();
        activityLock.unlock();
    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {

    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        Log.e(TAG, error.getMessage());
        error.printStackTrace();
    }

    @Override
    public void onPositionDiscontinuity(int reason) {

    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

    }

    @Override
    public void onSeekProcessed() {

    }

    class CustomNotificationListener implements PlayerNotificationManager.NotificationListener {

        @Override
        public void onNotificationCancelled(int notificationId, boolean dismissedByUser) {
            // TODO implement this
            Log.e(TAG, "onNotificationCancelled");
        }

        @Override
        public void onNotificationPosted(int notificationId, Notification notification, boolean ongoing) {
            // TODO implement this
            Log.e(TAG, "onNotificationPosted");
        }
    }

    class MDAdapter implements PlayerNotificationManager.MediaDescriptionAdapter {

        @Override
        public String getCurrentContentTitle(Player player) {
            return "Tittle";
        }

        @Nullable
        @Override
        public PendingIntent createCurrentContentIntent(Player player) {
            return PendingIntent.getActivity(
                    getApplicationContext(),
                    0,
                    new Intent(getApplicationContext(), SimplePlayerBaseActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT
            );
        }

        @Nullable
        @Override
        public String getCurrentContentText(Player player) {
            return "Automated test playback";
        }

        @Nullable
        @Override
        public Bitmap getCurrentLargeIcon(Player player, PlayerNotificationManager.BitmapCallback callback) {
            return getBitmapFromVectorDrawable(R.drawable.ic_launcher_foreground);
        }

        @MainThread
        private Bitmap getBitmapFromVectorDrawable(int drawableId) {
            Drawable drawable = ContextCompat.getDrawable(getApplicationContext(), drawableId);
            DrawableCompat.wrap(drawable).mutate();
            Bitmap bmp = Bitmap.createBitmap(
                    drawable.getIntrinsicWidth(),
                    drawable.getIntrinsicHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas cnvs = new Canvas(bmp);
            drawable.setBounds(0, 0, cnvs.getWidth(), cnvs.getHeight());
            drawable.draw(cnvs);
            return bmp;
        }
    }
}