# Source before Android builds: . scripts/android-env.sh
export ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"
export NDK_HOME="$ANDROID_HOME/ndk/$(ls "$ANDROID_HOME/ndk" | sort -V | tail -1)"
export JAVA_HOME="C:/Program Files/Java/jdk-21.0.12"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
