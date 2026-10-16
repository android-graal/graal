/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.headers;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.AllowNarrowingCast;
import org.graalvm.nativeimage.c.struct.AllowWideningCast;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.word.PointerBase;

// Checkstyle: stop

/**
 * Definitions manually translated from the C header file sys/resource.h.
 */
@CContext(PosixDirectives.class)
public class Resource {

    @CConstant
    public static native int RLIMIT_NOFILE();

    @CStruct(addStructKeyword = true)
    public interface rlimit extends PointerBase {
        @CField
        @AllowWideningCast
        long rlim_cur();

        @CField
        @AllowNarrowingCast
        void set_rlim_cur(long value);

        @CField
        @AllowWideningCast
        long rlim_max();

        @CField
        @AllowNarrowingCast
        void set_rlim_max(long value);
    }

    @CFunction(value = "getrlimit64")
    @Platforms(Platform.LINUX_ARM32.class)
    private static native int getrlimit64(int resource, rlimit rlimits);

    @CFunction(value = "getrlimit")
    private static native int getrlimit_default(int resource, rlimit rlimits);

    public static int getrlimit(int resource, rlimit rlimits) {
        if (Platform.includedIn(Platform.LINUX_ARM32.class)) {
            return getrlimit64(resource, rlimits);
        }
        return getrlimit_default(resource, rlimits);
    }

    @CFunction(value = "setrlimit64")
    @Platforms(Platform.LINUX_ARM32.class)
    private static native int setrlimit64(int resource, rlimit rlimits);

    @CFunction(value = "setrlimit")
    private static native int setrlimit_default(int resource, rlimit rlimits);

    public static int setrlimit(int resource, rlimit rlimits) {
        if (Platform.includedIn(Platform.LINUX_ARM32.class)) {
            return setrlimit64(resource, rlimits);
        }
        return setrlimit_default(resource, rlimits);
    }
}
