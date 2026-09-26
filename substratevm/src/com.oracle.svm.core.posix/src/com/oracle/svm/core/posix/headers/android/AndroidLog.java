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
package com.oracle.svm.core.posix.headers.android;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.type.CCharPointer;

// Checkstyle: stop

@CContext(AndroidDirectives.class)
@CLibrary("log")
public class AndroidLog {

    /*
     * liblog silently truncates an entry beyond LOGGER_ENTRY_MAX_PAYLOAD: the priority byte, the tag and the message,
     * both NUL-terminated.
     */
    private static final int LOGGER_ENTRY_MAX_PAYLOAD = 4068;

    public static int getMaxLine(String tag) {
        return LOGGER_ENTRY_MAX_PAYLOAD - 1 - (tag.length() + 1) - 1;
    }

    @CConstant
    public static native int ANDROID_LOG_INFO();

    @CConstant
    public static native int ANDROID_LOG_WARN();

    @CConstant
    public static native int ANDROID_LOG_ERROR();

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native int __android_log_write(int prio, CCharPointer tag, CCharPointer text);

    @CFunction(transition = Transition.NO_TRANSITION)
    public static native void android_set_abort_message(CCharPointer msg);
}
