/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.llvm.runtime;

import static com.oracle.svm.shared.util.VMError.shouldNotReachHere;
import static org.graalvm.nativeimage.c.function.CFunction.Transition.NO_TRANSITION;

import java.lang.reflect.Executable;
import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CFieldAddress;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.llvm.util.LLVMDirectives;
import com.oracle.svm.core.graal.nodes.WriteCurrentVMThreadNode;
import com.oracle.svm.core.graal.snippets.CEntryPointSnippets;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.snippets.ExceptionUnwind;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.guest.staging.c.function.CEntryPointOptions;
import com.oracle.svm.hosted.code.CEntryPointCallStubSupport;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.DisallowLayered;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@CContext(LLVMDirectives.class)
public class LLVMExceptionUnwind {

    /*
     * Exception handling using libunwind happens using the following steps:
     *
     * 1. The exception is raised using _Unwind_RaiseException
     *
     * 2. libunwind walks the stack twice, and for each Java call frame calls the personality
     * function below: personalityItanium, or personalityEHABI on 32-bit ARM, which has its own
     * signature, must advance the frame itself through __gnu_unwind_frame, and publishes the
     * control block to virtual r12 for the unwinder's helper functions. Everything past that is
     * shared in personalityCommon.
     *
     * 3. During the first stack walk (UA_SEARCH_PHASE; _US_VIRTUAL_UNWIND_FRAME on EHABI), the
     * personality function tells whether it has a registered handler able to handle the exception
     * (URC_HANDLER_FOUND).
     *
     * 4. During the second stack walk (UA_CLEANUP_PHASE; _US_UNWIND_FRAME_STARTING or _US_UNWIND_FRAME_RESUME on EHABI),
     *  the frame that accepted the exception prepares the context to jump to the handler (URC_INSTALL_CONTEXT).
     *
     * In order for the personality function to function normally, it needs the context from the
     * thread which threw the exception to be restored when the function is called.
     * The thread is written in the SVM extension behind the control block.
     * The actual exception is then retrieved from the thread-local variable ExceptionUnwind.currentException.
     */

    /** The vendor/language identifier this runtime writes into {@code exception_class}. */
    private static final long SVM_EXCEPTION_CLASS = ((long) 'O') |
                    ((long) 'R' << 8) |
                    ((long) 'C' << 16) |
                    ((long) 'L' << 24) |
                    ((long) 'S' << 32) |
                    ((long) 'V' << 40) |
                    ((long) 'M' << 48) |
                    (0L << 56);

    /** Second, private check on the SVM extension. See {@link #isSVMControlBlock}. */
    private static final long SVM_UNWIND_MAGIC = ((long) 'S') |
                    ((long) 'V' << 8) |
                    ((long) 'M' << 16) |
                    ((long) 'U' << 24) |
                    ((long) 'C' << 32) |
                    ((long) 'B' << 40) |
                    ((long) '0' << 48) |
                    ((long) '1' << 56);

    private static final int SVM_EXT_MAGIC_OFFSET = 0;
    private static final int SVM_EXT_THREAD_OFFSET = 8;

    @CEntryPoint(include = IncludeForItaniumUnwind.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = InitializeReservedRegistersFromUCBItaniumPrologue.class, epilogue = CEntryPointOptions.NoEpilogue.class)
    @Uninterruptible(reason = "Must not execute a recurring callback before returning", calleeMustBe = false)
    @SuppressWarnings("unused")
    public static int personalityItanium(int version, int action, long exceptionClass, _Unwind_Exception unwindException, _Unwind_Context context) {
        if ((action & _UA_FORCE_UNWIND()) != 0 || !isSVMControlBlock(unwindException)) {
            /*
             * We are not allowed to intercept a forced unwind, and we also don't want to install a
             * Java handler for non-Java exceptions.
             */
            return continueUnwind(unwindException, context);
        }

        if ((action & _UA_SEARCH_PHASE()) != 0) {
            return personalityCommon(true, unwindException, context);
        } else if ((action & _UA_CLEANUP_PHASE()) != 0) {
            return personalityCommon(false, unwindException, context);
        } else {
            return _URC_FATAL_PHASE1_ERROR();
        }
    }

    @Platforms(Platform.ARM32.class)
    @CEntryPoint(include = IncludeForEHABI.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(prologue = InitializeReservedRegistersFromUCBPrologue.class, epilogue = CEntryPointOptions.NoEpilogue.class)
    @Uninterruptible(reason = "Must not execute a recurring callback before returning", calleeMustBe = false)
    public static int personalityEHABI(int state, _Unwind_Exception ucbp, _Unwind_Context context) {
        if ((state & EHABI._US_FORCE_UNWIND()) != 0 || !isSVMControlBlock(ucbp)) {
            /*
             * We are not allowed to intercept a forced unwind, and we also don't want to install a
             * Java handler for non-Java exceptions.
             */
            return continueUnwind(ucbp, context);
        }

        EHABI._Unwind_SetGR(context, EHABI.UNWIND_POINTER_REG, (int) ucbp.rawValue());

        int action = state & EHABI._US_ACTION_MASK();
        if (action == EHABI._US_VIRTUAL_UNWIND_FRAME()) {
            return personalityCommon(true, ucbp, context);
        } else if (action == EHABI._US_UNWIND_FRAME_STARTING() || action == EHABI._US_UNWIND_FRAME_RESUME()) {
            return personalityCommon(false, ucbp, context);
        } else {
            return EHABI._URC_FAILURE();
        }
    }

    @Uninterruptible(reason = "Must not execute a recurring callback before returning", calleeMustBe = false)
    private static int personalityCommon(boolean searchPhase, _Unwind_Exception ucbp, _Unwind_Context context) {
        CIntPointer ipBeforeInstruction = UnsafeStackValue.get(Integer.BYTES);
        Pointer ip = getIPInfo(context, ipBeforeInstruction);
        if (ipBeforeInstruction.read() == 0) {
            ip = ip.subtract(1);
        }
        Pointer functionStart = getRegionStart(context);
        long pcOffset = ip.rawValue() - functionStart.rawValue();

        Pointer lsda = getLanguageSpecificData(context);
        long handlerInfo = GCCExceptionTable.getHandlerInfo(lsda, pcOffset);

        if (handlerInfo == GCCExceptionTable.NO_HANDLER) {
            return continueUnwind(ucbp, context);
        }

        if (searchPhase) {
            if (GCCExceptionTable.isCleanup(handlerInfo)) {
                return continueUnwind(ucbp, context);
            }
            return _URC_HANDLER_FOUND();
        }

        setIP(context, functionStart.add((int) GCCExceptionTable.getHandlerOffset(handlerInfo)));
        Throwable exception = ExceptionUnwind.currentException.get();
        if (!GCCExceptionTable.isCleanup(handlerInfo) && exception instanceof StackOverflowError && StackOverflowCheck.singleton().isYellowZoneAvailable()) {
            StackOverflowCheck.singleton().protectYellowZone();
        }
        return _URC_INSTALL_CONTEXT();
    }

    /**
     * Decline this frame and ask the unwinder to carry on. On EHABI advancing the virtual register
     * set to the caller's frame is the personality routine's job, in both phases. On Itanium
     * ABI the unwinder steps frames itself.
     */
    @Uninterruptible(reason = "Called from the personality function while unwinding.")
    private static int continueUnwind(_Unwind_Exception ucbp, _Unwind_Context context) {
        if (Platform.includedIn(Platform.ARM32.class)) {
            if (EHABI.__gnu_unwind_frame(ucbp, context) != EHABI._URC_OK()) {
                return EHABI._URC_FAILURE();
            }
        }
        return _URC_CONTINUE_UNWIND();
    }

    @Platforms(Platform.ARM32.class)
    public static final class InitializeReservedRegistersFromUCBPrologue implements CEntryPointOptions.Prologue {
        @Uninterruptible(reason = "prologue")
        public static void enter(int state, _Unwind_Exception ucbp) {
            if ((state & EHABI._US_FORCE_UNWIND()) != 0 || !isSVMControlBlock(ucbp)) {
                /*
                 * We are not allowed to intercept a forced unwind, and we also don't want to install a
                 * Java handler for non-Java exceptions.
                 */
                return;
            }
            WriteCurrentVMThreadNode.writeCurrentVMThread(svmControlBlockThread(ucbp));
            CEntryPointSnippets.initBaseRegisters(VMThreads.IsolateTL.get());
        }
    }

    public static final class InitializeReservedRegistersFromUCBItaniumPrologue implements CEntryPointOptions.Prologue {
        @Uninterruptible(reason = "prologue")
        @SuppressWarnings("unused")
        public static void enter(int version, int action, _Unwind_Exception ucbp) {
            if ((action & _UA_FORCE_UNWIND()) != 0 || !isSVMControlBlock(ucbp)) {
                /*
                 * We are not allowed to intercept a forced unwind, and we also don't want to install a
                 * Java handler for non-Java exceptions.
                 */
                return;
            }
            WriteCurrentVMThreadNode.writeCurrentVMThread(svmControlBlockThread(ucbp));
            CEntryPointSnippets.initBaseRegisters(VMThreads.IsolateTL.get());
        }
    }

    @Uninterruptible(reason = "Called from the personality routine before the thread register is established.")
    private static Pointer svmExtension(_Unwind_Exception ucbp) {
        return ((Pointer) ucbp).add(SizeOf.get(_Unwind_Exception.class));
    }

    @Uninterruptible(reason = "Called from the personality routine before the thread register is established.")
    private static boolean isSVMControlBlock(_Unwind_Exception ucbp) {
        if (ucbp.addressOfExceptionClass().readLong(0) != SVM_EXCEPTION_CLASS) {
            return false;
        }
        return svmExtension(ucbp).readLong(SVM_EXT_MAGIC_OFFSET) == SVM_UNWIND_MAGIC;
    }

    @Uninterruptible(reason = "Called from the personality routine before the thread register is established.")
    private static IsolateThread svmControlBlockThread(_Unwind_Exception ucbp) {
        return svmExtension(ucbp).readWord(SVM_EXT_THREAD_OFFSET);
    }

    @Uninterruptible(reason = "Called before Java state is restored")
    public static Throwable retrieveException() {
        Throwable exception = ExceptionUnwind.currentException.get();
        ExceptionUnwind.currentException.set(null);
        return exception;
    }

    private static final class IncludeForItaniumUnwind implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return SubstrateOptions.useLLVMBackend() && !Platform.includedIn(Platform.ARM32.class);
        }
    }

    private static final class IncludeForEHABI implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return SubstrateOptions.useLLVMBackend() && Platform.includedIn(Platform.ARM32.class);
        }
    }

    public static ResolvedJavaMethod getPersonalityStub(MetaAccessProvider metaAccess) {
        try {
            Executable personalityMethod = Platform.includedIn(Platform.ARM32.class)
                            ? LLVMExceptionUnwind.class.getMethod("personalityEHABI", int.class, _Unwind_Exception.class, _Unwind_Context.class)
                            : LLVMExceptionUnwind.class.getMethod("personalityItanium", int.class, int.class, long.class, _Unwind_Exception.class, _Unwind_Context.class);
            return ((UniverseMetaAccess) metaAccess).getUniverse().lookup(CEntryPointCallStubSupport.singleton().getStubForMethod(personalityMethod));
        } catch (NoSuchMethodException e) {
            throw shouldNotReachHere(e);
        }
    }

    public static ResolvedJavaMethod getRetrieveExceptionMethod(MetaAccessProvider metaAccess) {
        try {
            return metaAccess.lookupJavaMethod(LLVMExceptionUnwind.class.getMethod("retrieveException"));
        } catch (NoSuchMethodException e) {
            throw shouldNotReachHere(e);
        }
    }

    public static ExceptionUnwind createRaiseExceptionHandler() {
        return new LLVMExceptionUnwindHandler();
    }

    @SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, other = DisallowLayered.class)
    private static final class LLVMExceptionUnwindHandler extends ExceptionUnwind {
        @Override
        @Uninterruptible(reason = "Code that is fully uninterruptible may throw and catch exceptions. Therefore, the exception handling must be fully uninterruptible as well.")
        protected void customUnwindException(Pointer callerSP) {
            /*
             * Two elements: the first is the control block the unwinder sees, the second is
             * scratch space for svmExtension().
             */
            _Unwind_Exception exceptionStructure = UnsafeStackValue.get(2, _Unwind_Exception.class);
            exceptionStructure.addressOfExceptionClass().writeLong(0, SVM_EXCEPTION_CLASS);
            Pointer extension = svmExtension(exceptionStructure);
            extension.writeLong(SVM_EXT_MAGIC_OFFSET, SVM_UNWIND_MAGIC);
            extension.writeWord(SVM_EXT_THREAD_OFFSET, CurrentIsolate.getCurrentThread());
            exceptionStructure.set_exception_cleanup(WordFactory.nullPointer());
            raiseException(exceptionStructure);
        }
    }

    // Allow methods with non-standard names: Checkstyle: stop

    // The following declarations are from <unwind.h>.
    //
    // See:
    // - https://clang.llvm.org/doxygen/unwind_8h_source.html
    // - https://gcc.gnu.org/git/?p=gcc.git;a=blob;f=libgcc/unwind-generic.h

    /* _Unwind_Reason_Code */
    @CConstant(include = IncludeForItaniumUnwind.class)
    private static native int _URC_FATAL_PHASE1_ERROR();

    @CConstant
    private static native int _URC_HANDLER_FOUND();

    @CConstant
    private static native int _URC_INSTALL_CONTEXT();

    @CConstant
    private static native int _URC_CONTINUE_UNWIND();

    /* _Unwind_Action */
    @CConstant
    private static native int _UA_SEARCH_PHASE();

    @CConstant
    private static native int _UA_CLEANUP_PHASE();

    @CConstant
    private static native int _UA_FORCE_UNWIND();

    @CStruct(addStructKeyword = true)
    private interface _Unwind_Exception extends PointerBase {
        @CFieldAddress("exception_class")
        Pointer addressOfExceptionClass();

        @CField
        PointerBase exception_cleanup();

        @CField
        void set_exception_cleanup(PointerBase value);
    }

    @CStruct(addStructKeyword = true, isIncomplete = true)
    private interface _Unwind_Context extends PointerBase {
    }

    @CFunction(value = "_Unwind_RaiseException", transition = NO_TRANSITION)
    public static native int raiseException(_Unwind_Exception exception);

    @CFunction(value = "_Unwind_GetIP", transition = NO_TRANSITION)
    public static native Pointer getIP(_Unwind_Context context);

    @CFunction(value = "_Unwind_GetIPInfo", transition = NO_TRANSITION)
    public static native Pointer getIPInfo(_Unwind_Context context, CIntPointer ipBeforeInstruction);

    @CFunction(value = "_Unwind_SetIP", transition = NO_TRANSITION)
    public static native Pointer setIP(_Unwind_Context context, Pointer ip);

    @CFunction(value = "_Unwind_GetRegionStart", transition = NO_TRANSITION)
    public static native Pointer getRegionStart(_Unwind_Context context);

    @CFunction(value = "_Unwind_GetLanguageSpecificData", transition = NO_TRANSITION)
    public static native Pointer getLanguageSpecificData(_Unwind_Context context);

    @Platforms(Platform.ARM32.class)
    public static final class EHABI {

        /* _Unwind_Reason_Code, the two values the generic ABI has no counterpart for. */
        @CConstant
        public static native int _URC_OK();

        @CConstant
        public static native int _URC_FAILURE();

        /* _Unwind_State. Note that these are enum values, not flags, below _US_ACTION_MASK. */
        @CConstant
        public static native int _US_VIRTUAL_UNWIND_FRAME();

        @CConstant
        public static native int _US_UNWIND_FRAME_STARTING();

        @CConstant
        public static native int _US_UNWIND_FRAME_RESUME();

        @CConstant
        public static native int _US_ACTION_MASK();

        @CConstant
        public static native int _US_FORCE_UNWIND();

        /* r12, GCC's UNWIND_POINTER_REG. clang's unwind.h does not define it. */
        public static final int UNWIND_POINTER_REG = 12;

        @CFunction(value = "__gnu_unwind_frame", transition = NO_TRANSITION)
        public static native int __gnu_unwind_frame(_Unwind_Exception ucbp, _Unwind_Context context);

        @CFunction(value = "_Unwind_SetGR_EHABI", transition = NO_TRANSITION)
        public static native void _Unwind_SetGR(_Unwind_Context context, int regno, int value);
    }
}

// Checkstyle: resume
