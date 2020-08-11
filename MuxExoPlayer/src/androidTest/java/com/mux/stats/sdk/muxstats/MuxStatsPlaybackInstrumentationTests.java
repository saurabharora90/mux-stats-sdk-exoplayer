package com.mux.stats.sdk.muxstats;

import android.util.Log;
import android.view.View;

import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.mux.stats.sdk.muxstats.mockup.http.SimpleHTTPServer;
import com.mux.stats.sdk.muxstats.ui.SimplePlayerTestActivity;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static org.junit.Assert.assertTrue;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class MuxStatsPlaybackInstrumentationTests {

    public static final String TAG = "LateInitTest";

    static final int PLAY_PERIOD_IN_MS = 10000;
    static final int PAUSE_PERIOD_IN_MS = 5000;

    @Rule
    public ActivityTestRule<SimplePlayerTestActivity> activityTestRule;


    SimplePlayerTestActivity testActivity;
    private SimpleHTTPServer httpServer;
    // 2 mega bits per second
    private int bandwithLimitInBitsPerSecond = 2000000;
    private int runHttpServerOnPort = 5000;


    @Before
    public void init(){
        try {
            httpServer = new SimpleHTTPServer(runHttpServerOnPort, bandwithLimitInBitsPerSecond);
        } catch (IOException e) {
            e.printStackTrace();
            // Failed to start server
            assertTrue(false);
        }
        activityTestRule = new ActivityTestRule<SimplePlayerTestActivity>(
                SimplePlayerTestActivity.class,
                true,
                false);
        activityTestRule.launchActivity(null);
        testActivity = activityTestRule.getActivity();
    }

    /*
     * According to the self validation guid: https://docs.google.com/document/d/1FU_09N3Cg9xfh784edBJpgg3YVhzBA6-bd5XHLK7IK4/edit#
     * We are implementing vod playback scenario.
     */
    @Test
    public void testVodPlayback() {
        try {
            testActivity.waitForPlaybackToStart();
            // play x seconds
            Thread.sleep(PLAY_PERIOD_IN_MS);
            PlayerView pView = testActivity.getPlayerView();
            PlayerControlView controlView = pView.findViewById(R.id.exo_controller);
            final View pauseButton = controlView.findViewById(R.id.exo_pause);
            final View playButton = controlView.findViewById(R.id.exo_play);
            // Pause video
            testActivity.runOnUiThread(new Runnable(){
                public void run() {
                    pauseButton.performClick();
                }
            });
            // Resume video
            Thread.sleep(PAUSE_PERIOD_IN_MS);
            testActivity.runOnUiThread(new Runnable(){
                public void run() {
                    playButton.performClick();
                }
            });
            // Play another x seconds
            Thread.sleep(PLAY_PERIOD_IN_MS);
            // Seek backward
            long currentPlaybackPosition = pView.getPlayer().getCurrentPosition();
            pView.getPlayer().seekTo(currentPlaybackPosition/2);
            // Play another x seconds
            Thread.sleep(PLAY_PERIOD_IN_MS);
            // eek forward in the video
            currentPlaybackPosition = pView.getPlayer().getCurrentPosition();
            long videoDuration = pView.getPlayer().getDuration();
            long seekToInFuture = currentPlaybackPosition + ((videoDuration - currentPlaybackPosition) / 2);
            pView.getPlayer().seekTo(seekToInFuture);
            // Play another x seconds
            Thread.sleep(PLAY_PERIOD_IN_MS);
            // Exit the player with back button
            testActivity.runOnUiThread(new Runnable(){
                public void run() {
                    testActivity.onBackPressed();
                }
            });
        } catch (InterruptedException e) {
            e.printStackTrace();
            // fail test
            assertTrue(false);
        }
        Log.e(TAG, "All done !!!");
    }
}