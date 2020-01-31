// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.features.start_surface;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.doesNotExist;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withContentDescription;
import static android.support.test.espresso.matcher.ViewMatchers.withId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import static org.chromium.base.test.util.CallbackHelper.WAIT_TIMEOUT_SECONDS;
import static org.chromium.chrome.browser.tasks.tab_management.TabUiTestHelper.verifyTabModelTabCount;
import static org.chromium.chrome.browser.tasks.tab_management.TabUiTestHelper.verifyTabSwitcherCardCount;
import static org.chromium.chrome.browser.util.UrlConstants.NTP_URL;
import static org.chromium.content_public.browser.test.util.CriteriaHelper.DEFAULT_MAX_TIME_TO_POLL;
import static org.chromium.content_public.browser.test.util.CriteriaHelper.DEFAULT_POLLING_INTERVAL;

import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.NoMatchingViewException;
import android.support.test.espresso.ViewAssertion;
import android.support.test.espresso.contrib.RecyclerViewActions;
import android.support.test.filters.MediumTest;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import org.chromium.base.Callback;
import org.chromium.base.ContextUtils;
import org.chromium.base.GarbageCollectionTestUtils;
import org.chromium.base.metrics.RecordHistogram;
import org.chromium.base.test.util.CommandLineFlags;
import org.chromium.base.test.util.DisableIf;
import org.chromium.base.test.util.DisabledTest;
import org.chromium.base.test.util.Feature;
import org.chromium.base.test.util.MinAndroidSdkLevel;
import org.chromium.base.test.util.Restriction;
import org.chromium.chrome.browser.ChromeFeatureList;
import org.chromium.chrome.browser.ChromeSwitches;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.compositor.layouts.Layout;
import org.chromium.chrome.browser.compositor.layouts.content.TabContentManager;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tabmodel.TabSelectionType;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tasks.tab_management.TabSwitcher;
import org.chromium.chrome.browser.tasks.tab_management.TabUiTestHelper;
import org.chromium.chrome.browser.util.FeatureUtilities;
import org.chromium.chrome.tab_ui.R;
import org.chromium.chrome.test.ChromeJUnit4ClassRunner;
import org.chromium.chrome.test.ChromeTabbedActivityTestRule;
import org.chromium.chrome.test.util.ApplicationTestUtils;
import org.chromium.chrome.test.util.ChromeTabUtils;
import org.chromium.chrome.test.util.MenuUtils;
import org.chromium.chrome.test.util.browser.Features;
import org.chromium.content_public.browser.test.util.Criteria;
import org.chromium.content_public.browser.test.util.CriteriaHelper;
import org.chromium.content_public.browser.test.util.TestThreadUtils;
import org.chromium.content_public.browser.test.util.WebContentsUtils;
import org.chromium.net.test.EmbeddedTestServer;
import org.chromium.ui.base.PageTransition;
import org.chromium.ui.test.util.UiRestriction;
import org.chromium.ui.widget.ViewLookupCachingFrameLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;

/** Tests for the {@link StartSurfaceLayout} */
@RunWith(ChromeJUnit4ClassRunner.class)
@CommandLineFlags.Add({ChromeSwitches.DISABLE_FIRST_RUN_EXPERIENCE,
        "enable-features=" + ChromeFeatureList.TAB_GRID_LAYOUT_ANDROID + "<Study",
        "force-fieldtrials=Study/Group"})
@Restriction(UiRestriction.RESTRICTION_TYPE_PHONE)
public class StartSurfaceLayoutTest {
    private static final String BASE_PARAMS = "force-fieldtrial-params="
            + "Study.Group:soft-cleanup-delay/0/cleanup-delay/0/skip-slow-zooming/false"
            + "/zooming-min-sdk-version/19/zooming-min-memory-mb/512";

    @Rule
    public ChromeTabbedActivityTestRule mActivityTestRule = new ChromeTabbedActivityTestRule();

    @Rule
    public TestRule mProcessor = new Features.InstrumentationProcessor();

    private StartSurfaceLayout mStartSurfaceLayout;
    private String mUrl;
    private int mRepeat;
    private List<WeakReference<Bitmap>> mAllBitmaps = new LinkedList<>();
    private Callback<Bitmap> mBitmapListener =
            (bitmap) -> mAllBitmaps.add(new WeakReference<>(bitmap));
    private TabSwitcher.TabListDelegate mTabListDelegate;

    @Before
    public void setUp() {
        FeatureUtilities.setGridTabSwitcherEnabledForTesting(true);

        EmbeddedTestServer testServer =
                EmbeddedTestServer.createAndStartServer(InstrumentationRegistry.getContext());
        mActivityTestRule.startMainActivityFromLauncher();

        Layout layout = mActivityTestRule.getActivity().getLayoutManager().getOverviewLayout();
        assertTrue(layout instanceof StartSurfaceLayout);
        mStartSurfaceLayout = (StartSurfaceLayout) layout;
        mUrl = testServer.getURL("/chrome/test/data/android/navigate/simple.html");
        mRepeat = 3;

        mTabListDelegate = mStartSurfaceLayout.getStartSurfaceForTesting().getTabListDelegate();
        mTabListDelegate.setBitmapCallbackForTesting(mBitmapListener);
        Assert.assertEquals(0, mTabListDelegate.getBitmapFetchCountForTesting());

        mActivityTestRule.getActivity().getTabContentManager().setCaptureMinRequestTimeForTesting(
                0);

        CriteriaHelper.pollUiThread(Criteria.equals(true,
                mActivityTestRule.getActivity()
                        .getTabModelSelector()
                        .getTabModelFilterProvider()
                        .getCurrentTabModelFilter()::isTabModelRestored));
    }

    @Test
    @MediumTest
    // clang-format off
    @Features.DisableFeatures(ChromeFeatureList.TAB_TO_GTS_ANIMATION)
    @CommandLineFlags.Add({BASE_PARAMS})
    public void testTabToGridFromLiveTab() throws InterruptedException {
        // clang-format on
        TabSwitcher.TabListDelegate delegate =
                mStartSurfaceLayout.getStartSurfaceForTesting().getTabListDelegate();
        assertEquals(0, delegate.getSoftCleanupDelayForTesting());
        assertEquals(0, delegate.getCleanupDelayForTesting());

        prepareTabs(2, NTP_URL);
        testTabToGrid(mUrl);
        assertThumbnailsAreReleased();
    }

    @Test
    @MediumTest
    // clang-format off
    @Features.EnableFeatures(ChromeFeatureList.TAB_TO_GTS_ANIMATION + "<Study")
    @CommandLineFlags.Add({BASE_PARAMS})
    @MinAndroidSdkLevel(Build.VERSION_CODES.LOLLIPOP)
    @DisabledTest(message = "crbug.com/991852 This test is flaky")
    public void testTabToGridFromLiveTabAnimation() throws InterruptedException {
        // clang-format on
        assertTrue(FeatureUtilities.isTabToGtsAnimationEnabled());

        prepareTabs(2, NTP_URL);
        testTabToGrid(mUrl);
        assertThumbnailsAreReleased();
    }

    @Test
    @MediumTest
    // clang-format off
    @Features.DisableFeatures(ChromeFeatureList.TAB_TO_GTS_ANIMATION)
    @CommandLineFlags.Add({BASE_PARAMS + "/soft-cleanup-delay/10000/cleanup-delay/10000"})
    public void testTabToGridFromLiveTabWarm() throws InterruptedException {
        // clang-format on
        TabSwitcher.TabListDelegate delegate =
                mStartSurfaceLayout.getStartSurfaceForTesting().getTabListDelegate();
        assertEquals(10000, delegate.getSoftCleanupDelayForTesting());
        assertEquals(10000, delegate.getCleanupDelayForTesting());

        prepareTabs(2, NTP_URL);
        testTabToGrid(mUrl);
    }

    @Test
    @MediumTest
    // clang-format off
    @Features.EnableFeatures(ChromeFeatureList.TAB_TO_GTS_ANIMATION + "<Study")
    @CommandLineFlags.Add({BASE_PARAMS + "/soft-cleanup-delay/10000/cleanup-delay/10000"})
    @MinAndroidSdkLevel(Build.VERSION_CODES.M) // TODO(crbug.com/997065#c8): remove SDK restriction.
    public void testTabToGridFromLiveTabWarmAnimation() throws InterruptedException {
        // clang-format on
        prepareTabs(2, NTP_URL);
        testTabToGrid(mUrl);
    }

    @Test
    @MediumTest
    // clang-format off
    @Features.DisableFeatures(ChromeFeatureList.TAB_TO_GTS_ANIMATION)
    @CommandLineFlags.Add({BASE_PARAMS + "/cleanup-delay/10000"})
    public void testTabToGridFromLiveTabSoft() throws InterruptedException {
        // clang-format on
        prepareTabs(2, NTP_URL);
        testTabToGrid(mUrl);
    }

    @Test
    @MediumTest
    // clang-format off
    @Features.EnableFeatures(ChromeFeatureList.TAB_TO_GTS_ANIMATION + "<Study")
    @CommandLineFlags.Add({BASE_PARAMS + "/cleanup-delay/10000"})
    @MinAndroidSdkLevel(Build.VERSION_CODES.M) // TODO(crbug.com/997065#c8): remove SDK restriction.
    public void testTabToGridFromLiveTabSoftAnimation() throws InterruptedException {
        // clang-format on
        prepareTabs(2, NTP_URL);
        testTabToGrid(mUrl);
    }

    @Test
    @MediumTest
    @CommandLineFlags.Add({BASE_PARAMS})
    public void testTabToGridFromNtp() throws InterruptedException {
        prepareTabs(2, NTP_URL);
        testTabToGrid(NTP_URL);
        assertThumbnailsAreReleased();
    }

    /**
     * Make Chrome have {@code numTabs} or Tabs with {@code url} loaded.
     * @see #prepareTabs(int, String, boolean)
     */
    private void prepareTabs(int numTabs, @Nullable String url) {
        prepareTabs(numTabs, url, true);
    }

    /**
     * Make Chrome have {@code numTabs} or Tabs with {@code url} loaded.
     * @param numTabs The number of tabs we expect after finishing
     * @param url The URL to load. Skip loading when null, but the thumbnail for the NTP might not
     *            be saved.
     * @param waitForLoading Whether wait for URL loading.
     */
    private void prepareTabs(int numTabs, @Nullable String url, boolean waitForLoading) {
        assertTrue(numTabs >= 1);
        assertEquals(1, mActivityTestRule.getActivity().getTabModelSelector().getTotalTabCount());

        if (url != null) mActivityTestRule.loadUrl(url);
        for (int i = 0; i < numTabs - 1; i++) {
            MenuUtils.invokeCustomMenuActionSync(InstrumentationRegistry.getInstrumentation(),
                    mActivityTestRule.getActivity(), org.chromium.chrome.R.id.new_tab_menu_id);
            if (url != null) mActivityTestRule.loadUrl(url);
            if (!waitForLoading) continue;

            Tab previousTab = mActivityTestRule.getActivity()
                                      .getTabModelSelector()
                                      .getCurrentModel()
                                      .getTabAt(i);

            boolean fixPendingReadbacks = mActivityTestRule.getActivity()
                                                  .getTabContentManager()
                                                  .getPendingReadbacksForTesting()
                    != 0;

            // When there are pending readbacks due to detached Tabs, try to fix it by switching
            // back to that tab.
            if (fixPendingReadbacks) {
                int lastIndex = i;
                // clang-format off
                TestThreadUtils.runOnUiThreadBlocking(() ->
                    mActivityTestRule.getActivity().getCurrentTabModel().setIndex(
                            lastIndex, TabSelectionType.FROM_USER)
                );
                // clang-format on
            }
            checkThumbnailsExist(previousTab);
            if (fixPendingReadbacks) {
                int currentIndex = i + 1;
                // clang-format off
                TestThreadUtils.runOnUiThreadBlocking(() ->
                    mActivityTestRule.getActivity().getCurrentTabModel().setIndex(
                            currentIndex, TabSelectionType.FROM_USER)
                );
                // clang-format on
            }
        }
        ChromeTabUtils.waitForTabPageLoaded(mActivityTestRule.getActivity().getActivityTab(), null,
                null, WAIT_TIMEOUT_SECONDS * 10);
        assertEquals(
                numTabs, mActivityTestRule.getActivity().getTabModelSelector().getTotalTabCount());

        if (waitForLoading) {
            // clang-format off
            CriteriaHelper.pollUiThread(Criteria.equals(0, () ->
                mActivityTestRule.getActivity()
                        .getTabContentManager()
                        .getPendingReadbacksForTesting()
            ));
            // clang-format on
        }
    }

    private void testTabToGrid(String fromUrl) throws InterruptedException {
        mActivityTestRule.loadUrl(fromUrl);

        final int initCount = getCaptureCount();

        StartSurface startSurface = mStartSurfaceLayout.getStartSurfaceForTesting();
        for (int i = 0; i < mRepeat; i++) {
            enterGTS();

            TestThreadUtils.runOnUiThreadBlocking(
                    () -> { startSurface.getController().onBackPressed(); });
            // clang-format off
            CriteriaHelper.pollInstrumentationThread(
                    () -> !mActivityTestRule.getActivity().getLayoutManager().overviewVisible(),
                    "Overview not hidden yet", DEFAULT_MAX_TIME_TO_POLL * 10,
                    DEFAULT_POLLING_INTERVAL);
            // clang-format on
        }
        checkFinalCaptureCount(false, initCount);
    }

    @Test
    @MediumTest
    public void testGridToTabToCurrentNTP() throws InterruptedException {
        prepareTabs(1, NTP_URL);
        testGridToTab(false, false);
    }

    @Test
    @MediumTest
    public void testGridToTabToOtherNTP() throws InterruptedException {
        prepareTabs(2, NTP_URL);
        testGridToTab(true, false);
    }

    @Test
    @MediumTest
    @Features.DisableFeatures(ChromeFeatureList.TAB_TO_GTS_ANIMATION)
    public void testGridToTabToCurrentLive() throws InterruptedException {
        prepareTabs(1, mUrl);
        testGridToTab(false, false);
    }

    @Test
    @MediumTest
    @Features.DisableFeatures(ChromeFeatureList.TAB_TO_GTS_ANIMATION)
    @DisabledTest(message = "crbug.com/986047. This works on emulators but not on real devices.")
    public void testGridToTabToCurrentLiveDetached() throws Exception {
        for (int i = 0; i < 10; i++) {
            // Quickly create some tabs, navigate to web pages, and don't wait for thumbnail
            // capturing.
            prepareTabs(2, mUrl, false);
            // Hopefully we are in a state where some pending readbacks are stuck because their tab
            // is not attached to the view.
            if (mActivityTestRule.getActivity()
                            .getTabContentManager()
                            .getPendingReadbacksForTesting()
                    > 0) {
                break;
            }

            // Restart Chrome.
            // Although we're destroying the activity, the Application will still live on since its
            // in the same process as this test.
            TestThreadUtils.runOnUiThreadBlocking(
                    () -> mActivityTestRule.getActivity().getTabModelSelector().closeAllTabs());
            ApplicationTestUtils.finishActivity(mActivityTestRule.getActivity());
            mActivityTestRule.startMainActivityOnBlankPage();
            Assert.assertEquals(1, mActivityTestRule.tabsCount(false));
        }
        assertNotEquals(0,
                mActivityTestRule.getActivity()
                        .getTabContentManager()
                        .getPendingReadbacksForTesting());
        // The last tab should still get thumbnail even though readbacks for other tabs are stuck.
        testGridToTab(false, false);
    }

    @Test
    @MediumTest
    @Features.EnableFeatures(ChromeFeatureList.TAB_TO_GTS_ANIMATION + "<Study")
    @MinAndroidSdkLevel(Build.VERSION_CODES.LOLLIPOP)
    @DisabledTest(message = "crbug.com/993201 This test fails deterministically on Nexus 5X")
    public void testGridToTabToCurrentLiveWithAnimation() throws InterruptedException {
        prepareTabs(1, mUrl);
        testGridToTab(false, false);
    }

    @Test
    @MediumTest
    @Features.DisableFeatures(ChromeFeatureList.TAB_TO_GTS_ANIMATION)
    public void testGridToTabToOtherLive() throws InterruptedException {
        prepareTabs(2, mUrl);
        testGridToTab(true, false);
    }

    @Test
    @MediumTest
    @Features.EnableFeatures(ChromeFeatureList.TAB_TO_GTS_ANIMATION + "<Study")
    @MinAndroidSdkLevel(Build.VERSION_CODES.LOLLIPOP)
    @DisabledTest(message = "crbug.com/993201 This test fails deterministically on Nexus 5X")
    public void testGridToTabToOtherLiveWithAnimation() throws InterruptedException {
        prepareTabs(2, mUrl);
        testGridToTab(true, false);
    }

    @Test
    @MediumTest
    @Features.DisableFeatures(ChromeFeatureList.TAB_TO_GTS_ANIMATION)
    public void testGridToTabToOtherFrozen() throws InterruptedException {
        prepareTabs(2, mUrl);
        testGridToTab(true, true);
    }

    private void testGridToTab(boolean switchToAnotherTab, boolean killBeforeSwitching)
            throws InterruptedException {
        final int initCount = getCaptureCount();

        for (int i = 0; i < mRepeat; i++) {
            enterGTS();

            final int index = mActivityTestRule.getActivity().getCurrentTabModel().index();
            final int targetIndex = switchToAnotherTab ? 1 - index : index;
            Tab targetTab =
                    mActivityTestRule.getActivity().getCurrentTabModel().getTabAt(targetIndex);
            if (killBeforeSwitching) {
                WebContentsUtils.simulateRendererKilled(targetTab.getWebContents(), false);
            }

            if (switchToAnotherTab) {
                waitForCaptureRateControl();
            }
            int count = getCaptureCount();
            onView(withId(org.chromium.chrome.tab_ui.R.id.tab_list_view))
                    .perform(RecyclerViewActions.actionOnItemAtPosition(targetIndex, click()));
            CriteriaHelper.pollUiThread(() -> {
                boolean doneHiding =
                        !mActivityTestRule.getActivity().getLayoutManager().overviewVisible();
                if (!doneHiding) {
                    // Before overview hiding animation is done, the tab index should not change.
                    Assert.assertEquals(
                            index, mActivityTestRule.getActivity().getCurrentTabModel().index());
                }
                return doneHiding;
            }, "Overview not hidden yet");
            int delta;
            if (switchToAnotherTab
                    && !TextUtils.equals(mActivityTestRule.getActivity()
                                                 .getCurrentWebContents()
                                                 .getLastCommittedUrl(),
                            NTP_URL)) {
                // Capture the original tab.
                delta = 1;
            } else {
                delta = 0;
            }
            checkCaptureCount(delta, count);
        }
        checkFinalCaptureCount(switchToAnotherTab, initCount);
        assertThumbnailsAreReleased();
    }

    @Test
    @MediumTest
    @CommandLineFlags.Add({BASE_PARAMS})
    public void testRestoredTabsDontFetch() throws Exception {
        prepareTabs(2, mUrl);
        TabSwitcher.TabListDelegate delegate =
                mStartSurfaceLayout.getStartSurfaceForTesting().getTabListDelegate();
        int oldCount = delegate.getBitmapFetchCountForTesting();

        // Restart Chrome.
        // Although we're destroying the activity, the Application will still live on since its in
        // the same process as this test.
        ApplicationTestUtils.finishActivity(mActivityTestRule.getActivity());
        mActivityTestRule.startMainActivityOnBlankPage();
        Assert.assertEquals(3, mActivityTestRule.tabsCount(false));

        Layout layout = mActivityTestRule.getActivity().getLayoutManager().getOverviewLayout();
        assertTrue(layout instanceof StartSurfaceLayout);
        mStartSurfaceLayout = (StartSurfaceLayout) layout;
        Assert.assertEquals(0, delegate.getBitmapFetchCountForTesting() - oldCount);
    }

    @Test
    @MediumTest
    @CommandLineFlags.Add({BASE_PARAMS})
    public void testInvisibleTabsDontFetch() throws InterruptedException {
        // Open a few new tabs.
        final int count = mAllBitmaps.size();
        for (int i = 0; i < 3; i++) {
            MenuUtils.invokeCustomMenuActionSync(InstrumentationRegistry.getInstrumentation(),
                    mActivityTestRule.getActivity(), org.chromium.chrome.R.id.new_tab_menu_id);
        }
        // Fetching might not happen instantly.
        Thread.sleep(1000);

        // No fetching should happen.
        Assert.assertEquals(0, mAllBitmaps.size() - count);
    }

    @Test
    @MediumTest
    @CommandLineFlags.Add({BASE_PARAMS + "/soft-cleanup-delay/10000/cleanup-delay/10000"})
    public void testInvisibleTabsDontFetchWarm() throws InterruptedException {
        // Get the GTS in the warm state.
        prepareTabs(2, NTP_URL);
        mRepeat = 2;
        testTabToGrid(NTP_URL);

        Thread.sleep(1000);

        // Open a few new tabs.
        final int count = mAllBitmaps.size();
        for (int i = 0; i < 3; i++) {
            MenuUtils.invokeCustomMenuActionSync(InstrumentationRegistry.getInstrumentation(),
                    mActivityTestRule.getActivity(), org.chromium.chrome.R.id.new_tab_menu_id);
        }
        // Fetching might not happen instantly.
        Thread.sleep(1000);

        // No fetching should happen.
        Assert.assertEquals(0, mAllBitmaps.size() - count);
    }

    @Test
    @MediumTest
    @CommandLineFlags.Add({BASE_PARAMS + "/cleanup-delay/10000"})
    public void testInvisibleTabsDontFetchSoft() throws InterruptedException {
        // Get the GTS in the soft cleaned up state.
        prepareTabs(2, NTP_URL);
        mRepeat = 2;
        testTabToGrid(NTP_URL);

        Thread.sleep(1000);

        // Open a few new tabs.
        final int count = mAllBitmaps.size();
        for (int i = 0; i < 3; i++) {
            MenuUtils.invokeCustomMenuActionSync(InstrumentationRegistry.getInstrumentation(),
                    mActivityTestRule.getActivity(), org.chromium.chrome.R.id.new_tab_menu_id);
        }
        // Fetching might not happen instantly.
        Thread.sleep(1000);

        // No fetching should happen.
        Assert.assertEquals(0, mAllBitmaps.size() - count);
    }

    @Test
    @MediumTest
    @CommandLineFlags.Add({BASE_PARAMS})
    // clang-format off
    @DisabledTest(message = "http://crbug/1005865 - Test was previously flaky but only on bots."
            + "Was not locally reproducible. Disabling until verified that it's deflaked on bots.")
    public void testIncognitoEnterGts() {
        // clang-format on
        mActivityTestRule.newIncognitoTabFromMenu();
        TestThreadUtils.runOnUiThreadBlocking(
                () -> mActivityTestRule.getActivity().getLayoutManager().showOverview(false));
        CriteriaHelper.pollInstrumentationThread(
                () -> mActivityTestRule.getActivity().getLayoutManager().overviewVisible());

        onView(withId(org.chromium.chrome.tab_ui.R.id.tab_list_view))
                .check(TabCountAssertion.havingTabCount(1));

        onView(withId(org.chromium.chrome.tab_ui.R.id.tab_list_view))
                .perform(RecyclerViewActions.actionOnItemAtPosition(0, click()));
        CriteriaHelper.pollInstrumentationThread(
                () -> !mActivityTestRule.getActivity().getLayoutManager().overviewVisible());

        TestThreadUtils.runOnUiThreadBlocking(
                () -> mActivityTestRule.getActivity().getLayoutManager().showOverview(false));
        CriteriaHelper.pollInstrumentationThread(
                () -> mActivityTestRule.getActivity().getLayoutManager().overviewVisible());

        onView(withId(org.chromium.chrome.tab_ui.R.id.tab_list_view))
                .check(TabCountAssertion.havingTabCount(1));
    }

    @Test
    @MediumTest
    @Feature("NewTabTile")
    // clang-format off
    @Features.DisableFeatures({ChromeFeatureList.TAB_TO_GTS_ANIMATION,
            ChromeFeatureList.CLOSE_TAB_SUGGESTIONS})
    @CommandLineFlags.Add({BASE_PARAMS + "/tab_grid_layout_android_new_tab_tile/NewTabTile"
            + "/tab_grid_layout_android_new_tab/false"})
    public void testNewTabTile() throws InterruptedException {
        // clang-format on
        // TODO(yuezhanggg): Modify TabUiTestHelper.verifyTabSwitcherCardCount so that it can be
        // used here to verify card count. Right now it doesn't work because when switching between
        // normal/incognito, the tab list fading-in animation has not finished when check happens.
        ChromeTabbedActivity cta = mActivityTestRule.getActivity();
        prepareTabs(2, 0, null);

        // New tab tile should be showing.
        enterGTSWithThumbnailChecking();
        onView(withId(R.id.new_tab_tile)).check(matches(isDisplayed()));
        onView(withId(R.id.tab_list_view)).check(TabCountAssertion.havingTabCount(3));
        verifyTabModelTabCount(cta, 2, 0);

        // Clicking new tab tile in normal mode should create a normal tab.
        onView(withId(R.id.new_tab_tile)).perform(click());
        CriteriaHelper.pollUiThread(() -> !cta.getOverviewModeBehavior().overviewVisible());
        enterGTSWithThumbnailChecking();
        onView(withId(R.id.new_tab_tile)).check(matches(isDisplayed()));
        onView(withId(R.id.tab_list_view)).check(TabCountAssertion.havingTabCount(4));
        verifyTabModelTabCount(cta, 3, 0);

        // New tab tile should be showing in incognito mode.
        switchTabModel(true);
        onView(withId(R.id.new_tab_tile)).check(matches(isDisplayed()));
        onView(withId(R.id.tab_list_view)).check(TabCountAssertion.havingTabCount(1));

        // Clicking new tab tile in incognito mode should create an incognito tab.
        onView(withId(R.id.new_tab_tile)).perform(click());
        CriteriaHelper.pollUiThread(() -> !cta.getOverviewModeBehavior().overviewVisible());
        enterGTSWithThumbnailChecking();
        onView(withId(R.id.new_tab_tile)).check(matches(isDisplayed()));
        onView(withId(R.id.tab_list_view)).check(TabCountAssertion.havingTabCount(2));
        verifyTabModelTabCount(cta, 3, 1);

        // Close all normal tabs and incognito tabs, the new tab tile should still show in both
        // modes.
        switchTabModel(false);
        onView(withId(R.id.tab_list_view)).check(TabCountAssertion.havingTabCount(4));
        MenuUtils.invokeCustomMenuActionSync(
                InstrumentationRegistry.getInstrumentation(), cta, R.id.close_all_tabs_menu_id);
        onView(withId(R.id.tab_list_view)).check(TabCountAssertion.havingTabCount(1));
        onView(withId(R.id.new_tab_tile)).check(matches(isDisplayed()));
        switchTabModel(true);
        onView(withId(R.id.tab_list_view)).check(TabCountAssertion.havingTabCount(1));
        onView(withId(R.id.new_tab_tile)).check(matches(isDisplayed()));
    }

    @Test
    @MediumTest
    @Feature("NewTabTile")
    // clang-format off
    @Features.DisableFeatures({ChromeFeatureList.TAB_TO_GTS_ANIMATION,
            ChromeFeatureList.CLOSE_TAB_SUGGESTIONS})
    @CommandLineFlags.Add({BASE_PARAMS + "/tab_grid_layout_android_new_tab_tile/false"
            + "/tab_grid_layout_android_new_tab/false"})
    public void testNewTabTile_Disabled() throws InterruptedException {
        // clang-format on
        ChromeTabbedActivity cta = mActivityTestRule.getActivity();
        prepareTabs(2, 0, null);

        enterGTSWithThumbnailChecking();

        onView(withId(R.id.new_tab_tile)).check(doesNotExist());
        verifyTabSwitcherCardCount(cta, 2);

        switchTabModel(true);
        onView(withId(R.id.new_tab_tile)).check(doesNotExist());
        verifyTabSwitcherCardCount(cta, 0);
    }

    @Test
    @MediumTest
    // clang-format off
    @Features.EnableFeatures(ChromeFeatureList.TAB_TO_GTS_ANIMATION + "<Study")
    @CommandLineFlags.Add({BASE_PARAMS})
    public void testThumbnailAspectRatio_default() throws Exception {
        // clang-format on
        prepareTabs(2, 0, mUrl);
        TabUiTestHelper.enterTabSwitcher(mActivityTestRule.getActivity());
        onView(withId(R.id.tab_list_view))
                .check(ThumbnailAspectRatioAssertion.havingAspectRatio(1.0));
    }

    @Test
    @MediumTest
    // clang-format off
    @Features.EnableFeatures(ChromeFeatureList.TAB_TO_GTS_ANIMATION + "<Study")
    @CommandLineFlags.Add({BASE_PARAMS + "/thumbnail_aspect_ratio/0.75"})
    public void testThumbnailAspectRatio_point75() throws Exception {
        prepareTabs(2, 0, mUrl);
        TabUiTestHelper.enterTabSwitcher(mActivityTestRule.getActivity());
        onView(withId(R.id.tab_list_view))
                .check(ThumbnailAspectRatioAssertion.havingAspectRatio(0.75));
        leaveGTSAndVerifyThumbnailsAreReleased();

        Tab tab = mActivityTestRule.getActivity().getTabModelSelector().getCurrentTab();
        mActivityTestRule.loadUrlInTab(
                NTP_URL, PageTransition.TYPED | PageTransition.FROM_ADDRESS_BAR, tab);
        TabUiTestHelper.enterTabSwitcher(mActivityTestRule.getActivity());
        onView(withId(R.id.tab_list_view))
                .check(ThumbnailAspectRatioAssertion.havingAspectRatio(0.75));
    }

    @Test
    @MediumTest
    // clang-format off
    @Features.EnableFeatures(ChromeFeatureList.TAB_TO_GTS_ANIMATION + "<Study")
    @CommandLineFlags.Add({BASE_PARAMS + "/thumbnail_aspect_ratio/2.0/allow_to_refetch/true"})
    public void testThumbnailAspectRatio_fromTwoToPoint75() throws Exception {
        // clang-format on
        prepareTabs(2, 0, mUrl);
        TabUiTestHelper.enterTabSwitcher(mActivityTestRule.getActivity());
        onView(withId(R.id.tab_list_view))
                .check(ThumbnailAspectRatioAssertion.havingAspectRatio(2.0));
        TabModel currentTabModel =
                mActivityTestRule.getActivity().getTabModelSelector().getCurrentModel();
        for (int i = 0; i < currentTabModel.getCount(); i++) {
            TabUiTestHelper.checkThumbnailsExist(currentTabModel.getTabAt(i));
        }
        leaveGTSAndVerifyThumbnailsAreReleased();

        simulateAspectRatioChangedToPoint75();
        verifyAllThumbnailHasAspectRatio(0.75);

        TabUiTestHelper.enterTabSwitcher(mActivityTestRule.getActivity());
        onView(withId(R.id.tab_list_view))
                .check(ThumbnailAspectRatioAssertion.havingAspectRatio(2.0));
        ApplicationTestUtils.finishActivity(mActivityTestRule.getActivity());
    }

    @Test
    @MediumTest
    // clang-format off
    @Features.EnableFeatures(ChromeFeatureList.TAB_TO_GTS_ANIMATION + "<Study")
    @CommandLineFlags.Add({BASE_PARAMS})
    public void testThumbnailFetchingResult_defaultAspectRatio() throws Exception {
        // clang-format on
        prepareTabs(2, 0, mUrl);
        int oldJpegCount = RecordHistogram.getHistogramValueCountForTesting(
                TabContentManager.UMA_THUMBNAIL_FETCHING_RESULT,
                TabContentManager.ThumbnailFetchingResult.GOT_JPEG);
        int oldEtc1Count = RecordHistogram.getHistogramValueCountForTesting(
                TabContentManager.UMA_THUMBNAIL_FETCHING_RESULT,
                TabContentManager.ThumbnailFetchingResult.GOT_ETC1);
        int oldNothingCount = RecordHistogram.getHistogramValueCountForTesting(
                TabContentManager.UMA_THUMBNAIL_FETCHING_RESULT,
                TabContentManager.ThumbnailFetchingResult.GOT_NOTHING);
        int oldDifferentAspectRatioJpegCount = RecordHistogram.getHistogramValueCountForTesting(
                TabContentManager.UMA_THUMBNAIL_FETCHING_RESULT,
                TabContentManager.ThumbnailFetchingResult.GOT_DIFFERENT_ASPECT_RATIO_JPEG);

        TabUiTestHelper.enterTabSwitcher(mActivityTestRule.getActivity());
        int currentJpegCount = RecordHistogram.getHistogramValueCountForTesting(
                TabContentManager.UMA_THUMBNAIL_FETCHING_RESULT,
                TabContentManager.ThumbnailFetchingResult.GOT_JPEG);
        int currentEtc1Count = RecordHistogram.getHistogramValueCountForTesting(
                TabContentManager.UMA_THUMBNAIL_FETCHING_RESULT,
                TabContentManager.ThumbnailFetchingResult.GOT_ETC1);
        int currentNothingCount = RecordHistogram.getHistogramValueCountForTesting(
                TabContentManager.UMA_THUMBNAIL_FETCHING_RESULT,
                TabContentManager.ThumbnailFetchingResult.GOT_NOTHING);
        int currentDifferentAspectRatioJpegCount = RecordHistogram.getHistogramValueCountForTesting(
                TabContentManager.UMA_THUMBNAIL_FETCHING_RESULT,
                TabContentManager.ThumbnailFetchingResult.GOT_DIFFERENT_ASPECT_RATIO_JPEG);

        assertEquals(1, currentJpegCount - oldJpegCount);
        assertEquals(0, currentEtc1Count - oldEtc1Count);
        assertEquals(0, currentDifferentAspectRatioJpegCount - oldDifferentAspectRatioJpegCount);
        // Get thumbnail from a live layer.
        assertEquals(1, currentNothingCount - oldNothingCount);

        oldJpegCount = currentJpegCount;
        oldEtc1Count = currentEtc1Count;
        oldNothingCount = currentNothingCount;
        oldDifferentAspectRatioJpegCount = currentDifferentAspectRatioJpegCount;

        TabModel currentTabModel =
                mActivityTestRule.getActivity().getTabModelSelector().getCurrentModel();
        for (int i = 0; i < currentTabModel.getCount(); i++) {
            TabUiTestHelper.checkThumbnailsExist(currentTabModel.getTabAt(i));
        }

        ApplicationTestUtils.finishActivity(mActivityTestRule.getActivity());
        mActivityTestRule.startMainActivityFromLauncher();

        TabUiTestHelper.enterTabSwitcher(mActivityTestRule.getActivity());
        assertEquals(2,
                RecordHistogram.getHistogramValueCountForTesting(
                        TabContentManager.UMA_THUMBNAIL_FETCHING_RESULT,
                        TabContentManager.ThumbnailFetchingResult.GOT_JPEG)
                        - oldJpegCount);
        assertEquals(0,
                RecordHistogram.getHistogramValueCountForTesting(
                        TabContentManager.UMA_THUMBNAIL_FETCHING_RESULT,
                        TabContentManager.ThumbnailFetchingResult.GOT_ETC1)
                        - oldEtc1Count);
        assertEquals(0,
                RecordHistogram.getHistogramValueCountForTesting(
                        TabContentManager.UMA_THUMBNAIL_FETCHING_RESULT,
                        TabContentManager.ThumbnailFetchingResult.GOT_NOTHING)
                        - oldNothingCount);
        assertEquals(0,
                RecordHistogram.getHistogramValueCountForTesting(
                        TabContentManager.UMA_THUMBNAIL_FETCHING_RESULT,
                        TabContentManager.ThumbnailFetchingResult.GOT_DIFFERENT_ASPECT_RATIO_JPEG)
                        - oldDifferentAspectRatioJpegCount);
    }

    @Test
    @MediumTest
    // clang-format off
    @Features.EnableFeatures(ChromeFeatureList.TAB_TO_GTS_ANIMATION + "<Study")
    @CommandLineFlags.Add({BASE_PARAMS + "/thumbnail_aspect_ratio/2.0/allow_to_refetch/true"})
    public void testThumbnailFetchingResult_changingAspectRatio() throws Exception {
        // clang-format on
        prepareTabs(2, 0, mUrl);
        int oldJpegCount = RecordHistogram.getHistogramValueCountForTesting(
                TabContentManager.UMA_THUMBNAIL_FETCHING_RESULT,
                TabContentManager.ThumbnailFetchingResult.GOT_JPEG);
        int oldEtc1Count = RecordHistogram.getHistogramValueCountForTesting(
                TabContentManager.UMA_THUMBNAIL_FETCHING_RESULT,
                TabContentManager.ThumbnailFetchingResult.GOT_ETC1);
        int oldNothingCount = RecordHistogram.getHistogramValueCountForTesting(
                TabContentManager.UMA_THUMBNAIL_FETCHING_RESULT,
                TabContentManager.ThumbnailFetchingResult.GOT_NOTHING);
        int oldDifferentAspectRatioJpegCount = RecordHistogram.getHistogramValueCountForTesting(
                TabContentManager.UMA_THUMBNAIL_FETCHING_RESULT,
                TabContentManager.ThumbnailFetchingResult.GOT_DIFFERENT_ASPECT_RATIO_JPEG);

        TabUiTestHelper.enterTabSwitcher(mActivityTestRule.getActivity());
        int currentJpegCount = RecordHistogram.getHistogramValueCountForTesting(
                TabContentManager.UMA_THUMBNAIL_FETCHING_RESULT,
                TabContentManager.ThumbnailFetchingResult.GOT_JPEG);
        int currentEtc1Count = RecordHistogram.getHistogramValueCountForTesting(
                TabContentManager.UMA_THUMBNAIL_FETCHING_RESULT,
                TabContentManager.ThumbnailFetchingResult.GOT_ETC1);
        int currentNothingCount = RecordHistogram.getHistogramValueCountForTesting(
                TabContentManager.UMA_THUMBNAIL_FETCHING_RESULT,
                TabContentManager.ThumbnailFetchingResult.GOT_NOTHING);
        int currentDifferentAspectRatioJpegCount = RecordHistogram.getHistogramValueCountForTesting(
                TabContentManager.UMA_THUMBNAIL_FETCHING_RESULT,
                TabContentManager.ThumbnailFetchingResult.GOT_DIFFERENT_ASPECT_RATIO_JPEG);

        assertEquals(1, currentJpegCount - oldJpegCount);
        assertEquals(0, currentEtc1Count - oldEtc1Count);
        assertEquals(0, currentDifferentAspectRatioJpegCount - oldDifferentAspectRatioJpegCount);
        // Get thumbnail from a live layer.
        assertEquals(1, currentNothingCount - oldNothingCount);

        oldJpegCount = currentJpegCount;
        oldEtc1Count = currentEtc1Count;
        oldNothingCount = currentNothingCount;
        oldDifferentAspectRatioJpegCount = currentDifferentAspectRatioJpegCount;

        onView(withId(R.id.tab_list_view))
                .check(ThumbnailAspectRatioAssertion.havingAspectRatio(2.0));

        TabModel currentTabModel =
                mActivityTestRule.getActivity().getTabModelSelector().getCurrentModel();
        for (int i = 0; i < currentTabModel.getCount(); i++) {
            TabUiTestHelper.checkThumbnailsExist(currentTabModel.getTabAt(i));
        }
        leaveGTSAndVerifyThumbnailsAreReleased();

        simulateAspectRatioChangedToPoint75();
        verifyAllThumbnailHasAspectRatio(0.75);

        TabUiTestHelper.enterTabSwitcher(mActivityTestRule.getActivity());
        assertEquals(0,
                RecordHistogram.getHistogramValueCountForTesting(
                        TabContentManager.UMA_THUMBNAIL_FETCHING_RESULT,
                        TabContentManager.ThumbnailFetchingResult.GOT_JPEG)
                        - oldJpegCount);
        assertEquals(2,
                RecordHistogram.getHistogramValueCountForTesting(
                        TabContentManager.UMA_THUMBNAIL_FETCHING_RESULT,
                        TabContentManager.ThumbnailFetchingResult.GOT_DIFFERENT_ASPECT_RATIO_JPEG)
                        - oldDifferentAspectRatioJpegCount);
        assertEquals(0,
                RecordHistogram.getHistogramValueCountForTesting(
                        TabContentManager.UMA_THUMBNAIL_FETCHING_RESULT,
                        TabContentManager.ThumbnailFetchingResult.GOT_ETC1)
                        - oldEtc1Count);
        assertEquals(0,
                RecordHistogram.getHistogramValueCountForTesting(
                        TabContentManager.UMA_THUMBNAIL_FETCHING_RESULT,
                        TabContentManager.ThumbnailFetchingResult.GOT_NOTHING)
                        - oldNothingCount);
        onView(withId(R.id.tab_list_view))
                .check(ThumbnailAspectRatioAssertion.havingAspectRatio(2.0));
    }

    @Test
    @MediumTest
    // clang-format off
    @Features.EnableFeatures(ChromeFeatureList.TAB_TO_GTS_ANIMATION + "<Study")
    @DisableIf.Build(sdk_is_less_than = Build.VERSION_CODES.M,
            message = "https://crbug.com/1023833")
    @CommandLineFlags.Add({BASE_PARAMS})
    public void testRecycling_defaultAspectRatio() throws InterruptedException {
        // clang-format on
        prepareTabs(10, 0, mUrl);
        ChromeTabUtils.switchTabInCurrentTabModel(mActivityTestRule.getActivity(), 0);
        enterGTSWithThumbnailChecking();
        onView(withId(R.id.tab_list_view)).perform(RecyclerViewActions.scrollToPosition(9));
    }

    @Test
    @MediumTest
    // clang-format off
    @Features.EnableFeatures(ChromeFeatureList.TAB_TO_GTS_ANIMATION + "<Study")
    @DisableIf.Build(sdk_is_less_than = Build.VERSION_CODES.M,
            message = "https://crbug.com/1023833")
    @CommandLineFlags.Add({BASE_PARAMS + "/thumbnail_aspect_ratio/0.75"})
    public void testRecycling_aspectRatioPoint75() throws InterruptedException {
        // clang-format on
        prepareTabs(10, 0, mUrl);
        ChromeTabUtils.switchTabInCurrentTabModel(mActivityTestRule.getActivity(), 0);
        enterGTSWithThumbnailChecking();
        onView(withId(R.id.tab_list_view)).perform(RecyclerViewActions.scrollToPosition(9));
    }

    @Test
    @MediumTest
    // clang-format off
    @Features.EnableFeatures(ChromeFeatureList.TAB_TO_GTS_ANIMATION + "<Study")
    @DisableIf.Build(sdk_is_less_than = Build.VERSION_CODES.M,
            message = "https://crbug.com/1023833")
    @CommandLineFlags.Add({BASE_PARAMS + "/thumbnail_aspect_ratio/0.75"})
    public void testExpandTab_withAspectRatioPoint75() throws InterruptedException {
        // clang-format on
        prepareTabs(1, 0, mUrl);
        enterGTSWithThumbnailChecking();
        leaveGTSAndVerifyThumbnailsAreReleased();
    }

    private void switchTabModel(boolean isIncognito) {
        assertTrue(isIncognito
                != mActivityTestRule.getActivity().getTabModelSelector().isIncognitoSelected());

        onView(withContentDescription(isIncognito
                               ? R.string.accessibility_tab_switcher_incognito_stack
                               : R.string.accessibility_tab_switcher_standard_stack))
                .perform(click());

        CriteriaHelper.pollInstrumentationThread(()
                                                         -> mActivityTestRule.getActivity()
                                                                    .getTabModelSelector()
                                                                    .isIncognitoSelected()
                        == isIncognito);
    }

    /**
     * Make Chrome have {@code numTabs} of regular Tabs and {@code numIncognitoTabs} of incognito
     * tabs with {@code url} loaded, and assert no bitmap fetching occurred.
     *
     * @param numTabs The number of regular tabs.
     * @param numIncognitoTabs The number of incognito tabs.
     * @param url The URL to load.
     */
    private void prepareTabs(int numTabs, int numIncognitoTabs, @Nullable String url) {
        int oldCount = mTabListDelegate.getBitmapFetchCountForTesting();
        TabUiTestHelper.prepareTabsWithThumbnail(mActivityTestRule, numTabs, numIncognitoTabs, url);
        assertEquals(0, mTabListDelegate.getBitmapFetchCountForTesting() - oldCount);
    }

    /**
     * TODO(wychen): move some of the callers to {@link TabUiTestHelper#enterTabSwitcher}.
     */
    private void enterGTSWithThumbnailChecking() throws InterruptedException {
        Tab currentTab = mActivityTestRule.getActivity().getTabModelSelector().getCurrentTab();
        // Native tabs need to be invalidated first to trigger thumbnail taking, so skip them.
        boolean checkThumbnail = !currentTab.isNativePage();

        if (checkThumbnail) {
            mActivityTestRule.getActivity().getTabContentManager().removeTabThumbnail(
                    currentTab.getId());
        }

        int count = getCaptureCount();
        waitForCaptureRateControl();
        // TODO(wychen): use TabUiTestHelper.enterTabSwitcher() instead.
        //  Might increase flakiness though. See crbug.com/1024742.
        TestThreadUtils.runOnUiThreadBlocking(
                () -> mActivityTestRule.getActivity().getLayoutManager().showOverview(true));
        assertTrue(mActivityTestRule.getActivity().getLayoutManager().overviewVisible());

        // Make sure the fading animation is done.
        int delta;
        if (TextUtils.equals(
                    mActivityTestRule.getActivity().getCurrentWebContents().getLastCommittedUrl(),
                    NTP_URL)) {
            // NTP is not invalidated, so no new captures.
            delta = 0;
        } else {
            // The final capture at StartSurfaceLayout#finishedShowing time.
            delta = 1;
            if (ChromeFeatureList.isEnabled(ChromeFeatureList.TAB_TO_GTS_ANIMATION)
                    && areAnimatorsEnabled()) {
                // The faster capturing without writing back to cache.
                delta += 1;
            }
        }
        checkCaptureCount(delta, count);
        TabUiTestHelper.verifyAllTabsHaveThumbnail(
                mActivityTestRule.getActivity().getCurrentTabModel());
    }

    private static class TabCountAssertion implements ViewAssertion {
        private int mExpectedCount;

        public static TabCountAssertion havingTabCount(int tabCount) {
            return new TabCountAssertion(tabCount);
        }

        public TabCountAssertion(int expectedCount) {
            mExpectedCount = expectedCount;
        }

        @Override
        public void check(View view, NoMatchingViewException noMatchException) {
            if (noMatchException != null) throw noMatchException;

            RecyclerView.Adapter adapter = ((RecyclerView) view).getAdapter();
            assertEquals(mExpectedCount, adapter.getItemCount());
        }
    }

    private static class ThumbnailAspectRatioAssertion implements ViewAssertion {
        private double mExpectedRatio;

        public static ThumbnailAspectRatioAssertion havingAspectRatio(double ratio) {
            return new ThumbnailAspectRatioAssertion(ratio);
        }

        private ThumbnailAspectRatioAssertion(double expectedRatio) {
            mExpectedRatio = expectedRatio;
        }

        @Override
        public void check(View view, NoMatchingViewException noMatchException) {
            if (noMatchException != null) throw noMatchException;

            RecyclerView recyclerView = (RecyclerView) view;

            RecyclerView.Adapter adapter = recyclerView.getAdapter();
            for (int i = 0; i < adapter.getItemCount(); i++) {
                RecyclerView.ViewHolder viewHolder =
                        recyclerView.findViewHolderForAdapterPosition(i);
                if (viewHolder != null) {
                    ViewLookupCachingFrameLayout tabView =
                            (ViewLookupCachingFrameLayout) viewHolder.itemView;
                    ImageView thumbnail = (ImageView) tabView.fastFindViewById(R.id.tab_thumbnail);
                    BitmapDrawable drawable = (BitmapDrawable) thumbnail.getDrawable();
                    Bitmap bitmap = drawable.getBitmap();
                    double bitmapRatio = bitmap.getWidth() * 1.0 / bitmap.getHeight();
                    assertTrue(
                            "Actual ratio: " + bitmapRatio + "; Expected ratio: " + mExpectedRatio,
                            Math.abs(bitmapRatio - mExpectedRatio)
                                    <= TabContentManager.ASPECT_RATIO_PRECISION);
                }
            }
        }
    }

    private void enterGTS() throws InterruptedException {
        Tab currentTab = mActivityTestRule.getActivity().getTabModelSelector().getCurrentTab();
        // Native tabs need to be invalidated first to trigger thumbnail taking, so skip them.
        boolean checkThumbnail = !currentTab.isNativePage();

        if (checkThumbnail) {
            mActivityTestRule.getActivity().getTabContentManager().removeTabThumbnail(
                    currentTab.getId());
        }

        int count = getCaptureCount();
        waitForCaptureRateControl();
        TestThreadUtils.runOnUiThreadBlocking(
                () -> mActivityTestRule.getActivity().getLayoutManager().showOverview(true));
        assertTrue(mActivityTestRule.getActivity().getLayoutManager().overviewVisible());

        // Make sure the fading animation is done.
        int delta;
        if (TextUtils.equals(
                    mActivityTestRule.getActivity().getCurrentWebContents().getLastCommittedUrl(),
                    NTP_URL)) {
            // NTP is not invalidated, so no new captures.
            delta = 0;
        } else {
            // The final capture at StartSurfaceLayout#finishedShowing time.
            delta = 1;
            // TODO(wychen): refactor areAnimatorsEnabled() to a util class.
            if (ChromeFeatureList.isEnabled(ChromeFeatureList.TAB_TO_GTS_ANIMATION)
                    && areAnimatorsEnabled()) {
                // The faster capturing without writing back to cache.
                delta += 1;
            }
        }
        checkCaptureCount(delta, count);
        if (checkThumbnail) checkThumbnailsExist(currentTab);
    }

    /**
     * TODO(wychen): create a version without thumbnail checking, which uses
     *  {@link TabUiTestHelper#clickFirstCardFromTabSwitcher} or simply {@link Espresso#pressBack},
     *  and {@link OverviewModeBehaviorWatcher}.
     */
    private void leaveGTSAndVerifyThumbnailsAreReleased() {
        assertTrue(mActivityTestRule.getActivity().getLayoutManager().overviewVisible());
        StartSurface startSurface = mStartSurfaceLayout.getStartSurfaceForTesting();
        TestThreadUtils.runOnUiThreadBlocking(
                () -> { startSurface.getController().onBackPressed(); });
        // TODO(wychen): using default timeout or even converting to
        //  OverviewModeBehaviorWatcher shouldn't increase flakiness.
        CriteriaHelper.pollInstrumentationThread(
                () -> !mActivityTestRule.getActivity().getLayoutManager().overviewVisible(),
                "Overview not hidden yet", DEFAULT_MAX_TIME_TO_POLL * 10,
                DEFAULT_POLLING_INTERVAL);
        assertThumbnailsAreReleased();
    }

    private void checkFinalCaptureCount(boolean switchToAnotherTab, int initCount) {
        int expected;
        if (TextUtils.equals(
                    mActivityTestRule.getActivity().getCurrentWebContents().getLastCommittedUrl(),
                    NTP_URL)) {
            expected = 0;
        } else {
            expected = mRepeat;
            if (ChromeFeatureList.isEnabled(ChromeFeatureList.TAB_TO_GTS_ANIMATION)
                    && areAnimatorsEnabled()) {
                expected += mRepeat;
            }
            if (switchToAnotherTab) {
                expected += mRepeat;
            }
        }
        checkCaptureCount(expected, initCount);
    }

    private void checkCaptureCount(int expectedDelta, int initCount) {
        // TODO(wychen): With animation, the 2nd capture might be skipped if the 1st takes too long.
        CriteriaHelper.pollUiThread(
                Criteria.equals(expectedDelta, () -> getCaptureCount() - initCount));
    }

    private void checkThumbnailsExist(Tab tab) {
        File etc1File = TabContentManager.getTabThumbnailFileEtc1(tab);
        CriteriaHelper.pollInstrumentationThread(etc1File::exists,
                "The thumbnail " + etc1File.getName() + " is not found",
                DEFAULT_MAX_TIME_TO_POLL * 10, DEFAULT_POLLING_INTERVAL);

        File jpegFile = TabContentManager.getTabThumbnailFileJpeg(tab);
        CriteriaHelper.pollInstrumentationThread(jpegFile::exists,
                "The thumbnail " + jpegFile.getName() + " is not found",
                DEFAULT_MAX_TIME_TO_POLL * 10, DEFAULT_POLLING_INTERVAL);
    }

    private int getCaptureCount() {
        return RecordHistogram.getHistogramTotalCountForTesting("Compositing.CopyFromSurfaceTime");
    }

    private void waitForCaptureRateControl() throws InterruptedException {
        // Needs to wait for at least |kCaptureMinRequestTimeMs| in order to capture another one.
        // TODO(wychen): find out why waiting is still needed after setting
        //               |kCaptureMinRequestTimeMs| to 0.
        Thread.sleep(2000);
    }

    private void assertThumbnailsAreReleased() {
        // Could not directly assert canAllBeGarbageCollected() because objects can be in Cleaner.
        CriteriaHelper.pollInstrumentationThread(() -> canAllBeGarbageCollected(mAllBitmaps));
    }

    private boolean canAllBeGarbageCollected(List<WeakReference<Bitmap>> bitmaps) {
        for (WeakReference<Bitmap> bitmap : bitmaps) {
            if (!GarbageCollectionTestUtils.canBeGarbageCollected(bitmap)) return false;
        }
        return true;
    }

    /**
     * Should be the same as {@link ValueAnimator#areAnimatorsEnabled}, which requires API level 26.
     * TODO(crbug.com/982018): put this interface in a place to share with
     * TabListContainerViewBinderTest.areAnimatorsEnabled.
     */
    private static boolean areAnimatorsEnabled() {
        // We default to assuming that animations are enabled in case ANIMATOR_DURATION_SCALE is not
        // defined.
        final float defaultScale = 1f;
        float durationScale =
                Settings.Global.getFloat(ContextUtils.getApplicationContext().getContentResolver(),
                        Settings.Global.ANIMATOR_DURATION_SCALE, defaultScale);
        return !(durationScale == 0.0);
    }

    private void simulateAspectRatioChangedToPoint75() throws IOException {
        TabModel currentModel = mActivityTestRule.getActivity().getCurrentTabModel();
        for (int i = 0; i < currentModel.getCount(); i++) {
            Tab tab = currentModel.getTabAt(i);
            Bitmap bitmap = TabContentManager.getJpegForTab(tab);
            bitmap = Bitmap.createScaledBitmap(
                    bitmap, bitmap.getWidth(), (int) (bitmap.getWidth() * 1.0 / 0.75), false);
            encodeJpeg(tab, bitmap);
        }
    }

    private void encodeJpeg(Tab tab, Bitmap bitmap) throws IOException {
        FileOutputStream outputStream =
                new FileOutputStream(TabContentManager.getTabThumbnailFileJpeg(tab));
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream);
        outputStream.close();
        Bitmap decodedBitmap = TabContentManager.getJpegForTab(tab);
    }

    private void verifyAllThumbnailHasAspectRatio(double ratio) {
        TabModel currentModel = mActivityTestRule.getActivity().getCurrentTabModel();
        for (int i = 0; i < currentModel.getCount(); i++) {
            Tab tab = currentModel.getTabAt(i);
            Bitmap bitmap = TabContentManager.getJpegForTab(tab);
            double bitmapRatio = bitmap.getWidth() * 1.0 / bitmap.getHeight();
            assertTrue("Actual ratio: " + bitmapRatio + "; Expected ratio: " + ratio,
                    Math.abs(bitmapRatio - ratio) <= TabContentManager.ASPECT_RATIO_PRECISION);
        }
    }
}
