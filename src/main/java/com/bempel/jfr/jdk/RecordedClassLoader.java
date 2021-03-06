/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

import jdk.jfr.ValueDescriptor;
import jdk.jfr.internal.Type;

import java.util.List;

/**
 * A recorded Java class loader.
 *
 * @since 8
 */
public final class RecordedClassLoader extends RecordedObject {

    static ObjectFactory<RecordedClassLoader> createFactory(Type type, TimeConverter timeConverter) {
        return new ObjectFactory<RecordedClassLoader>(type) {
            @Override
            RecordedClassLoader createTyped(List<ValueDescriptor> desc, long id, Object[] object) {
                return new RecordedClassLoader(desc, id, object, timeConverter);
            }
        };
    }

    private final long uniqueId;

    // package private
    private RecordedClassLoader(List<ValueDescriptor> descriptors, long id, Object[] values, TimeConverter timeConverter) {
        super(descriptors, values, timeConverter);
        this.uniqueId = id;
    }

    /**
     * Returns the class of the class loader.
     * <P>
     * If the bootstrap class loader is represented as {@code null} in the Java
     * Virtual Machine (JVM), then {@code null} is also the return value of this
     * method.
     *
     * @return class of the class loader, can be {@code null}
     */
    public RecordedClass getType() {
        return getTyped("type", RecordedClass.class, null);
    }

    /**
     * Returns the name of the class loader (for example, "boot", "platform", and
     * "app").
     *
     * @return the class loader name, can be {@code null}
     */
    public String getName() {
        return getTyped("name", String.class, null);
    }

    /**
     * Returns a unique ID for the class loader.
     * <p>
     * The ID might not be the same between Java Virtual Machine (JVM) instances.
     *
     * @return a unique ID
     */
    public long getId() {
        return uniqueId;
    }
}
