package com.minimal.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Exists only so Android lets the home screen read which media session is
 * playing (MediaSessionManager.getActiveSessions requires an enabled
 * notification listener). It ignores every notification it is sent.
 */
public class MediaListener extends NotificationListenerService {}
