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

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.IntSupplier;

import org.graalvm.nativeimage.PinnedObject;

import com.oracle.svm.core.posix.headers.android.AndroidLog;

final class LogcatStream extends OutputStream {

    private final byte[] tag;
    private final IntSupplier priority;
    private final byte[] line;
    private int length;

    private LogcatStream(String tag, IntSupplier priority) {
        byte[] bytes = tag.getBytes(StandardCharsets.UTF_8);
        this.tag = Arrays.copyOf(bytes, bytes.length + 1);
        this.priority = priority;
        this.line = new byte[AndroidLog.getMaxLine(tag) + 1];
    }

    static LogcatStream info(String tag) {
        return new LogcatStream(tag, AndroidLog::ANDROID_LOG_INFO);
    }

    static LogcatStream warn(String tag) {
        return new LogcatStream(tag, AndroidLog::ANDROID_LOG_WARN);
    }

    @Override
    public void write(int b) {
        if (b == '\n') {
            emit();
        } else if (length < line.length - 1) {
            line[length++] = (byte) b;
        }
    }

    @Override
    public void write(byte[] bytes, int offset, int count) {
        for (int i = offset; i < offset + count; i++) {
            write(bytes[i]);
        }
    }

    @Override
    public void flush() {
        if (length > 0) {
            emit();
        }
    }

    private void emit() {
        line[length] = 0;
        try (PinnedObject pinnedTag = PinnedObject.create(tag); PinnedObject pinnedLine = PinnedObject.create(line)) {
            AndroidLog.__android_log_write(priority.getAsInt(), pinnedTag.addressOfArrayElement(0), pinnedLine.addressOfArrayElement(0));
        }
        length = 0;
    }
}
