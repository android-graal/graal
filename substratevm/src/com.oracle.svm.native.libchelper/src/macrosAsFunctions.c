/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation. Oracle designates this
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

#if (defined(__APPLE__) || defined(__linux__))
/*
 * Macros from <sys/select.h> made available as C functions.
 */

#include <sys/select.h>

void sys_select_FD_SET(int fd, fd_set *set) {
  FD_SET(fd, set);
}

void sys_select_FD_ZERO(fd_set *set) {
  FD_ZERO(set);
}
#endif // (defined(__APPLE__) || defined(__linux__))

#if (defined(__APPLE__) || defined(__linux__))
/*
 * Macros from <sys/param.h> made available as C functions.
 */

#include <sys/param.h>

int sys_param_howmany(int x, int y) {
  return howmany(x, y);
}
#endif // (defined(__APPLE__) || defined(__linux__))

#if defined(__arm__) && !defined(__ARM_DWARF_EH__)
/*
 * Macros from the ARM EHABI <unwind.h> (GCC's unwind-arm-common.h) made available as C functions.
 */

#include <unwind.h>

#ifdef __ARM_EABI_UNWINDER__
/*
 * Only GCC's header defines __ARM_EABI_UNWINDER__ and these three as macros. With clang's header,
 * libunwind exports them as functions.
 */
_Unwind_Word (_Unwind_GetIP)(struct _Unwind_Context *context) {
  return _Unwind_GetIP(context);
}

_Unwind_Word (_Unwind_GetIPInfo)(struct _Unwind_Context *context, int *ip_before_insn) {
  return _Unwind_GetIPInfo(context, ip_before_insn);
}

void (_Unwind_SetIP)(struct _Unwind_Context *context, _Unwind_Word val) {
  _Unwind_SetIP(context, val);
}
#endif // __ARM_EABI_UNWINDER__

/*
 * Unlike the three above, _Unwind_SetGR is a static inline function in the header, not a macro, so
 * it can't keep its name. It is renamed instead.
 */
void _Unwind_SetGR_EHABI(struct _Unwind_Context *context, int regno, _Unwind_Word val) {
  _Unwind_SetGR(context, regno, val);
}
#endif // defined(__arm__) && !defined(__ARM_DWARF_EH__)
