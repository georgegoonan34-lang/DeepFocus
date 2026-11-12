#!/bin/sh
#
# Build DeepFocus, put it on the phone, and make it the home screen.
#
#   1. Plug the phone in with USB debugging on.
#   2. Run:  ./install.sh
#
# This is the only way to install or update DeepFocus, and the only way to
# make it the home screen: the phone's own settings screens for both are
# closed by DeepFocus itself once it is running.

set -e
cd "$(dirname "$0")"

PACKAGE=com.deepfocus.app
HOME_ACTIVITY=$PACKAGE/.presentation.ui.screens.launcher.LauncherActivity

# Gradle needs Java 17; the one bundled with Android Studio is the sure bet.
STUDIO_JAVA="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
if [ -z "$JAVA_HOME" ] && [ -d "$STUDIO_JAVA" ]; then
    export JAVA_HOME="$STUDIO_JAVA"
fi

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"

if [ "$("$ADB" get-state 2>/dev/null)" != "device" ]; then
    echo "No phone found."
    echo "Plug it in, unlock it, allow USB debugging if it asks, then run this again."
    exit 1
fi

./gradlew installDebug

# Lets DeepFocus switch its own accessibility service off and on around
# banking apps.
"$ADB" shell pm grant $PACKAGE android.permission.WRITE_SECURE_SETTINGS

# Screen time on the home page needs usage access.
"$ADB" shell appops set $PACKAGE GET_USAGE_STATS allow

# Make the Home button open the DeepFocus menu, then go there.
"$ADB" shell cmd package set-home-activity "$HOME_ACTIVITY"
"$ADB" shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null

echo
echo "Installed. DeepFocus is the home screen."
