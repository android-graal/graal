/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.posix.android;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateDiagnostics;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicWord;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.log.LogHandlerExtension;
import com.oracle.svm.core.posix.headers.Errno;
import com.oracle.svm.core.posix.headers.android.AndroidLog;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.guest.staging.c.CGlobalData;
import com.oracle.svm.guest.staging.c.CGlobalDataFactory;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.RuntimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

@AutomaticallyRegisteredFeature
class AndroidLogHandlerFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.firstImageBuild() && Platform.includedIn(Platform.ANDROID.class);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        Log.finalizeDefaultLogHandler(new AndroidLogHandler());
    }
}

/**
 * Writes the log to logcat, one entry per line. Sets message and exception of the fatal context to the abort message
 */
@SingletonTraits(access = RuntimeAccessOnly.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
public class AndroidLogHandler implements LogHandlerExtension {

    /*
     * liblog silently truncates an entry beyond LOGGER_ENTRY_MAX_PAYLOAD: the priority byte, the tag and the message,
     * both NUL-terminated.
     */
    private static final int LOGGER_ENTRY_MAX_PAYLOAD = 4068;
    private static final String TAG_NAME = "SubstrateVM";
    private static final int MAX_LINE = LOGGER_ENTRY_MAX_PAYLOAD - 1 - (TAG_NAME.length() + 1) - 1;
    private static final int RETRIES = 100;

    private static final CGlobalData<CCharPointer> TAG = CGlobalDataFactory.createCString(TAG_NAME);
    private static final CGlobalData<CCharPointer> ABORTING = CGlobalDataFactory.createCString("the isolate aborts after a fatal error");
    private static final CGlobalData<CCharPointer> LINE = CGlobalDataFactory.createBytes(() -> MAX_LINE + 1);
    private static final VMMutex MUTEX = new VMMutex("AndroidLogHandler.line");
    private static final AtomicWord<IsolateThread> FATAL_THREAD = new AtomicWord<>();
    private static final AbortMessageLog ABORT_MESSAGE_LOG = new AbortMessageLog();

    private int length;

    @Override
    @Uninterruptible(reason = "Holds the line mutex without a thread status transition.")
    public void log(CCharPointer bytes, UnsignedWord count) {
        final int savedErrno = LibC.errno();
        MUTEX.lockNoTransition();
        try {
            CCharPointer line = LINE.get();
            for (long i = 0; i < count.rawValue(); i++) {
                byte b = bytes.read(Word.signed(i));
                if (b == '\n') {
                    writeLine();
                } else {
                    line.write(length++, b);
                }

                if (length >= MAX_LINE) {
                    writeLine();
                }
            }
        } finally {
            MUTEX.unlock();
            LibC.setErrno(savedErrno);
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public boolean fatalContext(CodePointer callerIP, String msg, Throwable ex) {
        if (!FATAL_THREAD.compareAndSet(Word.nullPointer(), CurrentIsolate.getCurrentThread())) {
            return true;
        }
        if (msg != null) {
            ABORT_MESSAGE_LOG.string(msg);
        }
        if (ex != null) {
            if (msg != null) {
                ABORT_MESSAGE_LOG.string(": ");
            }
            ABORT_MESSAGE_LOG.exception(ex);
        }
        if (ABORT_MESSAGE_LOG.isWritten()) {
            AndroidLog.android_set_abort_message(ABORT_MESSAGE_LOG.message());
        }
        return true;
    }

    @Override
    @Uninterruptible(reason = "Holds the line mutex without a thread status transition.")
    public void fatalError() {
        if (SubstrateDiagnostics.isFatalErrorHandlingInProgress()) {
            // Delay the shutdown a bit if another thread has something important to report.
            VMThreads.singleton().nativeSleep(3000);
        }
        MUTEX.lockNoTransition();
        try {
            if (length > 0) {
                writeLine();
            }
            write(AndroidLog.ANDROID_LOG_ERROR(), ABORTING.get());
        } finally {
            MUTEX.unlock();
        }
        AndroidLog.android_set_abort_message(ABORTING.get());
        LibC.abort();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void writeLine() {
        CCharPointer line = LINE.get();
        line.write(length, (byte) 0);
        write(AndroidLog.ANDROID_LOG_WARN(), line);
        length = 0;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void write(int priority, CCharPointer text) {
        for (int i = 0; i < RETRIES; i++) {
            if (AndroidLog.__android_log_write(priority, TAG.get(), text) != -Errno.EAGAIN()) {
                return;
            }
            if (i % 5 == 4) {
                VMThreads.singleton().nativeSleep(1);
            } else {
                VMThreads.singleton().yield();
            }
        }
    }
}
