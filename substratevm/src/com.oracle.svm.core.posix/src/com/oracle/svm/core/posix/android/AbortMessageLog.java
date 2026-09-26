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

import static com.oracle.svm.core.heap.RestrictHeapAccess.Access.NO_ALLOCATION;

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.log.RealLog;
import com.oracle.svm.guest.staging.c.CGlobalData;
import com.oracle.svm.guest.staging.c.CGlobalDataFactory;

/** Composes the process's abort message in a NUL-terminated buffer. */
final class AbortMessageLog extends RealLog {
    private static final int ABORT_MESSAGE_SIZE = 16 * 1024;
    private static final CGlobalData<CCharPointer> BUFFER = CGlobalDataFactory.createBytes(() -> ABORT_MESSAGE_SIZE);

    private int length;

    @Override
    @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Must not allocate when logging.")
    protected Log rawBytes(CCharPointer bytes, UnsignedWord count) {
        CCharPointer buffer = BUFFER.get();
        for (long i = 0; i < count.rawValue() && length < ABORT_MESSAGE_SIZE - 1; i++) {
            buffer.write(length++, bytes.read(Word.signed(i)));
        }
        buffer.write(length, (byte) 0);
        return this;
    }

    boolean isWritten() {
        return length > 0;
    }

    CCharPointer message() {
        return BUFFER.get();
    }
}
