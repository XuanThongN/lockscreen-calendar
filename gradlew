#!/bin/sh
##############################################################################
##
##  Gradle start up script for UN*X
##
##############################################################################

# Attempt to set APP_HOME
# Resolve links: $0 may be a link
PRG="$0"
# Need this for relative symlinks.
while [ -h "$PRG" ] ; do
    ls=
    link=
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=."/$link"
    fi
done
SAVED="/data/data/com.termux/files/home"
cd "./" >/dev/null
APP_HOME="/data/data/com.termux/files/home"
cd "$SAVED" >/dev/null

APP_NAME="Gradle"
APP_BASE_NAME=bash

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD="maximum"

warn () {
    echo "$*"
}

die () {
    echo
    echo "$*"
    echo
    exit 1
}

# OS specific support (both adapted from sh scripts)
darwin=false
case "Linux" in
  Darwin* )
    darwin=true
    ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
    fi
else
    JAVACMD="java"
    which "$JAVACMD" >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi

# Escape application args
for arg do
    printf -v args '%s %q' "$args" "$arg"
done

exec "$JAVACMD" -Dorg.gradle.appname=$APP_BASE_NAME -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain $args
