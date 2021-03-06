/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.bempel.jfr.jdk;

import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import jdk.jfr.internal.consumer.ChunkHeader;

import java.time.DateTimeException;
import java.time.ZoneOffset;

/**
 * Converts ticks to nanoseconds
 */
final class TimeConverter {
    private final long startTicks;
    private final long startNanos;
    private final double divisor;
    private final ZoneOffset zoneOffet;

    TimeConverter(ChunkHeader chunkHeader, int rawOffset) {
        this.startTicks = chunkHeader.getStartTicks();
        this.startNanos = chunkHeader.getStartNanos();
        this.divisor = chunkHeader.getTicksPerSecond() / 1000_000_000L;
        this.zoneOffet = zoneOfSet(rawOffset);
    }

    private ZoneOffset zoneOfSet(int rawOffset) {
        try {
            return ZoneOffset.ofTotalSeconds(rawOffset / 1000);
        } catch (DateTimeException dte) {
            Logger.log(LogTag.JFR_SYSTEM_PARSER, LogLevel.INFO, "Could not create ZoneOffset from raw offset " + rawOffset);
        }
        return ZoneOffset.UTC;
    }

    public long convertTimestamp(long ticks) {
        return startNanos + (long) ((ticks - startTicks) / divisor);
    }

    public long convertTimespan(long ticks) {
        return (long) (ticks / divisor);
    }

    public ZoneOffset getZoneOffset() {
        return zoneOffet;
    }
}
