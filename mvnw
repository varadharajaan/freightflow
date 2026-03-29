#!/bin/sh
# ----------------------------------------------------------------------------
# Maven Wrapper startup script
# ----------------------------------------------------------------------------

set -e

BASE_DIR="$(cd "$(dirname "$0")"; pwd -P)"
WRAPPER_DIR="$BASE_DIR/.mvn/wrapper"
WRAPPER_JAR="$WRAPPER_DIR/maven-wrapper.jar"
WRAPPER_PROPS="$WRAPPER_DIR/maven-wrapper.properties"

if [ ! -f "$WRAPPER_PROPS" ]; then
  echo "Missing $WRAPPER_PROPS"
  exit 1
fi

if [ ! -f "$WRAPPER_JAR" ]; then
  WRAPPER_URL="$(grep '^wrapperUrl=' "$WRAPPER_PROPS" | sed 's/wrapperUrl=//')"
  if [ -z "$WRAPPER_URL" ]; then
    echo "wrapperUrl missing in $WRAPPER_PROPS"
    exit 1
  fi

  mkdir -p "$WRAPPER_DIR"
  echo "Downloading Maven wrapper jar from $WRAPPER_URL"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$WRAPPER_URL" -o "$WRAPPER_JAR"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$WRAPPER_URL" -O "$WRAPPER_JAR"
  else
    echo "Neither curl nor wget found; cannot download Maven wrapper jar."
    exit 1
  fi
fi

if [ -n "$JAVA_HOME" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD="java"
fi

exec "$JAVACMD" \
  -Dmaven.multiModuleProjectDirectory="$BASE_DIR" \
  -classpath "$WRAPPER_JAR" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"
