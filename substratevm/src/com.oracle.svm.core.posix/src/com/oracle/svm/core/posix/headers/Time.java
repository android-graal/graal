/*
 * Copyright (c) 2013, 2026, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumValue;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.struct.AllowNarrowingCast;
import org.graalvm.nativeimage.c.struct.AllowWideningCast;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.shared.Uninterruptible;

// Checkstyle: stop

/**
 * Definitions manually translated from the C header file sys/time.h.
 */
@CContext(PosixDirectives.class)
public class Time {

    @CStruct(addStructKeyword = true)
    public interface timeval extends PointerBase {
        @CField
        @AllowWideningCast
        long tv_sec();

        @CFieldAddress
        CLongPointer addressOftv_sec();

        @CField
        @AllowNarrowingCast
        void set_tv_sec(long value);

        @CField
        @AllowWideningCast
        long tv_usec();

        @CField
        @AllowNarrowingCast
        void set_tv_usec(long value);

        timeval addressOf(int index);
    }

    public interface timezone extends PointerBase {
    }

    @CStruct(addStructKeyword = true)
    public interface timespec extends PointerBase {
        @CField
        @AllowWideningCast
        long tv_sec();

        @CField
        @AllowNarrowingCast
        void set_tv_sec(long value);

        @CField
        @AllowWideningCast
        long tv_nsec();

        @CField
        @AllowNarrowingCast
        void set_tv_nsec(long value);
    }

    @CStruct(addStructKeyword = true)
    public interface itimerval extends PointerBase {
        @CFieldAddress
        timeval it_interval();

        @CFieldAddress
        timeval it_value();
    }

    @CStruct(addStructKeyword = true)
    public interface tm extends PointerBase {
        @CField
        int tm_sec();

        @CField
        int tm_min();

        @CField
        int tm_hour();

        @CField
        int tm_mday();

        @CField
        int tm_mon();

        @CField
        int tm_year();
    }

    @CEnum
    @CContext(PosixDirectives.class)
    public enum TimerTypeEnum {
        ITIMER_REAL,
        ITIMER_VIRTUAL,
        ITIMER_PROF;

        @CEnumValue
        public native int getCValue();
    }

    @CConstant
    public static native int CLOCK_REALTIME();

    public static class NoTransitions {

        @CFunction(value = "__setitimer64", transition = CFunction.Transition.NO_TRANSITION)
        @Platforms(Platform.LINUX_ARM32.class)
        private static native int setitimer_time64(TimerTypeEnum which, itimerval newValue, itimerval oldValue);

        @CFunction(value = "setitimer", transition = CFunction.Transition.NO_TRANSITION)
        private static native int setitimer_default(TimerTypeEnum which, itimerval newValue, itimerval oldValue);

        /**
         * @param which from {@link TimerTypeEnum#getCValue()}
         */
        public static int setitimer(TimerTypeEnum which, itimerval newValue, itimerval oldValue) {
            if (Platform.includedIn(Platform.LINUX_ARM32.class)) {
                return setitimer_time64(which, newValue, oldValue);
            }
            return setitimer_default(which, newValue, oldValue);
        }

        @CFunction(value = "__gettimeofday64", transition = CFunction.Transition.NO_TRANSITION)
        @Platforms(Platform.LINUX_ARM32.class)
        private static native int gettimeofday_time64(timeval tv, timezone tz);

        @CFunction(value = "gettimeofday", transition = CFunction.Transition.NO_TRANSITION)
        private static native int gettimeofday_default(timeval tv, timezone tz);

        public static int gettimeofday(timeval tv, timezone tz) {
            if (Platform.includedIn(Platform.LINUX_ARM32.class)) {
                return gettimeofday_time64(tv, tz);
            }
            return gettimeofday_default(tv, tz);
        }

        @CFunction(value = "__localtime64_r", transition = CFunction.Transition.NO_TRANSITION)
        @Platforms(Platform.LINUX_ARM32.class)
        private static native tm localtime_r_time64(CLongPointer timep, tm result);

        @CFunction(value = "localtime_r", transition = CFunction.Transition.NO_TRANSITION)
        private static native tm localtime_r_default(CLongPointer timep, tm result);

        public static tm localtime_r(CLongPointer timep, tm result) {
            if (Platform.includedIn(Platform.LINUX_ARM32.class)) {
                return localtime_r_time64(timep, result);
            }
            return localtime_r_default(timep, result);
        }

        @CFunction(value = "__nanosleep64", transition = Transition.NO_TRANSITION)
        @Platforms(Platform.LINUX_ARM32.class)
        private static native int nanosleep_time64(timespec requestedtime, timespec remaining);

        @CFunction(value = "nanosleep", transition = Transition.NO_TRANSITION)
        private static native int nanosleep_default(timespec requestedtime, timespec remaining);

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static int nanosleep(timespec requestedtime, timespec remaining) {
            if (Platform.includedIn(Platform.LINUX_ARM32.class)) {
                return nanosleep_time64(requestedtime, remaining);
            }
            return nanosleep_default(requestedtime, remaining);
        }
    }
}
