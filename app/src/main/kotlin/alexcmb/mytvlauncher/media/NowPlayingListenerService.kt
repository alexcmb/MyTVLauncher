package alexcmb.mytvlauncher.media

import android.service.notification.NotificationListenerService

/**
 * Does nothing on its own — it exists only so the launcher can be granted notification-listener
 * access, which is what unlocks [android.media.session.MediaSessionManager.getActiveSessions]
 * for other apps' media. The grant is one-time; on a TV it comes over adb (see
 * [NowPlayingController]).
 */
class NowPlayingListenerService : NotificationListenerService()
