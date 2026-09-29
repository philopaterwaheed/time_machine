# time_machine

A small Android app that locks this phone when a timer ends. The timer is an exact alarm, so it still fires after you leave the app or swipe it away.

## Download the APK

Push this repo to GitHub. The **Build APK** action runs on that push, and you can also start it from the Actions tab.

When the run is green, it publishes a release. Open the Releases page and download `time-machine.apk`. On the phone, allow install from your browser or file app, then open the APK. The build is signed with the debug key, so Android will install it.

## Run

Open this folder in Android Studio and run the `app` configuration on a phone.

On first use:

1. Tap **Turn on device admin** and accept the system screen. Android only lets an app lock the screen after you approve it as a device admin.
2. Set a duration and tap **Start**. A 10 second preset is there so you can try it.
3. Leave the app. When the time is up, the screen locks.

Swiping the app away does not cancel the timer. **Force stop** in system settings does, because Android clears alarms for force-stopped apps. After a reboot, the timer is scheduled again if it has not expired yet.
