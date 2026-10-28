# Build toolchain only: JDK + android.jar + build-tools (aapt2, d8, zipalign, apksigner).
# No Gradle, no AGP, no cmdline-tools/sdkmanager, no AndroidX.
#
# Three stages so the pruning happens before the files land in the final image
# (rm in a single-stage build only writes whiteouts, the bytes stay in the layer).

ARG REPO=https://dl.google.com/android/repository
ARG PLATFORM=platform-34-ext7_r03.zip
ARG BUILD_TOOLS=build-tools_r34-linux.zip

# The JDK parts javac/d8/apksigner never load.
FROM eclipse-temurin:17-jdk-jammy AS jdk
RUN rm -rf /opt/java/openjdk/jmods /opt/java/openjdk/man /opt/java/openjdk/lib/ct.sym /opt/java/openjdk/lib/src.zip

# android.jar + the five build tools, everything else from the two zips is dropped here.
FROM ubuntu:jammy AS sdk
ARG REPO
ARG PLATFORM
ARG BUILD_TOOLS
RUN set -eux \
 && apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl unzip \
 && rm -rf /var/lib/apt/lists/* \
 && mkdir -p /opt/android/platforms /opt/android/build-tools \
 && curl -fsSL "$REPO/$PLATFORM" -o /tmp/p.zip \
 && curl -fsSL "$REPO/$BUILD_TOOLS" -o /tmp/bt.zip \
 && unzip -q /tmp/p.zip -d /tmp/p && unzip -q /tmp/bt.zip -d /tmp/bt \
 && mv /tmp/p/* /opt/android/platforms/ && mv /tmp/bt/* /opt/android/build-tools/ \
 && rm -rf /opt/android/platforms/*/data /opt/android/platforms/*/skins /opt/android/platforms/*/templates \
      /opt/android/platforms/*/optional /opt/android/platforms/*/android-stubs-src.jar \
      /opt/android/platforms/*/core-for-system-modules.jar /opt/android/platforms/*/framework.aidl \
 && rm -rf /opt/android/build-tools/*/renderscript /opt/android/build-tools/*/lld-bin \
      /opt/android/build-tools/*/lld /opt/android/build-tools/*/llvm-rs-cc /opt/android/build-tools/*/bcc_compat \
      /opt/android/build-tools/*/aidl /opt/android/build-tools/*/aapt /opt/android/build-tools/*/dexdump \
      /opt/android/build-tools/*/split-select /opt/android/build-tools/*/NOTICE.txt \
      /opt/android/build-tools/*/core-lambda-stubs.jar /opt/android/build-tools/*/*-linux-android-ld \
      /opt/android/build-tools/*/lib64/libLLVM_android.so /opt/android/build-tools/*/lib64/libclang_android.so \
      /opt/android/build-tools/*/lib64/libbcc.so /opt/android/build-tools/*/lib64/libbcinfo.so \
 && ln -s /opt/android/platforms/* /opt/android/platform \
 && ln -s /opt/android/build-tools/* /opt/android/tools \
 && chmod -R a+rX /opt/android \
 && rm -rf /tmp/p /tmp/bt /tmp/p.zip /tmp/bt.zip

FROM ubuntu:jammy
COPY --from=jdk /opt/java/openjdk /opt/java/openjdk
COPY --from=sdk /opt/android /opt/android
RUN apt-get update \
 && apt-get install -y --no-install-recommends zip \
 && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=/opt/java/openjdk/bin:/opt/android/tools:$PATH
ENV ANDROID_JAR=/opt/android/platform/android.jar
ENV MIN_SDK=24 TARGET_SDK=34 VERSION_CODE=4 VERSION_NAME=2.2

COPY entrypoint.sh /usr/local/bin/entrypoint.sh
WORKDIR /src
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
