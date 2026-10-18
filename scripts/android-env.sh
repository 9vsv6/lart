# Source before Android builds: . scripts/android-env.sh
export ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"
export NDK_HOME="$ANDROID_HOME/ndk/$(ls "$ANDROID_HOME/ndk" | sort -V | tail -1)"
# JDK 21: an existing JAVA_HOME, else an installed JDK 21. Gradle 8.14 cannot
# run on newer JDKs such as the one Android Studio bundles.
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
  for jdk in "C:/Program Files/Java/jdk-21"* "C:/Program Files/Eclipse Adoptium/jdk-21"* \
    "C:/Program Files/Microsoft/jdk-21"*; do
    [ -x "$jdk/bin/java" ] && { JAVA_HOME="$jdk"; break; }
  done
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
