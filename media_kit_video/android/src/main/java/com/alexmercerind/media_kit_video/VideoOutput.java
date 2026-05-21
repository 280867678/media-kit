/**
 * This file is a part of media_kit (https://github.com/media-kit/media-kit).
 * <p>
 * Copyright © 2021 & onwards, Hitesh Kumar Saini <saini123hitesh@gmail.com>.
 * All rights reserved.
 * Use of this source code is governed by MIT license that can be found in the LICENSE file.
 */
package com.alexmercerind.media_kit_video;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;

import io.flutter.view.TextureRegistry;

public class VideoOutput {
    private static final String TAG = "VideoOutput";
    private static final Method newGlobalObjectRef;
    private static final Method deleteGlobalObjectRef;
    private static final HashSet<Long> deletedGlobalObjectRefs = new HashSet<>();
    private static final Handler handler = new Handler(Looper.getMainLooper());

    static {
        try {
            Class<?> mediaKitAndroidHelperClass = Class.forName("com.alexmercerind.mediakitandroidhelper.MediaKitAndroidHelper");
            newGlobalObjectRef = mediaKitAndroidHelperClass.getDeclaredMethod("newGlobalObjectRef", Object.class);
            deleteGlobalObjectRef = mediaKitAndroidHelperClass.getDeclaredMethod("deleteGlobalObjectRef", long.class);
            newGlobalObjectRef.setAccessible(true);
            deleteGlobalObjectRef.setAccessible(true);
        } catch (Throwable e) {
            Log.i("media_kit", "package:media_kit_libs_android_video missing.");
            throw new RuntimeException("Failed to initialize VideoOutput.");
        }
    }

    private long id = 0;
    private long wid = 0;
    private int width = 0;
    private int height = 0;

    private final TextureUpdateCallback textureUpdateCallback;
    private final TextureRegistry.SurfaceTextureEntry surfaceTextureEntry;
    private final Surface surface;

    private final Object lock = new Object();

    VideoOutput(TextureRegistry textureRegistryReference, TextureUpdateCallback textureUpdateCallback) {
        this.textureUpdateCallback = textureUpdateCallback;
        this.surfaceTextureEntry = textureRegistryReference.createSurfaceTexture();
        this.surface = new Surface(surfaceTextureEntry.surfaceTexture());
        this.id = surfaceTextureEntry.id();
        onSurfaceAvailable();
    }

    public void dispose() {
        synchronized (lock) {
            try {
                surface.release();
            } catch (Throwable e) {
                Log.e(TAG, "dispose", e);
            }
            try {
                surfaceTextureEntry.release();
            } catch (Throwable e) {
                Log.e(TAG, "dispose", e);
            }
            onSurfaceCleanup();
        }
    }

    public void setSurfaceSize(int width, int height) {
        setSurfaceSize(width, height, false);
    }

    private void setSurfaceSize(int width, int height, boolean force) {
        synchronized (lock) {
            try {
                if (!force && this.width == width && this.height == height) {
                    return;
                }
                this.width = width;
                this.height = height;
                surfaceTextureEntry.surfaceTexture().setDefaultBufferSize(width, height);
                onSurfaceAvailable();
            } catch (Throwable e) {
                Log.e(TAG, "setSurfaceSize", e);
            }
        }
    }

    public void onSurfaceAvailable() {
        synchronized (lock) {
            id = surfaceTextureEntry.id();
            wid = newGlobalObjectRef(surface);
            textureUpdateCallback.onTextureUpdate(id, wid, width, height);
        }
    }

    public void onSurfaceCleanup() {
        synchronized (lock) {
            textureUpdateCallback.onTextureUpdate(id, 0, width, height);
            if (wid != 0) {
                final long widReference = wid;
                handler.postDelayed(() -> deleteGlobalObjectRef(widReference), 5000);
            }
        }
    }

    private static long newGlobalObjectRef(Object object) {
        try {
            return (long) Objects.requireNonNull(newGlobalObjectRef.invoke(null, object));
        } catch (Throwable e) {
            Log.e(TAG, "newGlobalObjectRef", e);
            return 0;
        }
    }

    private static void deleteGlobalObjectRef(long ref) {
        if (deletedGlobalObjectRefs.contains(ref)) return;
        if (deletedGlobalObjectRefs.size() > 100) deletedGlobalObjectRefs.clear();
        deletedGlobalObjectRefs.add(ref);
        try {
            deleteGlobalObjectRef.invoke(null, ref);
        } catch (Throwable e) {
            Log.e(TAG, "deleteGlobalObjectRef", e);
        }
    }
}
