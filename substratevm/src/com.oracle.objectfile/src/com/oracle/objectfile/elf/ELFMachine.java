/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.objectfile.elf;

import java.util.Locale;

import com.oracle.objectfile.ObjectFile.RelocationKind;
import com.oracle.objectfile.ObjectFile.RelocationMethod;
import com.oracle.objectfile.elf.ELFRelocationSection.ELFRelocationMethod;

import jdk.graal.compiler.serviceprovider.GraalServices;

/**
 * ELF machine type (incomplete). Each machine type also defines its set of relocation types.
 */
public enum ELFMachine/* implements Integral */ {
    X86_64 {
        @Override
        Class<? extends Enum<? extends RelocationMethod>> relocationTypes() {
            return ELFX86_64Relocation.class;
        }

        @Override
        int flags() {
            /*
             * e_flags are always 0 for X86
             */
            return NO_FLAGS;
        }

        @Override
        ELFObjectFile.ELFClass elfClass() {
            return ELFObjectFile.ELFClass.ELFCLASS64;
        }
    },
    AArch64 {
        @Override
        Class<? extends Enum<? extends RelocationMethod>> relocationTypes() {
            return ELFAArch64Relocation.class;
        }

        @Override
        int flags() {
            /*
             * e_flags are always 0 for AArch64
             */
            return NO_FLAGS;
        }

        @Override
        ELFObjectFile.ELFClass elfClass() {
            return ELFObjectFile.ELFClass.ELFCLASS64;
        }
    },
    RISCV64 {
        @Override
        Class<? extends Enum<? extends RelocationMethod>> relocationTypes() {
            return ELFRISCV64Relocation.class;
        }

        @Override
        int flags() {
            /*
             * Since we use the most powerful rv64gc model variant, we need to set e_flags to 5,
             * which are RVC and double-float ABI.
             */
            return RVC_DOUBLE_FLOAT_ABI;
        }

        @Override
        ELFObjectFile.ELFClass elfClass() {
            return ELFObjectFile.ELFClass.ELFCLASS64;
        }
    },
    ARM32 {
        @Override
        Class<? extends Enum<? extends RelocationMethod>> relocationTypes() {
            return ELFARM32Relocation.class;
        }

        @Override
        int flags() {
            return ARM_EABI_VER5;
        }

        @Override
        ELFObjectFile.ELFClass elfClass() {
            return ELFObjectFile.ELFClass.ELFCLASS32;
        }
    };

    private static final int NO_FLAGS = 0;
    private static final int RVC_DOUBLE_FLOAT_ABI = 5;
    private static final int ARM_EABI_VER5 = 0x05000000;
    private static final int ARM_EABI_HARD_FLOAT = 0x00000400;
    private static final int ARM_EABI_SOFT_FLOAT = 0x00000200;
    private static final int ARM_EABI_VER5_HARD_FLOAT = ARM_EABI_VER5 | ARM_EABI_HARD_FLOAT;
    private static final int ARM_EABI_VER5_SOFT_FLOAT = ARM_EABI_VER5 | ARM_EABI_SOFT_FLOAT;

    abstract Class<? extends Enum<? extends RelocationMethod>> relocationTypes();

    abstract int flags();

    abstract ELFObjectFile.ELFClass elfClass();

    public static ELFMachine from(String s) {
        switch (s.toLowerCase(Locale.ROOT)) {
            case "amd64":
            case "x86_64":
                return X86_64;
            case "arm64":
            case "aarch64":
                return AArch64;
            case "riscv64":
                return RISCV64;
            case "arm":
                return ARM32;
        }
        throw new IllegalStateException("Unknown CPU type: " + s);
    }

    public static ELFRelocationMethod getRelocation(ELFMachine m, RelocationKind k) {
        switch (m) {
            case X86_64:
                switch (k) {
                    case DIRECT_1:
                        return ELFX86_64Relocation.R_8;
                    case DIRECT_2:
                        return ELFX86_64Relocation.R_16;
                    case DIRECT_4:
                        return ELFX86_64Relocation.R_32;
                    case DIRECT_8:
                        return ELFX86_64Relocation.R_64;
                    case PC_RELATIVE_1:
                        return ELFX86_64Relocation.R_PC8;
                    case PC_RELATIVE_2:
                        return ELFX86_64Relocation.R_PC16;
                    case PC_RELATIVE_4:
                        return ELFX86_64Relocation.R_PC32;
                    case PC_RELATIVE_8:
                        return ELFX86_64Relocation.R_PC64;
                    default:
                    case UNKNOWN:
                        throw new IllegalArgumentException("Cannot map unknown relocation kind to an ELF x86-64 relocation type");
                }
            case AArch64:
                switch (k) {
                    case DIRECT_2:
                        return ELFAArch64Relocation.R_AARCH64_ABS16;
                    case DIRECT_4:
                        return ELFAArch64Relocation.R_AARCH64_ABS32;
                    case DIRECT_8:
                        return ELFAArch64Relocation.R_AARCH64_ABS64;
                    case AARCH64_R_MOVW_UABS_G0:
                        return ELFAArch64Relocation.R_AARCH64_MOVW_UABS_G0;
                    case AARCH64_R_MOVW_UABS_G0_NC:
                        return ELFAArch64Relocation.R_AARCH64_MOVW_UABS_G0_NC;
                    case AARCH64_R_MOVW_UABS_G1:
                        return ELFAArch64Relocation.R_AARCH64_MOVW_UABS_G1;
                    case AARCH64_R_MOVW_UABS_G1_NC:
                        return ELFAArch64Relocation.R_AARCH64_MOVW_UABS_G1_NC;
                    case AARCH64_R_MOVW_UABS_G2:
                        return ELFAArch64Relocation.R_AARCH64_MOVW_UABS_G2;
                    case AARCH64_R_MOVW_UABS_G2_NC:
                        return ELFAArch64Relocation.R_AARCH64_MOVW_UABS_G2_NC;
                    case AARCH64_R_MOVW_UABS_G3:
                        return ELFAArch64Relocation.R_AARCH64_MOVW_UABS_G3;
                    case AARCH64_R_AARCH64_ADR_PREL_PG_HI21:
                        return ELFAArch64Relocation.R_AARCH64_ADR_PREL_PG_HI21;
                    case AARCH64_R_AARCH64_ADD_ABS_LO12_NC:
                        return ELFAArch64Relocation.R_AARCH64_ADD_ABS_LO12_NC;
                    case AARCH64_R_GOT_LD_PREL19:
                        return ELFAArch64Relocation.R_AARCH64_GOT_LD_PREL19;
                    case AARCH64_R_LD_PREL_LO19:
                        return ELFAArch64Relocation.R_AARCH64_LD_PREL_LO19;
                    case AARCH64_R_AARCH64_LDST128_ABS_LO12_NC:
                        return ELFAArch64Relocation.R_AARCH64_LDST128_ABS_LO12_NC;
                    case AARCH64_R_AARCH64_LDST64_ABS_LO12_NC:
                        return ELFAArch64Relocation.R_AARCH64_LDST64_ABS_LO12_NC;
                    case AARCH64_R_AARCH64_LDST32_ABS_LO12_NC:
                        return ELFAArch64Relocation.R_AARCH64_LDST32_ABS_LO12_NC;
                    case AARCH64_R_AARCH64_LDST16_ABS_LO12_NC:
                        return ELFAArch64Relocation.R_AARCH64_LDST16_ABS_LO12_NC;
                    case AARCH64_R_AARCH64_LDST8_ABS_LO12_NC:
                        return ELFAArch64Relocation.R_AARCH64_LDST8_ABS_LO12_NC;
                    default:
                    case UNKNOWN:
                        throw new IllegalArgumentException("Cannot map unknown relocation kind to an ELF aarch64 relocation type: " + k);

                }
            case RISCV64:
                switch (k) {
                    case DIRECT_8:
                        return ELFRISCV64Relocation.R_RISCV_64;
                    default:
                    case UNKNOWN:
                        throw new IllegalArgumentException("Cannot map unknown relocation kind to an ELF riscv64 relocation type: " + k);
                }
            case ARM32:
                switch (k) {
                    case DIRECT_1:
                        return ELFARM32Relocation.R_ARM_ABS8;
                    case DIRECT_2:
                        return ELFARM32Relocation.R_ARM_ABS16;
                    case DIRECT_4:
                        return ELFARM32Relocation.R_ARM_ABS32;
                    case PC_RELATIVE_4:
                        return ELFARM32Relocation.R_ARM_REL32;
                    default:
                    case UNKNOWN:
                        throw new IllegalArgumentException("Cannot map unknown relocation kind to an ELF arm32 relocation type: " + k);
                }
            default:
                throw new IllegalStateException("Unknown ELF machine type");
        }
    }

    // TODO: use explicit enum values
    public static ELFMachine from(int m) {
        switch (m) {
            case 0x28:
                return ARM32;
            case 0x3E:
                return X86_64;
            case 0xB7:
                return AArch64;
            case 0xF3:
                return RISCV64;
            default:
                throw new IllegalStateException("Unknown ELF machine type");
        }
    }

    public short toShort() {
        if (this == AArch64) {
            return 0xB7;
        } else if (this == X86_64) {
            return 0x3E;
        } else if (this == RISCV64) {
            return 0xF3;
        } else if (this == ARM32) {
            return 0x28;
        } else {
            throw new IllegalStateException("Should not reach here");
        }
    }
}

enum ELFX86_64Relocation implements ELFRelocationMethod {
    // These are all named R_X86_64_... in elf.h,
    // but we just use R_... to keep it short.
    // We need the "R_" because some begin with digits.
    R_NONE,
    R_64,
    R_PC32,
    R_GOT32,
    R_PLT32,
    R_COPY,
    R_GLOB_DAT,
    R_JUMP_SLOT,
    R_RELATIVE,
    R_GOTPCREL,
    R_32,
    R_32S,
    R_16,
    R_PC16,
    R_8,
    R_PC8,
    R_DTPMOD64,
    R_DTPOFF64,
    R_TPOFF64,
    R_TLSGD,
    R_TLSLD,
    R_DTPOFF32,
    R_GOTTPOFF,
    R_TPOFF32,
    R_PC64,
    R_GOTOFF64,
    R_GOTPC32,
    R_GOT64,
    R_GOTPCREL64,
    R_GOTPC64,
    R_GOTPLT64,
    R_PLTOFF64,
    R_SIZE32,
    R_SIZE64,
    R_GOTPC32_TLSDESC,
    R_TLSDESC_CALL,
    R_TLSDESC,
    R_IRELATIVE,
    R_RELATIVE64,
    R_COUNT;

    static {
        // spot check
        assert R_COUNT.ordinal() == 39;
    }

    @Override
    public long toLong() {
        return ordinal();
    }
}

/**
 * Reference: https://developer.arm.com/docs/ihi0056/latest.
 */
enum ELFAArch64Relocation implements ELFRelocationMethod {
    R_AARCH64_NONE(0),
    R_AARCH64_ABS64(0x101),
    R_AARCH64_ABS32(0x102),
    R_AARCH64_ABS16(0x103),
    R_AARCH64_PREL64(0x104),
    R_AARCH64_PREL32(0x105),
    R_AARCH64_PREL16(0x106),
    R_AARCH64_MOVW_UABS_G0(0x107),
    R_AARCH64_MOVW_UABS_G0_NC(0x108),
    R_AARCH64_MOVW_UABS_G1(0x109),
    R_AARCH64_MOVW_UABS_G1_NC(0x10a),
    R_AARCH64_MOVW_UABS_G2(0x10b),
    R_AARCH64_MOVW_UABS_G2_NC(0x10c),
    R_AARCH64_MOVW_UABS_G3(0x10d),
    R_AARCH64_MOVW_SABS_G0(0x10e),
    R_AARCH64_MOVW_SABS_G1(0x10f),
    R_AARCH64_MOVW_SABS_G2(0x110),
    R_AARCH64_LD_PREL_LO19(0x111),
    R_AARCH64_ADR_PREL_LO21(0x112),
    R_AARCH64_ADR_PREL_PG_HI21(0x113),
    R_AARCH64_ADR_PREL_PG_HI21_NC(0x114),
    R_AARCH64_ADD_ABS_LO12_NC(0x115),
    R_AARCH64_LDST8_ABS_LO12_NC(0x116),
    R_AARCH64_TSTBR14(0x117),
    R_AARCH64_CONDBR19(0x118),
    R_AARCH64_JUMP26(0x11a),
    R_AARCH64_CALL26(0x11b),
    R_AARCH64_LDST16_ABS_LO12_NC(0x11c),
    R_AARCH64_LDST32_ABS_LO12_NC(0x11d),
    R_AARCH64_LDST64_ABS_LO12_NC(0x11e),
    R_AARCH64_MOVW_PREL_G0(0x11f),
    R_AARCH64_MOVW_PREL_G0_NC(0x120),
    R_AARCH64_MOVW_PREL_G1(0x121),
    R_AARCH64_MOVW_PREL_G1_NC(0x122),
    R_AARCH64_MOVW_PREL_G2(0x123),
    R_AARCH64_MOVW_PREL_G2_NC(0x124),
    R_AARCH64_MOVW_PREL_G3(0x125),
    R_AARCH64_LDST128_ABS_LO12_NC(0x12b),
    R_AARCH64_MOVW_GOTOFF_G0(0x12c),
    R_AARCH64_MOVW_GOTOFF_G0_NC(0x12d),
    R_AARCH64_MOVW_GOTOFF_G1(0x12e),
    R_AARCH64_MOVW_GOTOFF_G1_NC(0x12f),
    R_AARCH64_MOVW_GOTOFF_G2(0x130),
    R_AARCH64_MOVW_GOTOFF_G2_NC(0x131),
    R_AARCH64_MOVW_GOTOFF_G3(0x132),
    R_AARCH64_GOTREL64(0x133),
    R_AARCH64_GOTREL32(0x134),
    R_AARCH64_GOT_LD_PREL19(0x135),
    R_AARCH64_LD64_GOTOFF_LO15(0x136),
    R_AARCH64_ADR_GOT_PAGE(0x137),
    R_AARCH64_LD64_GOT_LO12_NC(0x138),
    R_AARCH64_LD64_GOTPAGE_LO15(0x139),
    R_AARCH64_TLSGD_ADR_PREL21(0x200),
    R_AARCH64_TLSGD_ADR_PAGE21(0x201),
    R_AARCH64_TLSGD_ADD_LO12_NC(0x202),
    R_AARCH64_TLSGD_MOVW_G1(0x203),
    R_AARCH64_TLSGD_MOVW_G0_NC(0x204),
    R_AARCH64_TLSLD_ADR_PREL21(0x205),
    R_AARCH64_TLSLD_ADR_PAGE21(0x206),
    R_AARCH64_TLSLD_ADD_LO12_NC(0x207),
    R_AARCH64_TLSLD_MOVW_G1(0x208),
    R_AARCH64_TLSLD_MOVW_G0_NC(0x209),
    R_AARCH64_TLSLD_LD_PREL19(0x20a),
    R_AARCH64_TLSLD_MOVW_DTPREL_G2(0x20b),
    R_AARCH64_TLSLD_MOVW_DTPREL_G1(0x20c),
    R_AARCH64_TLSLD_MOVW_DTPREL_G1_NC(0x20d),
    R_AARCH64_TLSLD_MOVW_DTPREL_G0(0x20e),
    R_AARCH64_TLSLD_MOVW_DTPREL_G0_NC(0x20f),
    R_AARCH64_TLSLD_ADD_DTPREL_HI12(0x210),
    R_AARCH64_TLSLD_ADD_DTPREL_LO12(0x211),
    R_AARCH64_TLSLD_ADD_DTPREL_LO12_NC(0x212),
    R_AARCH64_TLSLD_LDST8_DTPREL_LO12(0x213),
    R_AARCH64_TLSLD_LDST8_DTPREL_LO12_NC(0x214),
    R_AARCH64_TLSLD_LDST16_DTPREL_LO12(0x215),
    R_AARCH64_TLSLD_LDST16_DTPREL_LO12_NC(0x216),
    R_AARCH64_TLSLD_LDST32_DTPREL_LO12(0x217),
    R_AARCH64_TLSLD_LDST32_DTPREL_LO12_NC(0x218),
    R_AARCH64_TLSLD_LDST64_DTPREL_LO12(0x219),
    R_AARCH64_TLSLD_LDST64_DTPREL_LO12_NC(0x21a),
    R_AARCH64_TLSIE_MOVW_GOTTPREL_G1(0x21b),
    R_AARCH64_TLSIE_MOVW_GOTTPREL_G0_NC(0x21c),
    R_AARCH64_TLSIE_ADR_GOTTPREL_PAGE21(0x21d),
    R_AARCH64_TLSIE_LD64_GOTTPREL_LO12_NC(0x21e),
    R_AARCH64_TLSIE_LD_GOTTPREL_PREL19(0x21f),
    R_AARCH64_TLSLE_MOVW_TPREL_G2(0x220),
    R_AARCH64_TLSLE_MOVW_TPREL_G1(0x221),
    R_AARCH64_TLSLE_MOVW_TPREL_G1_NC(0x222),
    R_AARCH64_TLSLE_MOVW_TPREL_G0(0x223),
    R_AARCH64_TLSLE_MOVW_TPREL_G0_NC(0x224),
    R_AARCH64_TLSLE_ADD_TPREL_HI12(0x225),
    R_AARCH64_TLSLE_ADD_TPREL_LO12(0x226),
    R_AARCH64_TLSLE_ADD_TPREL_LO12_NC(0x227),
    R_AARCH64_TLSLE_LDST8_TPREL_LO12(0x228),
    R_AARCH64_TLSLE_LDST8_TPREL_LO12_NC(0x229),
    R_AARCH64_TLSLE_LDST16_TPREL_LO12(0x22a),
    R_AARCH64_TLSLE_LDST16_TPREL_LO12_NC(0x22b),
    R_AARCH64_TLSLE_LDST32_TPREL_LO12(0x22c),
    R_AARCH64_TLSLE_LDST32_TPREL_LO12_NC(0x22d),
    R_AARCH64_TLSLE_LDST64_TPREL_LO12(0x22e),
    R_AARCH64_TLSLE_LDST64_TPREL_LO12_NC(0x22f),
    R_AARCH64_TLSDESC_LD_PREL19(0x230),
    R_AARCH64_TLSDESC_ADR_PREL21(0x231),
    R_AARCH64_TLSDESC_ADR_PAGE21(0x232),
    R_AARCH64_TLSDESC_LD64_LO12_NC(0x233),
    R_AARCH64_TLSDESC_ADD_LO12_NC(0x234),
    R_AARCH64_TLSDESC_OFF_G1(0x235),
    R_AARCH64_TLSDESC_OFF_G0_NC(0x236),
    R_AARCH64_TLSDESC_LDR(0x237),
    R_AARCH64_TLSDESC_ADD(0x238),
    R_AARCH64_TLSDESC_CALL(0x239),
    R_AARCH64_TLSLE_LDST128_TPREL_LO12(0x23a),
    R_AARCH64_TLSLE_LDST128_TPREL_LO12_NC(0x23b),
    R_AARCH64_TLSLD_LDST128_DTPREL_LO12(0x23c),
    R_AARCH64_TLSLD_LDST128_DTPREL_LO12_NC(0x23d),
    R_AARCH64_COPY(0x400),
    R_AARCH64_GLOB_DAT(0x401),
    R_AARCH64_JUMP_SLOT(0x402),
    R_AARCH64_RELATIVE(0x403),
    R_AARCH64_TLS_DTPREL64(0x404),
    R_AARCH64_TLS_DTPMOD64(0x405),
    R_AARCH64_TLS_TPREL64(0x406),
    R_AARCH64_TLSDESC(0x407),
    R_AARCH64_IRELATIVE(0x408);

    private final long code;

    ELFAArch64Relocation(long code) {
        this.code = code;
    }

    @Override
    public long toLong() {
        return code;
    }
}

/**
 * ARM32 (AArch32) ELF relocation types.
 * Reference:<br>
 * <cite>https://github.com/ARM-software/abi-aa/blob/main/aaelf32/aaelf32.rst#relocation-codes</cite>
 */
enum ELFARM32Relocation implements ELFRelocationMethod {
    R_ARM_NONE(0),
    R_ARM_PC24(1),
    R_ARM_ABS32(2),
    R_ARM_REL32(3),
    R_ARM_LDR_PC_G0(4),
    R_ARM_ABS16(5),
    R_ARM_ABS12(6),
    R_ARM_THM_ABS5(7),
    R_ARM_ABS8(8),
    R_ARM_SBREL32(9),
    R_ARM_THM_CALL(10),
    R_ARM_THM_PC8(11),
    R_ARM_BREL_ADJ(12),
    R_ARM_TLS_DESC(13),
    R_ARM_THM_SWI8(14),
    R_ARM_XPC25(15),
    R_ARM_THM_XPC22(16),
    R_ARM_TLS_DTPMOD32(17),
    R_ARM_TLS_DTPOFF32(18),
    R_ARM_TLS_TPOFF32(19),
    R_ARM_COPY(20),
    R_ARM_GLOB_DAT(21),
    R_ARM_JUMP_SLOT(22),
    R_ARM_RELATIVE(23),
    R_ARM_GOTOFF32(24),
    R_ARM_BASE_PREL(25),
    R_ARM_GOT_BREL(26),
    R_ARM_PLT32(27),
    R_ARM_CALL(28),
    R_ARM_JUMP24(29),
    R_ARM_THM_JUMP24(30),
    R_ARM_BASE_ABS(31),
    R_ARM_ALU_PCREL_7_0(32),
    R_ARM_ALU_PCREL_15_8(33),
    R_ARM_ALU_PCREL_23_15(34),
    R_ARM_LDR_SBREL_11_0_NC(35),
    R_ARM_ALU_SBREL_19_12_NC(36),
    R_ARM_ALU_SBREL_27_20_CK(37),
    R_ARM_TARGET1(38),
    R_ARM_SBREL31(39),
    R_ARM_V4BX(40),
    R_ARM_TARGET2(41),
    R_ARM_PREL31(42),
    R_ARM_MOVW_ABS_NC(43),
    R_ARM_MOVT_ABS(44),
    R_ARM_MOVW_PREL_NC(45),
    R_ARM_MOVT_PREL(46),
    R_ARM_THM_MOVW_ABS_NC(47),
    R_ARM_THM_MOVT_ABS(48),
    R_ARM_THM_MOVW_PREL_NC(49),
    R_ARM_THM_MOVT_PREL(50),
    R_ARM_THM_JUMP19(51),
    R_ARM_THM_JUMP6(52),
    R_ARM_THM_ALU_PREL_11_0(53),
    R_ARM_THM_PC12(54),
    R_ARM_ABS32_NOI(55),
    R_ARM_REL32_NOI(56),
    R_ARM_ALU_PC_G0_NC(57),
    R_ARM_ALU_PC_G0(58),
    R_ARM_ALU_PC_G1_NC(59),
    R_ARM_ALU_PC_G1(60),
    R_ARM_ALU_PC_G2(61),
    R_ARM_LDR_PC_G1(62),
    R_ARM_LDR_PC_G2(63),
    R_ARM_LDRS_PC_G0(64),
    R_ARM_LDRS_PC_G1(65),
    R_ARM_LDRS_PC_G2(66),
    R_ARM_LDC_PC_G0(67),
    R_ARM_LDC_PC_G1(68),
    R_ARM_LDC_PC_G2(69),
    R_ARM_ALU_SB_G0_NC(70),
    R_ARM_ALU_SB_G0(71),
    R_ARM_ALU_SB_G1_NC(72),
    R_ARM_ALU_SB_G1(73),
    R_ARM_ALU_SB_G2(74),
    R_ARM_LDR_SB_G0(75),
    R_ARM_LDR_SB_G1(76),
    R_ARM_LDR_SB_G2(77),
    R_ARM_LDRS_SB_G0(78),
    R_ARM_LDRS_SB_G1(79),
    R_ARM_LDRS_SB_G2(80),
    R_ARM_LDC_SB_G0(81),
    R_ARM_LDC_SB_G1(82),
    R_ARM_LDC_SB_G2(83),
    R_ARM_MOVW_BREL_NC(84),
    R_ARM_MOVT_BREL(85),
    R_ARM_MOVW_BREL(86),
    R_ARM_THM_MOVW_BREL_NC(87),
    R_ARM_THM_MOVT_BREL(88),
    R_ARM_THM_MOVW_BREL(89),
    R_ARM_TLS_GOTDESC(90),
    R_ARM_TLS_CALL(91),
    R_ARM_TLS_DESCSEQ(92),
    R_ARM_THM_TLS_CALL(93),
    R_ARM_PLT32_ABS(94),
    R_ARM_GOT_ABS(95),
    R_ARM_GOT_PREL(96),
    R_ARM_GOT_BREL12(97),
    R_ARM_GOTOFF12(98),
    R_ARM_GOTRELAX(99),
    R_ARM_GNU_VTENTRY(100),
    R_ARM_GNU_VTINHERIT(101),
    R_ARM_THM_JUMP11(102),
    R_ARM_THM_JUMP8(103),
    R_ARM_TLS_GD32(104),
    R_ARM_TLS_LDM32(105),
    R_ARM_TLS_LDO32(106),
    R_ARM_TLS_IE32(107),
    R_ARM_TLS_LE32(108),
    R_ARM_TLS_LDO12(109),
    R_ARM_TLS_LE12(110),
    R_ARM_TLS_IE12GP(111),
    R_ARM_PRIVATE_0(112),
    R_ARM_PRIVATE_1(113),
    R_ARM_PRIVATE_2(114),
    R_ARM_PRIVATE_3(115),
    R_ARM_PRIVATE_4(116),
    R_ARM_PRIVATE_5(117),
    R_ARM_PRIVATE_6(118),
    R_ARM_PRIVATE_7(119),
    R_ARM_PRIVATE_8(120),
    R_ARM_PRIVATE_9(121),
    R_ARM_PRIVATE_10(122),
    R_ARM_PRIVATE_11(123),
    R_ARM_PRIVATE_12(124),
    R_ARM_PRIVATE_13(125),
    R_ARM_PRIVATE_14(126),
    R_ARM_PRIVATE_15(127),
    R_ARM_ME_TOO(128),
    R_ARM_THM_TLS_DESCSEQ16(129),
    R_ARM_THM_TLS_DESCSEQ32(130),
    R_ARM_THM_ALU_ABS_G0_NC(132),
    R_ARM_THM_ALU_ABS_G1_NC(133),
    R_ARM_THM_ALU_ABS_G2_NC(134),
    R_ARM_THM_ALU_ABS_G3(135),
    R_ARM_THM_BF16(136),
    R_ARM_THM_BF12(137),
    R_ARM_THM_BF18(138),
    R_ARM_IRELATIVE(160),
    R_ARM_GOTFUNCDESC(161),
    R_ARM_GOTOFFFUNCDESC(162),
    R_ARM_FUNCDESC(163),
    R_ARM_FUNCDESC_VALUE(164),
    R_ARM_TLS_GD32_FDPIC(165),
    R_ARM_TLS_LDM32_FDPIC(166),
    R_ARM_TLS_IE32_FDPIC(167);

    private final long code;

    ELFARM32Relocation(long code) {
        this.code = code;
    }

    @Override
    public long toLong() {
        return code;
    }
}

/**
 * Reference: https://github.com/riscv-non-isa/riscv-elf-psabi-doc/blob/master/riscv-elf.adoc.
 */
enum ELFRISCV64Relocation implements ELFRelocationMethod {
    R_RISCV_NONE(0),
    R_RISCV_32(1),
    R_RISCV_64(2),
    R_RISCV_RELATIVE(3),
    R_RISCV_COPY(4),
    R_RISCV_JUMP_SLOT(5),
    R_RISCV_TLS_DTPMOD32(6),
    R_RISCV_TLS_DTPMOD64(7),
    R_RISCV_TLS_DTPREL32(8),
    R_RISCV_TLS_DTPREL64(9),
    R_RISCV_TLS_TPREL32(10),
    R_RISCV_TLS_TPREL64(11),
    R_RISCV_BRANCH(16),
    R_RISCV_JAL(17),
    R_RISCV_CALL(18),
    R_RISCV_CALL_PLT(19),
    R_RISCV_GOT_HI20(20),
    R_RISCV_TLS_GOT_HI20(21),
    R_RISCV_TLS_GD_HI20(22),
    R_RISCV_PCREL_HI20(23),
    R_RISCV_PCREL_LO12_I(24),
    R_RISCV_PCREL_LO12_S(25),
    R_RISCV_HI20(26),
    R_RISCV_LO12_I(27),
    R_RISCV_LO12_S(28),
    R_RISCV_TPREL_HI20(29),
    R_RISCV_TPREL_LO12_I(30),
    R_RISCV_TPREL_LO12_S(31),
    R_RISCV_TPREL_ADD(32),
    R_RISCV_ADD8(33),
    R_RISCV_ADD16(34),
    R_RISCV_ADD32(35),
    R_RISCV_ADD64(36),
    R_RISCV_SUB8(37),
    R_RISCV_SUB16(38),
    R_RISCV_SUB32(39),
    R_RISCV_SUB64(40),
    R_RISCV_GNU_VTINHERIT(41),
    R_RISCV_GNU_VTENTRY(42),
    R_RISCV_ALIGN(43),
    R_RISCV_RVC_BRANCH(44),
    R_RISCV_RVC_JUMP(45),
    R_RISCV_RVC_LUI(46),
    R_RISCV_GPREL_I(47),
    R_RISCV_GPREL_S(48),
    R_RISCV_TPREL_I(49),
    R_RISCV_TPREL_S(50),
    R_RISCV_RELAX(51),
    R_RISCV_SUB6(52),
    R_RISCV_SET6(53),
    R_RISCV_SET8(54),
    R_RISCV_SET16(55),
    R_RISCV_SET32(56),
    R_RISCV_32_PCREL(57),
    R_RISCV_IRELATIVE(58);

    private final long code;

    ELFRISCV64Relocation(long code) {
        this.code = code;
    }

    @Override
    public long toLong() {
        return code;
    }
}
