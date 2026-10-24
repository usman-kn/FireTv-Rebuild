package com.minimal.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Fire OS always routes the Home button to Amazon's launcher and handles the
 * key before accessibility filters see it, so it can't be intercepted. Instead,
 * when that launcher's home activity comes to the front, bring ours up.
 * Amazon's Settings screens live in the same package, so match the home
 * activity class only.
 *
 * This service runs in the main (small) process; HomeActivity runs in ":ui"
 * so the system can reclaim the home screen's memory while an app is open.
 */
public class HomeKeyService extends AccessibilityService {
    private static final String AMAZON = "com.amazon.tv.launcher";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (e.getPackageName() == null || e.getClassName() == null) return;
        if (!AMAZON.contentEquals(e.getPackageName())) return;
        String cls = e.getClassName().toString();
        if (!cls.endsWith(".HomeActivity") && !cls.endsWith(".HomeActivity_vNext")) return;

        startActivity(new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION));
    }

    @Override
    public void onInterrupt() {}
}
